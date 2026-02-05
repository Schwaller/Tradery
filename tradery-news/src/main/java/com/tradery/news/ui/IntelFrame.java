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
 * Combined intelligence window with tabs for News and Coin Relationships.
 */
public class IntelFrame extends JFrame {

    private final JTabbedPane tabbedPane;

    // News tab components
    private final SqliteNewsStore store;
    private final Path dataDir;
    private TimelineGraphPanel newsGraphPanel;
    private JTextArea newsDetailArea;
    private JLabel newsStatusLabel;
    private JComboBox<String> limitCombo;
    private JButton fetchBtn;
    private volatile boolean fetching = false;

    // Coins tab components
    private CoinGraphPanel coinGraphPanel;
    private JPanel coinDetailContent;
    private JLabel coinStatusLabel;
    private JProgressBar coinProgressBar;
    private EntityStore entityStore;
    private List<CoinEntity> currentEntities;
    private List<CoinRelationship> currentRelationships;

    public IntelFrame() {
        super("Tradery - Intelligence");

        // Initialize news store
        this.dataDir = Path.of(System.getProperty("user.home"), ".cryptonews");
        this.store = new SqliteNewsStore(dataDir.resolve("news.db"));

        // Initialize entity store
        this.entityStore = new EntityStore();

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1800, 1000);
        setLocationRelativeTo(null);

