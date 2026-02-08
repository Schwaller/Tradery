package com.tradery.news.ui;

import com.tradery.news.model.Article;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.coin.SchemaType;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Graph visualization with tripartite layout:
 * - Top row: Topics (fixed Y, spread on X)
 * - Middle row: Coins (fixed Y, spread on X)
 * - Bottom half: News articles (time on X, spring physics on Y)
 * - Connections between news and their topics/coins
 */
public class TimelineGraphPanel extends JPanel {

    private static final int MARGIN_LEFT = 60;
    private static final int MARGIN_RIGHT = 20;
    private static final int MARGIN_TOP = 30;
    private static final int MARGIN_BOTTOM = 50;
    private static final int TIMELINE_OFFSET = 25;  // Extra offset to push timeline lower

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d");

    private final List<NewsNode> newsNodes = new ArrayList<>();
    private final List<TopicNode> topicNodes = new ArrayList<>();  // Topics only
    private final List<TopicNode> coinNodes = new ArrayList<>();   // Coins only
    private final Map<String, TopicNode> topicMap = new HashMap<>();
    private final Set<String> existingArticleIds = new HashSet<>();

    private Instant minTime;
    private Instant maxTime;

    private Timer physicsTimer;
    private Object hoveredNode;  // NewsNode or TopicNode
    private Object selectedNode;
    private Object draggedNode;  // Currently being dragged
    private Consumer<NewsNode> onNodeSelected;
    private Consumer<TopicNode> onTopicSelected;

    // View settings
    private boolean showConnections = true;
    private boolean showLabels = true;
    private int maxNodes = 500;

    // Config
    private final IntelConfig config;
    private SchemaRegistry schemaRegistry;
    private Rectangle topicsLabelClickArea;  // Clickable area for topics config

