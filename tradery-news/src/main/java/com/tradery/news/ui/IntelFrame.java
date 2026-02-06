package com.tradery.news.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.tradery.news.ai.ClaudeCliProcessor;
import com.tradery.news.fetch.FetchScheduler;
import com.tradery.news.fetch.FetcherRegistry;
import com.tradery.news.model.Article;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.topic.TopicRegistry;
import com.tradery.news.ui.coin.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Combined intelligence window with Coins and News visualization.
 * Layout: Left (graph tabs) | Right (detail panel + log panel)
 */
public class IntelFrame extends JFrame {

    // News components
    private final SqliteNewsStore store;
    private final Path dataDir;
    private TimelineGraphPanel newsGraphPanel;
    private JLabel newsStatusLabel;
    private JComboBox<String> limitCombo;
    private JButton fetchBtn;
    private volatile boolean fetching = false;

    // Coins components
    private CoinGraphPanel coinGraphPanel;
    private JLabel coinStatusLabel;
    private JProgressBar coinProgressBar;
    private EntityStore entityStore;
    private List<CoinEntity> currentEntities;
    private List<CoinRelationship> currentRelationships;

    // Shared components
    private JTabbedPane graphTabs;
    private JPanel detailPanel;
    private JPanel detailContent;
    private JLabel detailTitleLabel;
    private IntelLogPanel logPanel;

    // Current selection
    private enum DetailMode { NONE, ARTICLE, ENTITY }
    private DetailMode currentMode = DetailMode.NONE;
    private NewsNode selectedArticle;
    private CoinEntity selectedEntity;

    public IntelFrame() {
        super("Tradery - Intelligence");

        // Initialize stores
        this.dataDir = Path.of(System.getProperty("user.home"), ".cryptonews");
        this.store = new SqliteNewsStore(dataDir.resolve("news.db"));
        this.entityStore = new EntityStore();

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1800, 1000);
        setLocationRelativeTo(null);

        initUI();

