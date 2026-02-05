package com.tradery.news.ui.coin;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Window for coin relationship visualization.
 */
public class CoinGraphFrame extends JFrame {

    private final CoinGraphPanel graphPanel;
    private final JTextArea detailArea;
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

        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setBackground(new Color(38, 40, 44));
        detailArea.setForeground(new Color(200, 200, 210));
        detailArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        detailArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        detailArea.setText("Click an entity to see details\n\nScroll to zoom\nShift+drag to pan\nDouble-click to pin/unpin");

        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(null);
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
        SwingUtilities.invokeLater(this::loadRealData);
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(38, 40, 44));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        JButton resetBtn = new JButton("Reset View");
        resetBtn.addActionListener(e -> graphPanel.resetView());
        toolbar.add(resetBtn);

        toolbar.addSeparator();

        JCheckBox labelsCheck = new JCheckBox("Labels", true);
        labelsCheck.addActionListener(e -> graphPanel.setShowLabels(labelsCheck.isSelected()));
        toolbar.add(labelsCheck);

        JCheckBox relLabelsCheck = new JCheckBox("Relationship Labels", false);
        relLabelsCheck.addActionListener(e -> graphPanel.setShowRelationshipLabels(relLabelsCheck.isSelected()));
        toolbar.add(relLabelsCheck);

        JCheckBox categoriesCheck = new JCheckBox("Category Bounds", false);
        categoriesCheck.addActionListener(e -> graphPanel.setShowCategories(categoriesCheck.isSelected()));
        toolbar.add(categoriesCheck);

        toolbar.add(Box.createHorizontalGlue());

        JLabel hint = new JLabel("Scroll=zoom  Shift+drag=pan  Double-click=pin  ");
        hint.setForeground(new Color(120, 120, 130));
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        toolbar.add(hint);

        return toolbar;
    }

    private void loadRealData() {
        statusLabel.setText("Fetching data from CoinGecko...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            private List<CoinEntity> entities;
            private List<CoinRelationship> relationships;

            @Override
            protected Void doInBackground() {
                try {
                    publish("Fetching top 200 coins from CoinGecko...");
                    CoinGeckoClient client = new CoinGeckoClient();
                    entities = client.fetchTopCoins(200);

                    publish("Building relationships...");
                    relationships = client.buildRelationships(entities);

                    // Add ETFs, VCs, and exchanges (not from CoinGecko)
                    addManualEntities(entities, relationships);

                    // Show graph immediately
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        graphPanel.setData(entities, relationships);
                        statusLabel.setText(entities.size() + " entities  |  " + relationships.size() + " relationships");
                        progressBar.setVisible(true);
                        progressBar.setValue(0);
                        progressBar.setString("Categories: 0%");
                    });

                    // Fetch categories for all coins in background (rate limited)
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
                                // Find entity and update
                                for (CoinEntity entity : entities) {
                                    if (entity.id().equals(coinId)) {
                                        entity.setCategories(cats);
                                        break;
                                    }
                                }
                                // Trigger repaint to show updated categories
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

                } catch (Exception e) {
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
            }
        };
        worker.execute();
    }

    private void addManualEntities(List<CoinEntity> entities, List<CoinRelationship> relationships) {
        java.util.Set<String> existingIds = new java.util.HashSet<>();
        for (CoinEntity e : entities) {
            existingIds.add(e.id());
        }

        // ETFs
        entities.add(createETF("ibit", "iShares Bitcoin Trust", "IBIT"));
        entities.add(createETF("fbtc", "Fidelity Bitcoin Fund", "FBTC"));
        entities.add(createETF("gbtc", "Grayscale Bitcoin Trust", "GBTC"));
        entities.add(createETF("arkb", "ARK 21Shares Bitcoin", "ARKB"));
        entities.add(createETF("etha", "iShares Ethereum Trust", "ETHA"));
        entities.add(createETF("feth", "Fidelity Ethereum Fund", "FETH"));

        // ETF relationships
        if (existingIds.contains("bitcoin")) {
            relationships.add(new CoinRelationship("ibit", "bitcoin", CoinRelationship.Type.ETF_TRACKS));
            relationships.add(new CoinRelationship("fbtc", "bitcoin", CoinRelationship.Type.ETF_TRACKS));
            relationships.add(new CoinRelationship("gbtc", "bitcoin", CoinRelationship.Type.ETF_TRACKS));
            relationships.add(new CoinRelationship("arkb", "bitcoin", CoinRelationship.Type.ETF_TRACKS));
        }
        if (existingIds.contains("ethereum")) {
            relationships.add(new CoinRelationship("etha", "ethereum", CoinRelationship.Type.ETF_TRACKS));
            relationships.add(new CoinRelationship("feth", "ethereum", CoinRelationship.Type.ETF_TRACKS));
        }

        // VCs
        entities.add(createVC("a16z", "Andreessen Horowitz"));
        entities.add(createVC("paradigm", "Paradigm"));
        entities.add(createVC("polychain", "Polychain Capital"));
        entities.add(createVC("multicoin", "Multicoin Capital"));
        entities.add(createVC("dragonfly", "Dragonfly"));
        entities.add(createVC("pantera", "Pantera Capital"));
        entities.add(createVC("jump", "Jump Crypto"));

        // VC investments
        if (existingIds.contains("solana")) {
            relationships.add(new CoinRelationship("a16z", "solana", CoinRelationship.Type.INVESTED_IN));
            relationships.add(new CoinRelationship("multicoin", "solana", CoinRelationship.Type.INVESTED_IN));
            relationships.add(new CoinRelationship("pantera", "solana", CoinRelationship.Type.INVESTED_IN));
            relationships.add(new CoinRelationship("jump", "solana", CoinRelationship.Type.INVESTED_IN));
        }
        if (existingIds.contains("near")) {
            relationships.add(new CoinRelationship("a16z", "near", CoinRelationship.Type.INVESTED_IN));
            relationships.add(new CoinRelationship("dragonfly", "near", CoinRelationship.Type.INVESTED_IN));
        }
        if (existingIds.contains("optimism")) {
            relationships.add(new CoinRelationship("a16z", "optimism", CoinRelationship.Type.INVESTED_IN));
            relationships.add(new CoinRelationship("paradigm", "optimism", CoinRelationship.Type.INVESTED_IN));
        }
        if (existingIds.contains("arbitrum")) {
            relationships.add(new CoinRelationship("a16z", "arbitrum", CoinRelationship.Type.INVESTED_IN));
        }
        if (existingIds.contains("aptos")) {
            relationships.add(new CoinRelationship("a16z", "aptos", CoinRelationship.Type.INVESTED_IN));
            relationships.add(new CoinRelationship("jump", "aptos", CoinRelationship.Type.INVESTED_IN));
        }
        if (existingIds.contains("sui")) {
            relationships.add(new CoinRelationship("a16z", "sui", CoinRelationship.Type.INVESTED_IN));
            relationships.add(new CoinRelationship("jump", "sui", CoinRelationship.Type.INVESTED_IN));
        }
        if (existingIds.contains("cosmos")) {
            relationships.add(new CoinRelationship("polychain", "cosmos", CoinRelationship.Type.INVESTED_IN));
            relationships.add(new CoinRelationship("paradigm", "cosmos", CoinRelationship.Type.INVESTED_IN));
        }
        if (existingIds.contains("polkadot")) {
            relationships.add(new CoinRelationship("polychain", "polkadot", CoinRelationship.Type.INVESTED_IN));
            relationships.add(new CoinRelationship("pantera", "polkadot", CoinRelationship.Type.INVESTED_IN));
        }
        if (existingIds.contains("avalanche-2")) {
            relationships.add(new CoinRelationship("dragonfly", "avalanche-2", CoinRelationship.Type.INVESTED_IN));
            relationships.add(new CoinRelationship("polychain", "avalanche-2", CoinRelationship.Type.INVESTED_IN));
        }
        if (existingIds.contains("starknet")) {
            relationships.add(new CoinRelationship("paradigm", "starknet", CoinRelationship.Type.INVESTED_IN));
        }

        // Exchanges and their native tokens
        entities.add(createExchange("binance-ex", "Binance"));
        entities.add(createExchange("coinbase-ex", "Coinbase"));
        entities.add(createExchange("okx-ex", "OKX"));
        entities.add(createExchange("kraken-ex", "Kraken"));
        entities.add(createExchange("crypto-com-ex", "Crypto.com"));

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

        // Coinbase - Base relationship
        if (existingIds.contains("base")) {
            relationships.add(new CoinRelationship("coinbase-ex", "base", CoinRelationship.Type.FOUNDED_BY));
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
        StringBuilder sb = new StringBuilder();
        sb.append("NAME\n").append(entity.name()).append("\n\n");

        if (entity.symbol() != null) {
            sb.append("SYMBOL\n").append(entity.symbol()).append("\n\n");
        }

        sb.append("TYPE\n").append(entity.type()).append("\n\n");

        if (entity.marketCap() > 0) {
            sb.append("MARKET CAP\n$").append(formatMarketCap(entity.marketCap())).append("\n\n");
        }

        if (entity.parentId() != null) {
            sb.append("BUILT ON\n").append(entity.parentId().toUpperCase()).append("\n\n");
        }

        sb.append("CONNECTIONS\n").append(entity.connectionCount()).append(" relationships\n\n");

        if (!entity.categories().isEmpty()) {
            sb.append("CATEGORIES\n");
            for (String cat : entity.categories()) {
                sb.append("  - ").append(cat).append("\n");
            }
            sb.append("\n");
        }

        sb.append("STATUS\n").append(entity.isPinned() ? "Pinned" : "Free-floating");

        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);
    }

    private String formatMarketCap(double num) {
        if (num >= 1_000_000_000_000L) return String.format("%.2fT", num / 1_000_000_000_000L);
        if (num >= 1_000_000_000L) return String.format("%.2fB", num / 1_000_000_000L);
        if (num >= 1_000_000L) return String.format("%.2fM", num / 1_000_000L);
        return String.format("%.0f", num);
    }

    private void showCategoryFilter() {
        Set<String> allCats = graphPanel.getAllCategories();
        if (allCats.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No categories loaded yet.", "Categories", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JCheckBox showAllCheck = new JCheckBox("Show All Categories", true);
        panel.add(showAllCheck);
        panel.add(Box.createVerticalStrut(10));

        Map<String, JCheckBox> checkBoxes = new LinkedHashMap<>();
        for (String cat : allCats) {
            JCheckBox cb = new JCheckBox(cat, true);
            cb.setEnabled(false);
            checkBoxes.put(cat, cb);
            panel.add(cb);
        }

        showAllCheck.addActionListener(e -> {
            boolean showAll = showAllCheck.isSelected();
            for (JCheckBox cb : checkBoxes.values()) {
                cb.setEnabled(!showAll);
                if (showAll) cb.setSelected(true);
            }
        });

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new Dimension(300, 400));

        int result = JOptionPane.showConfirmDialog(this, scroll, "Filter Categories",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            if (showAllCheck.isSelected()) {
                graphPanel.setVisibleCategories(null);  // Show all
            } else {
                Set<String> visible = new HashSet<>();
                for (Map.Entry<String, JCheckBox> entry : checkBoxes.entrySet()) {
                    if (entry.getValue().isSelected()) {
                        visible.add(entry.getKey());
                    }
                }
                graphPanel.setVisibleCategories(visible);
            }
        }
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