    // Theme-aware color helpers
    private static Color bgColor() {
        Color c = UIManager.getColor("Panel.background");
        return c != null ? c : new Color(30, 32, 36);
    }
    private static Color gridLineColor() {
        Color bg = bgColor();
        int lum = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3;
        int offset = lum < 128 ? 20 : -20;
        return new Color(clamp(bg.getRed() + offset), clamp(bg.getGreen() + offset), clamp(bg.getBlue() + offset));
    }
    private static Color labelColor() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : new Color(220, 220, 230);
    }
    private static Color secondaryColor() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : new Color(150, 150, 160);
    }
    private static Color tooltipBg() {
        Color bg = bgColor();
        int lum = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3;
        int offset = lum < 128 ? 15 : -15;
        return new Color(clamp(bg.getRed() + offset), clamp(bg.getGreen() + offset), clamp(bg.getBlue() + offset), 240);
    }
    private static Color tooltipBorder() {
        Color bg = bgColor();
        int lum = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3;
        int offset = lum < 128 ? 40 : -40;
        return new Color(clamp(bg.getRed() + offset), clamp(bg.getGreen() + offset), clamp(bg.getBlue() + offset));
    }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    public TimelineGraphPanel() {
        this.config = IntelConfig.get();
        setPreferredSize(new Dimension(1200, 600));

        // Mouse interaction
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                startDrag(e.getX(), e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleDrag(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                stopDrag();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        // Physics simulation timer
        physicsTimer = new Timer(32, e -> {
            // Skip physics + repaint entirely when window is inactive (avoid starving other windows)
            java.awt.Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null && !w.isActive()) return;
            boolean moving = runPhysicsStep();
            repaint();
            if (!moving) physicsTimer.stop();
        });
    }

    public void setSchemaRegistry(SchemaRegistry registry) {
        this.schemaRegistry = registry;
    }

    private java.awt.Color resolveSchemaColor(TopicNode.Type type) {
        if (schemaRegistry == null) return null;
        String typeId = type == TopicNode.Type.TOPIC ? "topic" : "coin";
        SchemaType st = schemaRegistry.getType(typeId);
        return st != null ? st.color() : null;
    }

    /**
     * Set articles to display.
     */
    public void setArticles(List<Article> articles) {
        newsNodes.clear();
        topicNodes.clear();
        coinNodes.clear();
        topicMap.clear();
        existingArticleIds.clear();

        // Sort by time, limit to max
        List<Article> sorted = articles.stream()
            .filter(a -> a.publishedAt() != null)
            .sorted(Comparator.comparing(Article::publishedAt).reversed())
            .limit(maxNodes)
            .toList();

        if (sorted.isEmpty()) {
            repaint();
            return;
        }

        // Find time range
        minTime = sorted.stream().map(Article::publishedAt).min(Instant::compareTo).orElse(Instant.now());
        maxTime = sorted.stream().map(Article::publishedAt).max(Instant::compareTo).orElse(Instant.now());

        if (ChronoUnit.MINUTES.between(minTime, maxTime) < 60) {
            minTime = maxTime.minus(1, ChronoUnit.HOURS);
        }

        // Calculate article zone (topics:coins:articles = 2:3:5)
        int usableHeight = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
        int articleZoneTop = MARGIN_TOP + usableHeight * 5 / 10;  // After topics (2) + coins (3)
        int articleZoneBottom = getHeight() - MARGIN_BOTTOM;

        // Create news nodes (article zone)
        for (Article article : sorted) {
            existingArticleIds.add(article.id());
            NewsNode node = new NewsNode(article);
            node.setY(articleZoneTop + 20 + Math.random() * (articleZoneBottom - articleZoneTop - 60));
            newsNodes.add(node);

            // Create/link topic nodes (skip hidden topics)
            for (String topic : article.topics()) {
                if (config.isTopicHidden(topic)) continue;
                TopicNode topicNode = topicMap.computeIfAbsent(topic, t -> {
                    TopicNode tn = new TopicNode(t, formatTopicLabel(t), TopicNode.Type.TOPIC);
                    Color sc = resolveSchemaColor(TopicNode.Type.TOPIC);
                    if (sc != null) tn.setColor(sc);
                    topicNodes.add(tn);  // Topics go in topicNodes
                    return tn;
                });
                topicNode.addConnection(node);
                node.addTopicConnection(topicNode);
            }

            // Create/link coin nodes
            for (String coin : article.coins()) {
                String coinId = "coin:" + coin;
                TopicNode coinNode = topicMap.computeIfAbsent(coinId, c -> {
                    TopicNode cn = new TopicNode(coinId, coin, TopicNode.Type.COIN);
                    Color sc = resolveSchemaColor(TopicNode.Type.COIN);
                    if (sc != null) cn.setColor(sc);
                    coinNodes.add(cn);  // Coins go in coinNodes
                    return cn;
                });
                coinNode.addConnection(node);
                node.addTopicConnection(coinNode);
            }
        }

        // Layout: zones proportioned as topics:coins:articles = 2:3:5
        int totalHeight = getHeight() - MARGIN_BOTTOM;  // Use full height minus bottom margin
        int topicZoneTop = 20;  // Small top margin for label
        int topicZoneBottom = totalHeight * 2 / 10;
        int coinZoneTop = topicZoneBottom;
        int coinZoneBottom = totalHeight * 5 / 10;
        int topicZoneHeight = topicZoneBottom - topicZoneTop;
        int coinZoneHeight = coinZoneBottom - coinZoneTop;
        int width = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;

        // Sort by article count for better placement
        topicNodes.sort((a, b) -> Integer.compare(b.articleCount(), a.articleCount()));
        coinNodes.sort((a, b) -> Integer.compare(b.articleCount(), a.articleCount()));

        // Topics: evenly distribute rows within zone
        int topicRowCount = Math.min(3, Math.max(1, (int)Math.ceil(topicNodes.size() / 10.0)));
        int topicsPerRow = (int) Math.ceil(topicNodes.size() / (double) topicRowCount);
        int topicRowSpacing = topicZoneHeight / (topicRowCount + 1);
        for (int i = 0; i < topicNodes.size(); i++) {
            TopicNode tn = topicNodes.get(i);
            int row = i / Math.max(1, topicsPerRow);
            int indexInRow = i % Math.max(1, topicsPerRow);
            int countInRow = Math.min(topicsPerRow, topicNodes.size() - row * topicsPerRow);
            tn.setX(MARGIN_LEFT + (indexInRow + 0.5) * width / Math.max(1, countInRow));
            tn.setY(topicZoneTop + topicRowSpacing * (row + 1));
        }

        // Coins: evenly distribute rows within zone
        int coinRowCount = Math.min(4, Math.max(1, (int)Math.ceil(coinNodes.size() / 8.0)));
        int coinRowSpacing = coinZoneHeight / (coinRowCount + 1);
        int[] coinRowCounts = new int[coinRowCount];
        for (int i = 0; i < coinNodes.size(); i++) {
            coinRowCounts[i % coinRowCount]++;
        }
        int[] coinRowIndices = new int[coinRowCount];
        for (int i = 0; i < coinNodes.size(); i++) {
            TopicNode cn = coinNodes.get(i);
            int row = i % coinRowCount;
            int indexInRow = coinRowIndices[row]++;
            int countInRow = coinRowCounts[row];
            cn.setX(MARGIN_LEFT + (indexInRow + 0.5) * width / Math.max(1, countInRow));
            cn.setY(coinZoneTop + coinRowSpacing * (row + 1));
        }

        updateXPositions();
        physicsTimer.start();
        repaint();
    }

    /**
     * Add new articles without resetting existing nodes.
     * Returns the number of new articles added.
     */
    public int addArticles(List<Article> articles) {
        // Filter to only new articles
        List<Article> newArticles = articles.stream()
            .filter(a -> a.publishedAt() != null)
            .filter(a -> !existingArticleIds.contains(a.id()))
            .sorted(Comparator.comparing(Article::publishedAt).reversed())
            .toList();

        if (newArticles.isEmpty()) {
            return 0;
        }

        // Calculate article zone (topics:coins:articles = 2:3:5)
        int usableHeight = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
        int articleZoneTop = MARGIN_TOP + usableHeight * 5 / 10;
        int articleZoneBottom = getHeight() - MARGIN_BOTTOM;

        // Update time range if needed
        for (Article article : newArticles) {
            Instant pubTime = article.publishedAt();
            if (minTime == null || pubTime.isBefore(minTime)) minTime = pubTime;
            if (maxTime == null || pubTime.isAfter(maxTime)) maxTime = pubTime;
        }

        // Add new news nodes
        List<NewsNode> addedNodes = new ArrayList<>();
        for (Article article : newArticles) {
            existingArticleIds.add(article.id());
            NewsNode node = new NewsNode(article);
            node.setY(articleZoneTop + 20 + Math.random() * (articleZoneBottom - articleZoneTop - 60));
            newsNodes.add(node);
            addedNodes.add(node);

            // Create/link topic nodes (skip hidden topics)
            for (String topic : article.topics()) {
                if (config.isTopicHidden(topic)) continue;
                TopicNode topicNode = topicMap.computeIfAbsent(topic, t -> {
                    TopicNode tn = new TopicNode(t, formatTopicLabel(t), TopicNode.Type.TOPIC);
                    Color sc = resolveSchemaColor(TopicNode.Type.TOPIC);
                    if (sc != null) tn.setColor(sc);
                    topicNodes.add(tn);
                    return tn;
                });
                topicNode.addConnection(node);
                node.addTopicConnection(topicNode);
            }

            // Create/link coin nodes
            for (String coin : article.coins()) {
                String coinId = "coin:" + coin;
                TopicNode coinNode = topicMap.computeIfAbsent(coinId, c -> {
                    TopicNode cn = new TopicNode(coinId, coin, TopicNode.Type.COIN);
                    Color sc = resolveSchemaColor(TopicNode.Type.COIN);
                    if (sc != null) cn.setColor(sc);
                    coinNodes.add(cn);
                    return cn;
                });
                coinNode.addConnection(node);
                node.addTopicConnection(coinNode);
            }
        }

        // Re-layout topics and coins (they may have new ones)
        relayoutTopicsAndCoins();
        updateXPositions();
        repaint();

        return addedNodes.size();
    }

    /**
     * Re-layout topic and coin nodes without changing news node positions.
     */
    private void relayoutTopicsAndCoins() {
        // Layout: zones proportioned as topics:coins:articles = 2:3:5
        int totalHeight = getHeight() - MARGIN_BOTTOM;
        int topicZoneTop = 20;
        int topicZoneBottom = totalHeight * 2 / 10;
        int coinZoneTop = topicZoneBottom;
        int coinZoneBottom = totalHeight * 5 / 10;
        int topicZoneHeight = topicZoneBottom - topicZoneTop;
        int coinZoneHeight = coinZoneBottom - coinZoneTop;
        int width = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;

        // Sort by article count
        topicNodes.sort((a, b) -> Integer.compare(b.articleCount(), a.articleCount()));
        coinNodes.sort((a, b) -> Integer.compare(b.articleCount(), a.articleCount()));

        // Topics: evenly distribute rows within zone
        int topicRowCount = Math.min(3, Math.max(1, (int)Math.ceil(topicNodes.size() / 10.0)));
        int topicsPerRow = (int) Math.ceil(topicNodes.size() / (double) topicRowCount);
        int topicRowSpacing = topicZoneHeight / (topicRowCount + 1);
        for (int i = 0; i < topicNodes.size(); i++) {
            TopicNode tn = topicNodes.get(i);
            int row = i / Math.max(1, topicsPerRow);
            int indexInRow = i % Math.max(1, topicsPerRow);
            int countInRow = Math.min(topicsPerRow, topicNodes.size() - row * topicsPerRow);
            tn.setX(MARGIN_LEFT + (indexInRow + 0.5) * width / Math.max(1, countInRow));
            tn.setY(topicZoneTop + topicRowSpacing * (row + 1));
        }

        // Coins: evenly distribute rows within zone
        int coinRowCount = Math.min(4, Math.max(1, (int)Math.ceil(coinNodes.size() / 8.0)));
        int coinRowSpacing = coinZoneHeight / (coinRowCount + 1);
        int[] coinRowCounts = new int[coinRowCount];
        for (int i = 0; i < coinNodes.size(); i++) {
            coinRowCounts[i % coinRowCount]++;
        }
        int[] coinRowIndices = new int[coinRowCount];
        for (int i = 0; i < coinNodes.size(); i++) {
            TopicNode cn = coinNodes.get(i);
            int row = i % coinRowCount;
            int indexInRow = coinRowIndices[row]++;
            int countInRow = coinRowCounts[row];
            cn.setX(MARGIN_LEFT + (indexInRow + 0.5) * width / Math.max(1, countInRow));
            cn.setY(coinZoneTop + coinRowSpacing * (row + 1));
        }
    }

    private String formatTopicLabel(String topic) {
        // "crypto.regulation" -> "regulation"
        int dot = topic.lastIndexOf('.');
        if (dot >= 0) return topic.substring(dot + 1);
        return topic;
    }

    /**
     * Update X positions for news nodes based on time.
     */
    private void updateXPositions() {
        if (newsNodes.isEmpty() || minTime == null || maxTime == null) return;

        int width = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
        long range = maxTime.toEpochMilli() - minTime.toEpochMilli();
        if (range == 0) range = 1;

        for (NewsNode node : newsNodes) {
            long t = node.publishedAt().toEpochMilli() - minTime.toEpochMilli();
            double ratio = (double) t / range;
            node.setX(MARGIN_LEFT + ratio * width);
        }
    }

    /**
     * Run one physics simulation step.
     */
    private boolean runPhysicsStep() {
        if (newsNodes.isEmpty()) return false;

        double damping = 0.85;
        double repulsion = 400;
        double topicAttraction = 0.02;
        double minVelocity = 0.1;
        boolean anyMoving = false;

        // Calculate article zone (topics:coins:articles = 2:3:5)
        int usableHeight = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
        int articleZoneTop = MARGIN_TOP + usableHeight * 5 / 10 + 20;
        int articleZoneBottom = getHeight() - MARGIN_BOTTOM - 10;
        double newsCenterY = (articleZoneTop + articleZoneBottom) / 2.0;

        for (NewsNode node : newsNodes) {
            // Skip physics for dragged node
            if (node == draggedNode) continue;

            double fy = 0;

            // Repulsion from other news nodes
            for (NewsNode other : newsNodes) {
                if (other == node) continue;
                double dy = node.y() - other.y();
                double dx = node.x() - other.x();
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 1) dist = 1;
                if (dist < 80) {
                    fy += (dy / dist) * repulsion / (dist * dist);
                }
            }

            // Weak pull toward center of article zone
            fy += (newsCenterY - node.y()) * 0.001;

            double vy = (node.vy() + fy) * damping;
            if (Math.abs(vy) < minVelocity) vy = 0;
            node.setVy(vy);
            if (vy != 0) {
                node.setY(node.y() + vy);
                node.setY(Math.max(articleZoneTop, Math.min(articleZoneBottom, node.y())));
                anyMoving = true;
            }
        }

        // Gentle X-axis physics for topics (stay on fixed Y line)
        int leftBound = MARGIN_LEFT + 30;
        int rightBound = getWidth() - MARGIN_RIGHT - 30;
        double rowDamping = 0.92;  // Higher damping for smoother movement
        double strongRepulsion = repulsion * 2.0;  // Stronger repulsion to spread out
        double weakAttraction = topicAttraction * 0.3;  // Weaker attraction to news

        anyMoving |= applyRowPhysics(topicNodes, leftBound, rightBound, strongRepulsion, weakAttraction, rowDamping);
        anyMoving |= applyRowPhysics(coinNodes, leftBound, rightBound, strongRepulsion, weakAttraction, rowDamping);
        return anyMoving;
    }

    /**
     * Apply gentle horizontal physics to a row of nodes (topics or coins).
     * Nodes repel each other and are attracted to the average X of their connected articles.
     */
    private boolean applyRowPhysics(List<TopicNode> nodes, int leftBound, int rightBound,
                                  double repulsion, double attraction, double damping) {
        double maxSpeed = 1.5;
        boolean anyMoving = false;

        for (TopicNode node : nodes) {
            if (node == draggedNode) continue;

            double fx = 0;

            for (TopicNode other : nodes) {
                if (other == node) continue;
                if (Math.abs(node.y() - other.y()) > 10) continue;

                double dx = node.x() - other.x();
                double dist = Math.abs(dx);
                if (dist < 1) dist = 1;
                if (dist < 180) {
                    fx += Math.signum(dx) * repulsion / (dist * dist);
                }
            }

            if (!node.connections().isEmpty()) {
                double avgX = node.connections().stream()
                    .mapToDouble(NewsNode::x)
                    .average()
                    .orElse(node.x());
                fx += (avgX - node.x()) * attraction;
            }

            double vx = (node.vx() + fx * 0.3) * damping;

            if (vx > maxSpeed) vx = maxSpeed;
            if (vx < -maxSpeed) vx = -maxSpeed;
            if (Math.abs(vx) < 0.05) vx = 0;

            node.setVx(vx);
            if (vx != 0) {
                node.setX(node.x() + vx);
                node.setX(Math.max(leftBound, Math.min(rightBound, node.x())));
                anyMoving = true;
            }
        }
        return anyMoving;
    }

    @Override
    protected void paintComponent(Graphics g) {
        setBackground(bgColor());
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        updateXPositions();

        // Calculate zone boundaries (topics:coins:articles = 2:3:5)
        int totalHeight = getHeight() - MARGIN_BOTTOM;
        int topicZoneTop = 20;
        int coinZoneTop = totalHeight * 2 / 10;
        int articleZoneTop = totalHeight * 5 / 10;
        int timelineY = getHeight() - MARGIN_BOTTOM + TIMELINE_OFFSET;

        // Draw full-width separator lines
        g2.setColor(gridLineColor());
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(0, coinZoneTop, getWidth(), coinZoneTop);       // Between topics and coins
        g2.drawLine(0, articleZoneTop, getWidth(), articleZoneTop); // Between coins and articles
        g2.drawLine(0, timelineY, getWidth(), timelineY);           // Timeline separator

        // Labels for each zone (8px from zone top + font ascent)
        g2.setColor(secondaryColor());
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        int labelOffset = 8 + g2.getFontMetrics().getAscent();

        // Topics label with clickable triangle
        String topicsLabel = "TOPICS";
        g2.drawString(topicsLabel, MARGIN_LEFT, labelOffset);
        int topicsLabelWidth = g2.getFontMetrics().stringWidth(topicsLabel);
        int triangleX = MARGIN_LEFT + topicsLabelWidth + 6;
        int triangleY = labelOffset - 6;
        // Draw triangle (pointing down)
        int[] xPoints = {triangleX, triangleX + 8, triangleX + 4};
        int[] yPoints = {triangleY, triangleY, triangleY + 5};
        g2.setColor(secondaryColor());
        g2.fillPolygon(xPoints, yPoints, 3);
        // Store clickable area
        topicsLabelClickArea = new Rectangle(MARGIN_LEFT, 0, topicsLabelWidth + 20, labelOffset + 4);

        g2.setColor(secondaryColor());
        g2.drawString("COINS", MARGIN_LEFT, coinZoneTop + labelOffset);
        g2.drawString("NEWS ARTICLES", MARGIN_LEFT, articleZoneTop + labelOffset);

        // Draw time axis
        drawTimeAxis(g2);

        // Draw connections
        if (showConnections) {
            drawConnections(g2);
        }

        // Draw topic nodes (upper half)
        drawTopicNodes(g2);

        // Draw news nodes (lower half)
        drawNewsNodes(g2);

        // Draw hover tooltip
        drawTooltip(g2);
    }

    private void drawTimeAxis(Graphics2D g2) {
        if (minTime == null || maxTime == null) return;

        int y = getHeight() - MARGIN_BOTTOM + TIMELINE_OFFSET + 15;
        int width = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;

        g2.setColor(gridLineColor());
        g2.drawLine(MARGIN_LEFT, y - 15, getWidth() - MARGIN_RIGHT, y - 15);

        g2.setColor(secondaryColor());
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));

        long range = maxTime.toEpochMilli() - minTime.toEpochMilli();
        int numLabels = Math.min(10, width / 100);

        for (int i = 0; i <= numLabels; i++) {
            double ratio = (double) i / numLabels;
            int x = MARGIN_LEFT + (int)(ratio * width);
            Instant time = Instant.ofEpochMilli(minTime.toEpochMilli() + (long)(ratio * range));
            LocalDateTime ldt = LocalDateTime.ofInstant(time, ZoneId.systemDefault());

            String label = (range < 24 * 60 * 60 * 1000)
                ? ldt.format(TIME_FMT)
                : ldt.format(DATE_FMT) + " " + ldt.format(TIME_FMT);

            g2.drawLine(x, y - 20, x, y - 10);
            g2.drawString(label, x - 25, y);
        }
    }

    private void drawConnections(Graphics2D g2) {
        // Draw connections from topics
        for (TopicNode topic : topicNodes) {
            drawNodeConnections(g2, topic);
        }

        // Draw connections from coins
        for (TopicNode coin : coinNodes) {
            drawNodeConnections(g2, coin);
        }
    }

    private void drawNodeConnections(Graphics2D g2, TopicNode node) {
        for (NewsNode news : node.connections()) {
            boolean highlight = node.isHovered() || node.isSelected() ||
                               news.isHovered() || news.isSelected();

            int alpha = highlight ? 180 : 25;
            Color c = node.getColor();
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            g2.setStroke(new BasicStroke(highlight ? 2.0f : 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Shorten line to stop at node edges + gap
            double gap = 4;
            double dx = news.x() - node.x();
            double dy = news.y() - node.y();
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
                double nx = dx / dist;
                double ny = dy / dist;
                double startOffset = node.getRadius() + gap;
                double endOffset = news.getRadius() + gap;
                double x1 = node.x() + nx * startOffset;
                double y1 = node.y() + ny * startOffset;
                double x2 = news.x() - nx * endOffset;
                double y2 = news.y() - ny * endOffset;
                g2.draw(new Line2D.Double(x1, y1, x2, y2));
            }
        }
    }

    private void drawTopicNodes(Graphics2D g2) {
        // Draw topics (upper row)
        for (TopicNode node : topicNodes) {
            drawSingleTopicNode(g2, node);
        }
        // Draw coins (middle row)
        for (TopicNode node : coinNodes) {
            drawSingleTopicNode(g2, node);
        }
    }

    private void drawSingleTopicNode(Graphics2D g2, TopicNode node) {
        int r = node.getRadius();
        Color c = node.getColor();
        boolean highlight = node.isHovered() || node.isSelected();

        // Glow for highlight
        if (highlight) {
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
            g2.fillOval((int)node.x() - r - 5, (int)node.y() - r - 5, (r + 5) * 2, (r + 5) * 2);
        }

        // Node
        g2.setColor(c);
        g2.fillOval((int)node.x() - r, (int)node.y() - r, r * 2, r * 2);
        g2.setColor(c.darker());
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval((int)node.x() - r, (int)node.y() - r, r * 2, r * 2);

        // Label
        if (showLabels) {
            g2.setColor(labelColor());
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            String label = node.label();
            int labelWidth = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, (int)node.x() - labelWidth / 2, (int)node.y() + r + 14);
        }
    }

    private void drawNewsNodes(Graphics2D g2) {
        for (NewsNode node : newsNodes) {
            int r = node.getRadius();
            Color c = node.getColor();
            boolean highlight = node.isHovered() || node.isSelected();

            // Glow for highlight
            if (highlight) {
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
                g2.fillOval((int)node.x() - r - 4, (int)node.y() - r - 4, (r + 4) * 2, (r + 4) * 2);
            }

            // Node
            g2.setColor(c);
            g2.fillOval((int)node.x() - r, (int)node.y() - r, r * 2, r * 2);
            g2.setColor(c.darker());
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval((int)node.x() - r, (int)node.y() - r, r * 2, r * 2);
        }
    }

    private void drawTooltip(Graphics2D g2) {
        if (hoveredNode == null) return;

        java.util.List<String> linesList = new java.util.ArrayList<>();
        double nodeX, nodeY;

        int padding = 8;
        int lineHeight = 16;
        int width = 320;
        int maxCharsPerLine = 48;

        if (hoveredNode instanceof NewsNode news) {
            nodeX = news.x();
            nodeY = news.y();

            // Wrap title to up to 3 lines
            String title = news.title();
            if (title.length() <= maxCharsPerLine) {
                linesList.add(title);
            } else {
                int maxTitleLines = 3;
                int pos = 0;
                while (pos < title.length() && linesList.size() < maxTitleLines) {
                    int end = Math.min(pos + maxCharsPerLine, title.length());
                    // Find a good break point (space) if not at end
                    if (end < title.length() && linesList.size() < maxTitleLines - 1) {
                        int spacePos = title.lastIndexOf(' ', end);
                        if (spacePos > pos + maxCharsPerLine / 2) {
                            end = spacePos;
                        }
                    }
                    String line = title.substring(pos, end).trim();
                    // Add ellipsis if this is the last line and there's more text
                    if (linesList.size() == maxTitleLines - 1 && end < title.length()) {
                        line = line + "...";
                    }
                    linesList.add(line);
                    pos = end;
                    if (pos < title.length() && title.charAt(pos) == ' ') pos++;
                }
            }

            String sentimentStr = formatSentiment(news.sentiment());
            linesList.add(news.source() + " • " + formatTime(news.publishedAt()));
            linesList.add("Importance: " + news.importance() + "  Sentiment: " + sentimentStr);
            linesList.add("Topics: " + String.join(", ", news.topics()));
            linesList.add("Coins: " + (news.coins().isEmpty() ? "-" : String.join(", ", news.coins())));
        } else if (hoveredNode instanceof TopicNode topic) {
            nodeX = topic.x();
            nodeY = topic.y();
            if (topic.type() == TopicNode.Type.COIN) {
                linesList.add("Coin: " + topic.label());
                linesList.add(topic.articleCount() + " articles");
            } else {
                String fullPath = topic.id();
                linesList.add("Topic: " + topic.label());
                linesList.add("Path: " + fullPath);
                linesList.add(topic.articleCount() + " articles");
            }
        } else {
            return;
        }

        int height = linesList.size() * lineHeight + padding * 2;

        int x = (int)nodeX + 15;
        int y = (int)nodeY - height / 2;

        if (x + width > getWidth() - 10) x = (int)nodeX - width - 15;
        if (y < 10) y = 10;
        if (y + height > getHeight() - 10) y = getHeight() - height - 10;

        g2.setColor(tooltipBg());
        g2.fillRoundRect(x, y, width, height, 8, 8);
        g2.setColor(tooltipBorder());
        g2.drawRoundRect(x, y, width, height, 8, 8);

        g2.setColor(labelColor());
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (int i = 0; i < linesList.size(); i++) {
            g2.drawString(truncate(linesList.get(i), maxCharsPerLine), x + padding, y + padding + (i + 1) * lineHeight - 4);
        }
    }

    private void updateHover(int mx, int my) {
        Object newHover = null;

        // Check topic nodes (upper row)
        for (TopicNode node : topicNodes) {
            if (node.contains(mx, my)) {
                newHover = node;
                break;
            }
        }

        // Check coin nodes (middle row)
        if (newHover == null) {
            for (TopicNode node : coinNodes) {
                if (node.contains(mx, my)) {
                    newHover = node;
                    break;
                }
            }
        }

        // Then check news nodes
        if (newHover == null) {
            for (NewsNode node : newsNodes) {
                if (node.contains(mx, my)) {
                    newHover = node;
                    break;
                }
            }
        }

        if (newHover != hoveredNode) {
            if (hoveredNode instanceof NewsNode n) n.setHovered(false);
            if (hoveredNode instanceof TopicNode t) t.setHovered(false);

            hoveredNode = newHover;

            if (hoveredNode instanceof NewsNode n) n.setHovered(true);
            if (hoveredNode instanceof TopicNode t) t.setHovered(true);

            setCursor(hoveredNode != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            repaint();
        }
    }

    private void handleClick(int mx, int my) {
        // Check for topics label click (config button)
        if (topicsLabelClickArea != null && topicsLabelClickArea.contains(mx, my)) {
            showTopicsConfigDialog();
            return;
        }

        if (selectedNode instanceof NewsNode n) n.setSelected(false);
        if (selectedNode instanceof TopicNode t) t.setSelected(false);

        selectedNode = null;

        // Check topics
        for (TopicNode node : topicNodes) {
            if (node.contains(mx, my)) {
                selectedNode = node;
                node.setSelected(true);
                if (onTopicSelected != null) {
                    onTopicSelected.accept(node);
                }
                break;
            }
        }

        // Check coins
        if (selectedNode == null) {
            for (TopicNode node : coinNodes) {
                if (node.contains(mx, my)) {
                    selectedNode = node;
                    node.setSelected(true);
                    if (onTopicSelected != null) {
                        onTopicSelected.accept(node);
                    }
                    break;
                }
            }
        }

        // Check news
        if (selectedNode == null) {
            for (NewsNode node : newsNodes) {
                if (node.contains(mx, my)) {
                    selectedNode = node;
                    node.setSelected(true);
                    if (onNodeSelected != null) {
                        onNodeSelected.accept(node);
                    }
                    break;
                }
            }
        }

        repaint();
    }

    private void startDrag(int mx, int my) {
        // Find node under cursor
        for (TopicNode node : topicNodes) {
            if (node.contains(mx, my)) {
                draggedNode = node;
                if (!physicsTimer.isRunning()) physicsTimer.start();
                return;
            }
        }
        for (TopicNode node : coinNodes) {
            if (node.contains(mx, my)) {
                draggedNode = node;
                if (!physicsTimer.isRunning()) physicsTimer.start();
                return;
            }
        }
        for (NewsNode node : newsNodes) {
            if (node.contains(mx, my)) {
                draggedNode = node;
                if (!physicsTimer.isRunning()) physicsTimer.start();
                return;
            }
        }
    }

    private void handleDrag(int mx, int my) {
        if (draggedNode == null) return;

        int leftBound = MARGIN_LEFT + 30;
        int rightBound = getWidth() - MARGIN_RIGHT - 30;

        if (draggedNode instanceof TopicNode topic) {
            // Topics/coins drag on X axis only (Y is fixed)
            double newX = Math.max(leftBound, Math.min(rightBound, mx));
            topic.setX(newX);
            topic.setVx(0);  // Stop physics momentum
        } else if (draggedNode instanceof NewsNode news) {
            // News nodes drag on Y axis only (X is time-locked)
            int usableHeight = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
            int articleZoneTop = MARGIN_TOP + usableHeight * 5 / 10 + 20;
            int articleZoneBottom = getHeight() - MARGIN_BOTTOM - 10;
            double newY = Math.max(articleZoneTop, Math.min(articleZoneBottom, my));
            news.setY(newY);
            news.setVy(0);  // Stop physics momentum
        }
        repaint();
    }

    private void stopDrag() {
        draggedNode = null;
    }

    public void setOnNodeSelected(Consumer<NewsNode> callback) {
        this.onNodeSelected = callback;
    }

    public void setOnTopicSelected(Consumer<TopicNode> callback) {
        this.onTopicSelected = callback;
    }

    public void setShowConnections(boolean show) {
        this.showConnections = show;
        repaint();
    }

    public void setShowLabels(boolean show) {
        this.showLabels = show;
        repaint();
    }

    public void setMaxNodes(int max) {
        this.maxNodes = max;
    }

    public void stopPhysics() {
        if (physicsTimer != null) physicsTimer.stop();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String formatSentiment(double sentiment) {
        int percent = (int) Math.round(sentiment * 100);
        if (percent > 0) {
            return "+" + percent + "%";
        } else if (percent < 0) {
            return percent + "%";
        } else {
            return "0%";
        }
    }

    private String formatTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d HH:mm"));
    }

    /**
     * Show dialog to configure topic visibility.
     */
    private void showTopicsConfigDialog() {
        // Collect all known topics
        Set<String> allTopics = new TreeSet<>();
        for (NewsNode node : newsNodes) {
            allTopics.addAll(node.topics());
        }
        // Also add currently visible topics
        for (TopicNode tn : topicNodes) {
            allTopics.add(tn.id());
        }
        // Add hidden topics from config
        allTopics.addAll(config.getHiddenTopics());

        if (allTopics.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No topics found", "Topics", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Create dialog
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Configure Topics", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(300, 400);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("Select topics to show:");
        panel.add(label, BorderLayout.NORTH);

        // Checkbox list
        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));

        Map<String, JCheckBox> checkboxes = new LinkedHashMap<>();
        for (String topic : allTopics) {
            JCheckBox cb = new JCheckBox(formatTopicLabel(topic), !config.isTopicHidden(topic));
            checkboxes.put(topic, cb);
            checkboxPanel.add(cb);
        }

        JScrollPane scroll = new JScrollPane(checkboxPanel);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton showAllBtn = new JButton("Show All");
        showAllBtn.addActionListener(e -> checkboxes.values().forEach(cb -> cb.setSelected(true)));
        buttonPanel.add(showAllBtn);

        JButton hideAllBtn = new JButton("Hide All");
        hideAllBtn.addActionListener(e -> checkboxes.values().forEach(cb -> cb.setSelected(false)));
        buttonPanel.add(hideAllBtn);

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            // Update config
            for (var entry : checkboxes.entrySet()) {
                config.setTopicHidden(entry.getKey(), !entry.getValue().isSelected());
            }
            config.save();
            dialog.dispose();
            // Rebuild topics to apply filter
            rebuildTopics();
        });
        buttonPanel.add(okBtn);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(cancelBtn);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    /**
     * Rebuild topic nodes after config change.
     */
    private void rebuildTopics() {
        // Clear and rebuild only topics (not coins)
        List<TopicNode> toRemove = new ArrayList<>();
        for (TopicNode tn : topicNodes) {
            if (config.isTopicHidden(tn.id())) {
                toRemove.add(tn);
                topicMap.remove(tn.id());
            }
        }
        topicNodes.removeAll(toRemove);

        // Re-add any topics that are no longer hidden
        for (NewsNode news : newsNodes) {
            for (String topic : news.topics()) {
                if (!config.isTopicHidden(topic) && !topicMap.containsKey(topic)) {
                    TopicNode tn = new TopicNode(topic, formatTopicLabel(topic), TopicNode.Type.TOPIC);
                    Color sc = resolveSchemaColor(TopicNode.Type.TOPIC);
                    if (sc != null) tn.setColor(sc);
                    topicNodes.add(tn);
                    topicMap.put(topic, tn);
                }
                // Update connections
                TopicNode topicNode = topicMap.get(topic);
                if (topicNode != null) {
                    topicNode.addConnection(news);
                }
            }
        }

        relayoutTopicsAndCoins();
        repaint();
    }
}
