package com.tradery.forge.ui;

import com.tradery.core.model.Exchange;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.dataclient.DataServiceClient.HlTradeSubscription;
import com.tradery.forge.ApplicationContext;
import com.tradery.symbols.service.SymbolService;
import com.tradery.symbols.service.SymbolService.CoinInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Standalone window for managing dex trade data collection.
 * Shows a tree of available exchanges and coins with checkboxes
 * to toggle collection on/off.
 */
public class DexCollectionWindow extends JFrame {

    private static DexCollectionWindow instance;

    private static final int ROW_HEIGHT = 28;
    private static final int INDENT = 24;
    private static final int CHECKBOX_SIZE = 14;
    private static final int CHECKBOX_X = 8;
    private static final Color ACTIVE_COLOR = new Color(76, 175, 80);
    private static final Color PENDING_COLOR = new Color(255, 193, 7);
    private static final DecimalFormat COUNT_FORMAT = new DecimalFormat("#,###");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault());

    private final DataServiceClient client;
    private final SymbolService symbolService;
    private final List<RowData> rows = new ArrayList<>();
    private final Set<String> activeCoins = new HashSet<>(); // coin keys: "exchange:coin"
    private final Map<String, Long> tradeCounts = new HashMap<>();
    private final Map<String, Long> prevTradeCounts = new HashMap<>();
    private final Map<String, Long> firstTimes = new HashMap<>();
    private final Map<String, Long> lastTimes = new HashMap<>();
    private long lastRefreshTime;

    private volatile boolean loading = true;
    private int hoveredRow = -1;
    private javax.swing.Timer refreshTimer;

    public static void showWindow() {
        if (instance == null || !instance.isDisplayable()) {
            instance = new DexCollectionWindow();
            instance.setVisible(true);
        } else {
            instance.toFront();
            instance.requestFocus();
        }
    }

    private DexCollectionWindow() {
        super("Dex Data Collection");
        this.client = ApplicationContext.getInstance().getDataServiceClient();
        this.symbolService = ApplicationContext.getInstance().getSymbolService();

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(560, 520);
        setMinimumSize(new Dimension(450, 300));

        // macOS styling
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

        JPanel contentPane = new JPanel(new BorderLayout());

        // 52px header bar with centered title
        int barHeight = 52;
        JPanel headerWrapper = new JPanel(new BorderLayout());
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setPreferredSize(new Dimension(0, barHeight));
        JLabel titleLabel = new JLabel("Dex Data Collection", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerBar.add(titleLabel, BorderLayout.CENTER);
        headerWrapper.add(headerBar, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        contentPane.add(headerWrapper, BorderLayout.NORTH);

        // Scrollable list panel
        ListPanel listPanel = new ListPanel();
        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        // Bottom info + close button
        contentPane.add(createBottomPanel(), BorderLayout.SOUTH);

        setContentPane(contentPane);

        // Load data
        loadData();

        // Auto-refresh timer for trade counts
        refreshTimer = new javax.swing.Timer(5000, e -> refreshSubscriptions());
        refreshTimer.start();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (refreshTimer != null) refreshTimer.stop();
                instance = null;
            }
        });
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        panel.add(new JSeparator(), BorderLayout.NORTH);

        JPanel inner = new JPanel(new BorderLayout(8, 4));
        inner.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel infoLabel = new JLabel("<html><small>Trades collected via WebSocket in real-time.<br>" +
            "Data accumulates from first enable. No historical backfill.</small></html>");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        inner.add(infoLabel, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.add(closeButton);
        inner.add(buttonPanel, BorderLayout.EAST);

        panel.add(inner, BorderLayout.CENTER);
        return panel;
    }

    private void loadData() {
        loading = true;
        Thread.startVirtualThread(() -> {
            try {
                // Fetch current subscriptions
                List<HlTradeSubscription> subs = List.of();
                try {
                    subs = client.getHlTradeSubscriptions();
                } catch (Exception ignored) {}

                Set<String> active = new HashSet<>();
                Map<String, Long> counts = new HashMap<>();
                Map<String, Long> firsts = new HashMap<>();
                Map<String, Long> lasts = new HashMap<>();
                for (HlTradeSubscription sub : subs) {
                    String key = sub.exchange() + ":" + sub.coin();
                    active.add(key);
                    counts.put(key, sub.tradeCount());
                    firsts.put(key, sub.firstTradeTime());
                    lasts.put(key, sub.lastTradeTime());
                }

                // Fetch available HL exchanges
                List<String> allExchanges = symbolService.getExchanges();
                List<String> hlExchanges = allExchanges.stream()
                    .filter(ex -> ex.equals("hyperliquid") || ex.startsWith("hl-"))
                    .toList();

                // Fetch coins per exchange
                Map<String, List<CoinInfo>> coinsByExchange = new LinkedHashMap<>();
                for (String exchange : hlExchanges) {
                    List<CoinInfo> coins = symbolService.getCoins(exchange, "perp", 10000);
                    coinsByExchange.put(exchange, coins);
                }

                // Also add exchanges from active subs that might not be in symbol DB
                for (HlTradeSubscription sub : subs) {
                    if (!coinsByExchange.containsKey(sub.exchange())) {
                        coinsByExchange.put(sub.exchange(), List.of());
                    }
                }

                Set<String> finalActive = active;
                Map<String, Long> finalCounts = counts;
                Map<String, Long> finalFirsts = firsts;
                Map<String, Long> finalLasts = lasts;
                Map<String, List<CoinInfo>> finalCoins = coinsByExchange;

                SwingUtilities.invokeLater(() -> {
                    activeCoins.clear();
                    activeCoins.addAll(finalActive);
                    tradeCounts.clear();
                    tradeCounts.putAll(finalCounts);
                    firstTimes.clear();
                    firstTimes.putAll(finalFirsts);
                    lastTimes.clear();
                    lastTimes.putAll(finalLasts);
                    loading = false;
                    rebuildRows(finalCoins);
                });
            } catch (Exception e) {
                System.err.println("Failed to load dex collection data: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    loading = false;
                    rows.clear();
                    rows.add(new RowData(null, null, true, false, false, "Failed to load data", null, null));
                    repaint();
                });
            }
        });
    }

    private void refreshSubscriptions() {
        Thread.startVirtualThread(() -> {
            try {
                List<HlTradeSubscription> subs = client.getHlTradeSubscriptions();
                Set<String> active = new HashSet<>();
                Map<String, Long> counts = new HashMap<>();
                Map<String, Long> firsts = new HashMap<>();
                Map<String, Long> lasts = new HashMap<>();
                for (HlTradeSubscription sub : subs) {
                    String key = sub.exchange() + ":" + sub.coin();
                    active.add(key);
                    counts.put(key, sub.tradeCount());
                    firsts.put(key, sub.firstTradeTime());
                    lasts.put(key, sub.lastTradeTime());
                }

                SwingUtilities.invokeLater(() -> {
                    activeCoins.clear();
                    activeCoins.addAll(active);
                    prevTradeCounts.clear();
                    prevTradeCounts.putAll(tradeCounts);
                    long prevRefresh = lastRefreshTime;
                    tradeCounts.clear();
                    tradeCounts.putAll(counts);
                    firstTimes.clear();
                    firstTimes.putAll(firsts);
                    lastTimes.clear();
                    lastTimes.putAll(lasts);
                    lastRefreshTime = System.currentTimeMillis();
                    long elapsed = lastRefreshTime - prevRefresh;
                    // Update info in existing rows without full rebuild
                    for (RowData row : rows) {
                        if (row.coin != null && row.exchange != null) {
                            String key = row.exchange + ":" + row.coin;
                            row.checked = activeCoins.contains(key);
                            row.info = formatCoinInfo(key, elapsed);
                            row.statusColor = row.checked ? ACTIVE_COLOR : null;
                            row.status = null; // Clear transient status
                        }
                    }
                    repaint();
                });
            } catch (Exception ignored) {
                // Data service may not be running
            }
        });
    }

    private void rebuildRows(Map<String, List<CoinInfo>> coinsByExchange) {
        rows.clear();

        if (loading) {
            rows.add(new RowData(null, null, true, false, false, "Loading...", null, null));
            repaint();
            return;
        }

        if (coinsByExchange.isEmpty()) {
            rows.add(new RowData(null, null, true, false, false, "No Hyperliquid exchanges found", null, null));
            repaint();
            return;
        }

        for (Map.Entry<String, List<CoinInfo>> entry : coinsByExchange.entrySet()) {
            String exchange = entry.getKey();
            List<CoinInfo> coins = entry.getValue();

            // Exchange header row
            boolean allChecked = !coins.isEmpty() && coins.stream()
                .allMatch(c -> activeCoins.contains(exchange + ":" + c.base()));
            RowData exchangeRow = new RowData(exchange, null, false, true, allChecked,
                Exchange.formatDisplayName(exchange), null, null);
            rows.add(exchangeRow);

            // Coin rows
            for (CoinInfo coin : coins) {
                String key = exchange + ":" + coin.base();
                boolean checked = activeCoins.contains(key);
                String info = formatCoinInfo(key, 0);
                Color statusColor = checked ? ACTIVE_COLOR : null;
                rows.add(new RowData(exchange, coin.base(), false, false, checked, coin.base(), info, statusColor));
            }
        }

        revalidate();
        repaint();
    }

    private void toggleCoin(String exchange, String coin, boolean enable) {
        // Set transient status immediately
        setRowStatus(exchange, coin, enable ? "Starting..." : "Stopping...");
        repaint();

        Thread.startVirtualThread(() -> {
            try {
                if (enable) {
                    client.addHlTradeSubscription(coin, exchange, coin);
                } else {
                    client.removeHlTradeSubscription(coin);
                }
                // Refresh clears transient status
                refreshSubscriptions();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setRowStatus(exchange, coin, "Failed");
                    repaint();
                    JOptionPane.showMessageDialog(this,
                        "Failed to " + (enable ? "enable" : "disable") + " collection for " + coin + ": " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void toggleExchange(String exchange, boolean enable) {
        // Find all coins under this exchange
        List<RowData> coinRows = rows.stream()
            .filter(r -> !r.isExchangeHeader && !r.isInfoRow && exchange.equals(r.exchange))
            .toList();

        // Set transient status on all children
        String status = enable ? "Starting..." : "Stopping...";
        for (RowData row : coinRows) {
            row.status = status;
        }
        repaint();

        Thread.startVirtualThread(() -> {
            for (RowData row : coinRows) {
                try {
                    if (enable) {
                        client.addHlTradeSubscription(row.coin, exchange, row.coin);
                    } else {
                        client.removeHlTradeSubscription(row.coin);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to toggle " + row.coin + ": " + e.getMessage());
                }
            }
            refreshSubscriptions();
        });
    }

    private String formatCoinInfo(String key, long elapsedMs) {
        boolean active = activeCoins.contains(key);
        Long count = tradeCounts.get(key);

        if (!active) return null;

        // Active but no trades yet
        if (count == null || count == 0) return "Collecting...";

        StringBuilder sb = new StringBuilder();
        sb.append(COUNT_FORMAT.format(count)).append(" trades");

        // Show rate if we have a previous count and enough time elapsed
        if (elapsedMs > 1000) {
            Long prev = prevTradeCounts.get(key);
            if (prev != null && count > prev) {
                double rate = (count - prev) / (elapsedMs / 1000.0);
                if (rate >= 1) {
                    sb.append("  (+").append(COUNT_FORMAT.format((long) rate)).append("/s)");
                } else if (rate > 0) {
                    sb.append("  (<1/s)");
                }
            }
        }

        Long first = firstTimes.get(key);
        Long last = lastTimes.get(key);
        if (first != null && first > 0 && last != null && last > 0) {
            sb.append("  ");
            sb.append(DATE_FMT.format(Instant.ofEpochMilli(first)));
            sb.append(" \u2013 ");
            sb.append(DATE_FMT.format(Instant.ofEpochMilli(last)));
        }

        return sb.toString();
    }

    private void setRowStatus(String exchange, String coin, String status) {
        for (RowData r : rows) {
            if (!r.isExchangeHeader && !r.isInfoRow && exchange.equals(r.exchange) && coin.equals(r.coin)) {
                r.status = status;
                return;
            }
        }
    }

    // Mutable row model
    private static class RowData {
        final String exchange;
        final String coin; // null for exchange headers
        final boolean isInfoRow;
        final boolean isExchangeHeader;
        boolean checked;
        final String label;
        String info;
        Color statusColor;
        String status; // transient: "Starting...", "Stopping...", null when settled

        RowData(String exchange, String coin, boolean isInfoRow, boolean isExchangeHeader,
                boolean checked, String label, String info, Color statusColor) {
            this.exchange = exchange;
            this.coin = coin;
            this.isInfoRow = isInfoRow;
            this.isExchangeHeader = isExchangeHeader;
            this.checked = checked;
            this.label = label;
            this.info = info;
            this.statusColor = statusColor;
        }
    }

    /**
     * Custom-painted panel for the checkbox list.
     */
    private class ListPanel extends JPanel {

        ListPanel() {
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int rowIdx = e.getY() / ROW_HEIGHT;
                    if (rowIdx < 0 || rowIdx >= rows.size()) return;
                    RowData row = rows.get(rowIdx);

                    if (row.isInfoRow) return;

                    // Click anywhere on the row toggles checkbox
                    if (row.isExchangeHeader) {
                        boolean newState = !row.checked;
                        row.checked = newState;
                        // Update all children visually immediately
                        for (RowData r : rows) {
                            if (!r.isExchangeHeader && !r.isInfoRow && row.exchange.equals(r.exchange)) {
                                r.checked = newState;
                            }
                        }
                        repaint();
                        toggleExchange(row.exchange, newState);
                    } else {
                        boolean newState = !row.checked;
                        row.checked = newState;
                        // Update exchange header checkbox state
                        updateExchangeHeaderState(row.exchange);
                        repaint();
                        toggleCoin(row.exchange, row.coin, newState);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    int row = e.getY() / ROW_HEIGHT;
                    if (row != hoveredRow) {
                        hoveredRow = row;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoveredRow = -1;
                    repaint();
                }
            };

            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int y = 0;
            for (int i = 0; i < rows.size(); i++) {
                RowData row = rows.get(i);

                if (row.isInfoRow) {
                    // Info/loading text
                    g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                    g2.setColor(UIManager.getColor("Label.disabledForeground"));
                    g2.drawString(row.label, INDENT + 8, y + 18);
                    y += ROW_HEIGHT;
                    continue;
                }

                // Hover highlight
                boolean isHovered = i == hoveredRow;
                if (isHovered) {
                    g2.setColor(UIManager.getColor("List.selectionInactiveBackground"));
                    g2.fillRect(0, y, getWidth(), ROW_HEIGHT);
                }

                int x;
                if (row.isExchangeHeader) {
                    x = CHECKBOX_X;
                    // Draw checkbox
                    drawCheckbox(g2, x, y + (ROW_HEIGHT - CHECKBOX_SIZE) / 2, row.checked);

                    // Exchange name (bold)
                    g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                    g2.setColor(UIManager.getColor("Label.foreground"));
                    g2.drawString(row.label, x + CHECKBOX_SIZE + 8, y + 18);
                } else {
                    x = CHECKBOX_X + INDENT;
                    // Draw checkbox
                    drawCheckbox(g2, x, y + (ROW_HEIGHT - CHECKBOX_SIZE) / 2, row.checked);

                    // Coin name
                    g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                    g2.setColor(UIManager.getColor("Label.foreground"));
                    g2.drawString(row.label, x + CHECKBOX_SIZE + 8, y + 18);

                    // Status label or trade count info
                    int labelWidth = g2.getFontMetrics().stringWidth(row.label);
                    int infoX = x + CHECKBOX_SIZE + 8 + labelWidth + 8;

                    if (row.status != null) {
                        // Transient status (Starting.../Stopping.../Failed)
                        g2.setColor(PENDING_COLOR);
                        g2.fillOval(infoX, y + 10, 8, 8);
                        infoX += 12;
                        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                        g2.setColor(PENDING_COLOR);
                        g2.drawString(row.status, infoX, y + 18);
                    } else if (row.info != null) {
                        // Settled state with trade count
                        if (row.statusColor != null) {
                            g2.setColor(row.statusColor);
                            g2.fillOval(infoX, y + 10, 8, 8);
                            infoX += 12;
                        }
                        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                        g2.setColor(UIManager.getColor("Label.disabledForeground"));
                        g2.drawString(row.info, infoX, y + 18);
                    }
                }

                y += ROW_HEIGHT;
            }

            g2.dispose();
        }

        private void drawCheckbox(Graphics2D g2, int x, int y, boolean checked) {
            // Box
            g2.setColor(UIManager.getColor("CheckBox.icon.borderColor"));
            if (g2.getColor() == null) g2.setColor(UIManager.getColor("Component.borderColor"));
            if (g2.getColor() == null) g2.setColor(Color.GRAY);
            g2.drawRoundRect(x, y, CHECKBOX_SIZE, CHECKBOX_SIZE, 3, 3);

            if (checked) {
                // Fill
                Color accentColor = UIManager.getColor("Component.accentColor");
                if (accentColor == null) accentColor = new Color(0, 122, 255);
                g2.setColor(accentColor);
                g2.fillRoundRect(x + 1, y + 1, CHECKBOX_SIZE - 1, CHECKBOX_SIZE - 1, 3, 3);

                // Checkmark
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 3, y + 7, x + 6, y + 11);
                g2.drawLine(x + 6, y + 11, x + 11, y + 4);
                g2.setStroke(new BasicStroke(1f));
            }
        }

        @Override
        public Dimension getPreferredSize() {
            int height = Math.max(200, rows.size() * ROW_HEIGHT + 10);
            return new Dimension(520, height);
        }
    }

    private void updateExchangeHeaderState(String exchange) {
        boolean allChecked = true;
        boolean anyFound = false;
        for (RowData r : rows) {
            if (!r.isExchangeHeader && !r.isInfoRow && exchange.equals(r.exchange)) {
                anyFound = true;
                if (!r.checked) {
                    allChecked = false;
                    break;
                }
            }
        }
        for (RowData r : rows) {
            if (r.isExchangeHeader && exchange.equals(r.exchange)) {
                r.checked = anyFound && allChecked;
            }
        }
    }
}