        // Cleanup on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (newsGraphPanel != null) newsGraphPanel.stopPhysics();
                if (coinGraphPanel != null) coinGraphPanel.stopPhysics();
                if (entityStore != null) entityStore.close();
            }
        });

        // Load data
        SwingUtilities.invokeLater(() -> {
            logPanel.info("Starting Intelligence module...");
            loadCoinData(false);
            loadNewsData();
        });
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(30, 32, 36));

        // Left side: Graph tabs with toolbars
        JPanel leftPanel = createGraphPanel();

        // Right side: Detail panel + Log panel
        JPanel rightPanel = createRightPanel();
        rightPanel.setPreferredSize(new Dimension(400, 0));
        rightPanel.setMinimumSize(new Dimension(300, 0));

        // Main split
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplit.setResizeWeight(1.0);
        mainSplit.setDividerSize(4);
        mainSplit.setBorder(null);

        mainPanel.add(mainSplit, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    private JPanel createGraphPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 32, 36));

        graphTabs = new JTabbedPane();
        graphTabs.setTabPlacement(JTabbedPane.TOP);
        graphTabs.setFont(new Font("SansSerif", Font.BOLD, 12));

        // Coins tab
        JPanel coinsPanel = new JPanel(new BorderLayout());
        coinsPanel.add(createCoinsToolbar(), BorderLayout.NORTH);
        coinGraphPanel = new CoinGraphPanel();
        coinGraphPanel.setOnEntitySelected(this::showEntityDetails);
        coinsPanel.add(coinGraphPanel, BorderLayout.CENTER);
        coinsPanel.add(createCoinsStatusBar(), BorderLayout.SOUTH);
        graphTabs.addTab("Coins", coinsPanel);

        // News tab
        JPanel newsPanel = new JPanel(new BorderLayout());
        newsPanel.add(createNewsToolbar(), BorderLayout.NORTH);
        newsGraphPanel = new TimelineGraphPanel();
        newsGraphPanel.setOnNodeSelected(this::showArticleDetails);
        newsGraphPanel.setOnTopicSelected(this::showTopicDetails);
        newsPanel.add(newsGraphPanel, BorderLayout.CENTER);
        newsPanel.add(createNewsStatusBar(), BorderLayout.SOUTH);
        graphTabs.addTab("News", newsPanel);

        panel.add(graphTabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(38, 40, 44));
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(50, 52, 56)));

        // Detail panel (top)
        detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBackground(new Color(38, 40, 44));

        JPanel detailHeader = new JPanel(new BorderLayout());
        detailHeader.setBackground(new Color(35, 37, 41));
        detailHeader.setBorder(new EmptyBorder(8, 10, 8, 10));

        detailTitleLabel = new JLabel("Details");
        detailTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        detailTitleLabel.setForeground(new Color(180, 180, 190));
        detailHeader.add(detailTitleLabel, BorderLayout.WEST);

        detailPanel.add(detailHeader, BorderLayout.NORTH);

        detailContent = new JPanel();
        detailContent.setLayout(new BoxLayout(detailContent, BoxLayout.Y_AXIS));
        detailContent.setBackground(new Color(38, 40, 44));
        detailContent.setBorder(new EmptyBorder(10, 10, 10, 10));
        showPlaceholderDetails();

        JScrollPane detailScroll = new JScrollPane(detailContent);
        detailScroll.setBorder(null);
        detailScroll.getVerticalScrollBar().setUnitIncrement(16);
        detailPanel.add(detailScroll, BorderLayout.CENTER);

        // Log panel (bottom)
        logPanel = new IntelLogPanel();
        logPanel.setPreferredSize(new Dimension(0, 200));

        // Vertical split
        JSplitPane vertSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, detailPanel, logPanel);
        vertSplit.setResizeWeight(0.7);
        vertSplit.setDividerSize(4);
        vertSplit.setBorder(null);

        panel.add(vertSplit, BorderLayout.CENTER);
        return panel;
    }

    // ==================== TOOLBARS ====================

    private JToolBar createCoinsToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(38, 40, 44));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        JButton resetBtn = new JButton("Reset View");
        resetBtn.addActionListener(e -> coinGraphPanel.resetView());
        toolbar.add(resetBtn);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadCoinData(true));
        toolbar.add(refreshBtn);

        toolbar.addSeparator();

        JButton manageBtn = new JButton("Manage Entities");
        manageBtn.addActionListener(e -> showEntityManager());
        toolbar.add(manageBtn);

        JButton addRelBtn = new JButton("+ Relationship");
        addRelBtn.addActionListener(e -> showAddRelationshipDialog(null));
        toolbar.add(addRelBtn);

        toolbar.addSeparator();

        JCheckBox labelsCheck = new JCheckBox("Labels", true);
        labelsCheck.addActionListener(e -> coinGraphPanel.setShowLabels(labelsCheck.isSelected()));
        toolbar.add(labelsCheck);

        return toolbar;
    }

    private JPanel createCoinsStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusBar.setBackground(new Color(35, 37, 41));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 52, 56)));

        coinStatusLabel = new JLabel("Loading...");
        coinStatusLabel.setForeground(new Color(140, 140, 150));
        statusBar.add(coinStatusLabel);

        coinProgressBar = new JProgressBar(0, 100);
        coinProgressBar.setPreferredSize(new Dimension(150, 16));
        coinProgressBar.setStringPainted(true);
        coinProgressBar.setVisible(false);
        statusBar.add(coinProgressBar);

        return statusBar;
    }

    private JToolBar createNewsToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(38, 40, 44));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        fetchBtn = new JButton("Fetch New");
        fetchBtn.setToolTipText("Fetch new articles with AI extraction");
        fetchBtn.addActionListener(e -> fetchNewArticles());
        toolbar.add(fetchBtn);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadNewsData());
        toolbar.add(refreshBtn);

        toolbar.addSeparator();

        toolbar.add(new JLabel(" Show: "));
        limitCombo = new JComboBox<>(new String[]{"100", "250", "500", "1000"});
        limitCombo.setSelectedItem("500");
        limitCombo.addActionListener(e -> {
            newsGraphPanel.setMaxNodes(Integer.parseInt((String) limitCombo.getSelectedItem()));
            loadNewsData();
        });
        toolbar.add(limitCombo);

        toolbar.addSeparator();

        JCheckBox connectionsCheck = new JCheckBox("Connections", true);
        connectionsCheck.addActionListener(e -> newsGraphPanel.setShowConnections(connectionsCheck.isSelected()));
        toolbar.add(connectionsCheck);

        JCheckBox labelsCheck = new JCheckBox("Labels", true);
        labelsCheck.addActionListener(e -> newsGraphPanel.setShowLabels(labelsCheck.isSelected()));
        toolbar.add(labelsCheck);

        return toolbar;
    }

    private JPanel createNewsStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusBar.setBackground(new Color(35, 37, 41));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 52, 56)));

        newsStatusLabel = new JLabel("Loading...");
        newsStatusLabel.setForeground(new Color(140, 140, 150));
        statusBar.add(newsStatusLabel);

        return statusBar;
    }

    // ==================== DETAIL PANEL ====================

    private void showPlaceholderDetails() {
        detailContent.removeAll();
        currentMode = DetailMode.NONE;
        detailTitleLabel.setText("Details");

        addDetailLabel("Select an entity or article to see details", new Color(140, 140, 150));
        addDetailSpacer();
        addDetailLabel("Coins tab:", new Color(120, 120, 130));
        addDetailLabel("  Click entity to select", new Color(100, 100, 110));
        addDetailLabel("  Drag background to pan", new Color(100, 100, 110));
        addDetailLabel("  Scroll to zoom", new Color(100, 100, 110));
        addDetailLabel("  Double-click to pin", new Color(100, 100, 110));

        detailContent.revalidate();
        detailContent.repaint();
    }

    private void showTopicDetails(TopicNode node) {
        selectedArticle = null;
        selectedEntity = null;
        currentMode = DetailMode.NONE;

        boolean isCoin = node.type() == TopicNode.Type.COIN;
        detailTitleLabel.setText(isCoin ? "Coin" : "Topic");

        detailContent.removeAll();

        addDetailHeader(node.label());
        addDetailSpacer();

        addDetailSection("TYPE");
        addDetailLabel(isCoin ? "Cryptocurrency" : "News Topic",
            isCoin ? new Color(200, 160, 80) : new Color(100, 140, 200));
        addDetailSpacer();

        if (!isCoin) {
            addDetailSection("FULL PATH");
            addDetailLabel(node.id());
            addDetailSpacer();
        }

        addDetailSection("ARTICLES (" + node.articleCount() + ")");

        // Show connected articles (most recent first, limit to 20)
        List<NewsNode> articles = node.connections().stream()
            .sorted((a, b) -> b.publishedAt().compareTo(a.publishedAt()))
            .limit(20)
            .toList();

        for (NewsNode article : articles) {
            addArticleRow(article);
        }

        if (node.articleCount() > 20) {
            addDetailLabel("... and " + (node.articleCount() - 20) + " more", new Color(120, 120, 130));
        }

        detailContent.revalidate();
        detailContent.repaint();
    }

    private void addArticleRow(NewsNode article) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(new Color(38, 40, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        // Sentiment indicator
        Color sentColor = article.sentiment() > 0.2 ? new Color(80, 180, 100) :
                          article.sentiment() < -0.2 ? new Color(200, 80, 80) :
                          new Color(120, 120, 130);
        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(sentColor);
                g.fillOval(2, 12, 8, 8);
            }
        };
        dot.setPreferredSize(new Dimension(12, 32));
        dot.setBackground(new Color(38, 40, 44));
        row.add(dot, BorderLayout.WEST);

        // Title and source
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(new Color(38, 40, 44));

        String title = article.title();
        if (title.length() > 50) title = title.substring(0, 47) + "...";
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        titleLabel.setForeground(new Color(200, 200, 210));
        textPanel.add(titleLabel);

        LocalDateTime ldt = LocalDateTime.ofInstant(article.publishedAt(), ZoneId.systemDefault());
        JLabel metaLabel = new JLabel(article.source() + " • " +
            ldt.format(DateTimeFormatter.ofPattern("MMM d HH:mm")));
        metaLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        metaLabel.setForeground(new Color(120, 120, 130));
        textPanel.add(metaLabel);

        row.add(textPanel, BorderLayout.CENTER);

        // Click to show article details
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showArticleDetails(article);
            }
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                row.setBackground(new Color(45, 47, 51));
                textPanel.setBackground(new Color(45, 47, 51));
                dot.setBackground(new Color(45, 47, 51));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                row.setBackground(new Color(38, 40, 44));
                textPanel.setBackground(new Color(38, 40, 44));
                dot.setBackground(new Color(38, 40, 44));
            }
        });

        detailContent.add(row);
    }

    private void showArticleDetails(NewsNode node) {
        selectedArticle = node;
        selectedEntity = null;
        currentMode = DetailMode.ARTICLE;
        detailTitleLabel.setText("Article");

        detailContent.removeAll();

        addDetailHeader(node.title());
        addDetailSpacer();

        addDetailSection("SOURCE");
        addDetailLabel(node.source());
        if (node.sourceUrl() != null && !node.sourceUrl().isEmpty()) {
            addDetailLink(node.sourceUrl());
        }
        addDetailSpacer();

        addDetailSection("PUBLISHED");
        LocalDateTime ldt = LocalDateTime.ofInstant(node.publishedAt(), ZoneId.systemDefault());
        addDetailLabel(ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        addDetailSpacer();

        addDetailSection("IMPORTANCE");
        addDetailLabel(node.importance().toString());
        addDetailSpacer();

        addDetailSection("SENTIMENT");
        String sentimentText = String.format("%.2f", node.sentiment());
        if (node.sentiment() > 0.3) sentimentText += " (Positive)";
        else if (node.sentiment() < -0.3) sentimentText += " (Negative)";
        else sentimentText += " (Neutral)";
        addDetailLabel(sentimentText);
        addDetailSpacer();

        if (!node.coins().isEmpty()) {
            addDetailSection("COINS");
            addDetailLabel(String.join(", ", node.coins()));
            addDetailSpacer();
        }

        if (!node.topics().isEmpty()) {
            addDetailSection("TOPICS");
            addDetailLabel(String.join(", ", node.topics()));
            addDetailSpacer();
        }

        if (node.summary() != null && !node.summary().isEmpty()) {
            addDetailSection("SUMMARY");
            addDetailText(node.summary());
            addDetailSpacer();
        }

        if (node.content() != null && !node.content().isEmpty()) {
            addDetailSection("CONTENT");
            addDetailText(node.content());
        }

        detailContent.revalidate();
        detailContent.repaint();
    }

    private void showEntityDetails(CoinEntity entity) {
        selectedEntity = entity;
        selectedArticle = null;
        currentMode = DetailMode.ENTITY;
        detailTitleLabel.setText("Entity");

        detailContent.removeAll();

        addDetailHeader(entity.name());
        if (entity.symbol() != null) {
            addDetailLabel(entity.symbol(), entity.type().color());
        }
        addDetailSpacer();

        addDetailSection("TYPE");
        addDetailLabel(entity.type().toString(), entity.type().color());
        addDetailSpacer();

        if (entity.marketCap() > 0) {
            addDetailSection("MARKET CAP");
            addDetailLabel("$" + formatMarketCap(entity.marketCap()));
            addDetailSpacer();
        }

        List<CoinRelationship> rels = coinGraphPanel.getRelationshipsFor(entity.id());
        if (!rels.isEmpty()) {
            addDetailSection("RELATIONSHIPS (" + rels.size() + ")");
            for (CoinRelationship rel : rels) {
                String otherId = rel.fromId().equals(entity.id()) ? rel.toId() : rel.fromId();
                CoinEntity other = coinGraphPanel.getEntity(otherId);
                if (other == null) continue;

                String description;
                if (rel.fromId().equals(entity.id())) {
                    description = rel.type().label() + " " + other.name();
                } else {
                    description = describeInverseRelation(rel.type(), other.name());
                }

                addRelationshipRow(description, rel.type().color(), otherId, other.name());
            }
            addDetailSpacer();
        }

        if (!entity.categories().isEmpty()) {
            addDetailSection("CATEGORIES");
            for (String cat : entity.categories()) {
                addDetailLabel("  " + cat);
            }
            addDetailSpacer();
        }

        addDetailSection("STATUS");
        addDetailLabel(entity.isPinned() ? "Pinned" : "Free-floating");

        // Action buttons
        addDetailSpacer();
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        btnPanel.setBackground(new Color(38, 40, 44));
        btnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JButton searchRelatedBtn = new JButton("Search Related...");
        searchRelatedBtn.addActionListener(e -> {
            EntitySearchDialog dialog = new EntitySearchDialog(this, entity, entityStore);
            dialog.setVisible(true);
            loadCoinData(false);
        });
        btnPanel.add(searchRelatedBtn);

        JButton addRelBtn = new JButton("+ Relationship");
        addRelBtn.addActionListener(e -> showAddRelationshipDialog(entity.id()));
        btnPanel.add(addRelBtn);

        detailContent.add(btnPanel);

        detailContent.revalidate();
        detailContent.repaint();
    }

    // Detail panel helper methods
    private void addDetailHeader(String text) {
        JLabel label = new JLabel("<html><body style='width: 280px'>" + text + "</body></html>");
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        label.setForeground(new Color(220, 220, 230));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContent.add(label);
    }

    private void addDetailSection(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setForeground(new Color(120, 120, 130));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContent.add(label);
        detailContent.add(Box.createVerticalStrut(3));
    }

    private void addDetailLabel(String text) {
        addDetailLabel(text, new Color(200, 200, 210));
    }

    private void addDetailLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContent.add(label);
    }

    private void addDetailText(String text) {
        JTextArea area = new JTextArea(text);
        area.setFont(new Font("SansSerif", Font.PLAIN, 11));
        area.setForeground(new Color(180, 180, 190));
        area.setBackground(new Color(35, 37, 41));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        detailContent.add(area);
    }

    private void addDetailLink(String url) {
        JLabel label = new JLabel("<html><a href=''>" + truncate(url, 40) + "</a></html>");
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));
        label.setForeground(new Color(100, 150, 220));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new java.net.URI(url));
                } catch (Exception ex) {
                    logPanel.error("Failed to open URL: " + ex.getMessage());
                }
            }
        });
        detailContent.add(label);
    }

    private void addDetailSpacer() {
        detailContent.add(Box.createVerticalStrut(12));
    }

    private void addRelationshipRow(String description, Color color, String targetId, String targetName) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(new Color(38, 40, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JPanel colorDot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(color);
                g.fillOval(2, 5, 8, 8);
            }
        };
        colorDot.setPreferredSize(new Dimension(12, 20));
        colorDot.setBackground(new Color(38, 40, 44));
        row.add(colorDot, BorderLayout.WEST);

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descLabel.setForeground(new Color(200, 200, 210));
        row.add(descLabel, BorderLayout.CENTER);

        JButton navBtn = new JButton("\u25B6");
        navBtn.setFont(new Font("SansSerif", Font.PLAIN, 9));
        navBtn.setPreferredSize(new Dimension(24, 20));
        navBtn.setMargin(new Insets(0, 0, 0, 0));
        navBtn.setToolTipText("Go to " + targetName);
        navBtn.addActionListener(e -> coinGraphPanel.selectAndPanTo(targetId));
        row.add(navBtn, BorderLayout.EAST);

        detailContent.add(row);
        detailContent.add(Box.createVerticalStrut(2));
    }

    private String describeInverseRelation(CoinRelationship.Type type, String otherName) {
        return switch (type) {
            case L2_OF -> "L1 for " + otherName;
            case ETF_TRACKS, ETP_TRACKS -> "tracked by " + otherName;
            case INVESTED_IN -> "investor: " + otherName;
            case FOUNDED_BY -> "founded " + otherName;
            case FORK_OF -> "forked to " + otherName;
            case ECOSYSTEM -> "ecosystem: " + otherName;
            default -> type.label() + " " + otherName;
        };
    }

    // ==================== DATA LOADING ====================

    private void loadNewsData() {
        newsStatusLabel.setText("Loading...");
        logPanel.data("Loading news articles...");

        SwingWorker<List<Article>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Article> doInBackground() {
                int limit = Integer.parseInt((String) limitCombo.getSelectedItem());
                return store.getArticles(SqliteNewsStore.ArticleQuery.all(limit));
            }

            @Override
            protected void done() {
                try {
                    List<Article> articles = get();
                    newsGraphPanel.setArticles(articles);
                    newsStatusLabel.setText(articles.size() + " articles  |  " + store.getArticleCount() + " total");
                    logPanel.success("Loaded " + articles.size() + " news articles");
                } catch (Exception e) {
                    newsStatusLabel.setText("Error: " + e.getMessage());
                    logPanel.error("Failed to load news: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void fetchNewArticles() {
        if (fetching) return;
        fetching = true;
        fetchBtn.setEnabled(false);
        fetchBtn.setText("Fetching...");
        newsStatusLabel.setText("Fetching...");
        logPanel.ai("Starting AI-powered news fetch...");

        SwingWorker<FetchScheduler.FetchResult, String> worker = new SwingWorker<>() {
            @Override
            protected FetchScheduler.FetchResult doInBackground() {
                FetcherRegistry fetchers = new FetcherRegistry();
                fetchers.registerDefaults();

                TopicRegistry topics = new TopicRegistry(dataDir.resolve("topics.json"));
                ClaudeCliProcessor ai = new ClaudeCliProcessor();

                if (!ai.isAvailable()) {
                    publish("Claude CLI not available");
                    ai = null;
                }

                try (var scheduler = new FetchScheduler(fetchers, topics, store, ai)) {
                    scheduler.withAiEnabled(ai != null).withArticlesPerSource(ai != null ? 5 : 10);
                    return scheduler.fetchAndProcess();
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    logPanel.warn(msg);
                }
            }

            @Override
            protected void done() {
                fetching = false;
                fetchBtn.setEnabled(true);
                fetchBtn.setText("Fetch New");
                try {
                    FetchScheduler.FetchResult result = get();
                    if (result.newArticles() > 0) {
                        int limit = Integer.parseInt((String) limitCombo.getSelectedItem());
                        List<Article> allArticles = store.getArticles(SqliteNewsStore.ArticleQuery.all(limit));
                        int added = newsGraphPanel.addArticles(allArticles);
                        newsStatusLabel.setText(added + " new  |  " + store.getArticleCount() + " total");
                        logPanel.success("Fetched " + result.newArticles() + " articles (" + result.aiProcessed() + " AI processed)");
                    } else {
                        newsStatusLabel.setText("No new articles  |  " + store.getArticleCount() + " total");
                        logPanel.info("No new articles found");
                    }
                } catch (Exception e) {
                    newsStatusLabel.setText("Error: " + e.getMessage());
                    logPanel.error("Fetch failed: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void loadCoinData(boolean forceRefresh) {
        coinStatusLabel.setText("Loading...");
        logPanel.data("Loading coin entities...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            private List<CoinEntity> entities;
            private List<CoinRelationship> relationships;

            @Override
            protected Void doInBackground() {
                try {
                    CoinGeckoClient client = new CoinGeckoClient();
                    boolean fromCache = false;

                    if (!forceRefresh && entityStore.isCoinGeckoCacheValid()) {
                        publish("Loading from cache...");
                        entities = entityStore.loadEntitiesBySource("coingecko");
                        fromCache = !entities.isEmpty();
                    }

                    if (entities == null || entities.isEmpty()) {
                        publish("Fetching from CoinGecko...");
                        List<CoinEntity> cgEntities = client.fetchTopCoins(200);
                        entityStore.saveCoinGeckoEntities(cgEntities);
                        entities = new ArrayList<>(cgEntities);
                    }

                    publish("Loading manual entities...");
                    List<CoinEntity> manualEntities = entityStore.loadEntitiesBySource("manual");
                    entities.addAll(manualEntities);

                    publish("Building relationships...");
                    List<CoinRelationship> autoRels = client.buildRelationships(entities);
                    entityStore.saveAutoRelationships(autoRels);
                    relationships = entityStore.loadAllRelationships();

                    if (manualEntities.isEmpty()) {
                        publish("Seeding defaults...");
                        seedDefaultManualEntities();
                        manualEntities = entityStore.loadEntitiesBySource("manual");
                        entities.addAll(manualEntities);
                        relationships = entityStore.loadAllRelationships();
                    }

                    final List<CoinEntity> finalEntities = entities;
                    final List<CoinRelationship> finalRels = relationships;
                    SwingUtilities.invokeLater(() -> {
                        currentEntities = finalEntities;
                        currentRelationships = finalRels;
                        coinGraphPanel.setData(finalEntities, finalRels);
                        updateCoinStatus();
                        coinProgressBar.setVisible(false);
                    });

                    if (!fromCache) {
                        fetchCategories(client, entities);
                    }

                } catch (Exception e) {
                    publish("Error: " + e.getMessage());
                    loadFallbackData();
                }
                return null;
            }

            private void fetchCategories(CoinGeckoClient client, List<CoinEntity> entities) {
                SwingUtilities.invokeLater(() -> {
                    coinProgressBar.setVisible(true);
                    coinProgressBar.setValue(0);
                });

                List<String> cgIds = entities.stream()
                    .filter(e -> e.type() == CoinEntity.Type.COIN)
                    .map(CoinEntity::id).toList();
                int total = cgIds.size();
                int count = 0;

                for (String coinId : cgIds) {
                    count++;
                    int pct = (count * 100) / total;
                    try {
                        Map<String, List<String>> catMap = client.fetchCoinCategories(List.of(coinId));
                        List<String> cats = catMap.get(coinId);
                        if (cats != null && !cats.isEmpty()) {
                            for (CoinEntity entity : entities) {
                                if (entity.id().equals(coinId)) {
                                    entity.setCategories(cats);
                                    entityStore.saveEntity(entity, "coingecko");
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    int finalPct = pct;
                    SwingUtilities.invokeLater(() -> {
                        coinProgressBar.setValue(finalPct);
                        coinProgressBar.setString(finalPct + "%");
                    });
                }

                SwingUtilities.invokeLater(() -> coinProgressBar.setVisible(false));
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    coinStatusLabel.setText(msg);
                    logPanel.data(msg);
                }
            }

            @Override
            protected void done() {
                if (entities != null) {
                    updateCoinStatus();
                    logPanel.success("Loaded " + entities.size() + " entities, " + relationships.size() + " relationships");
                }
            }

            private void loadFallbackData() {
                entities = new ArrayList<>();
                relationships = new ArrayList<>();
                loadSampleData(entities, relationships);
                SwingUtilities.invokeLater(() -> {
                    currentEntities = entities;
                    currentRelationships = relationships;
                    coinGraphPanel.setData(entities, relationships);
                    coinStatusLabel.setText(entities.size() + " entities (sample)");
                    coinProgressBar.setVisible(false);
                });
            }
        };
        worker.execute();
    }

    private void updateCoinStatus() {
        int manual = entityStore.getManualEntityCount();
        int manualRels = entityStore.getManualRelationshipCount();
        String status = currentEntities.size() + " entities  |  " + currentRelationships.size() + " rels";
        if (manual > 0) status += "  |  " + manual + " manual";
        coinStatusLabel.setText(status);
    }

    // ==================== DIALOGS ====================

    private void showEntityManager() {
        logPanel.info("Opening Entity Manager...");
        EntityManagerFrame manager = new EntityManagerFrame(entityStore, v -> loadCoinData(false));
        manager.setVisible(true);
    }

    private void showAddRelationshipDialog(String preselectedFromId) {
        if (currentEntities == null || currentEntities.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No entities loaded", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        RelationshipEditorDialog dialog = new RelationshipEditorDialog(
            this, entityStore, currentEntities, preselectedFromId, rel -> {
                if (currentRelationships != null) {
                    currentRelationships.add(rel);
                    coinGraphPanel.setData(currentEntities, currentRelationships);
                    updateCoinStatus();
                    logPanel.success("Added relationship: " + rel.type().label());
                }
            }
        );
        dialog.setVisible(true);
    }

    // ==================== SEED DATA ====================

    private void seedDefaultManualEntities() {
        Set<String> existingIds = new HashSet<>();
        for (CoinEntity e : entityStore.loadAllEntities()) existingIds.add(e.id());

        // ETFs
        saveManualEntity(createETF("ibit", "iShares Bitcoin Trust", "IBIT"));
        saveManualEntity(createETF("fbtc", "Fidelity Wise Origin Bitcoin", "FBTC"));
        saveManualEntity(createETF("gbtc", "Grayscale Bitcoin Trust", "GBTC"));
        saveManualEntity(createETF("etha", "iShares Ethereum Trust", "ETHA"));

        if (existingIds.contains("bitcoin")) {
            for (String etf : List.of("ibit", "fbtc", "gbtc"))
                saveManualRelationship(new CoinRelationship(etf, "bitcoin", CoinRelationship.Type.ETF_TRACKS));
        }
        if (existingIds.contains("ethereum")) {
            saveManualRelationship(new CoinRelationship("etha", "ethereum", CoinRelationship.Type.ETF_TRACKS));
        }

        // VCs
        saveManualEntity(createVC("a16z", "Andreessen Horowitz"));
        saveManualEntity(createVC("paradigm", "Paradigm"));
        saveManualEntity(createVC("multicoin", "Multicoin Capital"));

        seedInvestments(existingIds, "a16z", "solana", "ethereum", "optimism", "uniswap");
        seedInvestments(existingIds, "paradigm", "ethereum", "optimism", "uniswap");
        seedInvestments(existingIds, "multicoin", "solana", "helium");

        // Exchanges
        saveManualEntity(createExchange("binance-ex", "Binance"));
        saveManualEntity(createExchange("coinbase-ex", "Coinbase"));

        if (existingIds.contains("binancecoin"))
            saveManualRelationship(new CoinRelationship("binance-ex", "binancecoin", CoinRelationship.Type.FOUNDED_BY));
    }

    private void saveManualEntity(CoinEntity entity) { entityStore.saveEntity(entity, "manual"); }
    private void saveManualRelationship(CoinRelationship rel) { entityStore.saveRelationship(rel, "manual"); }

    private void seedInvestments(Set<String> existingIds, String vcId, String... coinIds) {
        for (String coinId : coinIds) {
            if (existingIds.contains(coinId))
                saveManualRelationship(new CoinRelationship(vcId, coinId, CoinRelationship.Type.INVESTED_IN));
        }
    }

    private void loadSampleData(List<CoinEntity> entities, List<CoinRelationship> relationships) {
        entities.add(createCoin("bitcoin", "Bitcoin", "BTC", 1_300_000_000_000L));
        entities.add(createCoin("ethereum", "Ethereum", "ETH", 350_000_000_000L));
        entities.add(createCoin("solana", "Solana", "SOL", 80_000_000_000L));
        entities.add(createL2("arbitrum", "Arbitrum", "ARB", "ethereum"));
        relationships.add(new CoinRelationship("arbitrum", "ethereum", CoinRelationship.Type.L2_OF));
        entities.add(createETF("ibit", "iShares Bitcoin Trust", "IBIT"));
        relationships.add(new CoinRelationship("ibit", "bitcoin", CoinRelationship.Type.ETF_TRACKS));
    }

    private CoinEntity createCoin(String id, String name, String symbol, long marketCap) {
        CoinEntity e = new CoinEntity(id, name, symbol, CoinEntity.Type.COIN);
        e.setMarketCap(marketCap);
        return e;
    }
    private CoinEntity createL2(String id, String name, String symbol, String parentId) {
        return new CoinEntity(id, name, symbol, CoinEntity.Type.L2, parentId);
    }
    private CoinEntity createETF(String id, String name, String symbol) {
        return new CoinEntity(id, name, symbol, CoinEntity.Type.ETF);
    }
    private CoinEntity createVC(String id, String name) {
        return new CoinEntity(id, name, null, CoinEntity.Type.VC);
    }
    private CoinEntity createExchange(String id, String name) {
        return new CoinEntity(id, name, null, CoinEntity.Type.EXCHANGE);
    }

    private String formatMarketCap(double num) {
        if (num >= 1_000_000_000_000L) return String.format("%.2fT", num / 1_000_000_000_000L);
        if (num >= 1_000_000_000L) return String.format("%.2fB", num / 1_000_000_000L);
        if (num >= 1_000_000L) return String.format("%.2fM", num / 1_000_000L);
        return String.format("%.0f", num);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ==================== MAIN ====================

    public static void main(String[] args) {
        try {
            FlatDarkLaf.setup();
            UIManager.put("Button.arc", 5);
            UIManager.put("Component.arc", 5);
            UIManager.put("TextComponent.arc", 5);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> new IntelFrame().setVisible(true));
    }
}