        // Main layout with tabbed pane
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(30, 32, 36));

        tabbedPane = new JTabbedPane();
        tabbedPane.setTabPlacement(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 12));

        // Create tabs
        tabbedPane.addTab("Coins", createCoinsTab());
        tabbedPane.addTab("News", createNewsTab());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        setContentPane(mainPanel);

        // Cleanup on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (newsGraphPanel != null) newsGraphPanel.stopPhysics();
                if (coinGraphPanel != null) coinGraphPanel.stopPhysics();
                if (entityStore != null) entityStore.close();
            }
        });

        // Load data for visible tab
        SwingUtilities.invokeLater(() -> {
            loadCoinData(false);
            loadNewsData();
        });
    }

    // ==================== NEWS TAB ====================

    private JPanel createNewsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(30, 32, 36));

        // Toolbar
        panel.add(createNewsToolbar(), BorderLayout.NORTH);

        // Graph panel
        newsGraphPanel = new TimelineGraphPanel();
        newsGraphPanel.setOnNodeSelected(this::showNewsNodeDetails);

        // Detail panel (right sidebar)
        JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.setPreferredSize(new Dimension(960, 0));
        detailPanel.setMinimumSize(new Dimension(640, 0));
        detailPanel.setBackground(new Color(38, 40, 44));
        detailPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(50, 52, 56)));

        JLabel detailTitle = new JLabel("  Article Details");
        detailTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        detailTitle.setForeground(new Color(180, 180, 190));
        detailTitle.setPreferredSize(new Dimension(0, 32));
        detailPanel.add(detailTitle, BorderLayout.NORTH);

        newsDetailArea = new JTextArea();
        newsDetailArea.setEditable(false);
        newsDetailArea.setLineWrap(true);
        newsDetailArea.setWrapStyleWord(true);
        newsDetailArea.setBackground(new Color(38, 40, 44));
        newsDetailArea.setForeground(new Color(200, 200, 210));
        newsDetailArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        newsDetailArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        newsDetailArea.setText("Click a node to see details");

        JScrollPane detailScroll = new JScrollPane(newsDetailArea);
        detailScroll.setBorder(null);
        detailPanel.add(detailScroll, BorderLayout.CENTER);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, newsGraphPanel, detailPanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);
        panel.add(splitPane, BorderLayout.CENTER);

        // Status bar
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusBar.setBackground(new Color(35, 37, 41));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 52, 56)));
        newsStatusLabel = new JLabel("Loading...");
        newsStatusLabel.setForeground(new Color(140, 140, 150));
        statusBar.add(newsStatusLabel);
        panel.add(statusBar, BorderLayout.SOUTH);

        return panel;
    }

    private JToolBar createNewsToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(38, 40, 44));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        fetchBtn = new JButton("Fetch New");
        fetchBtn.setToolTipText("Fetch new articles from RSS feeds with AI extraction");
        fetchBtn.addActionListener(e -> fetchNewArticles());
        toolbar.add(fetchBtn);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setToolTipText("Reload from database");
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

        toolbar.addSeparator();
        toolbar.add(Box.createHorizontalGlue());

        JLabel legend = new JLabel("Size = Importance  |  Color = Sentiment (green=positive, gray=neutral, red=negative)  ");
        legend.setForeground(new Color(120, 120, 130));
        legend.setFont(new Font("SansSerif", Font.PLAIN, 11));
        toolbar.add(legend);

        return toolbar;
    }

    private void loadNewsData() {
        newsStatusLabel.setText("Loading...");

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
                    newsStatusLabel.setText(articles.size() + " articles loaded  |  " +
                        store.getArticleCount() + " total in database  |  " +
                        store.getTopicCounts().size() + " topics");
                } catch (Exception e) {
                    newsStatusLabel.setText("Error: " + e.getMessage());
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
        newsStatusLabel.setText("Fetching new articles with AI extraction (this may take a while)...");

        SwingWorker<FetchScheduler.FetchResult, String> worker = new SwingWorker<>() {
            @Override
            protected FetchScheduler.FetchResult doInBackground() {
                FetcherRegistry fetchers = new FetcherRegistry();
                fetchers.registerDefaults();

                TopicRegistry topics = new TopicRegistry(dataDir.resolve("topics.json"));
                ClaudeCliProcessor ai = new ClaudeCliProcessor();

                if (!ai.isAvailable()) {
                    publish("Claude CLI not available - fetching without AI extraction");
                    ai = null;
                }

                try (var scheduler = new FetchScheduler(fetchers, topics, store, ai)) {
                    scheduler.withAiEnabled(ai != null)
                             .withArticlesPerSource(ai != null ? 5 : 10);
                    return scheduler.fetchAndProcess();
                }
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    newsStatusLabel.setText(chunks.get(chunks.size() - 1));
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
                        newsStatusLabel.setText(String.format("Added %d new articles (%d AI processed)  |  Total: %d",
                            added, result.aiProcessed(), store.getArticleCount()));
                    } else {
                        newsStatusLabel.setText("No new articles found  |  Total: " + store.getArticleCount());
                    }
                } catch (Exception e) {
                    newsStatusLabel.setText("Fetch error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void showNewsNodeDetails(NewsNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("TITLE\n").append(node.title()).append("\n\n");
        sb.append("SOURCE\n").append(node.source()).append("\n\n");

        if (node.sourceUrl() != null && !node.sourceUrl().isEmpty()) {
            sb.append("URL\n").append(node.sourceUrl()).append("\n\n");
        }

        sb.append("PUBLISHED\n");
        LocalDateTime ldt = LocalDateTime.ofInstant(node.publishedAt(), ZoneId.systemDefault());
        sb.append(ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        sb.append("IMPORTANCE\n").append(node.importance()).append("\n\n");

        sb.append("SENTIMENT\n");
        sb.append(String.format("%.2f", node.sentiment()));
        if (node.sentiment() > 0.3) sb.append(" (Positive)");
        else if (node.sentiment() < -0.3) sb.append(" (Negative)");
        else sb.append(" (Neutral)");
        sb.append("\n\n");

        sb.append("TOPICS\n").append(node.topics().isEmpty() ? "(none)" : String.join(", ", node.topics())).append("\n\n");
        sb.append("COINS\n").append(node.coins().isEmpty() ? "(none)" : String.join(", ", node.coins())).append("\n\n");

        if (node.summary() != null && !node.summary().isEmpty()) {
            sb.append("SUMMARY\n").append(node.summary()).append("\n\n");
        }
        if (node.content() != null && !node.content().isEmpty()) {
            sb.append("CONTENT\n").append(node.content()).append("\n\n");
        }

        sb.append("CONNECTIONS\n").append(node.connections().size()).append(" related articles");

        newsDetailArea.setText(sb.toString());
        newsDetailArea.setCaretPosition(0);
    }

    // ==================== COINS TAB ====================

    private JPanel createCoinsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(30, 32, 36));

        // Toolbar
        panel.add(createCoinsToolbar(), BorderLayout.NORTH);

        // Graph panel
        coinGraphPanel = new CoinGraphPanel();
        coinGraphPanel.setOnEntitySelected(this::showCoinEntityDetails);

        // Detail panel
        JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.setPreferredSize(new Dimension(350, 0));
        detailPanel.setMinimumSize(new Dimension(250, 0));
        detailPanel.setBackground(new Color(38, 40, 44));
        detailPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(50, 52, 56)));

        JLabel detailTitle = new JLabel("  Entity Details");
        detailTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        detailTitle.setForeground(new Color(180, 180, 190));
        detailTitle.setPreferredSize(new Dimension(0, 32));
        detailPanel.add(detailTitle, BorderLayout.NORTH);

        coinDetailContent = new JPanel();
        coinDetailContent.setLayout(new BoxLayout(coinDetailContent, BoxLayout.Y_AXIS));
        coinDetailContent.setBackground(new Color(38, 40, 44));
        coinDetailContent.setBorder(new EmptyBorder(10, 10, 10, 10));

        addCoinDetailLabel("Click an entity to see details");
        addCoinDetailLabel("");
        addCoinDetailLabel("Scroll to zoom");
        addCoinDetailLabel("Shift+drag to pan");
        addCoinDetailLabel("Double-click to pin/unpin");

        JScrollPane detailScroll = new JScrollPane(coinDetailContent);
        detailScroll.setBorder(null);
        detailScroll.getVerticalScrollBar().setUnitIncrement(16);
        detailPanel.add(detailScroll, BorderLayout.CENTER);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, coinGraphPanel, detailPanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);
        panel.add(splitPane, BorderLayout.CENTER);

        // Status bar
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusBar.setBackground(new Color(35, 37, 41));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 52, 56)));
        coinStatusLabel = new JLabel("Loading...");
        coinStatusLabel.setForeground(new Color(140, 140, 150));
        statusBar.add(coinStatusLabel);

        coinProgressBar = new JProgressBar(0, 100);
        coinProgressBar.setPreferredSize(new Dimension(200, 16));
        coinProgressBar.setStringPainted(true);
        coinProgressBar.setVisible(false);
        statusBar.add(coinProgressBar);

        panel.add(statusBar, BorderLayout.SOUTH);

        return panel;
    }

    private JToolBar createCoinsToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(38, 40, 44));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        JButton resetBtn = new JButton("Reset View");
        resetBtn.addActionListener(e -> coinGraphPanel.resetView());
        toolbar.add(resetBtn);

        JButton refreshBtn = new JButton("Refresh Data");
        refreshBtn.addActionListener(e -> loadCoinData(true));
        toolbar.add(refreshBtn);

        toolbar.addSeparator();

        JButton manageEntitiesBtn = new JButton("Manage Entities");
        manageEntitiesBtn.setToolTipText("Open entity manager to add/edit entities");
        manageEntitiesBtn.addActionListener(e -> showEntityManager());
        toolbar.add(manageEntitiesBtn);

        JButton addRelBtn = new JButton("+ Relationship");
        addRelBtn.setToolTipText("Add a relationship between entities");
        addRelBtn.addActionListener(e -> showAddRelationshipDialog(null));
        toolbar.add(addRelBtn);

        toolbar.addSeparator();

        JCheckBox labelsCheck = new JCheckBox("Labels", true);
        labelsCheck.addActionListener(e -> coinGraphPanel.setShowLabels(labelsCheck.isSelected()));
        toolbar.add(labelsCheck);

        JCheckBox relLabelsCheck = new JCheckBox("Relationship Labels", false);
        relLabelsCheck.addActionListener(e -> coinGraphPanel.setShowRelationshipLabels(relLabelsCheck.isSelected()));
        toolbar.add(relLabelsCheck);

        toolbar.add(Box.createHorizontalGlue());

        JLabel hint = new JLabel("Scroll=zoom  Shift+drag=pan  Double-click=pin  ");
        hint.setForeground(new Color(120, 120, 130));
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        toolbar.add(hint);

        return toolbar;
    }

    private void showEntityManager() {
        EntityManagerFrame manager = new EntityManagerFrame(entityStore, v -> {
            // Reload data when entities change
            loadCoinData(false);
        });
        manager.setVisible(true);
    }

    private void showAddRelationshipDialog(String preselectedFromId) {
        if (currentEntities == null || currentEntities.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No entities loaded yet", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        RelationshipEditorDialog dialog = new RelationshipEditorDialog(
            this, entityStore, currentEntities, preselectedFromId, rel -> {
                // Add to current list and refresh graph
                if (currentRelationships != null) {
                    currentRelationships.add(rel);
                    coinGraphPanel.setData(currentEntities, currentRelationships);
                    updateCoinStatus();
                }
            }
        );
        dialog.setVisible(true);
    }

    private void updateCoinStatus() {
        int manualEntities = entityStore.getManualEntityCount();
        int manualRels = entityStore.getManualRelationshipCount();
        String status = currentEntities.size() + " entities  |  " + currentRelationships.size() + " relationships";
        if (manualEntities > 0 || manualRels > 0) {
            status += "  |  " + manualEntities + " manual entities, " + manualRels + " manual relationships";
        }
        coinStatusLabel.setText(status);
    }

    private void loadCoinData(boolean forceRefresh) {
        coinStatusLabel.setText("Loading...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            private List<CoinEntity> entities;
            private List<CoinRelationship> relationships;

            @Override
            protected Void doInBackground() {
                try {
                    CoinGeckoClient client = new CoinGeckoClient();
                    boolean fromCache = false;

                    // Load CoinGecko entities (cached or fresh)
                    if (!forceRefresh && entityStore.isCoinGeckoCacheValid()) {
                        publish("Loading coins from database...");
                        entities = entityStore.loadEntitiesBySource("coingecko");
                        fromCache = !entities.isEmpty();
                    }

                    if (entities == null || entities.isEmpty()) {
                        publish("Fetching top 200 coins from CoinGecko...");
                        List<CoinEntity> cgEntities = client.fetchTopCoins(200);
                        entityStore.saveCoinGeckoEntities(cgEntities);
                        entities = new ArrayList<>(cgEntities);
                    }

                    // Load manual entities (always persist)
                    publish("Loading manual entities...");
                    List<CoinEntity> manualEntities = entityStore.loadEntitiesBySource("manual");
                    entities.addAll(manualEntities);

                    // Build auto relationships from CoinGecko data
                    publish("Building relationships...");
                    List<CoinRelationship> autoRels = client.buildRelationships(entities);
                    entityStore.saveAutoRelationships(autoRels);

                    // Load all relationships (auto + manual)
                    relationships = entityStore.loadAllRelationships();

                    // Seed default manual entities if this is a fresh DB with none
                    if (manualEntities.isEmpty()) {
                        publish("Seeding default entities...");
                        seedDefaultManualEntities();
                        // Reload after seeding
                        manualEntities = entityStore.loadEntitiesBySource("manual");
                        entities.addAll(manualEntities);
                        relationships = entityStore.loadAllRelationships();
                    }

                    final boolean cached = fromCache;
                    final List<CoinEntity> finalEntities = entities;
                    final List<CoinRelationship> finalRels = relationships;
                    SwingUtilities.invokeLater(() -> {
                        currentEntities = finalEntities;
                        currentRelationships = finalRels;
                        coinGraphPanel.setData(finalEntities, finalRels);
                        updateCoinStatus();
                        coinProgressBar.setVisible(false);
                    });

                    // Fetch categories if this was a fresh fetch
                    if (!fromCache) {
                        fetchCategories(client, entities);
                    }

                } catch (Exception e) {
                    System.err.println("Error loading coin data: " + e.getMessage());
                    e.printStackTrace();
                    publish("Error: " + e.getMessage());
                    loadFallbackData();
                }
                return null;
            }

            private void fetchCategories(CoinGeckoClient client, List<CoinEntity> entities) {
                SwingUtilities.invokeLater(() -> {
                    coinProgressBar.setVisible(true);
                    coinProgressBar.setValue(0);
                    coinProgressBar.setString("Categories: 0%");
                });

                List<String> cgIds = entities.stream()
                    .filter(e -> e.type() == CoinEntity.Type.COIN)
                    .map(CoinEntity::id)
                    .toList();
                int total = cgIds.size();
                int count = 0;

                for (String coinId : cgIds) {
                    count++;
                    int finalCount = count;
                    int percent = (count * 100) / total;
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
                            SwingUtilities.invokeLater(() -> coinGraphPanel.repaint());
                        }
                    } catch (Exception e) {
                        // Skip failed coins
                    }
                    int finalPercent = percent;
                    SwingUtilities.invokeLater(() -> {
                        coinProgressBar.setValue(finalPercent);
                        coinProgressBar.setString("Categories: " + finalPercent + "% (" + finalCount + "/" + total + ")");
                    });
                }

                SwingUtilities.invokeLater(() -> coinProgressBar.setVisible(false));
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    coinStatusLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                if (entities != null && !entities.isEmpty()) {
                    updateCoinStatus();
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
                    coinStatusLabel.setText(entities.size() + " entities  |  " + relationships.size() + " relationships  (sample data)");
                    coinProgressBar.setVisible(false);
                });
            }
        };
        worker.execute();
    }

    /**
     * Seeds the database with default manual entities (ETFs, VCs, exchanges)
     * on first run when no manual entities exist.
     */
    private void seedDefaultManualEntities() {
        // Get existing coin IDs to check for relationship targets
        Set<String> existingIds = new HashSet<>();
        for (CoinEntity e : entityStore.loadAllEntities()) {
            existingIds.add(e.id());
        }

        // ETFs
        saveManualEntity(createETF("ibit", "iShares Bitcoin Trust", "IBIT"));
        saveManualEntity(createETF("fbtc", "Fidelity Wise Origin Bitcoin", "FBTC"));
        saveManualEntity(createETF("gbtc", "Grayscale Bitcoin Trust", "GBTC"));
        saveManualEntity(createETF("arkb", "ARK 21Shares Bitcoin", "ARKB"));
        saveManualEntity(createETF("bitb", "Bitwise Bitcoin ETF", "BITB"));
        saveManualEntity(createETF("hodl", "VanEck Bitcoin Trust", "HODL"));
        saveManualEntity(createETF("etha", "iShares Ethereum Trust", "ETHA"));
        saveManualEntity(createETF("feth", "Fidelity Ethereum Fund", "FETH"));
        saveManualEntity(createETF("ethe", "Grayscale Ethereum Trust", "ETHE"));
        saveManualEntity(createETF("gsol", "Grayscale Solana Trust", "GSOL"));

        // ETF tracking relationships
        if (existingIds.contains("bitcoin")) {
            for (String etf : List.of("ibit", "fbtc", "gbtc", "arkb", "bitb", "hodl")) {
                saveManualRelationship(new CoinRelationship(etf, "bitcoin", CoinRelationship.Type.ETF_TRACKS));
            }
        }
        if (existingIds.contains("ethereum")) {
            for (String etf : List.of("etha", "feth", "ethe")) {
                saveManualRelationship(new CoinRelationship(etf, "ethereum", CoinRelationship.Type.ETF_TRACKS));
            }
        }
        if (existingIds.contains("solana")) {
            saveManualRelationship(new CoinRelationship("gsol", "solana", CoinRelationship.Type.ETF_TRACKS));
        }

        // VCs
        saveManualEntity(createVC("a16z", "Andreessen Horowitz"));
        saveManualEntity(createVC("paradigm", "Paradigm"));
        saveManualEntity(createVC("polychain", "Polychain Capital"));
        saveManualEntity(createVC("multicoin", "Multicoin Capital"));
        saveManualEntity(createVC("dragonfly", "Dragonfly"));
        saveManualEntity(createVC("pantera", "Pantera Capital"));
        saveManualEntity(createVC("jump", "Jump Crypto"));
        saveManualEntity(createVC("binance-labs", "Binance Labs"));
        saveManualEntity(createVC("coinbase-ventures", "Coinbase Ventures"));

        // VC investments
        seedInvestments(existingIds, "a16z", "solana", "ethereum", "optimism", "arbitrum", "aptos", "sui", "near", "uniswap", "maker");
        seedInvestments(existingIds, "paradigm", "ethereum", "optimism", "cosmos", "uniswap", "maker", "lido-dao");
        seedInvestments(existingIds, "polychain", "cosmos", "polkadot", "avalanche-2", "near", "filecoin");
        seedInvestments(existingIds, "multicoin", "solana", "helium", "the-graph", "arweave", "render-token");
        seedInvestments(existingIds, "jump", "solana", "aptos", "sui", "wormhole", "pyth-network");
        seedInvestments(existingIds, "binance-labs", "polygon-matic", "the-sandbox", "axie-infinity", "1inch", "pancakeswap-token");
        seedInvestments(existingIds, "coinbase-ventures", "polygon-matic", "optimism", "uniswap", "aave", "the-graph");

        // Exchanges
        saveManualEntity(createExchange("binance-ex", "Binance"));
        saveManualEntity(createExchange("coinbase-ex", "Coinbase"));
        saveManualEntity(createExchange("okx-ex", "OKX"));
        saveManualEntity(createExchange("kraken-ex", "Kraken"));

        // Exchange relationships
        if (existingIds.contains("binancecoin")) {
            saveManualRelationship(new CoinRelationship("binance-ex", "binancecoin", CoinRelationship.Type.FOUNDED_BY));
        }
        if (existingIds.contains("okb")) {
            saveManualRelationship(new CoinRelationship("okx-ex", "okb", CoinRelationship.Type.FOUNDED_BY));
        }
    }

    private void saveManualEntity(CoinEntity entity) {
        entityStore.saveEntity(entity, "manual");
    }

    private void saveManualRelationship(CoinRelationship rel) {
        entityStore.saveRelationship(rel, "manual");
    }

    private void seedInvestments(Set<String> existingIds, String vcId, String... coinIds) {
        for (String coinId : coinIds) {
            if (existingIds.contains(coinId)) {
                saveManualRelationship(new CoinRelationship(vcId, coinId, CoinRelationship.Type.INVESTED_IN));
            }
        }
    }

    private void showCoinEntityDetails(CoinEntity entity) {
        coinDetailContent.removeAll();

        addCoinDetailHeader(entity.name());
        if (entity.symbol() != null) {
            addCoinDetailLabel(entity.symbol());
        }
        addCoinDetailSpacer();

        addCoinDetailSection("TYPE");
        addCoinDetailLabel(entity.type().toString());
        addCoinDetailSpacer();

        if (entity.marketCap() > 0) {
            addCoinDetailSection("MARKET CAP");
            addCoinDetailLabel("$" + formatMarketCap(entity.marketCap()));
            addCoinDetailSpacer();
        }

        List<CoinRelationship> rels = coinGraphPanel.getRelationshipsFor(entity.id());
        if (!rels.isEmpty()) {
            addCoinDetailSection("RELATIONSHIPS (" + rels.size() + ")");
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

                addCoinRelationshipRow(description, rel.type().color(), otherId, other.name());
            }
            addCoinDetailSpacer();
        }

        if (!entity.categories().isEmpty()) {
            addCoinDetailSection("CATEGORIES");
            for (String cat : entity.categories()) {
                addCoinDetailLabel("  " + cat);
            }
            addCoinDetailSpacer();
        }

        addCoinDetailSection("STATUS");
        addCoinDetailLabel(entity.isPinned() ? "Pinned" : "Free-floating");

        coinDetailContent.revalidate();
        coinDetailContent.repaint();
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

    private void addCoinDetailHeader(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        label.setForeground(new Color(220, 220, 230));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        coinDetailContent.add(label);
    }

    private void addCoinDetailSection(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 11));
        label.setForeground(new Color(140, 140, 150));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        coinDetailContent.add(label);
        coinDetailContent.add(Box.createVerticalStrut(4));
    }

    private void addCoinDetailLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(new Color(200, 200, 210));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        coinDetailContent.add(label);
    }

    private void addCoinDetailSpacer() {
        coinDetailContent.add(Box.createVerticalStrut(12));
    }

    private void addCoinRelationshipRow(String description, Color color, String targetId, String targetName) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(new Color(38, 40, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JPanel colorDot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(color);
                g.fillOval(2, 6, 10, 10);
            }
        };
        colorDot.setPreferredSize(new Dimension(14, 22));
        colorDot.setBackground(new Color(38, 40, 44));
        row.add(colorDot, BorderLayout.WEST);

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        descLabel.setForeground(new Color(200, 200, 210));
        row.add(descLabel, BorderLayout.CENTER);

        JButton navBtn = new JButton("\u25B6");
        navBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        navBtn.setPreferredSize(new Dimension(28, 22));
        navBtn.setToolTipText("Go to " + targetName);
        navBtn.addActionListener(e -> coinGraphPanel.selectAndPanTo(targetId));
        row.add(navBtn, BorderLayout.EAST);

        coinDetailContent.add(row);
        coinDetailContent.add(Box.createVerticalStrut(2));
    }

    private String formatMarketCap(double num) {
        if (num >= 1_000_000_000_000L) return String.format("%.2fT", num / 1_000_000_000_000L);
        if (num >= 1_000_000_000L) return String.format("%.2fB", num / 1_000_000_000L);
        if (num >= 1_000_000L) return String.format("%.2fM", num / 1_000_000L);
        return String.format("%.0f", num);
    }

    // ==================== SAMPLE/FALLBACK DATA ====================

    private void loadSampleData(List<CoinEntity> entities, List<CoinRelationship> relationships) {
        entities.add(createCoin("bitcoin", "Bitcoin", "BTC", 1_300_000_000_000L));
        entities.add(createCoin("ethereum", "Ethereum", "ETH", 350_000_000_000L));
        entities.add(createCoin("solana", "Solana", "SOL", 80_000_000_000L));
        entities.add(createCoin("cardano", "Cardano", "ADA", 25_000_000_000L));
        entities.add(createCoin("avalanche-2", "Avalanche", "AVAX", 15_000_000_000L));

        entities.add(createL2("arbitrum", "Arbitrum", "ARB", "ethereum"));
        entities.add(createL2("optimism", "Optimism", "OP", "ethereum"));

        relationships.add(new CoinRelationship("arbitrum", "ethereum", CoinRelationship.Type.L2_OF));
        relationships.add(new CoinRelationship("optimism", "ethereum", CoinRelationship.Type.L2_OF));

        // Add sample ETF and VC for fallback mode
        entities.add(createETF("ibit", "iShares Bitcoin Trust", "IBIT"));
        relationships.add(new CoinRelationship("ibit", "bitcoin", CoinRelationship.Type.ETF_TRACKS));

        entities.add(createVC("a16z", "Andreessen Horowitz"));
        relationships.add(new CoinRelationship("a16z", "solana", CoinRelationship.Type.INVESTED_IN));
        relationships.add(new CoinRelationship("a16z", "ethereum", CoinRelationship.Type.INVESTED_IN));
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

        SwingUtilities.invokeLater(() -> {
            IntelFrame frame = new IntelFrame();
            frame.setVisible(true);
        });
    }
}
