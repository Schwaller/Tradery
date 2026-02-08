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
import java.util.function.Function;

/**
 * Graph visualization with configurable band layout.
 * Each band can display Topics, Coins, or Articles with different layout modes.
 */
public class TimelineGraphPanel extends JPanel {

    private static final int MARGIN_LEFT = 60;
    private static final int MARGIN_RIGHT = 20;
    private static final int MARGIN_TOP = 30;
    private static final int MARGIN_BOTTOM = 50;
    private static final int TIMELINE_OFFSET = 25;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d");

    // All nodes (flat lists for quick lookup)
    private final List<NewsNode> newsNodes = new ArrayList<>();
    private final Map<String, TopicNode> topicMap = new HashMap<>();
    private final Set<String> existingArticleIds = new HashSet<>();

    // Band-based layout
    private List<Band> bands = new ArrayList<>();
    private List<BandConfig> bandConfigs;

    private Instant minTime;
    private Instant maxTime;

    private Timer physicsTimer;
    private Object hoveredNode;
    private Object selectedNode;
    private Object draggedNode;
    private Consumer<NewsNode> onNodeSelected;
    private Consumer<TopicNode> onTopicSelected;

    // View settings
    private boolean showConnections = true;
    private boolean showLabels = true;
    private int maxNodes = 500;

    // Config
    private final IntelConfig config;
    private SchemaRegistry schemaRegistry;
    private Rectangle topicsLabelClickArea;

    // Data-driven node extraction: type ID → article field accessor
    private final Map<String, Function<Article, List<String>>> nodeExtractors = new LinkedHashMap<>();

    /**
     * Runtime band with pixel boundaries and node assignments.
     */
    private static class Band {
        final BandConfig config;
        int pixelTop, pixelBottom;
        final List<TopicNode> topicNodes = new ArrayList<>();
        final List<NewsNode> newsNodes = new ArrayList<>();

        Band(BandConfig config) {
            this.config = config;
        }

        int height() { return pixelBottom - pixelTop; }
    }

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

        // Register default node extractors
        nodeExtractors.put("topic", Article::topics);
        nodeExtractors.put("coin", Article::coins);
        nodeExtractors.put("category", Article::categories);
        nodeExtractors.put("tag", Article::tags);

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

    public void registerNodeExtractor(String typeId, Function<Article, List<String>> extractor) {
        nodeExtractors.put(typeId, extractor);
    }

    /**
     * Set band configurations. Rebuilds layout from scratch.
     */
    public void setBandConfigs(List<BandConfig> configs) {
        this.bandConfigs = configs != null ? configs : BandConfig.defaultNewsBands();
        buildBands();
        if (!newsNodes.isEmpty()) {
            assignNodesToBands();
            for (Band band : bands) {
                relayoutBand(band);
            }
            updateXPositions();
            physicsTimer.start();
            repaint();
        }
    }

    private List<BandConfig> effectiveConfigs() {
        return bandConfigs != null ? bandConfigs : BandConfig.defaultNewsBands();
    }

    private java.awt.Color resolveSchemaColor(String typeId) {
        if (schemaRegistry == null) return null;
        SchemaType st = schemaRegistry.getType(typeId);
        return st != null ? st.color() : null;
    }

    /**
     * Build Band objects from configs and compute pixel boundaries.
     */
    private void buildBands() {
        bands.clear();
        List<BandConfig> configs = effectiveConfigs();

        // Calculate total weight of visible bands
        double totalWeight = 0;
        for (BandConfig bc : configs) {
            if (bc.isVisible()) totalWeight += bc.getWeight();
        }
        if (totalWeight == 0) totalWeight = 1;

        int usableTop = 20; // small top margin for label
        int usableBottom = getHeight() - MARGIN_BOTTOM;
        int usableHeight = usableBottom - usableTop;

        int currentTop = usableTop;
        for (BandConfig bc : configs) {
            Band band = new Band(bc);
            if (bc.isVisible()) {
                int bandHeight = (int)(usableHeight * bc.getWeight() / totalWeight);
                band.pixelTop = currentTop;
                band.pixelBottom = currentTop + bandHeight;
                currentTop += bandHeight;
            } else {
                band.pixelTop = currentTop;
                band.pixelBottom = currentTop; // 0 height
            }
            bands.add(band);
        }
        // Adjust last visible band to fill any rounding gap
        for (int i = bands.size() - 1; i >= 0; i--) {
            if (bands.get(i).config.isVisible()) {
                bands.get(i).pixelBottom = usableBottom;
                break;
            }
        }
    }

