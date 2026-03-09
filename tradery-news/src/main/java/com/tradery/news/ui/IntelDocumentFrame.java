package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.ai.challenges.execution.ChallengeExecutor;
import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeEscalation;
import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.model.SignalConfig;
import com.tradery.ai.challenges.schedule.ChallengeScheduler;
import com.tradery.ai.challenges.store.ChallengeStore;
import com.tradery.ai.challenges.subject.ChallengeSubject;
import com.tradery.news.model.Article;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.api.IntelApiServer;
import com.tradery.news.source.*;
import com.tradery.news.ui.challenges.*;
import com.tradery.news.ui.coin.*;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.SegmentedToggle;
import com.tradery.ui.controls.ThinSplitPane;
import com.tradery.ui.controls.ToolbarButton;
import com.tradery.ui.controls.StatusBadge;

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
import java.util.LinkedHashMap;
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
        JComponent graphPanel
    ) {}
    private List<PanelInstance> panelInstances = new ArrayList<>();

    // Challenges panel (always added as last card, not in panelInstances)
    private static final String CHALLENGES_CARD_ID = "__challenges__";
    private JPanel challengesCard;
    private JPanel challengesContent;

    // News state
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

    // Shared components
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private SegmentedToggle viewToggle;

    // Header action buttons (shown conditionally)
    private JButton fetchBtn;
    private JButton resetViewBtn;
    private JPanel detailPanel;
    private JPanel detailHeader;
    private JPanel detailContent;
    private JLabel detailTitleLabel;
    private IntelLogPanel logPanel;

    // Sharing
    private static SharingService sharingService;
    public static void setSharingService(SharingService s) { sharingService = s; }
    public static SharingService getSharingService() { return sharingService; }

    // Chat
    private JButton chatBtn;
    private static ChatStore chatStore;
    public static void setChatStore(ChatStore s) { chatStore = s; }
    public static ChatStore getChatStore() { return chatStore; }

    // Network status bar
    private javax.swing.Timer networkStatusTimer;
    private StatusBadge[] networkBadges;

    // Singleton windows
    private DataStructureFrame dataStructureFrame;
    private NetworkStatusDialog networkStatusDialog;

    // API server
    private IntelApiServer apiServer;

    // Challenges
    private ChallengeStore challengeStore;
    private ChallengeExecutor challengeExecutor;
    private ChallengeScheduler challengeScheduler;

    // Curated mode (USER_CURATED governance)
    private boolean isCuratedMode = false;
    private JButton browsePoolBtn;

    // Current selection
    private enum DetailMode { NONE, ARTICLE, ENTITY }
    private DetailMode currentMode = DetailMode.NONE;
    private NewsNode selectedArticle;
    private CoinEntity selectedEntity;
    private int entityDetailViewIndex; // tracks which form/All Data tab is selected
    private SegmentedToggle detailViewToggle; // form selector in header bar

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
        super(docName);
        this.docId = docId;
        this.docDir = docDir;
        this.services = services;
        this.onClosed = onClosed;

        // Initialize per-document stores
        this.store = new SqliteNewsStore(docDir.resolve("news.db"));
        this.entityStore = new EntityStore(docDir.resolve("facts.db"));
        this.schemaRegistry = new SchemaRegistry(entityStore);
        this.sourceRegistry = new DataSourceRegistry(entityStore, schemaRegistry);

        // Register core schema types (always registered first)
        sourceRegistry.register(new CoreSource());

        // Register data sources based on template services
        if (services.isSourceEnabled("coingecko")) {
            sourceRegistry.register(new CoinGeckoSource());
        }
        if (services.isSourceEnabled("rss")) {
            sourceRegistry.register(new RssNewsSource(store, docDir));
        }

        // Initialize challenge infrastructure
        this.challengeStore = new SqliteChallengeStore(docDir.resolve("challenges.db"));
        seedDefaultChallenges();
        this.challengeExecutor = new ChallengeExecutor();
        this.challengeScheduler = new ChallengeScheduler(challengeStore, challengeExecutor, subjectId -> {
            CoinEntity entity = entityStore.getEntity(subjectId);
            return entity != null ? new CoinEntitySubject(entity, schemaRegistry, entityStore) : null;
        });
        challengeScheduler.setEnabled(IntelConfig.get().isChallengeAutoRefreshEnabled());
        challengeScheduler.start();

        // Register with sharing service for multi-device sync
        if (sharingService != null) {
            sharingService.registerDocument(docId, docDir, entityStore);

            // Detect USER_CURATED governance mode
            SharingService.SharingState state = sharingService.getState(docId);
            if (state != null && "USER_CURATED".equals(state.governanceType())) {
                isCuratedMode = true;
                entityStore.setCuratedMode(true);
            }
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
        setJMenuBar(IntelMenuBar.create(this));

        // Start API server
        try {
            EntitySearchProcessor searchProcessor = new EntitySearchProcessor(schemaRegistry);
            apiServer = new IntelApiServer(this::openWindow, entityStore, store, searchProcessor, schemaRegistry, sharingService, null);
            apiServer.setChallengeInfrastructure(challengeStore, challengeExecutor);
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
                if (challengeScheduler != null) challengeScheduler.stop();
                if (networkStatusTimer != null) networkStatusTimer.stop();
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

        JPanel rightPanel = createRightPanel();
        rightPanel.setPreferredSize(new Dimension(400, 0));
        rightPanel.setMinimumSize(new Dimension(300, 0));

        if (services.getPanels().isEmpty()) {
            // No graph panels — right panel (details + log) is the main content
            mainPanel.add(rightPanel, BorderLayout.CENTER);
        } else {
            JPanel leftPanel = createGraphPanel();
            ThinSplitPane mainSplit = new ThinSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
            mainSplit.setResizeWeight(1.0);
            mainPanel.add(mainSplit, BorderLayout.CENTER);
        }
        if (sharingService != null) {
            mainPanel.add(createNetworkStatusBar(), BorderLayout.SOUTH);
        }
        setContentPane(mainPanel);
        updateHeaderButtons();
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
        List<String> viewNames = new ArrayList<>();
        for (PanelConfig p : panels) viewNames.add(p.getName());
        viewNames.add("Challenges");
        viewToggle = new SegmentedToggle(viewNames.toArray(new String[0]));
        viewToggle.setOnSelectionChanged(i -> {
            if (i < panelInstances.size()) {
                cardLayout.show(cardPanel, panelInstances.get(i).config().getId());
            } else if (i == panelInstances.size()) {
                // Challenges tab
                refreshChallengesPanel();
                cardLayout.show(cardPanel, CHALLENGES_CARD_ID);
            }
            updateHeaderButtons();
        });
        // Always show toggle (we always have Challenges + at least the config panels)
        if (viewNames.size() > 1) {
            leftContent.add(viewToggle);
        }

        leftContent.add(Box.createHorizontalStrut(0)); // 8px gap (0 + 8 from FlowLayout)

        fetchBtn = new ToolbarButton("Fetch News");
        fetchBtn.setToolTipText("Fetch new articles with AI extraction");
        fetchBtn.addActionListener(e -> fetchNewArticles());
        leftContent.add(fetchBtn);

        resetViewBtn = new ToolbarButton("\u26F6");
        resetViewBtn.setToolTipText("Fit all nodes in view");
        resetViewBtn.addActionListener(e -> {
            CoinGraphPanel current = getCurrentCoinGraphPanel();
            if (current != null) current.fitToView();
        });
        resetViewBtn.setVisible(false);
        leftContent.add(resetViewBtn);

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.weightx = 1.0;
        leftPanel.add(leftContent, lc);
        headerBar.add(leftPanel, gbc);

        // Center: Title + settings cog
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel titleLabel = new JLabel(getTitle());
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

        browsePoolBtn = new ToolbarButton("Browse Pool");
        browsePoolBtn.setToolTipText("Browse and accept unaccepted entities");
        browsePoolBtn.addActionListener(e -> showEntityPoolBrowser());
        browsePoolBtn.setVisible(isCuratedMode);
        rightContent.add(browsePoolBtn);

        detailViewToggle = new SegmentedToggle(new String[0]);
        detailViewToggle.setVisible(false);
        rightContent.add(detailViewToggle);

        JButton historyBtn = new ToolbarButton("History");
        historyBtn.setToolTipText("Browse fact history log");
        historyBtn.addActionListener(e -> openWindow("fact-history"));
        rightContent.add(historyBtn);

        JButton docSettingsBtn = new ToolbarButton("Document Settings");
        docSettingsBtn.addActionListener(e -> showShareDialog());
        docSettingsBtn.setVisible(true);
        rightContent.add(docSettingsBtn);


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
                cgp.setSchemaRegistry(schemaRegistry);
                cgp.setOnEntitySelected(this::showEntityDetails);
                cgp.setShowLabels(config.isShowLabels());
                graphPanel = cgp;
            }

            card.add(graphPanel, BorderLayout.CENTER);

            cardPanel.add(card, config.getId());
            panelInstances.add(new PanelInstance(config, card, graphPanel));
        }

        // Always add a Challenges card as the last panel
        challengesCard = new JPanel(new BorderLayout());
        challengesContent = new JPanel();
        challengesContent.setLayout(new BoxLayout(challengesContent, BoxLayout.Y_AXIS));
        challengesContent.setBackground(bgMain());
        challengesContent.setBorder(new EmptyBorder(12, 16, 12, 16));
        BorderlessScrollPane challengesScroll = new BorderlessScrollPane(challengesContent);
        challengesScroll.getVerticalScrollBar().setUnitIncrement(16);
        challengesCard.add(challengesScroll, BorderLayout.CENTER);
        cardPanel.add(challengesCard, CHALLENGES_CARD_ID);
        refreshChallengesPanel();
    }

    private void updateHeaderButtons() {
        if (panelInstances.isEmpty()) {
            fetchBtn.setVisible(false);
            resetViewBtn.setVisible(false);
            return;
        }
        int idx = viewToggle.getSelectedIndex();
        boolean isNewsView = idx >= 0 && idx < panelInstances.size()
            && panelInstances.get(idx).config().getType() == PanelConfig.PanelType.NEWS_MAP;
        boolean isCoinView = idx >= 0 && idx < panelInstances.size()
            && panelInstances.get(idx).config().getType() == PanelConfig.PanelType.COIN_GRAPH;
        fetchBtn.setVisible(isNewsView);
        resetViewBtn.setVisible(isCoinView);
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.WEST);

        detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBackground(bgCard());

        detailHeader = new JPanel(new BorderLayout());
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
        detailViewToggle.setVisible(false);
        detailTitleLabel.setText("Details");
        detailHeader.setVisible(true);

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
        // Delegate to entity details if a matching entity exists
        if (currentEntities != null) {
            String symbol = node.id().contains(":") ? node.id().substring(node.id().indexOf(':') + 1) : node.id();
            for (CoinEntity e : currentEntities) {
                if (symbol.equalsIgnoreCase(e.symbol()) || node.id().equals(e.id())) {
                    showEntityDetails(e);
                    return;
                }
            }
        }

        selectedArticle = null;
        selectedEntity = null;
        currentMode = DetailMode.NONE;
        detailViewToggle.setVisible(false);

        detailTitleLabel.setText(node.typeId());
        detailHeader.setVisible(true);

        detailContent.removeAll();

        addDetailHeader(node.label());
        addDetailSpacer();

        addDetailSection("TYPE");
        addDetailLabel(node.typeId());
        addDetailSpacer();

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
        detailViewToggle.setVisible(false);
        detailTitleLabel.setText("Article");
        detailHeader.setVisible(true);

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
        detailTitleLabel.setText("");
        detailHeader.setVisible(false);

        // Check for form layouts
        String typeId = entity.type().name().toLowerCase();
        SchemaType schemaType = schemaRegistry != null ? schemaRegistry.getType(typeId) : null;
        List<FormLayout> layouts = (schemaType != null) ? schemaType.formLayouts() : null;
        boolean hasLayouts = layouts != null && !layouts.isEmpty();

        detailContent.removeAll();

        // Build segmented toggle: [form layouts...] + "All Data"
        if (hasLayouts) {
            List<String> segmentList = new ArrayList<>();
            for (FormLayout l : layouts) segmentList.add(l.name());
            segmentList.add("All Data");
            String[] segments = segmentList.toArray(new String[0]);

            detailViewToggle.setSegments(segments);
            if (entityDetailViewIndex < 0 || entityDetailViewIndex >= segments.length) {
                entityDetailViewIndex = 0;
            }
            detailViewToggle.setSelectedIndex(entityDetailViewIndex);
            detailViewToggle.setOnSelectionChanged(i -> {
                entityDetailViewIndex = i;
                showEntityDetails(entity);
            });
            detailViewToggle.setVisible(true);
        } else {
            detailViewToggle.setVisible(false);
        }

        if (hasLayouts && entityDetailViewIndex < layouts.size()) {
            renderFormLayoutView(entity, schemaType, layouts.get(entityDetailViewIndex));
        } else {
            renderAllDataView(entity, schemaType);
        }

        // Refresh the challenges panel if it's visible, so it shows context for this entity
        if (challengesCard != null) {
            refreshChallengesPanel();
        }

        detailContent.revalidate();
        detailContent.repaint();
    }

    private void renderFormLayoutView(CoinEntity entity, SchemaType schemaType, FormLayout layout) {
        String typeId = entity.type().name().toLowerCase();
        Map<String, AttributeValue> richValues = entityStore.getAttributeValuesRich(entity.id(), typeId);

        for (FormLayout.FormLayoutField f : layout.fields()) {
            if ("categories".equals(f.attributeName())) {
                addDetailSection("CATEGORIES");
                if (entity.categories().isEmpty()) {
                    addDetailLabel("\u2014");
                } else {
                    for (String cat : entity.categories()) {
                        addDetailLabel("  " + cat);
                    }
                }
                addDetailSpacer();
                continue;
            }

            SchemaAttribute attr = schemaType.attributes().stream()
                .filter(a -> a.name().equals(f.attributeName()))
                .findFirst().orElse(null);
            if (attr == null) continue;

            String displayLabel = attr.displayName(java.util.Locale.getDefault());
            AttributeValue av = richValues.get(attr.name());
            String value = (av != null && av.value() != null && !av.value().isEmpty())
                ? av.value() : "\u2014";

            if ("market_cap".equals(attr.name()) && av != null && av.value() != null && !av.value().isEmpty()) {
                try {
                    value = "$" + formatMarketCap(Double.parseDouble(av.value()));
                } catch (NumberFormatException ignored) {}
            }

            addDetailSection(displayLabel.toUpperCase());
            addDetailLabel(value);
            addDetailSpacer();
        }

        // Action buttons
        addEntityActionButtons(entity);
    }

    private void renderAllDataView(CoinEntity entity, SchemaType schemaType) {
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
                    description = rel.getLabel(schemaRegistry) + " " + other.name();
                } else {
                    description = describeInverseRelation(rel.typeId(), other.name());
                }

                addRelationshipRow(description, rel.getColor(schemaRegistry), otherId, other.name());
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
            addDetailSpacer();
        }

        // All custom attributes with values
        if (schemaType != null && !schemaType.attributes().isEmpty()) {
            String typeId = entity.type().name().toLowerCase();
            Map<String, AttributeValue> richValues = entityStore.getAttributeValuesRich(entity.id(), typeId);
            boolean hasAny = false;
            for (SchemaAttribute attr : schemaType.attributes()) {
                if ("name".equals(attr.name()) || "symbol".equals(attr.name()) ||
                    "market_cap".equals(attr.name())) continue;
                AttributeValue av = richValues.get(attr.name());
                if (av == null || av.value() == null || av.value().isEmpty()) continue;

                if (!hasAny) {
                    addDetailSection("ATTRIBUTES");
                    hasAny = true;
                }
                String displayLabel = attr.displayName(java.util.Locale.getDefault());
                addDetailLabel(displayLabel + ": " + av.value(), textSecondary());
            }
            if (hasAny) addDetailSpacer();
        }

        // Action buttons
        addEntityActionButtons(entity);
    }

    // ==================== CHALLENGES PANEL ====================

    private static final int RESULT_BOX_WIDTH = 320;
    private static final int RESULT_BOX_HEIGHT = 200;
    private void refreshChallengesPanel() {
        if (challengesContent == null) return;
        challengesContent.removeAll();

        List<Challenge> challenges = challengeStore.listChallenges().stream()
            .filter(Challenge::enabled)
            .toList();

        // Top bar: auto-refresh + new button
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setBackground(bgMain());
        topBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        topBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControls.setBackground(bgMain());

        JCheckBox autoToggle = new JCheckBox("Auto-refresh");
        autoToggle.setFont(new Font("SansSerif", Font.PLAIN, 10));
        autoToggle.setSelected(challengeScheduler.isEnabled());
        autoToggle.addActionListener(e -> {
            boolean enabled = autoToggle.isSelected();
            challengeScheduler.setEnabled(enabled);
            IntelConfig.get().setChallengeAutoRefreshEnabled(enabled);
            IntelConfig.get().save();
        });
        rightControls.add(autoToggle);

        JButton newChallengeBtn = new ToolbarButton("+ New");
        newChallengeBtn.addActionListener(e -> {
            ChallengeEditorDialog dlg = new ChallengeEditorDialog(this, challengeStore, null);
            dlg.setVisible(true);
            if (dlg.wasSaved()) refreshChallengesPanel();
        });
        rightControls.add(newChallengeBtn);

        JButton runAllBtn = new ToolbarButton("Run All");
        runAllBtn.addActionListener(e -> {
            runAllBtn.setEnabled(false);
            runAllBtn.setText("Running...");
            Thread.ofVirtual().start(() -> {
                for (Challenge ch : challenges) {
                    ChallengeSubject subject = new StandaloneChallengeSubject(ch);
                    ChallengeResult prev = challengeStore.getLatestResult(ch.id(), subject.id());
                    ChallengeResult result = challengeExecutor.execute(ch, subject, 0,
                        msg -> IntelLogPanel.logAI(msg), prev);
                    challengeStore.saveResult(result);
                }
                SwingUtilities.invokeLater(() -> {
                    runAllBtn.setEnabled(true);
                    runAllBtn.setText("Run All");
                    refreshChallengesPanel();
                });
            });
        });
        rightControls.add(runAllBtn);

        topBar.add(rightControls, BorderLayout.EAST);
        challengesContent.add(topBar);

        // Challenge rows
        for (Challenge challenge : challenges) {
            addChallengeRow(challenge);
        }

        if (challenges.isEmpty()) {
            JLabel empty = new JLabel("No challenges configured");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 12));
            empty.setForeground(textMuted());
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            challengesContent.add(empty);
        }

        challengesContent.add(Box.createVerticalGlue());
        challengesContent.revalidate();
        challengesContent.repaint();
    }

    private void addChallengeRow(Challenge challenge) {
        // Row container
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setBackground(bgMain());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(8, 0, 8, 0)
        ));

        // Top: title + run buttons
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(bgMain());

        // Left: title + description
        JPanel titleArea = new JPanel();
        titleArea.setLayout(new BoxLayout(titleArea, BoxLayout.Y_AXIS));
        titleArea.setBackground(bgMain());

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        titleRow.setBackground(bgMain());
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(challenge.title());
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(textPrimary());
        titleRow.add(titleLabel);

        JButton editBtn = new JButton("Edit");
        editBtn.setFont(new Font("SansSerif", Font.PLAIN, 9));
        editBtn.setForeground(linkColor());
        editBtn.setBorderPainted(false);
        editBtn.setContentAreaFilled(false);
        editBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        editBtn.addActionListener(e -> {
            ChallengeEditorDialog dlg = new ChallengeEditorDialog(this, challengeStore, challenge);
            dlg.setVisible(true);
            if (dlg.wasSaved()) refreshChallengesPanel();
        });
        titleRow.add(editBtn);

        if (challenge.output().isTracking()) {
            JButton itemsBtn = new JButton("Items");
            itemsBtn.setFont(new Font("SansSerif", Font.PLAIN, 9));
            itemsBtn.setForeground(linkColor());
            itemsBtn.setBorderPainted(false);
            itemsBtn.setContentAreaFilled(false);
            itemsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            itemsBtn.addActionListener(e -> {
                String subjectId = challenge.id(); // standalone subject = challenge id
                ChallengeItemsDialog dlg = new ChallengeItemsDialog(this, challenge, challengeStore, subjectId);
                dlg.setVisible(true);
                dlg.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent we) {
                        if (dlg.wasChanged()) refreshChallengesPanel();
                    }
                });
            });
            titleRow.add(itemsBtn);
        }

        titleArea.add(titleRow);

        if (challenge.description() != null) {
            String shortDesc = challenge.description();
            if (shortDesc.length() > 120) shortDesc = shortDesc.substring(0, 117) + "...";
            JLabel desc = new JLabel(shortDesc);
            desc.setFont(new Font("SansSerif", Font.PLAIN, 10));
            desc.setForeground(textMuted());
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);
            desc.setToolTipText("<html><body style='width:300px'>" + challenge.description() + "</body></html>");
            titleArea.add(desc);
        }
        header.add(titleArea, BorderLayout.CENTER);

        // Right: single run button
        JButton runBtn = new JButton("Run");
        runBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        runBtn.addActionListener(e -> {
            runBtn.setEnabled(false);
            runBtn.setText("...");
            ChallengeSubject subject = new StandaloneChallengeSubject(challenge);
            ChallengeResult prev = challengeStore.getLatestResult(challenge.id(), subject.id());
            Thread.ofVirtual().start(() -> {
                ChallengeResult result = challengeExecutor.execute(challenge, subject, 0,
                    msg -> IntelLogPanel.logAI(msg), prev);
                challengeStore.saveResult(result);
                SwingUtilities.invokeLater(() -> {
                    runBtn.setEnabled(true);
                    runBtn.setText("Run");
                    refreshChallengesPanel();
                });
            });
        });
        header.add(runBtn, BorderLayout.EAST);
        row.add(header, BorderLayout.NORTH);

        // Bottom: horizontal scrollable result boxes (oldest left → newest right)
        List<ChallengeResult> results = challengeStore.getResultsForChallenge(challenge.id(), 50);

        JPanel timeline = new JPanel();
        timeline.setLayout(new BoxLayout(timeline, BoxLayout.X_AXIS));
        timeline.setBackground(bgMain());

        if (results.isEmpty()) {
            JLabel noResults = new JLabel("  No results yet — click a run button above");
            noResults.setFont(new Font("SansSerif", Font.ITALIC, 10));
            noResults.setForeground(textMuted());
            noResults.setPreferredSize(new Dimension(300, RESULT_BOX_HEIGHT));
            timeline.add(noResults);
        } else {
            for (ChallengeResult r : results) {
                timeline.add(createResultBox(r));
                timeline.add(Box.createHorizontalStrut(4));
            }
        }

        BorderlessScrollPane timelineScroll = new BorderlessScrollPane(timeline);
        timelineScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        timelineScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        timelineScroll.setPreferredSize(new Dimension(0, RESULT_BOX_HEIGHT + 12));
        timelineScroll.setMinimumSize(new Dimension(0, RESULT_BOX_HEIGHT + 12));
        timelineScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, RESULT_BOX_HEIGHT + 12));
        // Scroll to the right (latest results)
        SwingUtilities.invokeLater(() -> {
            JScrollBar hbar = timelineScroll.getHorizontalScrollBar();
            hbar.setValue(hbar.getMaximum());
        });

        // Per-field charts over time (one chart per numeric field)
        List<ChallengeChartPanel> charts = ChallengeChartPanel.createCharts(challenge, results);
        if (!charts.isEmpty()) {
            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
            centerPanel.setBackground(bgMain());
            timelineScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            centerPanel.add(timelineScroll);
            for (ChallengeChartPanel chart : charts) {
                chart.setAlignmentX(Component.LEFT_ALIGNMENT);
                centerPanel.add(chart);
            }
            row.add(centerPanel, BorderLayout.CENTER);
        } else {
            row.add(timelineScroll, BorderLayout.CENTER);
        }

        challengesContent.add(row);
    }

    private JPanel createResultBox(ChallengeResult result) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(darker(bgCard(), 0.03f));
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(darker(bgMain(), 0.12f), 1),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        box.setPreferredSize(new Dimension(RESULT_BOX_WIDTH, RESULT_BOX_HEIGHT));
        box.setMinimumSize(new Dimension(RESULT_BOX_WIDTH, RESULT_BOX_HEIGHT));
        box.setMaximumSize(new Dimension(RESULT_BOX_WIDTH, RESULT_BOX_HEIGHT));

        // Timestamp
        long ago = System.currentTimeMillis() - result.timestamp();
        String agoStr = ago < 60_000 ? "just now"
            : ago < 3600_000 ? (ago / 60_000) + "m ago"
            : ago < 86_400_000 ? (ago / 3600_000) + "h ago"
            : (ago / 86_400_000) + "d ago";
        JLabel timeLabel = new JLabel(agoStr);
        timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
        timeLabel.setForeground(textMuted());
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(timeLabel);
        box.add(Box.createVerticalStrut(3));

        // Signal badge
        if (result.hasSignal()) {
            JLabel signalLabel = new JLabel(String.format("%.1f", result.signalValue()));
            signalLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
            signalLabel.setForeground(result.signalValue() >= 5
                ? new Color(80, 180, 80) : new Color(200, 100, 60));
            signalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(signalLabel);
            box.add(Box.createVerticalStrut(2));
        }

        // Result content
        int contentWidth = RESULT_BOX_WIDTH - 20;
        if (result.hasError()) {
            JLabel errLabel = new JLabel("ERROR");
            errLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
            errLabel.setForeground(new Color(220, 60, 60));
            errLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(errLabel);
            if (result.error() != null) {
                JLabel errDetail = new JLabel("<html><body style='width:" + contentWidth + "px'>"
                    + escapeHtml(result.error()) + "</body></html>");
                errDetail.setFont(new Font("SansSerif", Font.PLAIN, 9));
                errDetail.setForeground(textMuted());
                errDetail.setAlignmentX(Component.LEFT_ALIGNMENT);
                box.add(errDetail);
            }
        } else if (result.itemResults() != null && !result.itemResults().isEmpty()) {
            // Structured list — HTML table (rows=entities, cols=fields)
            Challenge ch = challengeStore.getChallenge(result.challengeId());
            List<ChallengeOutput.Field> fieldDefs = (ch != null && ch.output().fields() != null)
                ? ch.output().fields() : List.of();

            StringBuilder html = new StringBuilder();
            html.append("<html><body style='width:").append(contentWidth).append("px'>");
            html.append("<table cellspacing='0' cellpadding='1' style='font-size:9px'>");

            // Header row with field labels
            html.append("<tr>");
            for (ChallengeOutput.Field f : fieldDefs) {
                String lbl = f.label() != null ? f.label() : f.name();
                if (lbl.length() > 10) lbl = lbl.substring(0, 9) + ".";
                html.append("<td><b>").append(escapeHtml(lbl)).append("</b></td>");
            }
            html.append("</tr>");

            // Data rows
            int shown = 0;
            for (Map<String, String> item : result.itemResults()) {
                if (shown >= 10) break;
                boolean isRemoved = "removed".equals(item.get("_status"));
                html.append(isRemoved ? "<tr style='color:gray'>" : "<tr>");
                for (ChallengeOutput.Field f : fieldDefs) {
                    String v = item.getOrDefault(f.name(), "");
                    if (v.length() > 14) v = v.substring(0, 12) + "..";
                    boolean isPrimary = f.primary();
                    String reason = item.get(f.name() + "_reason");
                    String titleAttr = reason != null ? " title='" + escapeHtml(reason).replace("'", "&#39;") + "'" : "";
                    if (isRemoved) {
                        html.append("<td").append(titleAttr).append("><s>").append(escapeHtml(v)).append("</s></td>");
                    } else {
                        html.append("<td").append(titleAttr).append(">").append(isPrimary ? "<b>" : "").append(escapeHtml(v))
                            .append(isPrimary ? "</b>" : "").append("</td>");
                    }
                }
                html.append("</tr>");
                shown++;
            }
            if (result.itemResults().size() > 10) {
                html.append("<tr><td colspan='").append(fieldDefs.size()).append("'><i>+")
                    .append(result.itemResults().size() - 10).append(" more</i></td></tr>");
            }
            html.append("</table></body></html>");

            JLabel tableLabel = new JLabel(html.toString());
            tableLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
            tableLabel.setForeground(textSecondary());
            tableLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(tableLabel);

            // Full table tooltip with _reason details
            StringBuilder tip = new StringBuilder("<html><body style='width:600px'><table cellspacing='2' cellpadding='1'><tr>");
            for (ChallengeOutput.Field f : fieldDefs) {
                tip.append("<th style='text-align:left'><b>").append(escapeHtml(f.label())).append("</b></th>");
            }
            tip.append("</tr>");
            for (Map<String, String> item : result.itemResults()) {
                tip.append("<tr>");
                for (ChallengeOutput.Field f : fieldDefs) {
                    String val = item.getOrDefault(f.name(), "");
                    String reason = item.get(f.name() + "_reason");
                    tip.append("<td>").append(escapeHtml(val));
                    if (reason != null) {
                        tip.append("<br><i style='color:gray; font-size:9px'>").append(escapeHtml(reason)).append("</i>");
                    }
                    tip.append("</td>");
                }
                tip.append("</tr>");
            }
            tip.append("</table></body></html>");
            box.setToolTipText(tip.toString());
        } else if (result.fields() != null && !result.fields().isEmpty()) {
            // Structured result — show all fields with labels
            // Find the challenge to resolve field labels
            Challenge ch = challengeStore.getChallenge(result.challengeId());
            Map<String, String> labelMap = new LinkedHashMap<>();
            if (ch != null && ch.output().fields() != null) {
                for (ChallengeOutput.Field f : ch.output().fields()) {
                    labelMap.put(f.name(), f.label());
                }
            }

            StringBuilder tip = new StringBuilder("<html><body style='width:500px'>");
            for (Map.Entry<String, String> entry : result.fields().entrySet()) {
                String fieldName = entry.getKey();
                if (fieldName.endsWith("_reason")) continue; // skip reason keys in iteration
                String val = entry.getValue();
                String label = labelMap.getOrDefault(fieldName, fieldName);
                String reason = result.fields().get(fieldName + "_reason");
                boolean isNumber = false;
                try { Double.parseDouble(val); isNumber = true; } catch (NumberFormatException ignored) {}

                if (isNumber) {
                    JLabel numLabel = new JLabel(label + ": " + val);
                    numLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
                    numLabel.setForeground(textPrimary());
                    numLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    if (reason != null) {
                        numLabel.setToolTipText("<html><body style='width:400px'>" + escapeHtml(reason) + "</body></html>");
                    }
                    box.add(numLabel);
                } else {
                    // Label header
                    JLabel headerLabel = new JLabel(label);
                    headerLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
                    headerLabel.setForeground(textMuted());
                    headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    box.add(headerLabel);
                    // Value — wrap text, show generously
                    JLabel textLabel = new JLabel("<html><body style='width:" + contentWidth + "px'>"
                        + escapeHtml(val) + "</body></html>");
                    textLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    textLabel.setForeground(textSecondary());
                    textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    box.add(textLabel);
                    box.add(Box.createVerticalStrut(3));
                }
                tip.append("<b>").append(escapeHtml(label)).append(":</b> ")
                    .append(escapeHtml(val));
                if (reason != null) {
                    tip.append("<br><i style='color:gray'>").append(escapeHtml(reason)).append("</i>");
                }
                tip.append("<br><br>");
            }
            tip.append("</body></html>");
            box.setToolTipText(tip.toString());
        } else if (result.textResult() != null) {
            JLabel textLabel = new JLabel("<html><body style='width:" + contentWidth + "px'>"
                + escapeHtml(result.textResult()) + "</body></html>");
            textLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            textLabel.setForeground(textSecondary());
            textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(textLabel);
            box.setToolTipText("<html><body style='width:500px'>" + escapeHtml(result.textResult()) + "</body></html>");
        } else if (result.listResult() != null) {
            for (String item : result.listResult()) {
                JLabel itemLabel = new JLabel("\u2022 " + item);
                itemLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                itemLabel.setForeground(textSecondary());
                itemLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                box.add(itemLabel);
            }
            StringBuilder tip = new StringBuilder("<html><body style='width:500px'>");
            for (String s : result.listResult()) tip.append("\u2022 ").append(escapeHtml(s)).append("<br>");
            tip.append("</body></html>");
            box.setToolTipText(tip.toString());
        } else if (result.entityResult() != null && result.entityResult().entities() != null) {
            JLabel countLabel = new JLabel(result.entityResult().entities().size() + " entities");
            countLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            countLabel.setForeground(textSecondary());
            countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(countLabel);
        }

        box.add(Box.createVerticalGlue());
        return box;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void seedDefaultChallenges() {
        if (!challengeStore.listChallenges().isEmpty()) return;

        List<ChallengeOutput.Field> sentimentFields = List.of(
            ChallengeOutput.Field.text("headline", "Headline", true),
            ChallengeOutput.Field.text("explanation", "Explanation"),
            ChallengeOutput.Field.score("sentiment", "Sentiment", -1.0, 1.0),
            ChallengeOutput.Field.score("confidence", "Confidence %", 0, 100)
        );

        // 1. US Markets
        Challenge c1 = new Challenge("us-markets", "US Markets");
        c1.setDescription("How are the US markets doing right now? Cover the S&P 500, Nasdaq, Dow Jones, "
            + "and any major movers. Include recent Fed policy, economic data releases, earnings season impact, "
            + "and key risks. What's the overall mood?");
        ChallengeOutput usOutput = new ChallengeOutput(ChallengeOutput.Type.STRUCTURED);
        usOutput.setFields(sentimentFields);
        c1.setOutput(usOutput);
        c1.setEscalations(List.of(createEsc("Run", "standard", null, false, "US market analysis")));
        c1.setRefreshInterval(java.time.Duration.ofDays(1));
        c1.setDisplayOrder(1);
        challengeStore.saveChallenge(c1);

        // 2. Crypto Markets
        Challenge c2 = new Challenge("crypto-markets", "Crypto Markets");
        c2.setDescription("How are the crypto markets doing right now? Cover Bitcoin, Ethereum, "
            + "and the broader altcoin market. Include on-chain metrics, funding rates, exchange flows, "
            + "regulatory developments, and institutional activity. What's the overall mood?");
        ChallengeOutput cryptoOutput = new ChallengeOutput(ChallengeOutput.Type.STRUCTURED);
        cryptoOutput.setFields(sentimentFields);
        c2.setOutput(cryptoOutput);
        c2.setEscalations(List.of(createEsc("Run", "standard", null, false, "Crypto market analysis")));
        c2.setRefreshInterval(java.time.Duration.ofDays(1));
        c2.setDisplayOrder(2);
        challengeStore.saveChallenge(c2);

        // 3. Active Wars & Conflicts
        Challenge c3 = new Challenge("active-wars", "Active Wars & Conflicts");
        c3.setDescription("Survey all currently active wars and armed conflicts worldwide, "
            + "plus any that ended in the last 6 months. "
            + "Provide a headline summarizing the global situation, "
            + "list the high-intensity conflicts, medium-intensity conflicts, and low-intensity conflicts separately, "
            + "list any recently ended conflicts, and rate the overall global conflict intensity.");
        ChallengeOutput warsOutput = new ChallengeOutput(ChallengeOutput.Type.STRUCTURED);
        warsOutput.setFields(List.of(
            ChallengeOutput.Field.text("headline", "Headline", true),
            ChallengeOutput.Field.text("high_intensity", "High Intensity"),
            ChallengeOutput.Field.text("medium_intensity", "Medium Intensity"),
            ChallengeOutput.Field.text("low_intensity", "Low Intensity"),
            ChallengeOutput.Field.text("recently_ended", "Recently Ended"),
            ChallengeOutput.Field.score("global_intensity", "Global Intensity", 0, 10)
        ));
        c3.setOutput(warsOutput);
        c3.setEscalations(List.of(createEsc("Run", "standard", null, false, "Conflicts survey")));
        c3.setRefreshInterval(java.time.Duration.ofDays(7));
        c3.setDisplayOrder(3);
        challengeStore.saveChallenge(c3);

        // 4. War Market Impact (structured list mode)
        Challenge c4 = new Challenge("war-impact", "War Market Impact");
        c4.setDescription("For each currently active war or major armed conflict, estimate its market impact. "
            + "Consider direct and indirect effects on commodity prices, trade disruption, sanctions, "
            + "supply chain risks, and investor sentiment. "
            + "List ALL active conflicts you know about.");
        ChallengeOutput impactOutput = new ChallengeOutput(ChallengeOutput.Type.STRUCTURED);
        impactOutput.setListMode(true);
        impactOutput.setListBehavior(ChallengeOutput.ListBehavior.TRACKING);
        impactOutput.setFields(List.of(
            ChallengeOutput.Field.text("name", "Conflict", true),
            ChallengeOutput.Field.score("intensity", "Intensity", 0, 10),
            ChallengeOutput.Field.score("oil_impact", "Oil Impact", -10, 10),
            ChallengeOutput.Field.score("us_impact", "US Market", -10, 10),
            ChallengeOutput.Field.score("eu_impact", "EU Market", -10, 10),
            ChallengeOutput.Field.score("asia_impact", "Asia Market", -10, 10)
        ));
        c4.setOutput(impactOutput);
        c4.setEscalations(List.of(createEsc("Run", "standard", null, false, "War impact analysis")));
        c4.setRefreshInterval(java.time.Duration.ofDays(3));
        c4.setDisplayOrder(4);
        challengeStore.saveChallenge(c4);

        // 5. Meme Coins (structured list mode)
        Challenge c5 = new Challenge("meme-coins", "Meme Coins");
        c5.setDescription("Find the hippest and most talked-about meme coins right now. "
            + "Only include coins with a market cap of at least $1M. "
            + "Rank them by short-term potential (hype, momentum, community strength, catalysts). "
            + "Include the current market cap and highlight standout sizes.");
        ChallengeOutput memeOutput = new ChallengeOutput(ChallengeOutput.Type.STRUCTURED);
        memeOutput.setListMode(true);
        memeOutput.setListBehavior(ChallengeOutput.ListBehavior.SNAPSHOT);
        memeOutput.setFields(List.of(
            ChallengeOutput.Field.text("name", "Coin", true),
            ChallengeOutput.Field.text("ticker", "Ticker"),
            ChallengeOutput.Field.score("potential", "Potential", 0, 10),
            ChallengeOutput.Field.score("hype", "Hype", 0, 10),
            ChallengeOutput.Field.number("market_cap_m", "Mcap ($M)", 1, 50000),
            ChallengeOutput.Field.score("risk", "Risk", 0, 10)
        ));
        c5.setOutput(memeOutput);
        c5.setEscalations(List.of(createEsc("Run", "standard", null, false, "Meme coin scanner")));
        c5.setRefreshInterval(java.time.Duration.ofDays(1));
        c5.setDisplayOrder(5);
        challengeStore.saveChallenge(c5);
    }

    private static ChallengeEscalation createEsc(String label, String tier, String pipeline,
                                                   boolean verify, String description) {
        ChallengeEscalation esc = new ChallengeEscalation(label, tier);
        esc.setPipeline(pipeline);
        esc.setVerify(verify);
        esc.setDescription(description);
        return esc;
    }

    private void addEntityActionButtons(CoinEntity entity) {
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

        if (isCuratedMode) {
            JButton removeFromViewBtn = new JButton("Remove from View");
            removeFromViewBtn.addActionListener(e -> {
                entityStore.unacceptEntity(entity.id());
                selectedEntity = null;
                currentMode = DetailMode.NONE;
                detailViewToggle.setVisible(false);
                detailContent.removeAll();
                detailContent.revalidate();
                detailContent.repaint();
                loadCoinData(false);
                logPanel.info("Removed '" + entity.name() + "' from view");
            });
            btnPanel.add(removeFromViewBtn);
        }

        detailContent.add(btnPanel);
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

    private String describeInverseRelation(String typeId, String otherName) {
        SchemaType relSchema = schemaRegistry.getType(typeId);
        if (relSchema != null) {
            return relSchema.inverseDescription(otherName);
        }
        return typeId + " " + otherName;
    }

    // ==================== DATA LOADING ====================

    private void loadNewsData() {
        List<PanelInstance> newsPanels = panelInstances.stream()
            .filter(pi -> pi.config().getType() == PanelConfig.PanelType.NEWS_MAP)
            .toList();
        if (newsPanels.isEmpty()) return;

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
                    }
                    logPanel.success("Loaded " + articles.size() + " news articles");
                } catch (Exception e) {
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
                fetchBtn.setText("Fetch News");
                try {
                    DataSource.FetchResult result = get();
                    if (result.entitiesAdded() > 0) {
                        for (PanelInstance pi : newsPanels) {
                            TimelineGraphPanel tgp = (TimelineGraphPanel) pi.graphPanel();
                            int limit = pi.config().getMaxArticles();
                            List<Article> allArticles = store.getArticles(SqliteNewsStore.ArticleQuery.all(limit));
                            tgp.addArticles(allArticles);
                        }
                        logPanel.success(result.message());
                    } else {
                        logPanel.info("No new articles found");
                    }
                } catch (Exception e) {
                    logPanel.error("Fetch failed: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /** Trigger a forced refresh of coin data (used by CoinGeckoWindow). */
    public void refreshCoinData() {
        loadCoinData(true);
    }

    private void loadCoinData(boolean forceRefresh) {
        List<PanelInstance> coinPanels = panelInstances.stream()
            .filter(pi -> pi.config().getType() == PanelConfig.PanelType.COIN_GRAPH)
            .toList();
        if (coinPanels.isEmpty()) return;

        logPanel.data("Loading coin entities...");

        SwingWorker<DataSource.FetchResult, String> worker = new SwingWorker<>() {
            @Override
            protected DataSource.FetchResult doInBackground() {
                return sourceRegistry.refresh("coingecko", forceRefresh, (msg, pct) -> publish(msg));
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) logPanel.data(msg);
            }

            @Override
            protected void done() {
                try {
                    DataSource.FetchResult result = get();

                    List<CoinEntity> allEntities;
                    List<CoinRelationship> allRels;
                    if (isCuratedMode) {
                        allEntities = entityStore.loadAcceptedEntities();
                        allRels = entityStore.loadAcceptedRelationships();
                    } else {
                        allEntities = new ArrayList<>();
                        allEntities.addAll(entityStore.loadEntitiesBySource("coingecko"));
                        allEntities.addAll(entityStore.loadEntitiesBySource("manual"));
                        allEntities.addAll(entityStore.loadEntitiesBySource("ai-discovery"));
                        allRels = entityStore.loadAllRelationships();
                    }

                    currentEntities = allEntities;
                    currentRelationships = allRels;

                    for (PanelInstance pi : coinPanels) {
                        CoinGraphPanel cgp = (CoinGraphPanel) pi.graphPanel();
                        feedCoinPanel(cgp, pi.config());
                    }

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
                    }
                    logPanel.error("Failed to load coin data: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void feedCoinPanel(CoinGraphPanel cgp, PanelConfig config) {
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
                .filter(r -> relTypeFilter.contains(r.typeId()))
                .toList();
        }

        if (!config.isShowConnections()) {
            filteredRels = List.of();
        }

        cgp.setData(new ArrayList<>(entities), new ArrayList<>(filteredRels));
    }

    private void refreshAllCoinPanels() {
        for (PanelInstance pi : panelInstances) {
            if (pi.graphPanel() instanceof CoinGraphPanel cgp) {
                feedCoinPanel(cgp, pi.config());
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
        List<String> names = new ArrayList<>();
        for (PanelConfig p : panels) names.add(p.getName());
        names.add("Challenges");

        Container toggleParent = viewToggle.getParent();
        // Remove old toggle if it was added
        if (toggleParent != null) {
            toggleParent.remove(viewToggle);
        }
        viewToggle = new SegmentedToggle(names.toArray(new String[0]));
        viewToggle.setOnSelectionChanged(i -> {
            if (i < panelInstances.size()) {
                cardLayout.show(cardPanel, panelInstances.get(i).config().getId());
            } else if (i == panelInstances.size()) {
                refreshChallengesPanel();
                cardLayout.show(cardPanel, CHALLENGES_CARD_ID);
            }
            updateHeaderButtons();
        });
        // Always show toggle when we have 2+ items
        if (names.size() > 1 && toggleParent != null) {
            int insertIdx = toggleParent.getComponentCount() > 0
                && toggleParent.getComponent(0) instanceof JPanel ? 1 : 0;
            toggleParent.add(viewToggle, insertIdx);
            toggleParent.revalidate();
        } else if (toggleParent != null) {
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


    // ==================== NETWORK STATUS BAR ====================

    private JPanel createNetworkStatusBar() {
        // 5 badges: Identity, NAT, LAN, Rendezvous, Peers
        networkBadges = new StatusBadge[5];
        String[] labels = {"Not signed in", "No NAT", "LAN off", "No rendezvous", "No peers"};

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        bar.setPreferredSize(new Dimension(0, 24));

        java.awt.event.MouseListener clickHandler = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showNetworkStatusDialog();
            }
        };

        for (int i = 0; i < networkBadges.length; i++) {
            networkBadges[i] = new StatusBadge(labels[i]);
            networkBadges[i].addMouseListener(clickHandler);
            bar.add(networkBadges[i]);
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(new JSeparator(), BorderLayout.NORTH);
        wrapper.add(bar, BorderLayout.CENTER);

        updateNetworkStatus();
        networkStatusTimer = new javax.swing.Timer(3000, e -> updateNetworkStatus());
        networkStatusTimer.setRepeats(true);
        networkStatusTimer.start();

        return wrapper;
    }

    private void updateNetworkStatus() {
        if (sharingService == null || networkBadges == null) return;
        SharingService.NetworkStatus ns = sharingService.getNetworkStatus();
        if (ns == null) return;

        // 0: Identity
        if (ns.email() != null && !ns.email().isBlank()) {
            networkBadges[0].setText(ns.email());
            networkBadges[0].setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
        } else {
            networkBadges[0].setText("Not signed in");
            networkBadges[0].setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        }

        // 1: NAT
        if (ns.portMapping() != null) {
            String label = ns.portMapping();
            if (ns.publicIp() != null) label += " " + ns.publicIp();
            networkBadges[1].setText(label);
            networkBadges[1].setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
        } else if (ns.publicIp() != null) {
            networkBadges[1].setText("STUN " + ns.publicIp());
            networkBadges[1].setStatusColor(StatusBadge.BG_WARNING, StatusBadge.FG_WARNING);
        } else {
            networkBadges[1].setText("No NAT");
            networkBadges[1].setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        }

        // 2: LAN
        if (ns.lanActive()) {
            networkBadges[2].setText("LAN: " + ns.lanPeerCount());
            networkBadges[2].setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
        } else {
            networkBadges[2].setText("LAN off");
            networkBadges[2].setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        }

        // 3: Rendezvous
        if (ns.rendezvousAvailable()) {
            networkBadges[3].setText("Rendezvous");
            networkBadges[3].setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
        } else {
            networkBadges[3].setText("No rendezvous");
            networkBadges[3].setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        }

        // 4: Peers
        if (ns.connectedPeers() > 0 || ns.connectedDevices() > 0) {
            networkBadges[4].setText(ns.connectedPeers() + " peers / " + ns.connectedDevices() + " devices");
            networkBadges[4].setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
        } else {
            networkBadges[4].setText("No peers");
            networkBadges[4].setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        }
    }

    private void showNetworkStatusDialog() {
        if (networkStatusDialog != null && networkStatusDialog.isShowing()) {
            networkStatusDialog.toFront();
            networkStatusDialog.requestFocus();
            return;
        }
        networkStatusDialog = new NetworkStatusDialog(this, sharingService);
        networkStatusDialog.setVisible(true);
    }

    // ==================== DIALOGS ====================

    private void openWindow(String windowName) {
        switch (windowName) {
            case "data-structure" -> showDataStructureWindow();
            case "fact-history" -> showFactHistoryWindow();
            case "settings" -> showSettingsWindow();
            default -> { toFront(); requestFocus(); }
        }
    }

    private void showFactHistoryWindow() {
        FactHistoryFrame.open(entityStore.factStore(), entityStore, this);
    }

    void showDataStructureWindow() {
        if (dataStructureFrame != null && dataStructureFrame.isShowing()) {
            dataStructureFrame.toFront();
            dataStructureFrame.requestFocus();
            return;
        }
        logPanel.info("Opening Data Structure...");
        dataStructureFrame = new DataStructureFrame(entityStore, schemaRegistry, v -> loadCoinData(false));
        dataStructureFrame.setVisible(true);
    }

    private void showShareDialog() {
        ShareDialog dialog = new ShareDialog(this, docId, docDir, entityStore, schemaRegistry, sharingService, logPanel);
        dialog.setVisible(true);
    }

    private void showEntityPoolBrowser() {
        EntityPoolBrowserDialog dialog = new EntityPoolBrowserDialog(this, entityStore, schemaRegistry, () -> loadCoinData(false));
        dialog.setVisible(true);
    }

    private void showFriendsDialog() {
        if (sharingService == null) return;
        FriendsDialog dialog = new FriendsDialog(this, sharingService, chatStore);
        dialog.setVisible(true);
    }

    private void openChat() {
        if (sharingService == null) return;
        ChatFrame.open(sharingService, chatStore, this);
        ChatFrame.setOnUnreadChanged(this::updateChatBadge);
    }

    private void updateChatBadge() {
        if (chatBtn == null) return;
        int unread = ChatFrame.getUnreadCount();
        SwingUtilities.invokeLater(() ->
            chatBtn.setText(unread > 0 ? "Chat (" + unread + ")" : "Chat"));
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
        relationships.add(new CoinRelationship("arbitrum", "ethereum", "l2_of"));
        entities.add(new CoinEntity("ibit", "iShares Bitcoin Trust", "IBIT", CoinEntity.Type.ETF));
        relationships.add(new CoinRelationship("ibit", "bitcoin", "etf_tracks"));
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
