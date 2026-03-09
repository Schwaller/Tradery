package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.ai.AiClient;
import com.tradery.ai.AiConfig;
import com.tradery.ai.AiSetupDialog;
import com.tradery.ai.challenges.execution.ChallengeExecutor;
import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeEscalation;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.schedule.ChallengeScheduler;
import com.tradery.ai.challenges.store.ChallengeStore;
import com.tradery.license.LicenseGate;
import com.tradery.license.UpdateChecker;
import com.tradery.news.model.Article;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.api.IntelApiServer;
import com.tradery.news.source.*;
import com.tradery.news.ui.challenges.*;
import com.tradery.news.ui.coin.*;
import com.tradery.ui.ThemeHelper;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.SegmentedToggle;
import com.tradery.ui.controls.ThinSplitPane;
import com.tradery.ui.controls.ToolbarButton;

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
    private JButton fetchBtn;
    private JButton resetViewBtn;
    private JPanel detailPanel;
    private JPanel detailHeader;
    private JPanel detailContent;
    private JLabel detailTitleLabel;
    private IntelLogPanel logPanel;

    // Singleton windows
    private DataStructureFrame dataStructureFrame;
    private SchemaRegistry schemaRegistry;
    private DataSourceRegistry sourceRegistry;

    // API server
    private IntelApiServer apiServer;

    // Challenges
    private ChallengeStore challengeStore;
    private ChallengeExecutor challengeExecutor;
    private ChallengeScheduler challengeScheduler;

    // Current selection
    private enum DetailMode { NONE, ARTICLE, ENTITY }
    private DetailMode currentMode = DetailMode.NONE;
    private NewsNode selectedArticle;
    private CoinEntity selectedEntity;
    private int entityDetailViewIndex;

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
        sourceRegistry.register(new CoreSource());
        sourceRegistry.register(new CoinGeckoSource());
        sourceRegistry.register(new RssNewsSource(store, dataDir));

        // Initialize challenge infrastructure
        this.challengeStore = new SqliteChallengeStore(dataDir.resolve("challenges.db"));
        seedDefaultChallenges();
        this.challengeExecutor = new ChallengeExecutor();
        this.challengeScheduler = new ChallengeScheduler(challengeStore, challengeExecutor, subjectId -> {
            CoinEntity entity = entityStore.getEntity(subjectId);
            return entity != null ? new CoinEntitySubject(entity, schemaRegistry, entityStore) : null;
        });
        challengeScheduler.setEnabled(IntelConfig.get().isChallengeAutoRefreshEnabled());
        challengeScheduler.start();

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
        setJMenuBar(IntelMenuBar.create(this));

        // Start API server
        try {
            EntitySearchProcessor searchProcessor = new EntitySearchProcessor(schemaRegistry);
            apiServer = new IntelApiServer(this::openWindow, entityStore, store, searchProcessor, schemaRegistry, null, this::getUiState);
            apiServer.setChallengeInfrastructure(challengeStore, challengeExecutor);
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
                if (challengeScheduler != null) challengeScheduler.stop();
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

        // Fetch New button (for News view)
        fetchBtn = new ToolbarButton("Fetch News");
        fetchBtn.setToolTipText("Fetch new articles with AI extraction");
        fetchBtn.addActionListener(e -> fetchNewArticles());
        rightContent.add(fetchBtn);

        // Reset View button (for Coins view)
        resetViewBtn = new ToolbarButton("\u2194");
        resetViewBtn.setToolTipText("Fit all nodes in view");
        resetViewBtn.addActionListener(e -> {
            CoinGraphPanel current = getCurrentCoinGraphPanel();
            if (current != null) current.fitToView();
        });
        resetViewBtn.setVisible(false);  // Hidden by default (News is selected)
        rightContent.add(resetViewBtn);

        // Pending changes indicator + commit/discard (visible only when pending > 0)
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
        // Check for pending changes from previous session
        SwingUtilities.invokeLater(updatePending);

        rightContent.add(pendingPanel);

        JButton dataStructureBtn = new ToolbarButton("Data Structure");
        dataStructureBtn.addActionListener(e -> showDataStructureWindow());
        rightContent.add(dataStructureBtn);



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
        fetchBtn.setVisible(isNewsView);
        resetViewBtn.setVisible(isCoinView);
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.WEST);

        // Detail panel (top)
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

        detailTitleLabel.setText(node.typeId());
        detailHeader.setVisible(true);

        detailContent.removeAll();

        addDetailHeader(node.label());
        addDetailSpacer();

        addDetailSection("TYPE");
        addDetailLabel(node.typeId());
        addDetailSpacer();

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

        // Check for applicable challenges
        List<Challenge> challenges = challengeStore.listChallenges().stream()
            .filter(c -> c.enabled() && c.appliesTo(typeId))
            .toList();
        boolean hasChallenges = !challenges.isEmpty();

        detailContent.removeAll();

        // Build segmented toggle: [form layouts...] + "All Data" + "Challenges" (if applicable)
        boolean needsToggle = hasLayouts || hasChallenges;
        int challengesTabIndex = -1;

        if (needsToggle) {
            List<String> segmentList = new ArrayList<>();
            if (hasLayouts) {
                for (FormLayout l : layouts) segmentList.add(l.name());
            }
            segmentList.add("All Data");
            if (hasChallenges) {
                challengesTabIndex = segmentList.size();
                segmentList.add("Challenges");
            }
            String[] segments = segmentList.toArray(new String[0]);

            SegmentedToggle detailToggle = new SegmentedToggle(segments);
            if (entityDetailViewIndex < 0 || entityDetailViewIndex >= segments.length) {
                entityDetailViewIndex = hasLayouts ? 0 : segments.length - (hasChallenges ? 2 : 1);
            }
            detailToggle.setSelectedIndex(entityDetailViewIndex);
            detailToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, detailToggle.getPreferredSize().height));
            detailToggle.setOnSelectionChanged(i -> {
                entityDetailViewIndex = i;
                showEntityDetails(entity);
            });
            detailContent.add(detailToggle);
            addDetailSpacer();
        }

        if (hasChallenges && entityDetailViewIndex == challengesTabIndex) {
            renderChallengesView(entity, challenges);
        } else if (hasLayouts && entityDetailViewIndex < layouts.size()) {
            renderFormLayoutView(entity, schemaType, layouts.get(entityDetailViewIndex));
        } else {
            renderAllDataView(entity, schemaType);
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

        addEntityActionButtons(entity);
    }

    private void renderChallengesView(CoinEntity entity, List<Challenge> challenges) {
        addDetailHeader(entity.name());
        addDetailSpacer();

        // Global auto-refresh toggle
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        togglePanel.setBackground(bgCard());
        togglePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        togglePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JCheckBox autoToggle = new JCheckBox("Auto-refresh");
        autoToggle.setFont(new Font("SansSerif", Font.PLAIN, 11));
        autoToggle.setSelected(challengeScheduler.isEnabled());
        autoToggle.addActionListener(e -> {
            boolean enabled = autoToggle.isSelected();
            challengeScheduler.setEnabled(enabled);
            IntelConfig.get().setChallengeAutoRefreshEnabled(enabled);
            IntelConfig.get().save();
        });
        togglePanel.add(autoToggle);
        detailContent.add(togglePanel);
        addDetailSpacer();

        // Challenge rows
        for (Challenge challenge : challenges) {
            addChallengeRow(entity, challenge);
        }

        // Show latest result below the list
        ChallengeResult latestResult = null;
        for (Challenge c : challenges) {
            ChallengeResult r = challengeStore.getLatestResult(c.id(), entity.id());
            if (r != null && (latestResult == null || r.timestamp() > latestResult.timestamp())) {
                latestResult = r;
            }
        }
        if (latestResult != null) {
            addDetailSpacer();
            Challenge rc = challengeStore.getChallenge(latestResult.challengeId());
            String title = rc != null ? rc.title() : latestResult.challengeId();
            addDetailSection("LATEST: " + title.toUpperCase());
            if (latestResult.hasError()) {
                addDetailLabel("Error: " + latestResult.error(), new Color(220, 60, 60));
            } else if (latestResult.textResult() != null) {
                addDetailText(latestResult.textResult());
            } else if (latestResult.listResult() != null) {
                for (String item : latestResult.listResult()) {
                    addDetailLabel("  \u2022 " + item);
                }
            } else if (latestResult.entityResult() != null && latestResult.entityResult().entities() != null) {
                addDetailLabel(latestResult.entityResult().entities().size() + " entities found");
            }
            if (latestResult.hasSignal()) {
                addDetailLabel("Signal: " + String.format("%.1f", latestResult.signalValue()), textSecondary());
            }
        }
    }

    private void addChallengeRow(CoinEntity entity, Challenge challenge) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(bgCard());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Left: title + description
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(bgCard());

        JLabel titleLabel = new JLabel(challenge.title());
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        titleLabel.setForeground(textPrimary());
        textPanel.add(titleLabel);

        // Status line: run count + last run time + signal sparkline
        ChallengeResult latest = challengeStore.getLatestResult(challenge.id(), entity.id());
        List<ChallengeResult> signalHistory = challengeStore.getSignalHistory(challenge.id(), entity.id(), 10);

        StringBuilder status = new StringBuilder();
        if (latest != null) {
            long ago = System.currentTimeMillis() - latest.timestamp();
            String agoStr = ago < 3600_000 ? (ago / 60_000) + "m ago"
                : ago < 86_400_000 ? (ago / 3600_000) + "h ago"
                : (ago / 86_400_000) + "d ago";
            status.append("last: ").append(agoStr);
            if (latest.hasSignal()) {
                status.append(" \u2022 ").append(String.format("%.1f", latest.signalValue()));
            }
        } else {
            status.append("not run");
        }
        if (challenge.refreshInterval() != null) {
            long hours = challenge.refreshInterval().toHours();
            String interval = hours >= 24 ? (hours / 24) + "d" : hours + "h";
            status.append(" \u2022 \u23F1").append(interval);
        }

        JLabel statusLabel = new JLabel(status.toString());
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
        statusLabel.setForeground(textMuted());
        textPanel.add(statusLabel);
        row.add(textPanel, BorderLayout.CENTER);

        // Right: escalation buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        btnPanel.setBackground(bgCard());

        Color[] tierColors = { new Color(80, 180, 80), new Color(200, 180, 60), new Color(200, 80, 80) };
        for (int i = 0; i < challenge.escalations().size(); i++) {
            ChallengeEscalation esc = challenge.escalations().get(i);
            int escIndex = i;
            JButton btn = new JButton(esc.label());
            btn.setFont(new Font("SansSerif", Font.PLAIN, 9));
            btn.setToolTipText(esc.description());
            btn.setForeground(tierColors[Math.min(i, tierColors.length - 1)]);
            btn.addActionListener(e -> {
                btn.setEnabled(false);
                btn.setText("...");
                CoinEntitySubject subject = new CoinEntitySubject(entity, schemaRegistry, entityStore);
                ChallengeResult prev = challengeStore.getLatestResult(challenge.id(), subject.id());
                Thread.ofVirtual().start(() -> {
                    ChallengeResult result = challengeExecutor.execute(challenge, subject, escIndex,
                        msg -> IntelLogPanel.logAI(msg), prev);
                    challengeStore.saveResult(result);
                    challengeScheduler.ensureSubscribed(challenge.id(), entity.id());
                    SwingUtilities.invokeLater(() -> {
                        btn.setEnabled(true);
                        btn.setText(esc.label());
                        showEntityDetails(entity);
                    });
                });
            });
            btnPanel.add(btn);
        }
        row.add(btnPanel, BorderLayout.EAST);

        // Sparkline for signal history
        if (!signalHistory.isEmpty() && signalHistory.size() > 1) {
            JPanel sparkline = createSparkline(signalHistory);
            row.add(sparkline, BorderLayout.SOUTH);
        }

        detailContent.add(row);
        detailContent.add(Box.createVerticalStrut(4));
    }

    private JPanel createSparkline(List<ChallengeResult> history) {
        // Reverse to get chronological order
        List<Double> values = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            Double v = history.get(i).signalValue();
            if (v != null) values.add(v);
        }

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (values.size() < 2) return;

                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
                if (max == min) { max = min + 1; }

                int w = getWidth() - 4;
                int h = getHeight() - 4;
                int n = values.size();

                // Trend color: green if trending up, red if down
                boolean trendUp = values.get(values.size() - 1) >= values.get(0);
                g2.setColor(trendUp ? new Color(80, 180, 80, 180) : new Color(200, 80, 80, 180));
                g2.setStroke(new BasicStroke(1.5f));

                int[] xPoints = new int[n];
                int[] yPoints = new int[n];
                for (int i = 0; i < n; i++) {
                    xPoints[i] = 2 + (int) ((double) i / (n - 1) * w);
                    yPoints[i] = 2 + h - (int) ((values.get(i) - min) / (max - min) * h);
                }
                g2.drawPolyline(xPoints, yPoints, n);
            }
        };
        panel.setBackground(bgCard());
        panel.setPreferredSize(new Dimension(0, 16));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        return panel;
    }

    private void seedDefaultChallenges() {
        // Only seed if store is empty
        if (!challengeStore.listChallenges().isEmpty()) return;

        Challenge c1 = new Challenge("tokenomics-analysis", "Tokenomics");
        c1.setDescription("Analyze the tokenomics of this cryptocurrency: supply model, emission schedule, "
            + "staking mechanics, burn mechanisms, vesting schedules, and inflation/deflation dynamics. "
            + "Assess the long-term sustainability of the token economic model.");
        c1.setTargetTypeIds(List.of("coin", "l2"));
        c1.setOutput(new com.tradery.ai.challenges.model.ChallengeOutput(com.tradery.ai.challenges.model.ChallengeOutput.Type.TEXT));
        c1.setSignalConfig(com.tradery.ai.challenges.model.SignalConfig.explicit(
            "End your response with [SIGNAL: X] where X is a score from 0 (terrible tokenomics) to 10 (excellent)."));
        c1.setEscalations(List.of(
            createEsc("Quick", "fast", null, false, "Fast overview from cached knowledge"),
            createEsc("Standard", "standard", null, false, "Detailed analysis"),
            createEsc("Deep", "premium", null, true, "Comprehensive with verification")
        ));
        c1.setRefreshInterval(java.time.Duration.ofDays(7));
        c1.setDisplayOrder(1);
        challengeStore.saveChallenge(c1);

        Challenge c2 = new Challenge("regulatory-risks", "Regulatory Risks");
        c2.setDescription("Identify the key regulatory risks facing this entity. Consider jurisdiction-specific "
            + "regulations, pending legislation, enforcement actions, and compliance challenges.");
        c2.setTargetTypeIds(List.of("coin", "l2", "etf", "exchange"));
        c2.setOutput(new com.tradery.ai.challenges.model.ChallengeOutput(com.tradery.ai.challenges.model.ChallengeOutput.Type.LIST));
        c2.setSignalConfig(com.tradery.ai.challenges.model.SignalConfig.ordinal(
            java.util.Map.of("LOW", 1.0, "MEDIUM", 2.0, "HIGH", 3.0, "CRITICAL", 4.0)));
        c2.setEscalations(List.of(
            createEsc("Standard", "standard", null, false, "Identify major risks"),
            createEsc("Deep", "premium", null, true, "Comprehensive risk assessment with verification")
        ));
        c2.setRefreshInterval(java.time.Duration.ofDays(3));
        c2.setDisplayOrder(2);
        challengeStore.saveChallenge(c2);

        Challenge c3 = new Challenge("investment-thesis", "Investment Thesis");
        c3.setDescription("Construct a balanced investment thesis for this entity. Cover the bull case, "
            + "bear case, key catalysts, major risks, competitive moat, and market positioning. "
            + "Provide a clear assessment of the risk/reward profile.");
        c3.setTargetTypeIds(List.of("coin", "l2"));
        c3.setOutput(new com.tradery.ai.challenges.model.ChallengeOutput(com.tradery.ai.challenges.model.ChallengeOutput.Type.TEXT));
        c3.setSignalConfig(com.tradery.ai.challenges.model.SignalConfig.explicit(
            "End your response with [SIGNAL: X] where X is 0 (strong sell) to 10 (strong buy)."));
        c3.setEscalations(List.of(
            createEsc("Standard", "standard", null, false, "Balanced thesis overview"),
            createEsc("Deep", "premium", null, true, "In-depth thesis with verification")
        ));
        c3.setRefreshInterval(java.time.Duration.ofDays(7));
        c3.setDisplayOrder(3);
        challengeStore.saveChallenge(c3);

        Challenge c4 = new Challenge("competitive-landscape", "Competitors");
        c4.setDescription("Find the main competitors of this entity based on market positioning, "
            + "technology, target audience, and use case overlap.");
        c4.setTargetTypeIds(List.of("coin", "l2", "exchange"));
        com.tradery.ai.challenges.model.ChallengeOutput entityOutput =
            new com.tradery.ai.challenges.model.ChallengeOutput(com.tradery.ai.challenges.model.ChallengeOutput.Type.ENTITY_SET);
        entityOutput.config().put("relationshipTypeId", "competitor");
        c4.setOutput(entityOutput);
        c4.setSignalConfig(com.tradery.ai.challenges.model.SignalConfig.count());
        c4.setEscalations(List.of(
            createEsc("Quick", "fast", "quick", false, "Quick competitor scan"),
            createEsc("Deep", "premium", "deep", false, "Comprehensive competitor discovery")
        ));
        c4.setRefreshInterval(java.time.Duration.ofDays(14));
        c4.setDisplayOrder(4);
        challengeStore.saveChallenge(c4);

        Challenge c5 = new Challenge("sentiment-summary", "Market Sentiment");
        c5.setDescription("Summarize the current market sentiment around this entity. "
            + "Consider social media discourse, developer activity, whale movements, "
            + "funding rates, and recent news narratives.");
        c5.setTargetTypeIds(List.of("coin", "l2"));
        c5.setOutput(new com.tradery.ai.challenges.model.ChallengeOutput(com.tradery.ai.challenges.model.ChallengeOutput.Type.TEXT));
        c5.setSignalConfig(com.tradery.ai.challenges.model.SignalConfig.ordinal(
            java.util.Map.of("VERY_BEARISH", 1.0, "BEARISH", 2.0, "NEUTRAL", 3.0, "BULLISH", 4.0, "VERY_BULLISH", 5.0)));
        c5.setEscalations(List.of(
            createEsc("Quick", "fast", null, false, "Quick sentiment read"),
            createEsc("Standard", "standard", null, false, "Detailed sentiment analysis"),
            createEsc("Deep", "premium", null, true, "Deep sentiment with verification")
        ));
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

        detailContent.add(btnPanel);
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
                fetchBtn.setText("Fetch News");
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

    /** Trigger a forced refresh of coin data (used by CoinGeckoWindow). */
    public void refreshCoinData() {
        loadCoinData(true);
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
                    allEntities.addAll(entityStore.loadEntitiesBySource("ai-discovery"));
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

        // Apply relationship type filter
        if (config.getRelationshipTypeFilter() != null && !config.getRelationshipTypeFilter().isEmpty()) {
            Set<String> relTypeFilter = config.getRelationshipTypeFilter();
            filteredRels = filteredRels.stream()
                .filter(r -> relTypeFilter.contains(r.typeId()))
                .toList();
        }

        // Respect showConnections toggle
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
        if (windowName.startsWith("select-entity:")) {
            String entityId = windowName.substring("select-entity:".length());
            selectEntityById(entityId);
            return;
        }
        if (windowName.startsWith("view:")) {
            String viewArg = windowName.substring("view:".length());
            switchView(viewArg);
            return;
        }
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

    private void selectEntityById(String entityId) {
        // Switch to first COIN_GRAPH tab
        for (int i = 0; i < panelInstances.size(); i++) {
            if (panelInstances.get(i).config().getType() == PanelConfig.PanelType.COIN_GRAPH) {
                viewToggle.setSelectedIndex(i);
                CoinGraphPanel cgp = (CoinGraphPanel) panelInstances.get(i).graphPanel();
                cgp.selectAndPanTo(entityId);
                break;
            }
        }
        toFront();
        requestFocus();
    }

    private void switchView(String viewArg) {
        // Try as index first
        try {
            int index = Integer.parseInt(viewArg);
            if (index >= 0 && index < panelInstances.size()) {
                viewToggle.setSelectedIndex(index);
            }
            return;
        } catch (NumberFormatException ignored) {}

        // Match by panel name (case-insensitive)
        for (int i = 0; i < panelInstances.size(); i++) {
            if (panelInstances.get(i).config().getName().equalsIgnoreCase(viewArg)) {
                viewToggle.setSelectedIndex(i);
                return;
            }
        }
    }

    /** Returns current UI state for the /context API endpoint. */
    public UiState getUiState() {
        int activeIndex = viewToggle.getSelectedIndex();
        return new UiState(activeIndex, selectedEntity);
    }

    public record UiState(int activeViewIndex, CoinEntity selectedEntity) {}

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
            this, entityStore, schemaRegistry, currentEntities, preselectedFromId, rel -> {
                if (currentRelationships != null) {
                    currentRelationships.add(rel);
                    refreshAllCoinPanels();
                    logPanel.success("Added relationship: " + rel.getLabel(schemaRegistry));
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
        relationships.add(new CoinRelationship("arbitrum", "ethereum", "l2_of"));
        entities.add(createETF("ibit", "iShares Bitcoin Trust", "IBIT"));
        relationships.add(new CoinRelationship("ibit", "bitcoin", "etf_tracks"));
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
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Intelligence");

        try {
            // Apply saved theme (or default)
            ThemeHelper.applyCurrentTheme();
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

        // macOS application menu handlers
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
                desktop.setPreferencesHandler(e -> SwingUtilities.invokeLater(() -> {
                    Window active = javax.swing.FocusManager.getCurrentManager().getActiveWindow();
                    if (active == null) active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                    new IntelSettingsDialog(active).setVisible(true);
                }));
            }
        }

        SwingUtilities.invokeLater(() -> {
            // First-run: show setup if no AI profiles configured
            if (AiConfig.get().getProfiles().isEmpty()) {
                AiSetupDialog.showSetup(null);
            }

            // Wire up AI activity logging to the log panel
            AiClient.getInstance().setActivityListener((summary, prompt, response) ->
                IntelLogPanel.logAI(summary, prompt, response));

            // Initialize document manager and launch
            IntelDocumentManager documentManager = new IntelDocumentManager();
            try {
                documentManager.initialize();
            } catch (Exception e) {
                System.err.println("Failed to initialize documents: " + e.getMessage());
            }

            // Try to load sharing module (optional runtime dependency)
            try {
                Class<?> implClass = Class.forName("com.tradery.sharing.SharingServiceImpl");
                Object service = implClass.getConstructor(java.nio.file.Path.class)
                    .newInstance(java.nio.file.Path.of(System.getProperty("user.home"), ".tradery", "documents"));
                SharingService sharingService = (SharingService) service;
                IntelDocumentFrame.setSharingService(sharingService);

                // Initialize chat persistence
                java.nio.file.Path chatDbPath = java.nio.file.Path.of(
                    System.getProperty("user.home"), ".tradery", "chat.db");
                ChatStore chatStore = new ChatStore(chatDbPath);
                IntelDocumentFrame.setChatStore(chatStore);

                // Bootstrap peer infrastructure so LAN discovery starts immediately
                sharingService.bootstrap();
                System.out.println("[Sharing] Module loaded and bootstrapped");
            } catch (ClassNotFoundException e) {
                System.err.println("[Sharing] Module not available: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[Sharing] Failed to initialize: " + e.getMessage());
                e.printStackTrace();
            }

            IntelLauncherFrame launcher = new IntelLauncherFrame(documentManager);
            launcher.setVisible(true);

            // Auto-open last document if available
            String lastDocId = IntelConfig.get().getLastOpenedDocId();
            if (lastDocId != null) {
                launcher.openDocumentById(lastDocId);
            }
        });
    }
}
