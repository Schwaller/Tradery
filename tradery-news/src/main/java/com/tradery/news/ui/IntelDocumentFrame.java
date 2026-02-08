package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
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
import java.util.function.Consumer;

/**
 * Document window for one open intel document.
 * Replaces the old IntelFrame. Receives per-document stores via constructor.
 */
public class IntelDocumentFrame extends JFrame {

    // Per-document stores
    private final Path docDir;
    private final String docId;
    private final SqliteNewsStore store;
    private EntityStore entityStore;
    private SchemaRegistry schemaRegistry;
    private DataSourceRegistry sourceRegistry;
    private final DocumentServices services;
    private final Consumer<String> onClosed;

    EntityStore getEntityStore() { return entityStore; }
    SchemaRegistry getSchemaRegistry() { return schemaRegistry; }
    DocumentServices getDocumentServices() { return services; }
    Path getDocDir() { return docDir; }

    // Dynamic panel instances
    private record PanelInstance(
        PanelConfig config,
        JPanel card,
        JComponent graphPanel,
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
        int minutes = services.getFetchIntervalMinutes();
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

    // Sharing
    private static SharingService sharingService;
    public static void setSharingService(SharingService s) { sharingService = s; }

    // Chat
    private ChatPanel chatPanel;
    private JSplitPane chatSplit;
    private JButton chatBtn;
    private boolean chatVisible;

    // Singleton windows
    private DataStructureFrame dataStructureFrame;

    // API server
    private IntelApiServer apiServer;

    // Current selection
    private enum DetailMode { NONE, ARTICLE, ENTITY }
    private DetailMode currentMode = DetailMode.NONE;
    private NewsNode selectedArticle;
    private CoinEntity selectedEntity;

    // Theme-aware colors
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

    /**
     * @param docId   document ID
     * @param docName document display name
     * @param docDir  document directory (contains facts.db, news.db, services.yaml)
     * @param services  per-document service configuration
     * @param onClosed callback when this window closes, receives docId
     */
    public IntelDocumentFrame(String docId, String docName, Path docDir,
                               DocumentServices services, Consumer<String> onClosed) {
        super(docName + " \u2014 Intelligence");
        this.docId = docId;
        this.docDir = docDir;
        this.services = services;
        this.onClosed = onClosed;

        // Initialize per-document stores
        this.store = new SqliteNewsStore(docDir.resolve("news.db"));
        this.entityStore = new EntityStore(docDir.resolve("facts.db"));
        this.schemaRegistry = new SchemaRegistry(entityStore);
        this.sourceRegistry = new DataSourceRegistry(entityStore, schemaRegistry);

        // Register data sources based on template services
        if (services.isSourceEnabled("coingecko")) {
            sourceRegistry.register(new CoinGeckoSource());
        }
        if (services.isSourceEnabled("rss")) {
            sourceRegistry.register(new RssNewsSource(store, docDir));
        }

        // Register with sharing service for multi-device sync
        if (sharingService != null) {
            sharingService.registerDocument(docId, docDir, entityStore);
        }

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Transparent title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        // Restore window size from config
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
            EntitySearchProcessor searchProcessor = new EntitySearchProcessor(schemaRegistry);
            apiServer = new IntelApiServer(this::openWindow, entityStore, store, searchProcessor, schemaRegistry);
            apiServer.start();
        } catch (Exception e) {
            System.err.println("Failed to start Intel API server: " + e.getMessage());
        }

        // Save window state and cleanup on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                IntelConfig cfg = IntelConfig.get();
                cfg.setWindowWidth(getWidth());
                cfg.setWindowHeight(getHeight());
                cfg.setWindowX(getX());
                cfg.setWindowY(getY());
                cfg.save();

                if (autoFetchTimer != null) autoFetchTimer.stop();
                if (chatPanel != null) chatPanel.dispose();
                if (apiServer != null) apiServer.stop();
                for (PanelInstance pi : panelInstances) {
                    if (pi.graphPanel() instanceof TimelineGraphPanel tgp) tgp.stopPhysics();
                    if (pi.graphPanel() instanceof CoinGraphPanel cgp) cgp.stopPhysics();
                }
                if (sharingService != null) sharingService.unregisterDocument(docId);
                if (entityStore != null) entityStore.close();
                if (onClosed != null) onClosed.accept(docId);
            }
        });

        // Load data
        SwingUtilities.invokeLater(() -> {
            logPanel.info("Opening document: " + docName);
            if (services.isSourceEnabled("coingecko")) loadCoinData(false);
            if (services.isSourceEnabled("rss")) loadNewsData();
            updateAutoFetchTimer();
        });
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(bgMain());

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        JPanel headerBar = createHeaderBar();
        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(headerBar, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        mainPanel.add(headerWrapper, BorderLayout.NORTH);

        JPanel leftPanel = createGraphPanel();

        JPanel rightPanel = createRightPanel();
        rightPanel.setPreferredSize(new Dimension(400, 0));
        rightPanel.setMinimumSize(new Dimension(300, 0));

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

        // Left: Toggle buttons
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

        List<PanelConfig> panels = services.getPanels();
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

        // Right: Action buttons
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
            int idx = viewToggle.getSelectedIndex();
            if (idx >= 0 && idx < panelInstances.size()) {
                PanelInstance pi = panelInstances.get(idx);
                if (pi.config().getType() == PanelConfig.PanelType.NEWS_MAP) {
                    int newMax = Integer.parseInt((String) limitCombo.getSelectedItem());
                    pi.config().setMaxArticles(newMax);
                    ((TimelineGraphPanel) pi.graphPanel()).setMaxNodes(newMax);
                    services.save(docDir);
                    loadNewsData();
                }
            }
        });
        rightContent.add(limitCombo);

        fetchBtn = new ToolbarButton("Fetch New");
        fetchBtn.setToolTipText("Fetch new articles with AI extraction");
        fetchBtn.addActionListener(e -> fetchNewArticles());
        rightContent.add(fetchBtn);

        resetViewBtn = new ToolbarButton("Reset View");
        resetViewBtn.addActionListener(e -> {
            CoinGraphPanel current = getCurrentCoinGraphPanel();
            if (current != null) current.resetView();
        });
        resetViewBtn.setVisible(false);
        rightContent.add(resetViewBtn);

        // Pending changes indicator
        JPanel pendingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pendingPanel.setOpaque(false);
        JLabel pendingLabel = new JLabel();
        pendingLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        pendingLabel.setForeground(new Color(255, 180, 80));
        JButton commitBtn = new ToolbarButton("Commit");
        commitBtn.setToolTipText("Commit all pending changes");
        JButton discardBtn = new ToolbarButton("Discard");
        discardBtn.setToolTipText("Discard all pending changes");
        pendingPanel.add(pendingLabel);
        pendingPanel.add(commitBtn);
        pendingPanel.add(discardBtn);
        pendingPanel.setVisible(false);

        commitBtn.addActionListener(e -> {
            String commitId = entityStore.commit();
            if (commitId != null) {
                logPanel.success("Committed changes (" + commitId.substring(0, 8) + ")");
                loadCoinData(false);
            }
        });
        discardBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                "Discard all pending changes? This cannot be undone.",
                "Discard Changes", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                entityStore.rollback();
                logPanel.info("Discarded pending changes");
                loadCoinData(false);
            }
        });

        Runnable updatePending = () -> {
            int count = entityStore.getPendingCount();
            pendingPanel.setVisible(count > 0);
            pendingLabel.setText(count + " pending");
        };
        entityStore.setOnPendingChanged(updatePending);
        SwingUtilities.invokeLater(updatePending);

        rightContent.add(pendingPanel);

        JButton shareBtn = new ToolbarButton("Share");
        shareBtn.addActionListener(e -> showShareDialog());
        shareBtn.setVisible(sharingService != null);
        rightContent.add(shareBtn);

        chatBtn = new ToolbarButton("Chat");
        chatBtn.addActionListener(e -> toggleChat());
        chatBtn.setVisible(sharingService != null);
        rightContent.add(chatBtn);

        JButton entitiesBtn = new ToolbarButton("Entities");
        entitiesBtn.addActionListener(e -> showEntityManager());
        rightContent.add(entitiesBtn);

        JButton dataStructureBtn = new ToolbarButton("Data Structure");
        dataStructureBtn.addActionListener(e -> showDataStructureWindow());
        rightContent.add(dataStructureBtn);

        JButton settingsBtn = new ToolbarButton("Settings");
        settingsBtn.addActionListener(e -> showSettingsWindow());
        rightContent.add(settingsBtn);

        rightContent.add(Box.createHorizontalStrut(6));
        rightContent.add(createUserAvatar());

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
        buildPanelCards();
        panel.add(cardPanel, BorderLayout.CENTER);
        return panel;
    }

    private void buildPanelCards() {
        panelInstances.clear();
        cardPanel.removeAll();

        for (PanelConfig config : services.getPanels()) {
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
                if (config.getBands() != null) tgp.setBandConfigs(config.getBands());
                graphPanel = tgp;
            } else {
                CoinGraphPanel cgp = new CoinGraphPanel();
                cgp.setOnEntitySelected(this::showEntityDetails);
                cgp.setShowLabels(config.isShowLabels());
                graphPanel = cgp;
            }

            card.add(graphPanel, BorderLayout.CENTER);

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

        logPanel = new IntelLogPanel();
        logPanel.setPreferredSize(new Dimension(0, 200));

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
        if ("coin".equals(node.typeId()) && currentEntities != null) {
            String symbol = node.id().contains(":") ? node.id().substring(node.id().indexOf(':') + 1) : node.id();
            for (CoinEntity e : currentEntities) {
                if (symbol.equalsIgnoreCase(e.symbol())) {
                    showEntityDetails(e);
                    return;
                }
            }
        }

        selectedArticle = null;
        selectedEntity = null;
        currentMode = DetailMode.NONE;

        boolean isCoin = "coin".equals(node.typeId());
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
        JLabel metaLabel = new JLabel(article.source() + " \u2022 " +
            ldt.format(DateTimeFormatter.ofPattern("MMM d HH:mm")));
        metaLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        metaLabel.setForeground(textMuted());
        textPanel.add(metaLabel);

        row.add(textPanel, BorderLayout.CENTER);

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

        if (entity.isPinned()) {
            addDetailSection("STATUS");
            addDetailLabel("Pinned");
        }

        // Custom attributes from schema
        addEntityCustomAttributes(entity);

        addDetailSpacer();
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        btnPanel.setBackground(bgCard());
        btnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JButton searchRelatedBtn = new JButton("Search Related...");
        searchRelatedBtn.addActionListener(e -> {
            EntitySearchDialog dialog = new EntitySearchDialog(this, entity, entityStore, schemaRegistry);
            dialog.setVisible(true);
            loadCoinData(false);
        });
        btnPanel.add(searchRelatedBtn);

        detailContent.add(btnPanel);

        detailContent.revalidate();
        detailContent.repaint();
    }

    private void addEntityCustomAttributes(CoinEntity entity) {
        if (schemaRegistry == null) return;

        String typeId = entity.type().name().toLowerCase();
        SchemaType schemaType = schemaRegistry.getType(typeId);
        if (schemaType == null || schemaType.attributes().isEmpty()) return;

        Map<String, AttributeValue> richValues = entityStore.getAttributeValuesRich(entity.id(), typeId);

        // Determine which attributes to show
        List<SchemaAttribute> attrsToShow = new java.util.ArrayList<>();
        List<FormLayout> layouts = schemaType.formLayouts();
        if (layouts != null && !layouts.isEmpty()) {
            FormLayout layout = layouts.get(0);
            for (FormLayout.FormLayoutField f : layout.fields()) {
                SchemaAttribute attr = schemaType.attributes().stream()
                    .filter(a -> a.name().equals(f.attributeName()))
                    .findFirst().orElse(null);
                if (attr != null) attrsToShow.add(attr);
            }
        } else {
            attrsToShow.addAll(schemaType.attributes());
        }

        boolean hasAny = false;
        for (SchemaAttribute attr : attrsToShow) {
            if ("name".equals(attr.name()) || "symbol".equals(attr.name()) ||
                "market_cap".equals(attr.name())) continue;
            AttributeValue av = richValues.get(attr.name());
            if (av == null || av.value() == null || av.value().isEmpty()) continue;

            if (!hasAny) {
                addDetailSpacer();
                addDetailSection("ATTRIBUTES");
                hasAny = true;
            }

            String displayLabel = attr.displayName(java.util.Locale.getDefault());
            addDetailLabel(displayLabel + ": " + av.value(), textSecondary());
        }
    }

    // Detail panel helpers
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

    private void addDetailLabel(String text) { addDetailLabel(text, textPrimary()); }

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

    private void addDetailSpacer() { detailContent.add(Box.createVerticalStrut(12)); }

    private void addRelationshipRow(String description, Color color, String targetId, String targetName) {
        Color bg = bgCard();
        Color hover = bgHover();

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
                row.setBackground(hover);
                colorDot.setBackground(hover);
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
        SchemaType relSchema = schemaRegistry.getType(type.name().toLowerCase());
        if (relSchema != null) {
            return relSchema.inverseDescription(otherName);
        }
        return type.label() + " " + otherName;
    }

    // ==================== DATA LOADING ====================

    private void loadNewsData() {
        List<PanelInstance> newsPanels = panelInstances.stream()
            .filter(pi -> pi.config().getType() == PanelConfig.PanelType.NEWS_MAP)
            .toList();
        if (newsPanels.isEmpty()) return;

        for (PanelInstance pi : newsPanels) pi.statusLabel().setText("Loading...");
        logPanel.data("Loading news articles...");

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
                return sourceRegistry.refresh("rss", true, (msg, pct) -> publish(msg));
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) logPanel.data(msg);
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

                    List<CoinEntity> allEntities = new ArrayList<>();
                    allEntities.addAll(entityStore.loadEntitiesBySource("coingecko"));
                    allEntities.addAll(entityStore.loadEntitiesBySource("manual"));
                    List<CoinRelationship> allRels = entityStore.loadAllRelationships();

                    currentEntities = allEntities;
                    currentRelationships = allRels;

                    for (PanelInstance pi : coinPanels) {
                        CoinGraphPanel cgp = (CoinGraphPanel) pi.graphPanel();
                        feedCoinPanel(cgp, pi.config(), pi.statusLabel());
                    }
                    if (coinProgressBar != null) coinProgressBar.setVisible(false);

                    logPanel.success(result.message());
                } catch (Exception e) {
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

        if (config.getEntityTypeFilter() != null && !config.getEntityTypeFilter().isEmpty()) {
            Set<String> typeFilter = config.getEntityTypeFilter();
            entities = entities.stream()
                .filter(e -> typeFilter.contains(e.type().name().toLowerCase()))
                .toList();
        }

        if (config.getEntitySourceFilter() != null && !config.getEntitySourceFilter().isEmpty()) {
            Set<String> sourceFilter = config.getEntitySourceFilter();
            List<CoinEntity> filtered = new ArrayList<>();
            for (String source : sourceFilter) {
                filtered.addAll(entityStore.loadEntitiesBySource(source));
            }
            Set<String> filteredIds = new HashSet<>();
            for (CoinEntity e : filtered) filteredIds.add(e.id());
            entities = entities.stream()
                .filter(e -> filteredIds.contains(e.id()))
                .toList();
        }

        Set<String> entityIds = new HashSet<>();
        for (CoinEntity e : entities) entityIds.add(e.id());
        List<CoinRelationship> filteredRels = rels.stream()
            .filter(r -> entityIds.contains(r.fromId()) && entityIds.contains(r.toId()))
            .toList();

        if (config.getRelationshipTypeFilter() != null && !config.getRelationshipTypeFilter().isEmpty()) {
            Set<String> relTypeFilter = config.getRelationshipTypeFilter();
            filteredRels = filteredRels.stream()
                .filter(r -> relTypeFilter.contains(r.type().name().toLowerCase()))
                .toList();
        }

        if (!config.isShowConnections()) {
            filteredRels = List.of();
        }

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

    public void rebuildPanels() {
        for (PanelInstance pi : panelInstances) {
            if (pi.graphPanel() instanceof TimelineGraphPanel tgp) tgp.stopPhysics();
            if (pi.graphPanel() instanceof CoinGraphPanel cgp) cgp.stopPhysics();
        }

        buildPanelCards();

        List<PanelConfig> panels = services.getPanels();
        String[] names = panels.stream().map(PanelConfig::getName).toArray(String[]::new);

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
            int insertIdx = toggleParent.getComponentCount() > 0
                && toggleParent.getComponent(0) instanceof JPanel ? 1 : 0;
            toggleParent.add(viewToggle, insertIdx);
            toggleParent.revalidate();
        }

        if (!panelInstances.isEmpty()) {
            cardLayout.show(cardPanel, panelInstances.get(0).config().getId());
        }
        updateHeaderButtons();

        if (services.isSourceEnabled("coingecko")) loadCoinData(false);
        if (services.isSourceEnabled("rss")) loadNewsData();

        cardPanel.revalidate();
        cardPanel.repaint();
    }

    // ==================== USER AVATAR ====================

    private JButton userAvatarBtn;

    private JButton createUserAvatar() {
        userAvatarBtn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int size = Math.min(getWidth(), getHeight()) - 2;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                String email = IntelConfig.get().getUserEmail();
                boolean loggedIn = email != null && !email.isBlank();

                // Circle background
                g2.setColor(loggedIn
                    ? new Color(76, 148, 255)     // blue for logged in
                    : UIManager.getColor("Button.default.borderColor") != null
                        ? UIManager.getColor("Button.default.borderColor")
                        : Color.GRAY);
                g2.fillOval(x, y, size, size);

                // Text/icon
                g2.setColor(Color.WHITE);
                if (loggedIn) {
                    // Show first letter of email
                    String initial = email.substring(0, 1).toUpperCase();
                    g2.setFont(new Font("SansSerif", Font.BOLD, size / 2));
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = x + (size - fm.stringWidth(initial)) / 2;
                    int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(initial, tx, ty);
                } else {
                    // Person silhouette — head + shoulders
                    int cx = x + size / 2;
                    int headR = size / 5;
                    g2.fillOval(cx - headR, y + size / 4 - headR, headR * 2, headR * 2);
                    g2.fillArc(cx - size / 3, y + size / 2, size * 2 / 3, size / 2, 0, 180);
                }

                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() { return new Dimension(32, 32); }
            @Override
            public Dimension getMinimumSize() { return getPreferredSize(); }
            @Override
            public Dimension getMaximumSize() { return getPreferredSize(); }
        };

        userAvatarBtn.setContentAreaFilled(false);
        userAvatarBtn.setBorderPainted(false);
        userAvatarBtn.setFocusPainted(false);
        userAvatarBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String email = IntelConfig.get().getUserEmail();
        userAvatarBtn.setToolTipText(email != null && !email.isBlank()
            ? email : "Not logged in — click to set identity");

        userAvatarBtn.addActionListener(e -> {
            String avatarEmail = IntelConfig.get().getUserEmail();
            if (avatarEmail == null || avatarEmail.isBlank()) {
                // Not logged in — go straight to share dialog for identity setup
                showShareDialog();
                return;
            }
            // Show popup menu
            JPopupMenu menu = new JPopupMenu();
            JMenuItem emailItem = new JMenuItem(avatarEmail);
            emailItem.setEnabled(false);
            menu.add(emailItem);
            menu.addSeparator();
            JMenuItem shareItem = new JMenuItem("Share Document");
            shareItem.addActionListener(ev -> showShareDialog());
            menu.add(shareItem);
            JMenuItem friendsItem = new JMenuItem("Friends");
            friendsItem.addActionListener(ev -> showFriendsDialog());
            menu.add(friendsItem);
            JMenuItem chatItem = new JMenuItem("Chat");
            chatItem.addActionListener(ev -> toggleChat());
            menu.add(chatItem);
            menu.show(userAvatarBtn, 0, userAvatarBtn.getHeight());
        });

        return userAvatarBtn;
    }

    // ==================== DIALOGS ====================

    private void openWindow(String windowName) {
        switch (windowName) {
            case "data-structure" -> showDataStructureWindow();
            case "settings" -> showSettingsWindow();
            default -> { toFront(); requestFocus(); }
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

    private void showShareDialog() {
        if (sharingService == null) return;
        logPanel.info("Opening Share settings...");
        ShareDialog dialog = new ShareDialog(this, docId, docDir, entityStore, sharingService, logPanel);
        dialog.setVisible(true);
    }

    private void showFriendsDialog() {
        if (sharingService == null) return;
        FriendsDialog dialog = new FriendsDialog(this, sharingService);
        dialog.setVisible(true);
    }

    private void toggleChat() {
        if (sharingService == null) return;
        chatVisible = !chatVisible;

        if (chatVisible) {
            if (chatPanel == null) {
                chatPanel = new ChatPanel(sharingService);
                chatPanel.setOnUnreadChanged(() -> updateChatBadge());
            }
            chatPanel.clearUnread();
            updateChatBadge();

            // Wrap the main content in a split pane with chat on right
            Container contentPane = getContentPane();
            Component mainContent = contentPane.getComponent(0);
            contentPane.removeAll();

            chatSplit = new com.tradery.ui.controls.ThinSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, (JComponent) mainContent, chatPanel);
            chatSplit.setResizeWeight(1.0);
            chatSplit.setDividerLocation(getWidth() - 300);

            contentPane.add(chatSplit, BorderLayout.CENTER);
            contentPane.revalidate();
            contentPane.repaint();
            chatBtn.setText("Chat");
        } else {
            if (chatSplit != null) {
                Component mainContent = chatSplit.getLeftComponent();
                Container contentPane = getContentPane();
                contentPane.removeAll();
                contentPane.add(mainContent, BorderLayout.CENTER);
                contentPane.revalidate();
                contentPane.repaint();
                chatSplit = null;
            }
        }
    }

    private void updateChatBadge() {
        if (chatPanel == null || chatBtn == null) return;
        int unread = chatPanel.getUnreadCount();
        chatBtn.setText(unread > 0 ? "Chat (" + unread + ")" : "Chat");
    }

    private void showSettingsWindow() {
        logPanel.info("Opening Settings...");
        IntelSettingsDialog dialog = new IntelSettingsDialog(this);
        dialog.setVisible(true);
    }

    private void loadSampleData(List<CoinEntity> entities, List<CoinRelationship> relationships) {
        entities.add(new CoinEntity("bitcoin", "Bitcoin", "BTC", CoinEntity.Type.COIN));
        entities.add(new CoinEntity("ethereum", "Ethereum", "ETH", CoinEntity.Type.COIN));
        entities.add(new CoinEntity("solana", "Solana", "SOL", CoinEntity.Type.COIN));
        entities.add(new CoinEntity("arbitrum", "Arbitrum", "ARB", CoinEntity.Type.L2, "ethereum"));
        relationships.add(new CoinRelationship("arbitrum", "ethereum", CoinRelationship.Type.L2_OF));
        entities.add(new CoinEntity("ibit", "iShares Bitcoin Trust", "IBIT", CoinEntity.Type.ETF));
        relationships.add(new CoinRelationship("ibit", "bitcoin", CoinRelationship.Type.ETF_TRACKS));
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
}
