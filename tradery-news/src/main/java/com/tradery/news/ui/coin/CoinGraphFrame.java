package com.tradery.news.ui.coin;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;

/**
 * Window for coin relationship visualization.
 */
public class CoinGraphFrame extends JFrame {

    private final CoinGraphPanel graphPanel;
    private final JPanel detailContent;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    public CoinGraphFrame() {
        super("Tradery - Coin Relationships");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1600, 1000);
        setLocationRelativeTo(null);

        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(new Color(30, 32, 36));

        // Toolbar
        JToolBar toolbar = createToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // Graph panel
        graphPanel = new CoinGraphPanel();
        graphPanel.setOnEntitySelected(this::showEntityDetails);

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

        detailContent = new JPanel();
        detailContent.setLayout(new BoxLayout(detailContent, BoxLayout.Y_AXIS));
        detailContent.setBackground(new Color(38, 40, 44));
        detailContent.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Initial help text
        addDetailLabel("Click an entity to see details");
        addDetailLabel("");
        addDetailLabel("Scroll to zoom");
        addDetailLabel("Shift+drag to pan");
        addDetailLabel("Double-click to pin/unpin");

        JScrollPane detailScroll = new JScrollPane(detailContent);
        detailScroll.setBorder(null);
        detailScroll.getVerticalScrollBar().setUnitIncrement(16);
        detailPanel.add(detailScroll, BorderLayout.CENTER);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graphPanel, detailPanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Status bar
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusBar.setBackground(new Color(35, 37, 41));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 52, 56)));
        statusLabel = new JLabel("Loading...");
        statusLabel.setForeground(new Color(140, 140, 150));
        statusBar.add(statusLabel);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(200, 16));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        statusBar.add(progressBar);

        mainPanel.add(statusBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                graphPanel.stopPhysics();
            }
        });

        // Load real data
        SwingUtilities.invokeLater(() -> loadRealData(false));
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(38, 40, 44));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        JButton resetBtn = new JButton("Reset View");
        resetBtn.addActionListener(e -> graphPanel.resetView());
        toolbar.add(resetBtn);

        JButton refreshBtn = new JButton("Refresh Data");
        refreshBtn.addActionListener(e -> loadRealData(true));
        toolbar.add(refreshBtn);

        toolbar.addSeparator();

        JCheckBox labelsCheck = new JCheckBox("Labels", true);
        labelsCheck.addActionListener(e -> graphPanel.setShowLabels(labelsCheck.isSelected()));
        toolbar.add(labelsCheck);

        JCheckBox relLabelsCheck = new JCheckBox("Relationship Labels", false);
        relLabelsCheck.addActionListener(e -> graphPanel.setShowRelationshipLabels(relLabelsCheck.isSelected()));
        toolbar.add(relLabelsCheck);

        toolbar.add(Box.createHorizontalGlue());

        JLabel hint = new JLabel("Scroll=zoom  Shift+drag=pan  Double-click=pin  ");
        hint.setForeground(new Color(120, 120, 130));
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        toolbar.add(hint);

        return toolbar;
    }

    private void loadRealData(boolean forceRefresh) {
        statusLabel.setText("Loading...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            private List<CoinEntity> entities;
            private List<CoinRelationship> relationships;

            @Override
            protected Void doInBackground() {
                try {
                    CoinCache cache = new CoinCache();
                    CoinGeckoClient client = new CoinGeckoClient();
                    boolean fromCache = false;

                    // Try cache first (unless force refresh)
                    if (!forceRefresh && cache.isCacheValid()) {
                        publish("Loading coins from cache...");
                        entities = cache.loadCoins();
                        fromCache = !entities.isEmpty();
                        if (fromCache) {
                            System.out.println("Loaded " + entities.size() + " coins from cache");
                        }
                    }

                    // Fetch from API if cache empty, invalid, or force refresh
                    if (entities == null || entities.isEmpty()) {
                        publish("Fetching top 200 coins from CoinGecko...");
                        entities = client.fetchTopCoins(200);
                        System.out.println("Fetched " + entities.size() + " coins from API");

                        // Save to cache immediately (will update again after categories)
                        cache.saveCoins(entities);
                    }

                    publish("Building relationships...");
                    relationships = client.buildRelationships(entities);

                    // Add ETFs, VCs, and exchanges (not from CoinGecko)
                    addManualEntities(entities, relationships);

                    // Show graph immediately
                    final boolean cached = fromCache;
                    System.out.println("Setting graph data: " + entities.size() + " entities, " + relationships.size() + " relationships");
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        graphPanel.setData(entities, relationships);
                        System.out.println("Graph data set, panel size: " + graphPanel.getWidth() + "x" + graphPanel.getHeight());
                        statusLabel.setText(entities.size() + " entities  |  " + relationships.size() + " relationships" + (cached ? " (cached)" : ""));
                        progressBar.setVisible(false);
                    });

                    // Only fetch categories if we got fresh data from API
                    if (!fromCache) {
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            progressBar.setVisible(true);
                            progressBar.setValue(0);
                            progressBar.setString("Categories: 0%");
                        });

                        List<String> allIds = entities.stream()
                            .map(CoinEntity::id)
                            .toList();

                        int total = allIds.size();
                        int count = 0;
                        for (String coinId : allIds) {
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
                                            break;
                                        }
                                    }
                                    javax.swing.SwingUtilities.invokeLater(() -> graphPanel.repaint());
                                }
                            } catch (Exception e) {
                                // Skip failed coins
                            }
                            int finalPercent = percent;
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(finalPercent);
                                progressBar.setString("Categories: " + finalPercent + "% (" + finalCount + "/" + total + ")");
                            });
                        }

                        // Save to cache with categories
                        cache.saveCoins(entities);
                        javax.swing.SwingUtilities.invokeLater(() -> progressBar.setVisible(false));
                    }

                } catch (Exception e) {
                    System.err.println("Error loading data: " + e.getMessage());
                    e.printStackTrace();
                    publish("Error: " + e.getMessage() + " - Loading sample data...");
                    loadFallbackData();
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                if (entities != null && !entities.isEmpty()) {
                    int catCount = 0;
                    for (CoinEntity e : entities) {
                        if (!e.categories().isEmpty()) catCount++;
                    }
                    statusLabel.setText(entities.size() + " entities  |  " + relationships.size() + " relationships  |  " + catCount + " with categories");
                }
            }

            private void loadFallbackData() {
                entities = new ArrayList<>();
                relationships = new ArrayList<>();
                loadSampleData(entities, relationships);
                System.out.println("Loaded fallback data: " + entities.size() + " entities, " + relationships.size() + " relationships");
                // Show fallback data immediately
                javax.swing.SwingUtilities.invokeLater(() -> {
                    graphPanel.setData(entities, relationships);
                    statusLabel.setText(entities.size() + " entities  |  " + relationships.size() + " relationships  (sample data)");
                    progressBar.setVisible(false);
                });
            }
        };
        worker.execute();
    }

    private void addManualEntities(List<CoinEntity> entities, List<CoinRelationship> relationships) {
        Set<String> existingIds = new HashSet<>();
        for (CoinEntity e : entities) {
            existingIds.add(e.id());
        }

        // === BITCOIN ETFs ===
        entities.add(createETF("ibit", "iShares Bitcoin Trust", "IBIT"));
        entities.add(createETF("fbtc", "Fidelity Wise Origin Bitcoin", "FBTC"));
        entities.add(createETF("gbtc", "Grayscale Bitcoin Trust", "GBTC"));
        entities.add(createETF("arkb", "ARK 21Shares Bitcoin", "ARKB"));
        entities.add(createETF("bitb", "Bitwise Bitcoin ETF", "BITB"));
        entities.add(createETF("hodl", "VanEck Bitcoin Trust", "HODL"));
        entities.add(createETF("brrr", "Valkyrie Bitcoin Fund", "BRRR"));
        entities.add(createETF("ezbc", "Franklin Bitcoin ETF", "EZBC"));
        entities.add(createETF("btco", "Invesco Galaxy Bitcoin", "BTCO"));
        entities.add(createETF("btcw", "WisdomTree Bitcoin Fund", "BTCW"));

        // === ETHEREUM ETFs ===
        entities.add(createETF("etha", "iShares Ethereum Trust", "ETHA"));
        entities.add(createETF("feth", "Fidelity Ethereum Fund", "FETH"));
        entities.add(createETF("ethe", "Grayscale Ethereum Trust", "ETHE"));
        entities.add(createETF("ethv", "VanEck Ethereum ETF", "ETHV"));
        entities.add(createETF("ethw", "Bitwise Ethereum ETF", "ETHW"));
        entities.add(createETF("ceth", "21Shares Core Ethereum", "CETH"));
        entities.add(createETF("qeth", "Invesco Galaxy Ethereum", "QETH"));

        // === OTHER GRAYSCALE TRUSTS ===
        entities.add(createETF("gsol", "Grayscale Solana Trust", "GSOL"));
        entities.add(createETF("gxlm", "Grayscale Stellar Trust", "GXLM"));
        entities.add(createETF("glink", "Grayscale Chainlink Trust", "GLINK"));
        entities.add(createETF("gbat", "Grayscale BAT Trust", "GBAT"));
        entities.add(createETF("gfil", "Grayscale Filecoin Trust", "GFIL"));
        entities.add(createETF("gltc", "Grayscale Litecoin Trust", "GLTC"));
        entities.add(createETF("gsui", "Grayscale SUI Trust", "GSUI"));
        entities.add(createETF("gavax", "Grayscale Avalanche Trust", "GAVAX"));

        // ETF -> Coin relationships
        if (existingIds.contains("bitcoin")) {
            for (String etf : List.of("ibit", "fbtc", "gbtc", "arkb", "bitb", "hodl", "brrr", "ezbc", "btco", "btcw")) {
                relationships.add(new CoinRelationship(etf, "bitcoin", CoinRelationship.Type.ETF_TRACKS));
            }
        }
        if (existingIds.contains("ethereum")) {
            for (String etf : List.of("etha", "feth", "ethe", "ethv", "ethw", "ceth", "qeth")) {
                relationships.add(new CoinRelationship(etf, "ethereum", CoinRelationship.Type.ETF_TRACKS));
            }
        }
        if (existingIds.contains("solana")) {
            relationships.add(new CoinRelationship("gsol", "solana", CoinRelationship.Type.ETF_TRACKS));
        }
        if (existingIds.contains("stellar")) {
            relationships.add(new CoinRelationship("gxlm", "stellar", CoinRelationship.Type.ETF_TRACKS));
        }
        if (existingIds.contains("chainlink")) {
            relationships.add(new CoinRelationship("glink", "chainlink", CoinRelationship.Type.ETF_TRACKS));
        }
        if (existingIds.contains("basic-attention-token")) {
            relationships.add(new CoinRelationship("gbat", "basic-attention-token", CoinRelationship.Type.ETF_TRACKS));
        }
        if (existingIds.contains("filecoin")) {
            relationships.add(new CoinRelationship("gfil", "filecoin", CoinRelationship.Type.ETF_TRACKS));
        }
        if (existingIds.contains("litecoin")) {
            relationships.add(new CoinRelationship("gltc", "litecoin", CoinRelationship.Type.ETF_TRACKS));
        }
        if (existingIds.contains("sui")) {
            relationships.add(new CoinRelationship("gsui", "sui", CoinRelationship.Type.ETF_TRACKS));
        }
        if (existingIds.contains("avalanche-2")) {
            relationships.add(new CoinRelationship("gavax", "avalanche-2", CoinRelationship.Type.ETF_TRACKS));
        }

        // === VENTURE CAPITAL FIRMS ===
        entities.add(createVC("a16z", "Andreessen Horowitz"));
        entities.add(createVC("paradigm", "Paradigm"));
        entities.add(createVC("polychain", "Polychain Capital"));
        entities.add(createVC("multicoin", "Multicoin Capital"));
        entities.add(createVC("dragonfly", "Dragonfly"));
        entities.add(createVC("pantera", "Pantera Capital"));
        entities.add(createVC("jump", "Jump Crypto"));
        entities.add(createVC("dcg", "Digital Currency Group"));
        entities.add(createVC("binance-labs", "Binance Labs"));
        entities.add(createVC("coinbase-ventures", "Coinbase Ventures"));
        entities.add(createVC("sequoia", "Sequoia Capital"));
        entities.add(createVC("lightspeed", "Lightspeed Venture"));
        entities.add(createVC("framework", "Framework Ventures"));
        entities.add(createVC("delphi", "Delphi Digital"));
        entities.add(createVC("three-arrows", "Three Arrows Capital"));
        entities.add(createVC("alameda", "Alameda Research"));
        entities.add(createVC("galaxy", "Galaxy Digital"));
        entities.add(createVC("hashkey", "HashKey Capital"));
        entities.add(createVC("animoca", "Animoca Brands"));
        entities.add(createVC("electric", "Electric Capital"));

        // === VC INVESTMENTS (expanded) ===
        // a16z portfolio
        addInvestment(relationships, existingIds, "a16z", "solana", "ethereum", "optimism", "arbitrum",
            "aptos", "sui", "near", "flow", "celo", "compound-governance-token", "uniswap", "maker",
            "the-graph", "arweave", "worldcoin-wld", "layerzero");

        // Paradigm portfolio
        addInvestment(relationships, existingIds, "paradigm", "ethereum", "optimism", "cosmos",
            "starknet", "uniswap", "maker", "compound-governance-token", "lido-dao", "blur",
            "osmosis", "celestia", "monad", "eigenlayer");

        // Polychain portfolio
        addInvestment(relationships, existingIds, "polychain", "cosmos", "polkadot", "avalanche-2",
            "dfinity", "nervos-network", "celo", "near", "acala", "moonbeam", "filecoin");

        // Multicoin portfolio
        addInvestment(relationships, existingIds, "multicoin", "solana", "helium", "the-graph",
            "arweave", "livepeer", "audius", "render-token", "hivemapper", "pyth-network");

        // Dragonfly portfolio
        addInvestment(relationships, existingIds, "dragonfly", "avalanche-2", "near", "cosmos",
            "1inch", "compound-governance-token", "ribbon-finance", "bybit");

        // Pantera portfolio
        addInvestment(relationships, existingIds, "pantera", "polkadot", "solana", "filecoin",
            "zcash", "0x", "kyber-network-crystal", "injective-protocol", "ankr", "oasis-network");

        // Jump Crypto portfolio
        addInvestment(relationships, existingIds, "jump", "solana", "aptos", "sui", "wormhole",
            "pyth-network", "terra-luna", "serum");

        // Binance Labs portfolio
        addInvestment(relationships, existingIds, "binance-labs", "polygon-matic", "the-sandbox",
            "axie-infinity", "apecoin", "1inch", "dydx", "perpetual-protocol", "band-protocol",
            "injective-protocol", "terra-luna", "pancakeswap-token", "venus");

        // Coinbase Ventures portfolio
        addInvestment(relationships, existingIds, "coinbase-ventures", "polygon-matic", "optimism",
            "uniswap", "compound-governance-token", "aave", "the-graph", "opensea", "dydx",
            "alchemy-pay", "arweave", "celo");

        // Sequoia
        addInvestment(relationships, existingIds, "sequoia", "solana", "polygon-matic", "ethereum");

        // Lightspeed
        addInvestment(relationships, existingIds, "lightspeed", "solana", "sui", "aptos", "ethereum");

        // Framework Ventures
        addInvestment(relationships, existingIds, "framework", "chainlink", "aave", "synthetix",
            "yearn-finance", "tokemak", "fei-usd", "reflexer-ungovernance-token");

        // Delphi Digital
        addInvestment(relationships, existingIds, "delphi", "axie-infinity", "aavegotchi",
            "illuvium", "yield-guild-games", "treasure-magic");

        // Animoca Brands (gaming/metaverse focus)
        addInvestment(relationships, existingIds, "animoca", "the-sandbox", "apecoin", "axie-infinity",
            "decentraland", "gala", "star-atlas", "stepn", "mocaverse");

        // Galaxy Digital
        addInvestment(relationships, existingIds, "galaxy", "ethereum", "solana", "polygon-matic",
            "celestia", "sui", "worldcoin-wld");

        // Electric Capital
        addInvestment(relationships, existingIds, "electric", "near", "solana", "avalanche-2",
            "dfinity", "flow", "mina-protocol", "dydx");

        // DCG (Digital Currency Group)
        addInvestment(relationships, existingIds, "dcg", "ethereum", "ethereum-classic",
            "zcash", "decentraland", "livepeer", "lido-dao", "near");

        // === EXCHANGES ===
        entities.add(createExchange("binance-ex", "Binance"));
        entities.add(createExchange("coinbase-ex", "Coinbase"));
        entities.add(createExchange("okx-ex", "OKX"));
        entities.add(createExchange("kraken-ex", "Kraken"));
        entities.add(createExchange("crypto-com-ex", "Crypto.com"));
        entities.add(createExchange("bybit-ex", "Bybit"));
        entities.add(createExchange("kucoin-ex", "KuCoin"));
        entities.add(createExchange("gate-ex", "Gate.io"));
        entities.add(createExchange("htx-ex", "HTX (Huobi)"));
        entities.add(createExchange("bitfinex-ex", "Bitfinex"));

        // Exchange native token relationships
        if (existingIds.contains("binancecoin")) {
            relationships.add(new CoinRelationship("binance-ex", "binancecoin", CoinRelationship.Type.FOUNDED_BY));
        }
        if (existingIds.contains("okb")) {
            relationships.add(new CoinRelationship("okx-ex", "okb", CoinRelationship.Type.FOUNDED_BY));
        }
        if (existingIds.contains("crypto-com-chain")) {
            relationships.add(new CoinRelationship("crypto-com-ex", "crypto-com-chain", CoinRelationship.Type.FOUNDED_BY));
        }
        if (existingIds.contains("kucoin-shares")) {
            relationships.add(new CoinRelationship("kucoin-ex", "kucoin-shares", CoinRelationship.Type.FOUNDED_BY));
        }
        if (existingIds.contains("gatechain-token")) {
            relationships.add(new CoinRelationship("gate-ex", "gatechain-token", CoinRelationship.Type.FOUNDED_BY));
        }
        if (existingIds.contains("huobi-token")) {
            relationships.add(new CoinRelationship("htx-ex", "huobi-token", CoinRelationship.Type.FOUNDED_BY));
        }
        if (existingIds.contains("unus-sed-leo")) {
            relationships.add(new CoinRelationship("bitfinex-ex", "unus-sed-leo", CoinRelationship.Type.FOUNDED_BY));
        }

        // Coinbase - Base L2
        if (existingIds.contains("base")) {
            relationships.add(new CoinRelationship("coinbase-ex", "base", CoinRelationship.Type.FOUNDED_BY));
        }

        // Binance - BSC/BNB Chain
        if (existingIds.contains("binancecoin")) {
            relationships.add(new CoinRelationship("binance-labs", "binancecoin", CoinRelationship.Type.INVESTED_IN));
        }
    }

    private void addInvestment(List<CoinRelationship> relationships, Set<String> existingIds,
                               String vcId, String... coinIds) {
        for (String coinId : coinIds) {
            if (existingIds.contains(coinId)) {
                relationships.add(new CoinRelationship(vcId, coinId, CoinRelationship.Type.INVESTED_IN));
            }
        }
    }

    private void loadSampleData(List<CoinEntity> entities, List<CoinRelationship> relationships) {
        // Major L1s
        entities.add(createCoin("bitcoin", "Bitcoin", "BTC", 1_300_000_000_000L));
        entities.add(createCoin("ethereum", "Ethereum", "ETH", 350_000_000_000L));
        entities.add(createCoin("solana", "Solana", "SOL", 80_000_000_000L));
        entities.add(createCoin("cardano", "Cardano", "ADA", 25_000_000_000L));
        entities.add(createCoin("avalanche-2", "Avalanche", "AVAX", 15_000_000_000L));
        entities.add(createCoin("polkadot", "Polkadot", "DOT", 10_000_000_000L));
        entities.add(createCoin("chainlink", "Chainlink", "LINK", 12_000_000_000L));
        entities.add(createCoin("cosmos", "Cosmos", "ATOM", 5_000_000_000L));
        entities.add(createCoin("near", "NEAR Protocol", "NEAR", 6_000_000_000L));

        // L2s
        entities.add(createL2("arbitrum", "Arbitrum", "ARB", "ethereum"));
        entities.add(createL2("optimism", "Optimism", "OP", "ethereum"));
        entities.add(createL2("polygon-pos", "Polygon", "MATIC", "ethereum"));
        entities.add(createL2("base", "Base", null, "ethereum"));

        // L2 relationships
        relationships.add(new CoinRelationship("arbitrum", "ethereum", CoinRelationship.Type.L2_OF));
        relationships.add(new CoinRelationship("optimism", "ethereum", CoinRelationship.Type.L2_OF));
        relationships.add(new CoinRelationship("polygon-pos", "ethereum", CoinRelationship.Type.L2_OF));
        relationships.add(new CoinRelationship("base", "ethereum", CoinRelationship.Type.L2_OF));

        // Add manual entities
        addManualEntities(entities, relationships);
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

    private void showEntityDetails(CoinEntity entity) {
        detailContent.removeAll();

        // Name
        addDetailHeader(entity.name());
        if (entity.symbol() != null) {
            addDetailLabel(entity.symbol());
        }
        addDetailSpacer();

        // Type
        addDetailSection("TYPE");
        addDetailLabel(entity.type().toString());
        addDetailSpacer();

        // Market cap
        if (entity.marketCap() > 0) {
            addDetailSection("MARKET CAP");
            addDetailLabel("$" + formatMarketCap(entity.marketCap()));
            addDetailSpacer();
        }

        // Relationships
        List<CoinRelationship> rels = graphPanel.getRelationshipsFor(entity.id());
        if (!rels.isEmpty()) {
            addDetailSection("RELATIONSHIPS (" + rels.size() + ")");
            for (CoinRelationship rel : rels) {
                String otherId = rel.fromId().equals(entity.id()) ? rel.toId() : rel.fromId();
                CoinEntity other = graphPanel.getEntity(otherId);
                if (other == null) continue;

                // Build description based on relationship direction
                String description;
                if (rel.fromId().equals(entity.id())) {
                    // This entity -> other
                    description = rel.type().label() + " " + other.name();
                } else {
                    // Other -> this entity
                    description = describeInverseRelation(rel.type(), other.name());
                }

                addRelationshipRow(description, rel.type().color(), otherId, other.name());
            }
            addDetailSpacer();
        }

        // Categories
        if (!entity.categories().isEmpty()) {
            addDetailSection("CATEGORIES");
            for (String cat : entity.categories()) {
                addDetailLabel("  " + cat);
            }
            addDetailSpacer();
        }

        // Status
        addDetailSection("STATUS");
        addDetailLabel(entity.isPinned() ? "Pinned" : "Free-floating");

        detailContent.revalidate();
        detailContent.repaint();
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

    private void addDetailHeader(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        label.setForeground(new Color(220, 220, 230));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContent.add(label);
    }

    private void addDetailSection(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 11));
        label.setForeground(new Color(140, 140, 150));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContent.add(label);
        detailContent.add(Box.createVerticalStrut(4));
    }

    private void addDetailLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(new Color(200, 200, 210));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContent.add(label);
    }

    private void addDetailSpacer() {
        detailContent.add(Box.createVerticalStrut(12));
    }

    private void addRelationshipRow(String description, Color color, String targetId, String targetName) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(new Color(38, 40, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        // Color indicator
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

        // Description
        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        descLabel.setForeground(new Color(200, 200, 210));
        row.add(descLabel, BorderLayout.CENTER);

        // Navigate button
        JButton navBtn = new JButton("\u25B6");  // Triangle
        navBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        navBtn.setPreferredSize(new Dimension(28, 22));
        navBtn.setToolTipText("Go to " + targetName);
        navBtn.addActionListener(e -> graphPanel.selectAndPanTo(targetId));
        row.add(navBtn, BorderLayout.EAST);

        detailContent.add(row);
        detailContent.add(Box.createVerticalStrut(2));
    }

    private String formatMarketCap(double num) {
        if (num >= 1_000_000_000_000L) return String.format("%.2fT", num / 1_000_000_000_000L);
        if (num >= 1_000_000_000L) return String.format("%.2fB", num / 1_000_000_000L);
        if (num >= 1_000_000L) return String.format("%.2fM", num / 1_000_000L);
        return String.format("%.0f", num);
    }

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
            CoinGraphFrame frame = new CoinGraphFrame();
            frame.setVisible(true);
        });
    }
}
