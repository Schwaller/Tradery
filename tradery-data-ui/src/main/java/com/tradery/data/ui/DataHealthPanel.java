package com.tradery.data.ui;

import com.tradery.dataclient.DataServiceClient;
import com.tradery.dataclient.DataServiceClient.*;
import com.tradery.ui.coverage.CoverageHeatmapPanel;
import com.tradery.ui.coverage.CoverageLevel;
import com.tradery.ui.coverage.CoverageSlice;

import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;

/**
 * Hourly-resolution coverage heatmap for data health visualization.
 * Delegates rendering to CoverageHeatmapPanel from ui-common.
 * All data access goes through the data service API.
 */
public class DataHealthPanel extends JPanel {

    private final DataServiceClient client;
    private final CoverageHeatmapPanel heatmap;

    private String symbol;
    private String resolution;
    private String customMessage;
    private JLabel messageLabel;
    private JProgressBar loadingBar;
    private volatile int loadGeneration;  // cancellation token for stale loads

    public DataHealthPanel(DataServiceClient client) {
        this.client = client;
        this.heatmap = new CoverageHeatmapPanel();

        setLayout(new BorderLayout());

        messageLabel = new JLabel("", SwingConstants.CENTER);
        messageLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);
        loadingBar.setPreferredSize(new Dimension(0, 3));
        loadingBar.setVisible(false);

        JScrollPane scroll = new JScrollPane(heatmap,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(loadingBar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Set the symbol and resolution to display, and refresh data.
     */
    public void setData(String symbol, String resolution) {
        this.symbol = symbol;
        this.resolution = resolution;
        this.customMessage = null;
        refreshData();
    }

    /**
     * Set a custom message to display instead of data.
     */
    public void setCustomMessage(String message) {
        ++loadGeneration; // cancel any in-flight load
        this.symbol = null;
        this.resolution = null;
        this.customMessage = message;
        loadingBar.setVisible(false);
        heatmap.setData(List.of());
        showMessage(message);
    }

    public void setAggTradesData(String symbol) {
        this.symbol = symbol;
        this.resolution = "aggTrades";
        this.customMessage = null;
        loadCoverageFromApi(symbol, "agg_trades", "default");
    }

    public void setFundingRateData(String symbol) {
        this.symbol = symbol;
        this.resolution = "fundingRate";
        this.customMessage = null;
        loadCoverageFromApi(symbol, "funding_rates", "default");
    }

    public void setOpenInterestData(String symbol) {
        this.symbol = symbol;
        this.resolution = "openInterest";
        this.customMessage = null;
        loadCoverageFromApi(symbol, "open_interest", "default");
    }

    public void setPremiumIndexData(String symbol) {
        this.symbol = symbol;
        this.resolution = "premiumIndex";
        this.customMessage = null;
        loadCoverageFromApi(symbol, "premium_index", "1m");
    }

    /**
     * Refresh data while preserving context.
     */
    public void refreshKeepSelection() {
        if (symbol == null) return;

        if ("aggTrades".equals(resolution)) {
            loadCoverageFromApi(symbol, "agg_trades", "default");
        } else if ("fundingRate".equals(resolution)) {
            loadCoverageFromApi(symbol, "funding_rates", "default");
        } else if ("openInterest".equals(resolution)) {
            loadCoverageFromApi(symbol, "open_interest", "default");
        } else if ("premiumIndex".equals(resolution)) {
            loadCoverageFromApi(symbol, "premium_index", "1m");
        } else if (resolution != null) {
            refreshData();
        }
    }

    public void refreshData() {
        if (symbol == null || resolution == null) {
            heatmap.setData(List.of());
            showMessage("Select a symbol and resolution");
            return;
        }

        // For standard candle resolutions, use coverage API
        loadCoverageFromApi(symbol, "klines", resolution);
    }

    // ========== Unused stubs kept for API compatibility ==========

    public void setOnMonthSelected(java.util.function.Consumer<com.tradery.core.model.DataHealth> callback) {
        // No longer applicable — heatmap is hour-level, not month-click
    }

    // ========== Coverage loading ==========

    private void loadCoverageFromApi(String symbol, String dataType, String subKey) {
        int gen = ++loadGeneration;

        // Show loading state immediately on EDT
        hideMessage();
        loadingBar.setVisible(true);

        Thread.startVirtualThread(() -> {
            try {
                if (client == null) return;
                CoverageRangesResponse response = client.getCoverageRanges(symbol, dataType, subKey);
                if (gen != loadGeneration) return; // stale

                if (response.ranges() == null || response.ranges().isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        if (gen != loadGeneration) return;
                        loadingBar.setVisible(false);
                        heatmap.setData(List.of());
                        showMessage("No coverage data for " + symbol + " / " + (subKey.equals("default") ? dataType : subKey));
                    });
                    return;
                }

                // Build slices off EDT (can be large for multi-year data)
                List<CoverageSlice> slices = rangesToSlices(response.ranges());
                if (gen != loadGeneration) return; // stale

                SwingUtilities.invokeLater(() -> {
                    if (gen != loadGeneration) return;
                    loadingBar.setVisible(false);
                    hideMessage();
                    heatmap.setData(slices);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (gen != loadGeneration) return;
                    loadingBar.setVisible(false);
                    heatmap.setData(List.of());
                    showMessage("Error loading coverage: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Convert coverage ranges (millisecond timestamps) to hourly CoverageSlices.
     * Each range is a continuous block of covered time. Hours outside any range are MISSING.
     */
    private List<CoverageSlice> rangesToSlices(List<CoverageRange> ranges) {
        if (ranges.isEmpty()) return List.of();

        // Find overall time bounds
        long minStart = ranges.get(0).rangeStart();
        long maxEnd = ranges.get(ranges.size() - 1).rangeEnd();

        // Snap to hour boundaries
        ZoneOffset utc = ZoneOffset.UTC;
        LocalDateTime startDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(minStart), utc)
                .withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(maxEnd), utc)
                .withMinute(0).withSecond(0).withNano(0);

        List<CoverageSlice> slices = new ArrayList<>();

        // Iterate hour by hour from start to end
        LocalDateTime cursor = startDt;
        while (!cursor.isAfter(endDt)) {
            long hourStartMs = cursor.toInstant(utc).toEpochMilli();
            long hourEndMs = hourStartMs + 3_600_000 - 1;

            // Check if this hour overlaps any coverage range
            CoverageLevel level = CoverageLevel.MISSING;
            for (CoverageRange range : ranges) {
                if (range.rangeEnd() < hourStartMs) continue;
                if (range.rangeStart() > hourEndMs) break;

                // Overlap exists
                boolean fullyCovers = range.rangeStart() <= hourStartMs && range.rangeEnd() >= hourEndMs;
                if (fullyCovers) {
                    level = range.isComplete() ? CoverageLevel.FULL : CoverageLevel.PARTIAL;
                } else {
                    level = CoverageLevel.PARTIAL;
                }
                break;
            }

            slices.add(new CoverageSlice(
                    cursor.getYear(), cursor.getMonthValue(),
                    cursor.getDayOfMonth(), cursor.getHour(), level));

            cursor = cursor.plusHours(1);
        }

        return slices;
    }

    private void showMessage(String msg) {
        messageLabel.setText(msg);
        if (messageLabel.getParent() == null) {
            add(messageLabel, BorderLayout.SOUTH);
        }
        revalidate();
        repaint();
    }

    private void hideMessage() {
        if (messageLabel.getParent() != null) {
            remove(messageLabel);
            revalidate();
            repaint();
        }
    }
}
