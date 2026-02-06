package com.tradery.news.ui;

import com.tradery.news.model.Article;

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


    public TimelineGraphPanel() {
        setBackground(new Color(30, 32, 36));
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
        physicsTimer = new Timer(16, e -> {
            runPhysicsStep();
            repaint();
        });
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

            // Create/link topic nodes
            for (String topic : article.topics()) {
                TopicNode topicNode = topicMap.computeIfAbsent(topic, t -> {
                    TopicNode tn = new TopicNode(t, formatTopicLabel(t), TopicNode.Type.TOPIC);
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
                    coinNodes.add(cn);  // Coins go in coinNodes
                    return cn;
                });
                coinNode.addConnection(node);
                node.addTopicConnection(coinNode);
            }
        }

        // Layout: zones proportioned as topics:coins:articles = 2:3:5
        int topicZoneHeight = usableHeight * 2 / 10;
        int coinZoneHeight = usableHeight * 3 / 10;
        int topicZoneTop = MARGIN_TOP;
        int coinZoneTop = MARGIN_TOP + topicZoneHeight;
        int width = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;

        // Sort by article count for better placement
        topicNodes.sort((a, b) -> Integer.compare(b.articleCount(), a.articleCount()));
        coinNodes.sort((a, b) -> Integer.compare(b.articleCount(), a.articleCount()));

        // Dynamic row count based on node count (max 5 rows for topics, 4 for coins)
        int topicRowCount = Math.min(5, Math.max(1, (int)Math.ceil(topicNodes.size() / 8.0)));
        int topicsPerRow = (int) Math.ceil(topicNodes.size() / (double) topicRowCount);
        for (int i = 0; i < topicNodes.size(); i++) {
            TopicNode tn = topicNodes.get(i);
            int row = i / Math.max(1, topicsPerRow);
            int indexInRow = i % Math.max(1, topicsPerRow);
            int countInRow = Math.min(topicsPerRow, topicNodes.size() - row * topicsPerRow);
            tn.setX(MARGIN_LEFT + (indexInRow + 0.5) * width / Math.max(1, countInRow));
            // Evenly space rows within zone (with padding from edges)
            int rowY = topicZoneTop + 20 + (topicZoneHeight - 40) * row / Math.max(1, topicRowCount - 1);
            if (topicRowCount == 1) rowY = topicZoneTop + topicZoneHeight / 2;
            tn.setY(rowY);
        }

        // Dynamic row count for coins (max 4 rows)
        int coinRowCount = Math.min(4, Math.max(1, (int)Math.ceil(coinNodes.size() / 6.0)));
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
            // Evenly space rows within zone (with padding from edges)
            int rowY = coinZoneTop + 20 + (coinZoneHeight - 40) * row / Math.max(1, coinRowCount - 1);
            if (coinRowCount == 1) rowY = coinZoneTop + coinZoneHeight / 2;
            cn.setY(rowY);
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

            // Create/link topic nodes
            for (String topic : article.topics()) {
                TopicNode topicNode = topicMap.computeIfAbsent(topic, t -> {
                    TopicNode tn = new TopicNode(t, formatTopicLabel(t), TopicNode.Type.TOPIC);
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
        int usableHeight = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
        int topicZoneHeight = usableHeight * 2 / 10;
        int coinZoneHeight = usableHeight * 3 / 10;
        int topicZoneTop = MARGIN_TOP;
        int coinZoneTop = MARGIN_TOP + topicZoneHeight;
        int width = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;

        // Sort by article count
        topicNodes.sort((a, b) -> Integer.compare(b.articleCount(), a.articleCount()));
        coinNodes.sort((a, b) -> Integer.compare(b.articleCount(), a.articleCount()));

        // Dynamic row count for topics
        int topicRowCount = Math.min(5, Math.max(1, (int)Math.ceil(topicNodes.size() / 8.0)));
        int topicsPerRow = (int) Math.ceil(topicNodes.size() / (double) topicRowCount);
        for (int i = 0; i < topicNodes.size(); i++) {
            TopicNode tn = topicNodes.get(i);
            int row = i / Math.max(1, topicsPerRow);
            int indexInRow = i % Math.max(1, topicsPerRow);
            int countInRow = Math.min(topicsPerRow, topicNodes.size() - row * topicsPerRow);
            tn.setX(MARGIN_LEFT + (indexInRow + 0.5) * width / Math.max(1, countInRow));
            int rowY = topicZoneTop + 20 + (topicZoneHeight - 40) * row / Math.max(1, topicRowCount - 1);
            if (topicRowCount == 1) rowY = topicZoneTop + topicZoneHeight / 2;
            tn.setY(rowY);
        }

        // Dynamic row count for coins
        int coinRowCount = Math.min(4, Math.max(1, (int)Math.ceil(coinNodes.size() / 6.0)));
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
            int rowY = coinZoneTop + 20 + (coinZoneHeight - 40) * row / Math.max(1, coinRowCount - 1);
            if (coinRowCount == 1) rowY = coinZoneTop + coinZoneHeight / 2;
            cn.setY(rowY);
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
    private void runPhysicsStep() {
        if (newsNodes.isEmpty()) return;

        double damping = 0.85;
        double repulsion = 400;
        double topicAttraction = 0.02;

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

            node.setVy((node.vy() + fy) * damping);
            node.setY(node.y() + node.vy());
            node.setY(Math.max(articleZoneTop, Math.min(articleZoneBottom, node.y())));
        }

        // Gentle X-axis physics for topics (stay on fixed Y line)
        int leftBound = MARGIN_LEFT + 30;
        int rightBound = getWidth() - MARGIN_RIGHT - 30;
        double rowDamping = 0.92;  // Higher damping for smoother movement
        double strongRepulsion = repulsion * 2.0;  // Stronger repulsion to spread out
        double weakAttraction = topicAttraction * 0.3;  // Weaker attraction to news

        applyRowPhysics(topicNodes, leftBound, rightBound, strongRepulsion, weakAttraction, rowDamping);
        applyRowPhysics(coinNodes, leftBound, rightBound, strongRepulsion, weakAttraction, rowDamping);
    }

    /**
     * Apply gentle horizontal physics to a row of nodes (topics or coins).
     * Nodes repel each other and are attracted to the average X of their connected articles.
     */
    private void applyRowPhysics(List<TopicNode> nodes, int leftBound, int rightBound,
                                  double repulsion, double attraction, double damping) {
        double maxSpeed = 1.5;  // Limit max velocity for smooth movement

        for (TopicNode node : nodes) {
            // Skip physics for dragged node
            if (node == draggedNode) continue;

            double fx = 0;

            // Repulsion from other nodes on the same Y level (same row)
            for (TopicNode other : nodes) {
                if (other == node) continue;
                // Only repel nodes on the same row
                if (Math.abs(node.y() - other.y()) > 10) continue;

                double dx = node.x() - other.x();
                double dist = Math.abs(dx);
                if (dist < 1) dist = 1;
                if (dist < 180) {  // Larger repulsion range
                    fx += Math.signum(dx) * repulsion / (dist * dist);
                }
            }

            // Gentle attraction toward average X of connected news nodes
            if (!node.connections().isEmpty()) {
                double avgX = node.connections().stream()
                    .mapToDouble(NewsNode::x)
                    .average()
                    .orElse(node.x());
                fx += (avgX - node.x()) * attraction;
            }

            double vx = (node.vx() + fx * 0.3) * damping;  // Scale down force impact

            // Clamp velocity
            if (vx > maxSpeed) vx = maxSpeed;
            if (vx < -maxSpeed) vx = -maxSpeed;

            // Stop jitter when nearly stationary
            if (Math.abs(vx) < 0.05) vx = 0;

            node.setVx(vx);
            node.setX(node.x() + node.vx());
            node.setX(Math.max(leftBound, Math.min(rightBound, node.x())));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        updateXPositions();

        // Calculate zone boundaries (topics:coins:articles = 2:3:5)
        int usableHeight = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
        int topicZoneHeight = usableHeight * 2 / 10;
        int coinZoneHeight = usableHeight * 3 / 10;
        int topicZoneTop = MARGIN_TOP;
        int coinZoneTop = MARGIN_TOP + topicZoneHeight;
        int articleZoneTop = coinZoneTop + coinZoneHeight;
        int timelineY = getHeight() - MARGIN_BOTTOM;

        // Draw full-width separator lines
        g2.setColor(new Color(50, 52, 56));
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(0, coinZoneTop, getWidth(), coinZoneTop);       // Between topics and coins
        g2.drawLine(0, articleZoneTop, getWidth(), articleZoneTop); // Between coins and articles
        g2.drawLine(0, timelineY, getWidth(), timelineY);           // Timeline separator

        // Labels for each zone (8px from zone top + font ascent)
        g2.setColor(new Color(100, 100, 110));
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        int labelOffset = 8 + g2.getFontMetrics().getAscent();
        g2.drawString("TOPICS", MARGIN_LEFT, topicZoneTop + labelOffset);
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

        int y = getHeight() - MARGIN_BOTTOM + 15;
        int width = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;

        g2.setColor(new Color(50, 52, 56));
        g2.drawLine(MARGIN_LEFT, y - 15, getWidth() - MARGIN_RIGHT, y - 15);

        g2.setColor(new Color(120, 120, 130));
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
            g2.setColor(new Color(200, 200, 210));
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

        String[] lines;
        double nodeX, nodeY;

        if (hoveredNode instanceof NewsNode news) {
            nodeX = news.x();
            nodeY = news.y();
            String sentimentStr = formatSentiment(news.sentiment());
            lines = new String[]{
                news.title(),
                news.source() + " • " + formatTime(news.publishedAt()),
                "Importance: " + news.importance() + "  Sentiment: " + sentimentStr,
                "Topics: " + String.join(", ", news.topics()),
                "Coins: " + (news.coins().isEmpty() ? "-" : String.join(", ", news.coins()))
            };
        } else if (hoveredNode instanceof TopicNode topic) {
            nodeX = topic.x();
            nodeY = topic.y();
            if (topic.type() == TopicNode.Type.COIN) {
                lines = new String[]{
                    "Coin: " + topic.label(),
                    topic.articleCount() + " articles"
                };
            } else {
                // Show full topic path (id) and short label
                String fullPath = topic.id();
                lines = new String[]{
                    "Topic: " + topic.label(),
                    "Path: " + fullPath,
                    topic.articleCount() + " articles"
                };
            }
        } else {
            return;
        }

        int padding = 8;
        int lineHeight = 16;
        int width = 320;
        int height = lines.length * lineHeight + padding * 2;

        int x = (int)nodeX + 15;
        int y = (int)nodeY - height / 2;

        if (x + width > getWidth() - 10) x = (int)nodeX - width - 15;
        if (y < 10) y = 10;
        if (y + height > getHeight() - 10) y = getHeight() - height - 10;

        g2.setColor(new Color(45, 47, 52, 245));
        g2.fillRoundRect(x, y, width, height, 8, 8);
        g2.setColor(new Color(70, 72, 76));
        g2.drawRoundRect(x, y, width, height, 8, 8);

        g2.setColor(new Color(220, 220, 230));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (int i = 0; i < lines.length; i++) {
            g2.drawString(truncate(lines[i], 48), x + padding, y + padding + (i + 1) * lineHeight - 4);
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
                return;
            }
        }
        for (TopicNode node : coinNodes) {
            if (node.contains(mx, my)) {
                draggedNode = node;
                return;
            }
        }
        for (NewsNode node : newsNodes) {
            if (node.contains(mx, my)) {
                draggedNode = node;
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
}
