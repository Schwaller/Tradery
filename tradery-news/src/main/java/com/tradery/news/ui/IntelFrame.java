package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.news.ai.ClaudeCliProcessor;
import com.tradery.news.fetch.FetchScheduler;
import com.tradery.news.fetch.FetcherRegistry;
import com.tradery.news.model.Article;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.topic.TopicRegistry;
import com.tradery.news.api.IntelApiServer;
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

    // News components
    private final SqliteNewsStore store;
    private final Path dataDir;
    private TimelineGraphPanel newsGraphPanel;
    private JLabel newsStatusLabel;
    private JComboBox<String> limitCombo;
    private volatile boolean fetching = false;

    // Coins components
    private CoinGraphPanel coinGraphPanel;
    private JLabel coinStatusLabel;
    private JProgressBar coinProgressBar;
    private EntityStore entityStore;
    private List<CoinEntity> currentEntities;
    private List<CoinRelationship> currentRelationships;

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
    private static Color linkColor() { return linkColor(); }

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
        super("Tradery - Intelligence");

        // Initialize stores
        this.dataDir = Path.of(System.getProperty("user.home"), ".cryptonews");
        this.store = new SqliteNewsStore(dataDir.resolve("news.db"));
        this.entityStore = new EntityStore();

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

                if (apiServer != null) apiServer.stop();
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
        mainPanel.setBackground(bgMain());

        // Initialize card layout first (header buttons reference it)
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // Full-width header bar: [News][Coin Relations]  --title--  [Settings]
        JPanel headerBar = createHeaderBar();
        mainPanel.add(headerBar, BorderLayout.NORTH);

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
        headerBar.setBackground(bgCard());
        headerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()));
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

        String[] views = {"News", "Coin Relations"};
        String[] cards = {"news", "coins"};
        viewToggle = new SegmentedToggle(views);
        viewToggle.setOnSelectionChanged(i -> {
            cardLayout.show(cardPanel, cards[i]);
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
        JLabel titleLabel = new JLabel("Tradery - Intelligence");
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
            if (newsGraphPanel != null) {
                newsGraphPanel.setMaxNodes(Integer.parseInt((String) limitCombo.getSelectedItem()));
                loadNewsData();
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
        resetViewBtn.addActionListener(e -> coinGraphPanel.resetView());
        resetViewBtn.setVisible(false);  // Hidden by default (News is selected)
        rightContent.add(resetViewBtn);

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

        // News panel
        JPanel newsPanel = new JPanel(new BorderLayout());
        newsGraphPanel = new TimelineGraphPanel();
        newsGraphPanel.setOnNodeSelected(this::showArticleDetails);
        newsGraphPanel.setOnTopicSelected(this::showTopicDetails);
        newsPanel.add(newsGraphPanel, BorderLayout.CENTER);
        newsPanel.add(createNewsStatusBar(), BorderLayout.SOUTH);
        cardPanel.add(newsPanel, "news");

        // Coins panel
        JPanel coinsPanel = new JPanel(new BorderLayout());
        coinGraphPanel = new CoinGraphPanel();
        coinGraphPanel.setOnEntitySelected(this::showEntityDetails);
        coinsPanel.add(coinGraphPanel, BorderLayout.CENTER);
        coinsPanel.add(createCoinsStatusBar(), BorderLayout.SOUTH);
        cardPanel.add(coinsPanel, "coins");

        panel.add(cardPanel, BorderLayout.CENTER);
        return panel;
    }

    private void updateHeaderButtons() {
        boolean isNewsView = viewToggle.getSelectedIndex() == 0;
        showLabel.setVisible(isNewsView);
        limitCombo.setVisible(isNewsView);
        fetchBtn.setVisible(isNewsView);
        resetViewBtn.setVisible(!isNewsView);
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(bgCard());
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, borderColor()));

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

    // ==================== STATUS BARS ====================

    private JPanel createCoinsStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusBar.setBackground(bgHeader());
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor()));

        coinStatusLabel = new JLabel("Loading...");
        coinStatusLabel.setForeground(textSecondary());
        statusBar.add(coinStatusLabel);

        coinProgressBar = new JProgressBar(0, 100);
        coinProgressBar.setPreferredSize(new Dimension(150, 16));
        coinProgressBar.setStringPainted(true);
        coinProgressBar.setVisible(false);
        statusBar.add(coinProgressBar);

        return statusBar;
    }


    private JPanel createNewsStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusBar.setBackground(bgHeader());
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor()));

        newsStatusLabel = new JLabel("Loading...");
        newsStatusLabel.setForeground(textSecondary());
        statusBar.add(newsStatusLabel);

        return statusBar;
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
                coinGraphPanel.selectAndPanTo(targetId);
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
        dataStructureFrame = new DataStructureFrame(entityStore, v -> loadCoinData(false));
        dataStructureFrame.setVisible(true);
    }

    private void showSettingsWindow() {
        logPanel.info("Opening Settings...");
        EntityManagerFrame settings = new EntityManagerFrame(entityStore, v -> loadCoinData(false));
        settings.setVisible(true);
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
            // Apply saved theme (or default)
            IntelConfig.applyCurrentTheme();
            UIManager.put("Button.arc", 5);
            UIManager.put("Component.arc", 5);
            UIManager.put("TextComponent.arc", 5);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> new IntelFrame().setVisible(true));
    }
}
