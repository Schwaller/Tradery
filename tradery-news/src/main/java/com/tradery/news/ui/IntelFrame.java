package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.ai.AiClient;
import com.tradery.ai.AiConfig;
import com.tradery.ai.AiSetupDialog;
import com.tradery.license.LicenseGate;
import com.tradery.license.UpdateChecker;
import com.tradery.news.model.Article;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.api.IntelApiServer;
import com.tradery.news.source.*;
import com.tradery.news.ui.coin.*;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.SegmentedToggle;
import com.tradery.ui.controls.ThinSplitPane;
import com.tradery.ui.controls.ToolbarButton;
import com.tradery.ui.controls.ToolbarComboBox;

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

    // Stores
    private final SqliteNewsStore store;
    private final Path dataDir;
    private EntityStore entityStore;
    EntityStore getEntityStore() { return entityStore; }

    // Dynamic panel instances
    private record PanelInstance(
        PanelConfig config,
        JPanel card,
        JComponent graphPanel,    // TimelineGraphPanel or CoinGraphPanel
        JLabel statusLabel
    ) {}
    private List<PanelInstance> panelInstances = new ArrayList<>();

    // News state
    private ToolbarComboBox<String> limitCombo;
    private volatile boolean fetching = false;
    private javax.swing.Timer autoFetchTimer;

    void updateAutoFetchTimer() {
        if (autoFetchTimer != null) {
            autoFetchTimer.stop();
            autoFetchTimer = null;
        }
        int minutes = IntelConfig.get().getFetchIntervalMinutes();
        if (minutes > 0) {
            autoFetchTimer = new javax.swing.Timer(minutes * 60 * 1000, e -> fetchNewArticles());
            autoFetchTimer.setRepeats(true);
            autoFetchTimer.start();
        }
    }

    // Coin data (shared across all COIN_GRAPH panels)
    private List<CoinEntity> currentEntities;
    private List<CoinRelationship> currentRelationships;
    private JProgressBar coinProgressBar;

    // Shared components
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private SegmentedToggle viewToggle;

    // Header action buttons (shown conditionally)
    private JLabel showLabel;
    private JButton fetchBtn;
    private JButton resetViewBtn;
    private JPanel detailPanel;
    private JPanel detailContent;
    private JLabel detailTitleLabel;
    private IntelLogPanel logPanel;

    // Singleton windows
    private DataStructureFrame dataStructureFrame;
    private SchemaRegistry schemaRegistry;
    private DataSourceRegistry sourceRegistry;

    // API server
    private IntelApiServer apiServer;

    // Current selection
    private enum DetailMode { NONE, ARTICLE, ENTITY }
    private DetailMode currentMode = DetailMode.NONE;
    private NewsNode selectedArticle;
    private CoinEntity selectedEntity;

    // Theme-aware colors (call these methods to get current theme colors)
    private static Color bgMain() { return UIManager.getColor("Panel.background"); }
    private static Color bgHeader() {
        Color bg = UIManager.getColor("Panel.background");
        return darker(bg, 0.05f);
    }
    private static Color bgCard() { return UIManager.getColor("Panel.background"); }
    private static Color bgHover() {
        Color bg = UIManager.getColor("Panel.background");
        return brighter(bg, 0.05f);
    }
    private static Color borderColor() { return UIManager.getColor("Separator.foreground"); }
    private static Color textPrimary() { return UIManager.getColor("Label.foreground"); }
    private static Color textSecondary() { return UIManager.getColor("Label.disabledForeground"); }
    private static Color textMuted() {
        Color fg = UIManager.getColor("Label.disabledForeground");
        return new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 180);
    }
    private static Color linkColor() {
        Color c = UIManager.getColor("Component.linkColor");
        return c != null ? c : new Color(88, 157, 246);
    }

    private static Color darker(Color c, float factor) {
        return new Color(
            Math.max(0, (int)(c.getRed() * (1 - factor))),
            Math.max(0, (int)(c.getGreen() * (1 - factor))),
            Math.max(0, (int)(c.getBlue() * (1 - factor)))
        );
    }
    private static Color brighter(Color c, float factor) {
        return new Color(
            Math.min(255, (int)(c.getRed() + (255 - c.getRed()) * factor)),
            Math.min(255, (int)(c.getGreen() + (255 - c.getGreen()) * factor)),
            Math.min(255, (int)(c.getBlue() + (255 - c.getBlue()) * factor))
        );
    }

    public IntelFrame() {
        super("Intelligence");

        // Initialize stores
        this.dataDir = Path.of(System.getProperty("user.home"), ".cryptonews");
        this.store = new SqliteNewsStore(dataDir.resolve("news.db"));
        this.entityStore = new EntityStore();
        this.schemaRegistry = new SchemaRegistry(entityStore);
        this.sourceRegistry = new DataSourceRegistry(entityStore, schemaRegistry);
        sourceRegistry.register(new CoinGeckoSource());
        sourceRegistry.register(new RssNewsSource(store, dataDir));

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Transparent title bar - title shown in header bar instead
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        // Restore window size/position from config
        IntelConfig config = IntelConfig.get();
        setSize(config.getWindowWidth(), config.getWindowHeight());
        if (config.getWindowX() >= 0 && config.getWindowY() >= 0) {
            setLocation(config.getWindowX(), config.getWindowY());
        } else {
            setLocationRelativeTo(null);
        }

        initUI();

        // Start API server
        try {
            EntitySearchProcessor searchProcessor = new EntitySearchProcessor();
            apiServer = new IntelApiServer(this::openWindow, entityStore, store, searchProcessor);
            apiServer.start();
        } catch (Exception e) {
            System.err.println("Failed to start Intel API server: " + e.getMessage());
        }

        // Save window state and cleanup on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Save window position/size
                IntelConfig cfg = IntelConfig.get();
                cfg.setWindowWidth(getWidth());
                cfg.setWindowHeight(getHeight());
                cfg.setWindowX(getX());
                cfg.setWindowY(getY());
                cfg.save();

                if (autoFetchTimer != null) autoFetchTimer.stop();
                if (apiServer != null) apiServer.stop();
                for (PanelInstance pi : panelInstances) {
                    if (pi.graphPanel() instanceof TimelineGraphPanel tgp) tgp.stopPhysics();
                    if (pi.graphPanel() instanceof CoinGraphPanel cgp) cgp.stopPhysics();
                }
                if (entityStore != null) entityStore.close();
            }
        });

        // Load data
        SwingUtilities.invokeLater(() -> {
            logPanel.info("Starting Intelligence module...");
            loadCoinData(false);
            loadNewsData();
            updateAutoFetchTimer();
        });
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(bgMain());

        // Initialize card layout first (header buttons reference it)
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // Full-width header bar: [News][Coin Relations]  --title--  [Settings]
        JPanel headerBar = createHeaderBar();
        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(headerBar, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        mainPanel.add(headerWrapper, BorderLayout.NORTH);

        // Left side: Graph content (cards with toolbars)
        JPanel leftPanel = createGraphPanel();

        // Right side: Detail panel + Log panel
        JPanel rightPanel = createRightPanel();
        rightPanel.setPreferredSize(new Dimension(400, 0));
        rightPanel.setMinimumSize(new Dimension(300, 0));

        // Main split
        ThinSplitPane mainSplit = new ThinSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplit.setResizeWeight(1.0);

        mainPanel.add(mainSplit, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    private JPanel createHeaderBar() {
        int barHeight = 52;

        JPanel headerBar = new JPanel(new GridBagLayout());
        headerBar.setPreferredSize(new Dimension(0, barHeight));
        headerBar.setMinimumSize(new Dimension(0, barHeight));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;

        // Left: Toggle buttons (with FlatLaf placeholder for macOS traffic lights)
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.WEST;
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setOpaque(false);
        JPanel leftContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftContent.setOpaque(false);
        if (SystemInfo.isMacOS) {
            JPanel buttonsPlaceholder = new JPanel();
            buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
            buttonsPlaceholder.setOpaque(false);
            leftContent.add(buttonsPlaceholder);
        }

        List<PanelConfig> panels = IntelConfig.get().getPanels();
        String[] views = panels.stream().map(PanelConfig::getName).toArray(String[]::new);
        viewToggle = new SegmentedToggle(views);
        viewToggle.setOnSelectionChanged(i -> {
            if (i < panelInstances.size()) {
                cardLayout.show(cardPanel, panelInstances.get(i).config().getId());
            }
            updateHeaderButtons();
        });
        leftContent.add(viewToggle);

        JButton helpBtn = new ToolbarButton("Help");
        helpBtn.addActionListener(e -> IntelHelpDialog.show(this));
        leftContent.add(helpBtn);

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.weightx = 1.0;
        leftPanel.add(leftContent, lc);
        headerBar.add(leftPanel, gbc);

        // Center: Title
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel titleLabel = new JLabel("Intelligence");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(textSecondary());
        headerBar.add(titleLabel, gbc);

        // Right: Action buttons + Settings
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.EAST;
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        JPanel rightContent = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightContent.setOpaque(false);

        // Show: combo (for News view)
        showLabel = new JLabel("Show:");
        showLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        showLabel.setForeground(textSecondary());
        rightContent.add(showLabel);

        limitCombo = new ToolbarComboBox<>(new String[]{"100", "250", "500", "1000"});
        limitCombo.setSelectedItem("500");
        limitCombo.addActionListener(e -> {
            // Update current NEWS_MAP panel's maxArticles and reload
            int idx = viewToggle.getSelectedIndex();
            if (idx >= 0 && idx < panelInstances.size()) {
                PanelInstance pi = panelInstances.get(idx);
                if (pi.config().getType() == PanelConfig.PanelType.NEWS_MAP) {
                    int newMax = Integer.parseInt((String) limitCombo.getSelectedItem());
                    pi.config().setMaxArticles(newMax);
                    ((TimelineGraphPanel) pi.graphPanel()).setMaxNodes(newMax);
                    IntelConfig.get().save();
                    loadNewsData();
                }
            }
        });
        rightContent.add(limitCombo);

        // Fetch New button (for News view)
        fetchBtn = new ToolbarButton("Fetch New");
        fetchBtn.setToolTipText("Fetch new articles with AI extraction");
        fetchBtn.addActionListener(e -> fetchNewArticles());
        rightContent.add(fetchBtn);

        // Reset View button (for Coins view)
        resetViewBtn = new ToolbarButton("Reset View");
        resetViewBtn.addActionListener(e -> {
            CoinGraphPanel current = getCurrentCoinGraphPanel();
            if (current != null) current.resetView();
        });
        resetViewBtn.setVisible(false);  // Hidden by default (News is selected)
        rightContent.add(resetViewBtn);

        JButton entitiesBtn = new ToolbarButton("Entities");
        entitiesBtn.addActionListener(e -> showEntityManager());
        rightContent.add(entitiesBtn);

        JButton dataStructureBtn = new ToolbarButton("Data Structure");
        dataStructureBtn.addActionListener(e -> showDataStructureWindow());
        rightContent.add(dataStructureBtn);

        JButton settingsBtn = new ToolbarButton("Settings");
        settingsBtn.addActionListener(e -> showSettingsWindow());
        rightContent.add(settingsBtn);

        GridBagConstraints rc = new GridBagConstraints();
        rc.anchor = GridBagConstraints.EAST;
        rc.fill = GridBagConstraints.HORIZONTAL;
        rc.weightx = 1.0;
        rightPanel.add(rightContent, rc);
        headerBar.add(rightPanel, gbc);

        return headerBar;
    }

    private JPanel createGraphPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(bgMain());

        // cardLayout and cardPanel already initialized in initUI()
        buildPanelCards();

        panel.add(cardPanel, BorderLayout.CENTER);
        return panel;
    }

    private void buildPanelCards() {
        panelInstances.clear();
        cardPanel.removeAll();

        for (PanelConfig config : IntelConfig.get().getPanels()) {
            JPanel card = new JPanel(new BorderLayout());

            JComponent graphPanel;
            JLabel statusLabel = new JLabel("Loading...");
            statusLabel.setForeground(textSecondary());

            if (config.getType() == PanelConfig.PanelType.NEWS_MAP) {
                TimelineGraphPanel tgp = new TimelineGraphPanel();
                tgp.setSchemaRegistry(schemaRegistry);
                tgp.setOnNodeSelected(this::showArticleDetails);
                tgp.setOnTopicSelected(this::showTopicDetails);
                tgp.setMaxNodes(config.getMaxArticles());
                tgp.setShowLabels(config.isShowLabels());
                tgp.setShowConnections(config.isShowConnections());
                graphPanel = tgp;
            } else {
                CoinGraphPanel cgp = new CoinGraphPanel();
                cgp.setOnEntitySelected(this::showEntityDetails);
                cgp.setShowLabels(config.isShowLabels());
                graphPanel = cgp;
            }

            card.add(graphPanel, BorderLayout.CENTER);

            // Status bar
            JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            statusBar.add(statusLabel);
            if (config.getType() == PanelConfig.PanelType.COIN_GRAPH) {
                coinProgressBar = new JProgressBar(0, 100);
                coinProgressBar.setPreferredSize(new Dimension(150, 16));
                coinProgressBar.setStringPainted(true);
                coinProgressBar.setVisible(false);
                statusBar.add(coinProgressBar);
            }
            JPanel statusWrapper = new JPanel(new BorderLayout());
            statusWrapper.add(new JSeparator(), BorderLayout.NORTH);
            statusWrapper.add(statusBar, BorderLayout.CENTER);
            card.add(statusWrapper, BorderLayout.SOUTH);

            cardPanel.add(card, config.getId());
            panelInstances.add(new PanelInstance(config, card, graphPanel, statusLabel));
        }
    }

    private void updateHeaderButtons() {
        int idx = viewToggle.getSelectedIndex();
        boolean isNewsView = idx < panelInstances.size()
            && panelInstances.get(idx).config().getType() == PanelConfig.PanelType.NEWS_MAP;
        boolean isCoinView = idx < panelInstances.size()
            && panelInstances.get(idx).config().getType() == PanelConfig.PanelType.COIN_GRAPH;
        showLabel.setVisible(isNewsView);
        limitCombo.setVisible(isNewsView);
        fetchBtn.setVisible(isNewsView);
        resetViewBtn.setVisible(isCoinView);
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.WEST);

        // Detail panel (top)
        detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBackground(bgCard());

        JPanel detailHeader = new JPanel(new BorderLayout());
        detailHeader.setBorder(new EmptyBorder(8, 10, 8, 10));

        detailTitleLabel = new JLabel("Details");
        detailTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        detailHeader.add(detailTitleLabel, BorderLayout.WEST);

        detailPanel.add(detailHeader, BorderLayout.NORTH);

        detailContent = new JPanel();
        detailContent.setLayout(new BoxLayout(detailContent, BoxLayout.Y_AXIS));
        detailContent.setBackground(bgCard());
        detailContent.setBorder(new EmptyBorder(10, 10, 10, 10));
        showPlaceholderDetails();

        BorderlessScrollPane detailScroll = new BorderlessScrollPane(detailContent);
        detailScroll.getVerticalScrollBar().setUnitIncrement(16);
        detailPanel.add(detailScroll, BorderLayout.CENTER);

        // Log panel (bottom)
        logPanel = new IntelLogPanel();
        logPanel.setPreferredSize(new Dimension(0, 200));

        // Vertical split
        ThinSplitPane vertSplit = new ThinSplitPane(JSplitPane.VERTICAL_SPLIT, detailPanel, logPanel);
        vertSplit.setResizeWeight(0.7);

        panel.add(vertSplit, BorderLayout.CENTER);
        return panel;
    }

    // ==================== PANEL HELPERS ====================

    private CoinGraphPanel getCurrentCoinGraphPanel() {
        int idx = viewToggle.getSelectedIndex();
        if (idx >= 0 && idx < panelInstances.size()
            && panelInstances.get(idx).graphPanel() instanceof CoinGraphPanel cgp) {
            return cgp;
        }
        return null;
    }

    private CoinGraphPanel getFirstCoinGraphPanel() {
        for (PanelInstance pi : panelInstances) {
            if (pi.graphPanel() instanceof CoinGraphPanel cgp) return cgp;
        }
        return null;
    }

    // ==================== DETAIL PANEL ====================

    private void showPlaceholderDetails() {
        detailContent.removeAll();
        currentMode = DetailMode.NONE;
        detailTitleLabel.setText("Details");

        addDetailLabel("Select an entity or article to see details", textSecondary());
        addDetailSpacer();
        addDetailLabel("Coins tab:", textMuted());
        addDetailLabel("  Click entity to select", textMuted());
        addDetailLabel("  Drag background to pan", textMuted());
        addDetailLabel("  Scroll to zoom", textMuted());
        addDetailLabel("  Double-click to pin", textMuted());

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
            addDetailLabel("... and " + (node.articleCount() - 20) + " more", textMuted());
        }

        detailContent.revalidate();
        detailContent.repaint();
    }

    private void addArticleRow(NewsNode article) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(bgCard());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        // Sentiment indicator
        Color sentColor = article.sentiment() > 0.2 ? new Color(80, 180, 100) :
                          article.sentiment() < -0.2 ? new Color(200, 80, 80) :
                          textMuted();
        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(sentColor);
                g.fillOval(2, 12, 8, 8);
            }
        };
        dot.setPreferredSize(new Dimension(12, 32));
        dot.setBackground(bgCard());
        row.add(dot, BorderLayout.WEST);

        // Title and source
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(bgCard());

        String title = article.title();
        if (title.length() > 50) title = title.substring(0, 47) + "...";
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        titleLabel.setForeground(textPrimary());
        textPanel.add(titleLabel);

        LocalDateTime ldt = LocalDateTime.ofInstant(article.publishedAt(), ZoneId.systemDefault());
        JLabel metaLabel = new JLabel(article.source() + " • " +
            ldt.format(DateTimeFormatter.ofPattern("MMM d HH:mm")));
        metaLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        metaLabel.setForeground(textMuted());
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
                row.setBackground(bgHover());
                textPanel.setBackground(bgHover());
                dot.setBackground(bgHover());
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                row.setBackground(bgCard());
                textPanel.setBackground(bgCard());
                dot.setBackground(bgCard());
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

        CoinGraphPanel activeCoinPanel = getCurrentCoinGraphPanel();
        if (activeCoinPanel == null) activeCoinPanel = getFirstCoinGraphPanel();
        List<CoinRelationship> rels = activeCoinPanel != null ? activeCoinPanel.getRelationshipsFor(entity.id()) : List.of();
        if (!rels.isEmpty()) {
            CoinGraphPanel relPanel = activeCoinPanel;
            addDetailSection("RELATIONSHIPS (" + rels.size() + ")");
            for (CoinRelationship rel : rels) {
                String otherId = rel.fromId().equals(entity.id()) ? rel.toId() : rel.fromId();
                CoinEntity other = relPanel.getEntity(otherId);
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
        btnPanel.setBackground(bgCard());
        btnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JButton searchRelatedBtn = new JButton("Search Related...");
        searchRelatedBtn.addActionListener(e -> {
            EntitySearchDialog dialog = new EntitySearchDialog(this, entity, entityStore);
            dialog.setVisible(true);
            loadCoinData(false);
        });
        btnPanel.add(searchRelatedBtn);

        detailContent.add(btnPanel);

        detailContent.revalidate();
        detailContent.repaint();
    }

    // Detail panel helper methods
    private void addDetailHeader(String text) {
        JLabel label = new JLabel("<html><body style='width: 280px'>" + text + "</body></html>");
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        label.setForeground(textPrimary());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContent.add(label);
    }

    private void addDetailSection(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setForeground(textMuted());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContent.add(label);
        detailContent.add(Box.createVerticalStrut(3));
    }

    private void addDetailLabel(String text) {
        addDetailLabel(text, textPrimary());
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
        area.setForeground(textSecondary());
        area.setBackground(bgHeader());
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
        label.setForeground(linkColor());
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
        Color bg = bgCard();
        Color bgHover = bgHover();

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(bg);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel colorDot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(color);
                g.fillOval(2, 5, 8, 8);
            }
        };
        colorDot.setPreferredSize(new Dimension(12, 20));
        colorDot.setBackground(bg);
        row.add(colorDot, BorderLayout.WEST);

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descLabel.setForeground(textPrimary());
        row.add(descLabel, BorderLayout.CENTER);

        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                CoinGraphPanel cgp = getCurrentCoinGraphPanel();
                if (cgp == null) cgp = getFirstCoinGraphPanel();
                if (cgp != null) cgp.selectAndPanTo(targetId);
            }
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                row.setBackground(bgHover);
                colorDot.setBackground(bgHover);
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                row.setBackground(bg);
                colorDot.setBackground(bg);
            }
        });

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
        List<PanelInstance> newsPanels = panelInstances.stream()
            .filter(pi -> pi.config().getType() == PanelConfig.PanelType.NEWS_MAP)
            .toList();
        if (newsPanels.isEmpty()) return;

        for (PanelInstance pi : newsPanels) pi.statusLabel().setText("Loading...");
        logPanel.data("Loading news articles...");

        // Use the max limit across all news panels
        int maxLimit = newsPanels.stream()
            .mapToInt(pi -> pi.config().getMaxArticles())
            .max().orElse(500);

        SwingWorker<List<Article>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Article> doInBackground() {
                return store.getArticles(SqliteNewsStore.ArticleQuery.all(maxLimit));
            }

            @Override
            protected void done() {
                try {
                    List<Article> articles = get();
                    for (PanelInstance pi : newsPanels) {
                        TimelineGraphPanel tgp = (TimelineGraphPanel) pi.graphPanel();
                        int limit = pi.config().getMaxArticles();
                        List<Article> subset = articles.size() <= limit ? articles : articles.subList(0, limit);
                        tgp.setArticles(subset);
                        pi.statusLabel().setText(subset.size() + " articles  |  " + store.getArticleCount() + " total");
                    }
                    logPanel.success("Loaded " + articles.size() + " news articles");
                } catch (Exception e) {
                    for (PanelInstance pi : newsPanels) pi.statusLabel().setText("Error: " + e.getMessage());
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

        List<PanelInstance> newsPanels = panelInstances.stream()
            .filter(pi -> pi.config().getType() == PanelConfig.PanelType.NEWS_MAP)
            .toList();
        for (PanelInstance pi : newsPanels) pi.statusLabel().setText("Fetching...");
        logPanel.ai("Starting AI-powered news fetch...");

        SwingWorker<DataSource.FetchResult, String> worker = new SwingWorker<>() {
            @Override
            protected DataSource.FetchResult doInBackground() {
                return sourceRegistry.refresh("rss", true, (msg, pct) ->
                    publish(msg));
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    logPanel.data(msg);
                }
            }

            @Override
            protected void done() {
                fetching = false;
                fetchBtn.setEnabled(true);
                fetchBtn.setText("Fetch New");
                try {
                    DataSource.FetchResult result = get();
                    if (result.entitiesAdded() > 0) {
                        for (PanelInstance pi : newsPanels) {
                            TimelineGraphPanel tgp = (TimelineGraphPanel) pi.graphPanel();
                            int limit = pi.config().getMaxArticles();
                            List<Article> allArticles = store.getArticles(SqliteNewsStore.ArticleQuery.all(limit));
                            int added = tgp.addArticles(allArticles);
                            pi.statusLabel().setText(added + " new  |  " + store.getArticleCount() + " total");
                        }
                        logPanel.success(result.message());
                    } else {
                        for (PanelInstance pi : newsPanels) {
                            pi.statusLabel().setText("No new articles  |  " + store.getArticleCount() + " total");
                        }
                        logPanel.info("No new articles found");
                    }
                } catch (Exception e) {
                    for (PanelInstance pi : newsPanels) pi.statusLabel().setText("Error: " + e.getMessage());
                    logPanel.error("Fetch failed: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void loadCoinData(boolean forceRefresh) {
        List<PanelInstance> coinPanels = panelInstances.stream()
            .filter(pi -> pi.config().getType() == PanelConfig.PanelType.COIN_GRAPH)
            .toList();
        if (coinPanels.isEmpty()) return;

        for (PanelInstance pi : coinPanels) pi.statusLabel().setText("Loading...");
        logPanel.data("Loading coin entities...");

        SwingWorker<DataSource.FetchResult, String> worker = new SwingWorker<>() {
            @Override
            protected DataSource.FetchResult doInBackground() {
                return sourceRegistry.refresh("coingecko", forceRefresh, (msg, pct) -> {
                    publish(msg);
                    SwingUtilities.invokeLater(() -> {
                        if (coinProgressBar != null) {
                            if (pct > 0 && pct < 100) {
                                coinProgressBar.setVisible(true);
                                coinProgressBar.setValue(pct);
                                coinProgressBar.setString(pct + "%");
                            } else {
                                coinProgressBar.setVisible(false);
                            }
                        }
                    });
                });
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    for (PanelInstance pi : coinPanels) pi.statusLabel().setText(msg);
                    logPanel.data(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    DataSource.FetchResult result = get();

                    // Reload all data from store
                    List<CoinEntity> allEntities = new ArrayList<>();
                    allEntities.addAll(entityStore.loadEntitiesBySource("coingecko"));
                    allEntities.addAll(entityStore.loadEntitiesBySource("manual"));
                    List<CoinRelationship> allRels = entityStore.loadAllRelationships();

                    currentEntities = allEntities;
                    currentRelationships = allRels;

                    // Feed each coin panel with filtered data
                    for (PanelInstance pi : coinPanels) {
                        CoinGraphPanel cgp = (CoinGraphPanel) pi.graphPanel();
                        feedCoinPanel(cgp, pi.config(), pi.statusLabel());
                    }
                    if (coinProgressBar != null) coinProgressBar.setVisible(false);

                    logPanel.success(result.message());
                } catch (Exception e) {
                    // Fallback to sample data
                    List<CoinEntity> entities = new ArrayList<>();
                    List<CoinRelationship> relationships = new ArrayList<>();
                    loadSampleData(entities, relationships);
                    currentEntities = entities;
                    currentRelationships = relationships;
                    for (PanelInstance pi : coinPanels) {
                        CoinGraphPanel cgp = (CoinGraphPanel) pi.graphPanel();
                        cgp.setData(entities, relationships);
                        pi.statusLabel().setText(entities.size() + " entities (sample)");
                    }
                    if (coinProgressBar != null) coinProgressBar.setVisible(false);
                    logPanel.error("Failed to load coin data: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void feedCoinPanel(CoinGraphPanel cgp, PanelConfig config, JLabel statusLabel) {
        List<CoinEntity> entities = currentEntities;
        List<CoinRelationship> rels = currentRelationships;

        // Apply entity type filter
        if (config.getEntityTypeFilter() != null && !config.getEntityTypeFilter().isEmpty()) {
            Set<String> typeFilter = config.getEntityTypeFilter();
            entities = entities.stream()
                .filter(e -> typeFilter.contains(e.type().name().toLowerCase()))
                .toList();
        }

        // Apply entity source filter
        if (config.getEntitySourceFilter() != null && !config.getEntitySourceFilter().isEmpty()) {
            Set<String> sourceFilter = config.getEntitySourceFilter();
            List<CoinEntity> filtered = new ArrayList<>();
            for (String source : sourceFilter) {
                filtered.addAll(entityStore.loadEntitiesBySource(source));
            }
            // Intersect with type-filtered entities
            Set<String> filteredIds = new HashSet<>();
            for (CoinEntity e : filtered) filteredIds.add(e.id());
            entities = entities.stream()
                .filter(e -> filteredIds.contains(e.id()))
                .toList();
        }

        // Filter relationships to only those between remaining entities
        Set<String> entityIds = new HashSet<>();
        for (CoinEntity e : entities) entityIds.add(e.id());
        List<CoinRelationship> filteredRels = rels.stream()
            .filter(r -> entityIds.contains(r.fromId()) && entityIds.contains(r.toId()))
            .toList();

        cgp.setData(new ArrayList<>(entities), new ArrayList<>(filteredRels));

        int manual = entityStore.getManualEntityCount();
        String status = entities.size() + " entities  |  " + filteredRels.size() + " rels";
        if (manual > 0) status += "  |  " + manual + " manual";
        statusLabel.setText(status);
    }

    private void refreshAllCoinPanels() {
        for (PanelInstance pi : panelInstances) {
            if (pi.graphPanel() instanceof CoinGraphPanel cgp) {
                feedCoinPanel(cgp, pi.config(), pi.statusLabel());
            }
        }
    }

    /**
     * Rebuild all panels from updated config. Called from settings dialog.
     */
    public void rebuildPanels() {
        // Stop physics on existing panels
        for (PanelInstance pi : panelInstances) {
            if (pi.graphPanel() instanceof TimelineGraphPanel tgp) tgp.stopPhysics();
            if (pi.graphPanel() instanceof CoinGraphPanel cgp) cgp.stopPhysics();
        }

        // Rebuild cards
        buildPanelCards();

        // Rebuild toggle in header
        List<PanelConfig> panels = IntelConfig.get().getPanels();
        String[] names = panels.stream().map(PanelConfig::getName).toArray(String[]::new);

        // Find the leftContent panel that contains the toggle and replace it
        // The toggle's parent is leftContent (FlowLayout panel)
        Container toggleParent = viewToggle.getParent();
        if (toggleParent != null) {
            toggleParent.remove(viewToggle);
            viewToggle = new SegmentedToggle(names);
            viewToggle.setOnSelectionChanged(i -> {
                if (i < panelInstances.size()) {
                    cardLayout.show(cardPanel, panelInstances.get(i).config().getId());
                }
                updateHeaderButtons();
            });
            // Insert toggle at position 1 (after macOS placeholder or at start)
            int insertIdx = toggleParent.getComponentCount() > 0
                && toggleParent.getComponent(0) instanceof JPanel ? 1 : 0;
            toggleParent.add(viewToggle, insertIdx);
            toggleParent.revalidate();
        }

        // Show first panel
        if (!panelInstances.isEmpty()) {
            cardLayout.show(cardPanel, panelInstances.get(0).config().getId());
        }
        updateHeaderButtons();

        // Reload data into new panels
        loadCoinData(false);
        loadNewsData();

        cardPanel.revalidate();
        cardPanel.repaint();
    }

    SchemaRegistry getSchemaRegistry() { return schemaRegistry; }

    // ==================== DIALOGS ====================

    private void openWindow(String windowName) {
        switch (windowName) {
            case "data-structure" -> showDataStructureWindow();
            case "settings" -> showSettingsWindow();
            default -> {
                // Bring main frame to front
                toFront();
                requestFocus();
            }
        }
    }

    private void showDataStructureWindow() {
        if (dataStructureFrame != null && dataStructureFrame.isShowing()) {
            dataStructureFrame.toFront();
            dataStructureFrame.requestFocus();
            return;
        }
        logPanel.info("Opening Data Structure...");
        dataStructureFrame = new DataStructureFrame(entityStore, schemaRegistry, v -> loadCoinData(false));
        dataStructureFrame.setVisible(true);
    }

    private void showEntityManager() {
        logPanel.info("Opening Entity Manager...");
        EntityManagerFrame entityManager = new EntityManagerFrame(entityStore, v -> loadCoinData(false));
        entityManager.setSchemaRegistry(schemaRegistry);
        entityManager.setVisible(true);
    }

    private void showSettingsWindow() {
        logPanel.info("Opening Settings...");
        IntelSettingsDialog dialog = new IntelSettingsDialog(this);
        dialog.setVisible(true);
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
                    refreshAllCoinPanels();
                    logPanel.success("Added relationship: " + rel.type().label());
                }
            }
        );
        dialog.setVisible(true);
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
            // Apply saved theme (or default)
            IntelConfig.applyCurrentTheme();
            UIManager.put("Button.arc", 5);
            UIManager.put("Component.arc", 5);
            UIManager.put("TextComponent.arc", 5);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Check license before proceeding
        LicenseGate.checkOrExit(false);

        // Check for updates (non-blocking)
        String version = System.getProperty("tradery.version", "1.0.0");
        UpdateChecker.checkAsync(version, "https://plaiiin.com/api/app/intelligence/latest.json");

        SwingUtilities.invokeLater(() -> {
            // First-run: show setup if no AI profiles configured
            if (AiConfig.get().getProfiles().isEmpty()) {
                AiSetupDialog.showSetup(null);
            }

            // Wire up AI activity logging to the log panel
            AiClient.getInstance().setActivityListener((summary, prompt, response) ->
                IntelLogPanel.logAI(summary, prompt, response));

            new IntelFrame().setVisible(true);
        });
    }
}