    /**
     * Assign existing nodes to their matching bands based on filter.
     */
    private void assignNodesToBands() {
        // Clear all band node lists
        for (Band band : bands) {
            band.topicNodes.clear();
            band.newsNodes.clear();
        }

        // Assign each node to its matching band
        for (Band band : bands) {
            if (!band.config.isVisible()) continue;
            String filter = band.config.getFilter();
            if ("articles".equals(filter)) {
                band.newsNodes.addAll(newsNodes);
            } else {
                for (TopicNode tn : topicMap.values()) {
                    if (filter.equals(tn.typeId())) {
                        band.topicNodes.add(tn);
                    }
                }
            }
        }
    }

    /**
     * Set articles to display.
     */
    public void setArticles(List<Article> articles) {
        newsNodes.clear();
        topicMap.clear();
        existingArticleIds.clear();

        // Sort by time, limit to max
        List<Article> sorted = articles.stream()
            .filter(a -> a.publishedAt() != null)
            .sorted(Comparator.comparing(Article::publishedAt).reversed())
            .limit(maxNodes)
            .toList();

        if (sorted.isEmpty()) {
            for (Band band : bands) {
                band.topicNodes.clear();
                band.newsNodes.clear();
            }
            repaint();
            return;
        }

        // Find time range
        minTime = sorted.stream().map(Article::publishedAt).min(Instant::compareTo).orElse(Instant.now());
        maxTime = sorted.stream().map(Article::publishedAt).max(Instant::compareTo).orElse(Instant.now());

        if (ChronoUnit.MINUTES.between(minTime, maxTime) < 60) {
            minTime = maxTime.minus(1, ChronoUnit.HOURS);
        }

        // Create news nodes
        for (Article article : sorted) {
            existingArticleIds.add(article.id());
            NewsNode node = new NewsNode(article);
            newsNodes.add(node);

            // Create/link nodes for all registered extractors
            for (var ext : nodeExtractors.entrySet()) {
                String typeId = ext.getKey();
                for (String value : ext.getValue().apply(article)) {
                    if ("topic".equals(typeId) && config.isTopicHidden(value)) continue;
                    String nodeId = "topic".equals(typeId) ? value : typeId + ":" + value;
                    String label = "topic".equals(typeId) ? formatTopicLabel(value) : value;
                    TopicNode tn = topicMap.computeIfAbsent(nodeId, id -> {
                        TopicNode n = new TopicNode(id, label, typeId);
                        Color sc = resolveSchemaColor(typeId);
                        if (sc != null) n.setColor(sc);
                        return n;
                    });
                    tn.addConnection(node);
                    node.addTopicConnection(tn);
                }
            }
        }

        // Build bands and assign nodes
        buildBands();
        assignNodesToBands();

        // Layout each band
        for (Band band : bands) {
            relayoutBand(band);
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
        List<Article> newArticles = articles.stream()
            .filter(a -> a.publishedAt() != null)
            .filter(a -> !existingArticleIds.contains(a.id()))
            .sorted(Comparator.comparing(Article::publishedAt).reversed())
            .toList();

        if (newArticles.isEmpty()) {
            return 0;
        }

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
            newsNodes.add(node);
            addedNodes.add(node);

            // Create/link nodes for all registered extractors
            for (var ext : nodeExtractors.entrySet()) {
                String typeId = ext.getKey();
                for (String value : ext.getValue().apply(article)) {
                    if ("topic".equals(typeId) && config.isTopicHidden(value)) continue;
                    String nodeId = "topic".equals(typeId) ? value : typeId + ":" + value;
                    String label = "topic".equals(typeId) ? formatTopicLabel(value) : value;
                    TopicNode tn = topicMap.computeIfAbsent(nodeId, id -> {
                        TopicNode n = new TopicNode(id, label, typeId);
                        Color sc = resolveSchemaColor(typeId);
                        if (sc != null) n.setColor(sc);
                        return n;
                    });
                    tn.addConnection(node);
                    node.addTopicConnection(tn);
                }
            }
        }

        // Re-build bands and assign nodes
        buildBands();
        assignNodesToBands();

        // Only re-layout non-article bands (topics/coins may have new entries)
        // For article bands, just set Y for the new nodes
        for (Band band : bands) {
            if (!band.config.isVisible()) continue;
            if ("articles".equals(band.config.getFilter())) {
                // Set initial Y for new nodes only
                for (NewsNode node : addedNodes) {
                    int zoneTop = band.pixelTop + 20;
                    int zoneBottom = band.pixelBottom - 10;
                    node.setY(zoneTop + Math.random() * Math.max(1, zoneBottom - zoneTop - 40));
                }
            } else {
                relayoutBand(band);
            }
        }

        updateXPositions();
        repaint();

        return addedNodes.size();
    }

