package com.tradery.data.ui;

import com.tradery.dataclient.DataServiceClient;
import com.tradery.dataclient.DataServiceClient.*;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.ThinSplitPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Dialog for managing cached data (candles, aggTrades, funding, OI, premium, F&G).
 * All data access goes through the data service API.
 * Left side: tree-style navigation of data series
 * Right side: coverage heatmap for selected series.
 */
public class DataManagementDialog extends JDialog {

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.0");
    private static final DecimalFormat COUNT_FORMAT = new DecimalFormat("#,###");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final DataServiceClient client;
    private final Runnable onFetchNew;

    private DataBrowserPanel browserPanel;
    private DataHealthPanel healthPanel;
    private JLabel detailLabel;
    private JLabel storageLabel;
    private JButton deleteSeriesButton;
    private JButton deleteAllButton;
    private JProgressBar progressBar;

    private String currentSymbol;
    private String currentResolution;
    private Timer refreshTimer;

    public DataManagementDialog(Frame owner, DataServiceClient client, Runnable onFetchNew) {
        super(owner, "Manage Data", true);
        this.client = client;
        this.onFetchNew = onFetchNew;

        // Integrated macOS title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

        initUI();

        setSize(800, 550);
        setLocationRelativeTo(owner);

        // Auto-refresh every 10 seconds to show progress while data is loading
        refreshTimer = new Timer(10000, e -> {
            browserPanel.refreshData();
            if (currentSymbol != null && currentResolution != null) {
                healthPanel.refreshKeepSelection();
            }
            updateStorageLabel();
        });
        refreshTimer.start();

        // Handle close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                refreshTimer.stop();
            }
        });
    }

    private void initUI() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        setContentPane(contentPane);

        // 52px header bar with centered title
        int barHeight = 52;
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setPreferredSize(new Dimension(0, barHeight));
        JLabel titleLabel = new JLabel("Manage Data", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerBar.add(titleLabel, BorderLayout.CENTER);
        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(headerBar, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        contentPane.add(headerWrapper, BorderLayout.NORTH);

        // Left side: Data browser (tree-style navigation)
        browserPanel = new DataBrowserPanel(client);
        browserPanel.setOnSelectionChanged(this::onSeriesSelected);

        BorderlessScrollPane browserScroll = new BorderlessScrollPane(browserPanel);

        // Storage info at bottom of left panel
        storageLabel = new JLabel();
        storageLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        storageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        storageLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        updateStorageLabel();

        // Storage label with separator above
        JPanel storagePanel = new JPanel(new BorderLayout(0, 0));
        storagePanel.add(new JSeparator(), BorderLayout.NORTH);
        storagePanel.add(storageLabel, BorderLayout.CENTER);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.add(browserScroll, BorderLayout.CENTER);
        leftPanel.add(storagePanel, BorderLayout.SOUTH);

        // Right side: Title + Health panel (block diagram)
        detailLabel = new JLabel("Select a data series from the left");
        detailLabel.setFont(detailLabel.getFont().deriveFont(Font.BOLD, 12f));
        detailLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        healthPanel = new DataHealthPanel(client);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 0));
        rightPanel.add(detailLabel, BorderLayout.NORTH);
        rightPanel.add(healthPanel, BorderLayout.CENTER);

        // Split pane: left navigation | right detail
        ThinSplitPane splitPane = new ThinSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(298);
        add(splitPane, BorderLayout.CENTER);

        // Bottom panel with actions
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        // Separator line at top
        panel.add(new JSeparator(), BorderLayout.NORTH);

        // Button wrapper with padding
        JPanel buttonWrapper = new JPanel(new BorderLayout());
        buttonWrapper.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setOpaque(false);

        // Only show "Fetch New..." button if a fetch callback is provided
        if (onFetchNew != null) {
            JButton fetchButton = new JButton("Fetch New...");
            fetchButton.addActionListener(e -> showFetchDialog());
            buttonPanel.add(fetchButton);

            buttonPanel.add(Box.createHorizontalStrut(12));
        }

        deleteSeriesButton = new JButton("Delete Series");
        deleteSeriesButton.setEnabled(false);
        deleteSeriesButton.addActionListener(e -> deleteSelectedSeries());
        buttonPanel.add(deleteSeriesButton);

        deleteAllButton = new JButton("Delete All");
        deleteAllButton.addActionListener(e -> deleteAllData());
        buttonPanel.add(deleteAllButton);

        buttonPanel.add(Box.createHorizontalStrut(12));

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            dispose();
        });
        buttonPanel.add(closeButton);

        buttonWrapper.add(buttonPanel, BorderLayout.EAST);

        // Progress bar (hidden by default)
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        buttonWrapper.add(progressBar, BorderLayout.SOUTH);

        panel.add(buttonWrapper, BorderLayout.CENTER);

        return panel;
    }

    private void showFetchDialog() {
        if (onFetchNew != null) {
            onFetchNew.run();
        }
    }

    private void onSeriesSelected(String symbol, String resolution) {
        currentSymbol = symbol;
        currentResolution = resolution;

        if (resolution == null) {
            healthPanel.setData(null, null);
            detailLabel.setText("Select a data series from the left");
            updateButtons();
            return;
        }

        // Build info text from inventory
        detailLabel.setText(buildInfoText(symbol, resolution));
        updateButtons();

        // Update health panel
        switch (resolution) {
            case "aggTrades" -> healthPanel.setAggTradesData(symbol);
            case "fundingRate" -> healthPanel.setFundingRateData(symbol);
            case "openInterest" -> healthPanel.setOpenInterestData(symbol);
            case "premiumIndex" -> healthPanel.setPremiumIndexData(symbol);
            case "fearGreed" -> healthPanel.setCustomMessage("Fear & Greed Index \u2014 daily resolution, no hourly coverage view.");
            default -> healthPanel.setData(symbol, resolution);
        }
    }

    private String buildInfoText(String symbol, String resolution) {
        InventoryResponse inv = browserPanel.getInventory();
        if (inv == null) return (symbol != null ? symbol + " / " : "") + resolution;

        if ("fearGreed".equals(resolution)) {
            FearGreedInventory fg = inv.fearGreed();
            if (fg == null) return "Fear & Greed Index \u2014 No data";
            return "Fear & Greed Index \u2014 " + COUNT_FORMAT.format(fg.recordCount()) + " entries, "
                + DATE_FMT.format(Instant.ofEpochMilli(fg.startTime())) + " \u2192 "
                + DATE_FMT.format(Instant.ofEpochMilli(fg.endTime())) + ", latest: " + fg.latestValue();
        }

        SymbolInventory sym = browserPanel.findSymbolInventory(symbol);
        if (sym == null) return symbol + " / " + resolution;

        String selExchange = browserPanel.getSelectedExchange();
        String selMarketType = browserPanel.getSelectedMarketType();

        switch (resolution) {
            case "aggTrades" -> {
                if (sym.aggTrades() == null || sym.aggTrades().isEmpty()) return symbol + " \u2014 Aggregated Trades \u2014 No data";
                // Find the matching entry
                AggTradesInventory match = sym.aggTrades().stream()
                    .filter(at -> (selExchange == null || at.exchange().equals(selExchange))
                        && (selMarketType == null || at.marketType().equals(selMarketType)))
                    .findFirst().orElse(sym.aggTrades().get(0));
                long days = (match.endTime() - match.startTime()) / 86_400_000L;
                String range = DATE_FMT.format(Instant.ofEpochMilli(match.startTime())) + " \u2192 "
                    + DATE_FMT.format(Instant.ofEpochMilli(match.endTime()));
                String countInfo = match.recordCount() > 0
                    ? COUNT_FORMAT.format(match.recordCount()) + " trades, " : "";
                return symbol + " \u2014 " + formatExchangeName(match.exchange()) + " " + formatMarketTypeName(match.marketType())
                    + " \u2014 " + countInfo + range + " (" + days + " days)";
            }
            case "fundingRate" -> {
                if (sym.funding() == null) return symbol + " \u2014 Funding Rate \u2014 No data";
                FundingInventory f = sym.funding();
                long days = (f.endTime() - f.startTime()) / 86_400_000L;
                return symbol + " \u2014 Funding Rate (8h) \u2014 " + COUNT_FORMAT.format(f.recordCount()) + " rates, "
                    + DATE_FMT.format(Instant.ofEpochMilli(f.startTime())) + " \u2192 "
                    + DATE_FMT.format(Instant.ofEpochMilli(f.endTime())) + " (" + days + " days)";
            }
            case "openInterest" -> {
                if (sym.openInterest() == null) return symbol + " \u2014 Open Interest \u2014 No data";
                OpenInterestInventory oi = sym.openInterest();
                long days = (oi.endTime() - oi.startTime()) / 86_400_000L;
                return symbol + " \u2014 Open Interest (5m) \u2014 " + COUNT_FORMAT.format(oi.recordCount()) + " records, "
                    + DATE_FMT.format(Instant.ofEpochMilli(oi.startTime())) + " \u2192 "
                    + DATE_FMT.format(Instant.ofEpochMilli(oi.endTime())) + " (" + days + " days)";
            }
            case "premiumIndex" -> {
                if (sym.premiumIndex() == null || sym.premiumIndex().isEmpty()) return symbol + " \u2014 Premium Index \u2014 No data";
                String selInterval = browserPanel.getSelectedSubKey();
                if (selInterval != null) {
                    PremiumIndexInventory pi = sym.premiumIndex().stream()
                        .filter(p -> p.interval().equals(selInterval)).findFirst().orElse(null);
                    if (pi != null) {
                        long days = (pi.endTime() - pi.startTime()) / 86_400_000L;
                        return symbol + " \u2014 Premium Index " + pi.interval() + " \u2014 " + COUNT_FORMAT.format(pi.recordCount()) + " records, "
                            + DATE_FMT.format(Instant.ofEpochMilli(pi.startTime())) + " \u2192 "
                            + DATE_FMT.format(Instant.ofEpochMilli(pi.endTime())) + " (" + days + " days)";
                    }
                }
                int total = sym.premiumIndex().stream().mapToInt(PremiumIndexInventory::recordCount).sum();
                String intervals = String.join(", ", sym.premiumIndex().stream().map(PremiumIndexInventory::interval).toList());
                long minStart = sym.premiumIndex().stream().mapToLong(PremiumIndexInventory::startTime).min().orElse(0);
                long maxEnd = sym.premiumIndex().stream().mapToLong(PremiumIndexInventory::endTime).max().orElse(0);
                long days = (maxEnd - minStart) / 86_400_000L;
                return symbol + " \u2014 Premium Index [" + intervals + "] \u2014 " + COUNT_FORMAT.format(total) + " records, "
                    + DATE_FMT.format(Instant.ofEpochMilli(minStart)) + " \u2192 "
                    + DATE_FMT.format(Instant.ofEpochMilli(maxEnd)) + " (" + days + " days)";
            }
            default -> {
                // Candle timeframe - find matching candle inventory
                if (sym.candles() != null) {
                    CandleInventory match = sym.candles().stream()
                        .filter(c -> c.timeframe().equals(resolution)
                            && (selMarketType == null || c.marketType().equals(selMarketType)))
                        .findFirst().orElse(null);
                    if (match != null) {
                        long days = (match.endTime() - match.startTime()) / 86_400_000L;
                        return symbol + " \u2014 " + formatExchangeName(match.exchange()) + " " + formatMarketTypeName(match.marketType())
                            + " " + match.timeframe() + " \u2014 " + COUNT_FORMAT.format(match.recordCount()) + " candles, "
                            + DATE_FMT.format(Instant.ofEpochMilli(match.startTime())) + " \u2192 "
                            + DATE_FMT.format(Instant.ofEpochMilli(match.endTime())) + " (" + days + " days)";
                    }
                }
                return symbol + " / " + resolution;
            }
        }
    }

    private String formatExchangeName(String exchange) {
        if (exchange == null) return "";
        return switch (exchange) {
            case "binance" -> "Binance";
            case "bybit" -> "Bybit";
            case "okx" -> "OKX";
            default -> exchange.substring(0, 1).toUpperCase() + exchange.substring(1);
        };
    }

    private String formatMarketTypeName(String mt) {
        if (mt == null) return "";
        return switch (mt) {
            case "perp" -> "Futures";
            case "spot" -> "Spot";
            case "dated" -> "Dated";
            default -> mt;
        };
    }

    private void updateButtons() {
        boolean canDeleteSeries = currentResolution != null;
        deleteSeriesButton.setEnabled(canDeleteSeries);
    }

    private void deleteSelectedSeries() {
        if (currentResolution == null) return;

        String desc = currentSymbol != null ? currentSymbol + " / " + currentResolution : currentResolution;
        int result = JOptionPane.showConfirmDialog(this,
                "Delete all data for " + desc + "?",
                "Confirm Delete Series", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            Thread.startVirtualThread(() -> {
                try {
                    String dataType = mapResolutionToDataType(currentResolution);
                    String timeframe = isTimeframe(currentResolution) ? currentResolution : null;
                    String marketType = browserPanel.getSelectedMarketType();
                    String exchange = browserPanel.getSelectedExchange();

                    client.deleteData(currentSymbol, dataType, timeframe, marketType, exchange, null);

                    SwingUtilities.invokeLater(() -> {
                        currentSymbol = null;
                        currentResolution = null;
                        refreshAll();
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Delete failed: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE));
                }
            });
        }
    }

    private void deleteAllData() {
        InventoryResponse inv = browserPanel.getInventory();
        if (inv == null || inv.symbols() == null || inv.symbols().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No cached data to delete.");
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "Delete ALL cached data?\n\nData will be re-downloaded when needed.",
                "Confirm Delete All", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            Thread.startVirtualThread(() -> {
                try {
                    // Delete all data types for all symbols
                    for (SymbolInventory sym : inv.symbols()) {
                        if (sym.candles() != null && !sym.candles().isEmpty()) {
                            client.deleteData(sym.symbol(), "candles", null, null, null, null);
                        }
                        if (sym.aggTrades() != null && !sym.aggTrades().isEmpty()) {
                            client.deleteData(sym.symbol(), "aggTrades", null, null, null, null);
                        }
                        if (sym.funding() != null) {
                            client.deleteData(sym.symbol(), "funding", null, null, null, null);
                        }
                        if (sym.openInterest() != null) {
                            client.deleteData(sym.symbol(), "openInterest", null, null, null, null);
                        }
                        if (sym.premiumIndex() != null && !sym.premiumIndex().isEmpty()) {
                            client.deleteData(sym.symbol(), "premiumIndex", null, null, null, null);
                        }
                    }
                    if (inv.fearGreed() != null) {
                        client.deleteData(null, "fearGreed", null, null, null, null);
                    }

                    SwingUtilities.invokeLater(() -> {
                        currentSymbol = null;
                        currentResolution = null;
                        refreshAll();
                        JOptionPane.showMessageDialog(this, "All cached data deleted.",
                            "Deleted", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Delete failed: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE));
                }
            });
        }
    }

    private void refreshAll() {
        browserPanel.refreshData();
        healthPanel.refreshData();
        detailLabel.setText("Select a data series from the left");
        updateButtons();
        updateStorageLabel();
    }

    private void updateStorageLabel() {
        Thread.startVirtualThread(() -> {
            try {
                if (client == null) return;
                DiskUsageResponse usage = client.getDiskUsage();
                SwingUtilities.invokeLater(() -> {
                    storageLabel.setText(formatSize(usage.totalBytes()) + " (" + usage.bySymbol().size() + " databases)");
                });
            } catch (Exception e) {
                // Ignore - data service may not be running
            }
        });
    }

    private String mapResolutionToDataType(String resolution) {
        return switch (resolution) {
            case "aggTrades" -> "aggTrades";
            case "fundingRate" -> "funding";
            case "openInterest" -> "openInterest";
            case "premiumIndex" -> "premiumIndex";
            case "fearGreed" -> "fearGreed";
            default -> "candles"; // timeframe strings like "1h", "4h"
        };
    }

    private boolean isTimeframe(String resolution) {
        return resolution != null && !resolution.equals("aggTrades") && !resolution.equals("fundingRate")
            && !resolution.equals("openInterest") && !resolution.equals("premiumIndex")
            && !resolution.equals("fearGreed");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return SIZE_FORMAT.format(bytes / 1024.0) + " KB";
        if (bytes < 1024L * 1024 * 1024) return SIZE_FORMAT.format(bytes / (1024.0 * 1024)) + " MB";
        return SIZE_FORMAT.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    /**
     * Show the dialog with a fetch callback (shows "Fetch New..." button).
     */
    public static void show(Frame owner, DataServiceClient client, Runnable onFetchNew) {
        DataManagementDialog dialog = new DataManagementDialog(owner, client, onFetchNew);
        dialog.setVisible(true);
    }

    /**
     * Show the dialog without a fetch button.
     */
    public static void show(Frame owner, DataServiceClient client) {
        show(owner, client, null);
    }
}