    /**
     * Layout nodes within a single band based on its layout mode.
     */
    private void relayoutBand(Band band) {
        if (!band.config.isVisible() || band.height() < 10) return;

        switch (band.config.getLayoutMode()) {
            case HORIZONTAL_ROWS -> relayoutHorizontalRows(band);
            case SPRING_PHYSICS -> relayoutSpringPhysics(band);
            case MAPPED_TO_FIELD -> relayoutMappedToField(band);
        }
    }

    private void relayoutHorizontalRows(Band band) {
        List<TopicNode> nodes = band.topicNodes;
        if (nodes.isEmpty()) return;

        int zoneTop = band.pixelTop;
        int zoneHeight = band.height();
        int width = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;

        nodes.sort((a, b) -> Integer.compare(b.articleCount(), a.articleCount()));

        int maxRows = band.config.getMaxRows();
        int rowCount = Math.min(maxRows, Math.max(1, (int) Math.ceil(nodes.size() / 8.0)));
        int rowSpacing = zoneHeight / (rowCount + 1);

        int[] rowCounts = new int[rowCount];
        for (int i = 0; i < nodes.size(); i++) {
            rowCounts[i % rowCount]++;
        }
        int[] rowIndices = new int[rowCount];
        for (int i = 0; i < nodes.size(); i++) {
            TopicNode tn = nodes.get(i);
            int row = i % rowCount;
            int indexInRow = rowIndices[row]++;
            int countInRow = rowCounts[row];
            tn.setX(MARGIN_LEFT + (indexInRow + 0.5) * width / Math.max(1, countInRow));
            tn.setY(zoneTop + rowSpacing * (row + 1));
        }
    }

    private void relayoutSpringPhysics(Band band) {
        // For article bands: set initial random Y within band
        int zoneTop = band.pixelTop + 20;
        int zoneBottom = band.pixelBottom - 10;

        for (NewsNode node : band.newsNodes) {
            if (node.y() < zoneTop || node.y() > zoneBottom) {
                node.setY(zoneTop + Math.random() * Math.max(1, zoneBottom - zoneTop));
            }
        }

        // For topic/coin bands with spring physics (less common but supported)
        for (TopicNode node : band.topicNodes) {
            if (node.y() < zoneTop || node.y() > zoneBottom) {
                node.setY(zoneTop + Math.random() * Math.max(1, zoneBottom - zoneTop));
            }
        }
    }

    private void relayoutMappedToField(Band band) {
        String yField = band.config.getYField();
        if (yField == null) return;

        int zoneTop = band.pixelTop + 10;
        int zoneBottom = band.pixelBottom - 10;
        int zoneHeight = zoneBottom - zoneTop;

        if ("articles".equals(band.config.getFilter())) {
            for (NewsNode node : band.newsNodes) {
                double normalized = getFieldValue(node, yField);
                // Map normalized 0..1 to pixel range (0=top, 1=bottom)
                node.setY(zoneTop + normalized * zoneHeight);
            }
        } else {
            // Topics/Coins
            int maxCount = band.topicNodes.stream().mapToInt(TopicNode::articleCount).max().orElse(1);
            for (TopicNode node : band.topicNodes) {
                double normalized = getFieldValue(node, yField, maxCount);
                node.setY(zoneTop + normalized * zoneHeight);
                // Distribute X evenly
                int idx = band.topicNodes.indexOf(node);
                int width = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
                node.setX(MARGIN_LEFT + (idx + 0.5) * width / Math.max(1, band.topicNodes.size()));
            }
        }
    }

    /**
     * Get a normalized (0..1) field value from a NewsNode for Y-mapping.
     */
    private double getFieldValue(NewsNode node, String field) {
        return switch (field) {
            case "sentiment" -> (node.sentiment() + 1.0) / 2.0; // -1..+1 → 0..1
            case "importance" -> node.importance().ordinal() / 4.0; // 0..4 → 0..1
            default -> 0.5;
        };
    }

    /**
     * Get a normalized (0..1) field value from a TopicNode for Y-mapping.
     */
    private double getFieldValue(TopicNode node, String field, int maxArticleCount) {
        return switch (field) {
            case "articleCount" -> maxArticleCount > 0 ? 1.0 - (double) node.articleCount() / maxArticleCount : 0.5;
            default -> 0.5;
        };
    }

    private String formatTopicLabel(String topic) {
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

        boolean anyMoving = false;

        double damping = 0.85;
        double repulsion = 400;
        double topicAttraction = 0.02;

        for (Band band : bands) {
            if (!band.config.isVisible()) continue;

            switch (band.config.getLayoutMode()) {
                case SPRING_PHYSICS -> {
                    if ("articles".equals(band.config.getFilter())) {
                        anyMoving |= applyArticlePhysics(band, damping, repulsion);
                    } else {
                        // TopicNodes with spring physics: Y repulsion within band
                        anyMoving |= applyTopicSpringPhysics(band, damping, repulsion);
                    }
                }
                case HORIZONTAL_ROWS -> {
                    // X repulsion for row-based bands
                    int leftBound = MARGIN_LEFT + 30;
                    int rightBound = getWidth() - MARGIN_RIGHT - 30;
                    double rowDamping = 0.92;
                    double strongRepulsion = repulsion * 2.0;
                    double weakAttraction = topicAttraction * 0.3;
                    anyMoving |= applyRowPhysics(band.topicNodes, leftBound, rightBound, strongRepulsion, weakAttraction, rowDamping);
                }
                case MAPPED_TO_FIELD -> {
                    // Deterministic — no physics
                }
            }
        }

        return anyMoving;
    }

    private boolean applyArticlePhysics(Band band, double damping, double repulsion) {
        int zoneTop = band.pixelTop + 20;
        int zoneBottom = band.pixelBottom - 10;
        double centerY = (zoneTop + zoneBottom) / 2.0;
        double minVelocity = 0.1;
        boolean anyMoving = false;

        for (NewsNode node : band.newsNodes) {
            if (node == draggedNode) continue;

            double fy = 0;

            for (NewsNode other : band.newsNodes) {
                if (other == node) continue;
                double dy = node.y() - other.y();
                double dx = node.x() - other.x();
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 1) dist = 1;
                if (dist < 80) {
                    fy += (dy / dist) * repulsion / (dist * dist);
                }
            }

            fy += (centerY - node.y()) * 0.001;

            double vy = (node.vy() + fy) * damping;
            if (Math.abs(vy) < minVelocity) vy = 0;
            node.setVy(vy);
            if (vy != 0) {
                node.setY(node.y() + vy);
                node.setY(Math.max(zoneTop, Math.min(zoneBottom, node.y())));
                anyMoving = true;
            }
        }
        return anyMoving;
    }

    private boolean applyTopicSpringPhysics(Band band, double damping, double repulsion) {
        int zoneTop = band.pixelTop + 10;
        int zoneBottom = band.pixelBottom - 10;
        double centerY = (zoneTop + zoneBottom) / 2.0;
        double minVelocity = 0.1;
        boolean anyMoving = false;

        for (TopicNode node : band.topicNodes) {
            if (node == draggedNode) continue;

            double fy = 0;
            for (TopicNode other : band.topicNodes) {
                if (other == node) continue;
                double dy = node.y() - other.y();
                double dx = node.x() - other.x();
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 1) dist = 1;
                if (dist < 80) {
                    fy += (dy / dist) * repulsion / (dist * dist);
                }
            }

            fy += (centerY - node.y()) * 0.001;

            double vy = (node.vx() + fy) * damping; // reuse vx as velocity
            if (Math.abs(vy) < minVelocity) vy = 0;
            node.setVx(vy);
            if (vy != 0) {
                node.setY(node.y() + vy);
                node.setY(Math.max(zoneTop, Math.min(zoneBottom, node.y())));
                anyMoving = true;
            }
        }
        return anyMoving;
    }

    /**
     * Apply gentle horizontal physics to a row of nodes (topics or coins).
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

        // Ensure bands are built (may not have been if setArticles hasn't been called yet)
        if (bands.isEmpty()) {
            buildBands();
        }

        int timelineY = getHeight() - MARGIN_BOTTOM + TIMELINE_OFFSET;

        // Draw band separators, labels, and nodes
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        int labelFontAscent = g2.getFontMetrics().getAscent();
        topicsLabelClickArea = null; // reset

        for (int i = 0; i < bands.size(); i++) {
            Band band = bands.get(i);
            if (!band.config.isVisible()) continue;

            // Draw separator line at top of each band (except the first)
            if (i > 0) {
                g2.setColor(gridLineColor());
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(0, band.pixelTop, getWidth(), band.pixelTop);
            }

            // Draw band label
            int labelY = band.pixelTop + 8 + labelFontAscent;
            String label = band.config.getName().toUpperCase();
            g2.setColor(secondaryColor());
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.drawString(label, MARGIN_LEFT, labelY);

            // For the "topic" band, add clickable config triangle
            if ("topic".equals(band.config.getFilter())) {
                int topicsLabelWidth = g2.getFontMetrics().stringWidth(label);
                int triangleX = MARGIN_LEFT + topicsLabelWidth + 6;
                int triangleYPos = labelY - 6;
                int[] xPoints = {triangleX, triangleX + 8, triangleX + 4};
                int[] yPoints = {triangleYPos, triangleYPos, triangleYPos + 5};
                g2.setColor(secondaryColor());
                g2.fillPolygon(xPoints, yPoints, 3);
                topicsLabelClickArea = new Rectangle(MARGIN_LEFT, band.pixelTop, topicsLabelWidth + 20, labelY - band.pixelTop + 4);
            }
        }

        // Timeline separator
        g2.setColor(gridLineColor());
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(0, timelineY, getWidth(), timelineY);

        // Draw time axis
        drawTimeAxis(g2);

        // Draw connections
        if (showConnections) {
            drawConnections(g2);
        }

        // Draw nodes per band
        for (Band band : bands) {
            if (!band.config.isVisible()) continue;
            for (TopicNode node : band.topicNodes) {
                drawSingleTopicNode(g2, node);
            }
            for (NewsNode node : band.newsNodes) {
                drawSingleNewsNode(g2, node);
            }
        }

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
        for (Band band : bands) {
            if (!band.config.isVisible()) continue;
            for (TopicNode node : band.topicNodes) {
                drawNodeConnections(g2, node);
            }
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

    private void drawSingleTopicNode(Graphics2D g2, TopicNode node) {
        int r = node.getRadius();
        Color c = node.getColor();
        boolean highlight = node.isHovered() || node.isSelected();

        if (highlight) {
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
            g2.fillOval((int)node.x() - r - 5, (int)node.y() - r - 5, (r + 5) * 2, (r + 5) * 2);
        }

        g2.setColor(c);
        g2.fillOval((int)node.x() - r, (int)node.y() - r, r * 2, r * 2);
        g2.setColor(c.darker());
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval((int)node.x() - r, (int)node.y() - r, r * 2, r * 2);

        if (showLabels) {
            g2.setColor(labelColor());
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            String label = node.label();
            int labelWidth = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, (int)node.x() - labelWidth / 2, (int)node.y() + r + 14);
        }
    }

    private void drawSingleNewsNode(Graphics2D g2, NewsNode node) {
        int r = node.getRadius();
        Color c = node.getColor();
        boolean highlight = node.isHovered() || node.isSelected();

        if (highlight) {
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
            g2.fillOval((int)node.x() - r - 4, (int)node.y() - r - 4, (r + 4) * 2, (r + 4) * 2);
        }

        g2.setColor(c);
        g2.fillOval((int)node.x() - r, (int)node.y() - r, r * 2, r * 2);
        g2.setColor(c.darker());
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval((int)node.x() - r, (int)node.y() - r, r * 2, r * 2);
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

            String title = news.title();
            if (title.length() <= maxCharsPerLine) {
                linesList.add(title);
            } else {
                int maxTitleLines = 3;
                int pos = 0;
                while (pos < title.length() && linesList.size() < maxTitleLines) {
                    int end = Math.min(pos + maxCharsPerLine, title.length());
                    if (end < title.length() && linesList.size() < maxTitleLines - 1) {
                        int spacePos = title.lastIndexOf(' ', end);
                        if (spacePos > pos + maxCharsPerLine / 2) {
                            end = spacePos;
                        }
                    }
                    String line = title.substring(pos, end).trim();
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
            if ("coin".equals(topic.typeId())) {
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

        // Check all bands
        for (Band band : bands) {
            if (!band.config.isVisible()) continue;
            for (TopicNode node : band.topicNodes) {
                if (node.contains(mx, my)) {
                    newHover = node;
                    break;
                }
            }
            if (newHover != null) break;
            for (NewsNode node : band.newsNodes) {
                if (node.contains(mx, my)) {
                    newHover = node;
                    break;
                }
            }
            if (newHover != null) break;
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

        // Check all bands
        for (Band band : bands) {
            if (!band.config.isVisible()) continue;
            for (TopicNode node : band.topicNodes) {
                if (node.contains(mx, my)) {
                    selectedNode = node;
                    node.setSelected(true);
                    if (onTopicSelected != null) {
                        onTopicSelected.accept(node);
                    }
                    repaint();
                    return;
                }
            }
            for (NewsNode node : band.newsNodes) {
                if (node.contains(mx, my)) {
                    selectedNode = node;
                    node.setSelected(true);
                    if (onNodeSelected != null) {
                        onNodeSelected.accept(node);
                    }
                    repaint();
                    return;
                }
            }
        }

        repaint();
    }

    private void startDrag(int mx, int my) {
        for (Band band : bands) {
            if (!band.config.isVisible()) continue;
            for (TopicNode node : band.topicNodes) {
                if (node.contains(mx, my)) {
                    draggedNode = node;
                    if (!physicsTimer.isRunning()) physicsTimer.start();
                    return;
                }
            }
            for (NewsNode node : band.newsNodes) {
                if (node.contains(mx, my)) {
                    draggedNode = node;
                    if (!physicsTimer.isRunning()) physicsTimer.start();
                    return;
                }
            }
        }
    }

    private void handleDrag(int mx, int my) {
        if (draggedNode == null) return;

        int leftBound = MARGIN_LEFT + 30;
        int rightBound = getWidth() - MARGIN_RIGHT - 30;

        // Find which band this node belongs to
        Band ownerBand = findBandForNode(draggedNode);

        if (draggedNode instanceof TopicNode topic) {
            if (ownerBand != null && ownerBand.config.getLayoutMode() == BandConfig.LayoutMode.HORIZONTAL_ROWS) {
                // Drag on X axis only (Y is row-fixed)
                double newX = Math.max(leftBound, Math.min(rightBound, mx));
                topic.setX(newX);
                topic.setVx(0);
            } else if (ownerBand != null) {
                // Free drag within band
                double newX = Math.max(leftBound, Math.min(rightBound, mx));
                double newY = Math.max(ownerBand.pixelTop + 10, Math.min(ownerBand.pixelBottom - 10, my));
                topic.setX(newX);
                topic.setY(newY);
                topic.setVx(0);
            }
        } else if (draggedNode instanceof NewsNode news) {
            if (ownerBand != null) {
                // Constrain Y within band
                int zoneTop = ownerBand.pixelTop + 20;
                int zoneBottom = ownerBand.pixelBottom - 10;
                double newY = Math.max(zoneTop, Math.min(zoneBottom, my));
                news.setY(newY);
                news.setVy(0);
            }
        }
        repaint();
    }

    private Band findBandForNode(Object node) {
        for (Band band : bands) {
            if (node instanceof TopicNode tn && band.topicNodes.contains(tn)) return band;
            if (node instanceof NewsNode nn && band.newsNodes.contains(nn)) return band;
        }
        return null;
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
        for (TopicNode tn : topicMap.values()) {
            if ("topic".equals(tn.typeId())) {
                allTopics.add(tn.id());
            }
        }
        // Add hidden topics from config
        allTopics.addAll(config.getHiddenTopics());

        if (allTopics.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No topics found", "Topics", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Configure Topics", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(300, 400);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("Select topics to show:");
        panel.add(label, BorderLayout.NORTH);

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

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton showAllBtn = new JButton("Show All");
        showAllBtn.addActionListener(e -> checkboxes.values().forEach(cb -> cb.setSelected(true)));
        buttonPanel.add(showAllBtn);

        JButton hideAllBtn = new JButton("Hide All");
        hideAllBtn.addActionListener(e -> checkboxes.values().forEach(cb -> cb.setSelected(false)));
        buttonPanel.add(hideAllBtn);

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            for (var entry : checkboxes.entrySet()) {
                config.setTopicHidden(entry.getKey(), !entry.getValue().isSelected());
            }
            config.save();
            dialog.dispose();
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
        // Remove hidden topics from topicMap
        List<String> toRemove = new ArrayList<>();
        for (var entry : topicMap.entrySet()) {
            if ("topic".equals(entry.getValue().typeId()) && config.isTopicHidden(entry.getKey())) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(topicMap::remove);

        // Re-add any topics that are no longer hidden
        for (NewsNode news : newsNodes) {
            for (String topic : news.topics()) {
                if (!config.isTopicHidden(topic) && !topicMap.containsKey(topic)) {
                    TopicNode tn = new TopicNode(topic, formatTopicLabel(topic), "topic");
                    Color sc = resolveSchemaColor("topic");
                    if (sc != null) tn.setColor(sc);
                    topicMap.put(topic, tn);
                }
                TopicNode topicNode = topicMap.get(topic);
                if (topicNode != null) {
                    topicNode.addConnection(news);
                }
            }
        }

        // Reassign and relayout
        assignNodesToBands();
        for (Band band : bands) {
            if (!"articles".equals(band.config.getFilter())) {
                relayoutBand(band);
            }
        }
        repaint();
    }
}
