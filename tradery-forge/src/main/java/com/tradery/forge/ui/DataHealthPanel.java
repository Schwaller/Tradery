package com.tradery.forge.ui;

import com.tradery.forge.data.DataConfig;
import com.tradery.forge.data.sqlite.SqliteDataStore;
import com.tradery.forge.data.sqlite.dao.CoverageDao;
import com.tradery.ui.coverage.CoverageHeatmapPanel;
import com.tradery.ui.coverage.CoverageLevel;
import com.tradery.ui.coverage.CoverageSlice;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.List;

/**
 * Hourly-resolution coverage heatmap for data health visualization.
 * Delegates rendering to CoverageHeatmapPanel from ui-common.
 */
public class DataHealthPanel extends JPanel {

    private final SqliteDataStore dataStore;
    private final CoverageHeatmapPanel heatmap;

    private String symbol;
    private String resolution;
    private String customMessage;
    private JLabel messageLabel;

    public DataHealthPanel(SqliteDataStore dataStore) {
        this.dataStore = dataStore;
        this.heatmap = new CoverageHeatmapPanel();

        setLayout(new BorderLayout());

        messageLabel = new JLabel("", SwingConstants.CENTER);
        messageLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        JScrollPane scroll = new JScrollPane(heatmap,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
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
        this.symbol = null;
        this.resolution = null;
        this.customMessage = message;
        heatmap.setData(List.of());
        showMessage(message);
    }

    public void setAggTradesData(String symbol) {
        this.symbol = symbol;
        this.resolution = "aggTrades";
        this.customMessage = null;
        loadAggTradesCoverage(symbol);
    }

    public void setFundingRateData(String symbol) {
        this.symbol = symbol;
        this.resolution = "fundingRate";
        this.customMessage = null;
        loadCoverageFromDao(symbol, "funding_rates", "default");
    }

    public void setOpenInterestData(String symbol) {
        this.symbol = symbol;
        this.resolution = "openInterest";
        this.customMessage = null;
        loadCoverageFromDao(symbol, "open_interest", "default");
    }

    public void setPremiumIndexData(String symbol) {
        this.symbol = symbol;
        this.resolution = "premiumIndex";
        this.customMessage = null;
        loadCoverageFromDao(symbol, "premium_index", "1m");
    }

    /**
     * Refresh data while preserving context.
     */
    public void refreshKeepSelection() {
        if (symbol == null) return;

        if ("aggTrades".equals(resolution)) {
            loadAggTradesCoverage(symbol);
        } else if ("fundingRate".equals(resolution)) {
            loadCoverageFromDao(symbol, "funding_rates", "default");
        } else if ("openInterest".equals(resolution)) {
            loadCoverageFromDao(symbol, "open_interest", "default");
        } else if ("premiumIndex".equals(resolution)) {
            loadCoverageFromDao(symbol, "premium_index", "1m");
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

        // For standard candle resolutions, use coverage DAO
        loadCoverageFromDao(symbol, "klines", resolution);
    }

    // ========== Unused stubs kept for API compatibility ==========

    public void setOnMonthSelected(java.util.function.Consumer<com.tradery.core.model.DataHealth> callback) {
        // No longer applicable — heatmap is hour-level, not month-click
    }

    // ========== Coverage loading ==========

    private void loadCoverageFromDao(String symbol, String dataType, String subKey) {
        try {
            CoverageDao dao = dataStore.forSymbol(symbol).coverage();
            List<CoverageDao.CoverageRange> ranges = dao.getCoverageRanges(dataType, subKey);

            if (ranges.isEmpty()) {
                heatmap.setData(List.of());
                showMessage("No coverage data for " + symbol + " / " + (subKey.isEmpty() ? dataType : subKey));
                return;
            }

            hideMessage();
            List<CoverageSlice> slices = rangesToSlices(ranges);
            heatmap.setData(slices);
        } catch (SQLException e) {
            heatmap.setData(List.of());
            showMessage("Error loading coverage: " + e.getMessage());
        }
    }

    private void loadAggTradesCoverage(String symbol) {
        // AggTrades stored as hourly files: data/{symbol}/aggTrades/yyyy-MM-dd/HH.csv
        File aggDir = new File(DataConfig.getInstance().getDataDir(), symbol + "/aggTrades");
        if (!aggDir.exists()) {
            heatmap.setData(List.of());
            showMessage("No aggTrades data for " + symbol);
            return;
        }

        File[] dayDirs = aggDir.listFiles(File::isDirectory);
        if (dayDirs == null || dayDirs.length == 0) {
            heatmap.setData(List.of());
            showMessage("No aggTrades data for " + symbol);
            return;
        }

        List<CoverageSlice> slices = new ArrayList<>();

        for (File dayDir : dayDirs) {
            try {
                LocalDate date = LocalDate.parse(dayDir.getName());
                int year = date.getYear();
                int month = date.getMonthValue();
                int day = date.getDayOfMonth();

                // Scan hourly files
                Set<Integer> completeHours = new HashSet<>();
                Set<Integer> partialHours = new HashSet<>();
                File[] hourFiles = dayDir.listFiles((dir, name) -> name.endsWith(".csv"));
                if (hourFiles == null) continue;

                for (File hf : hourFiles) {
                    String name = hf.getName();
                    boolean partial = name.endsWith(".partial.csv");
                    String hourStr = name.replace(".partial.csv", "").replace(".csv", "");
                    try {
                        int hour = Integer.parseInt(hourStr);
                        if (partial) {
                            partialHours.add(hour);
                        } else {
                            completeHours.add(hour);
                        }
                    } catch (NumberFormatException ignored) {}
                }

                // Emit slices for all 24 hours
                for (int h = 0; h < 24; h++) {
                    CoverageLevel level;
                    if (completeHours.contains(h)) {
                        level = CoverageLevel.FULL;
                    } else if (partialHours.contains(h)) {
                        level = CoverageLevel.PARTIAL;
                    } else {
                        level = CoverageLevel.MISSING;
                    }
                    slices.add(new CoverageSlice(year, month, day, h, level));
                }
            } catch (Exception ignored) {
                // Skip invalid directory names
            }
        }

        if (slices.isEmpty()) {
            heatmap.setData(List.of());
            showMessage("No aggTrades data for " + symbol);
            return;
        }

        hideMessage();
        heatmap.setData(slices);
    }

    /**
     * Convert coverage ranges (millisecond timestamps) to hourly CoverageSlices.
     * Each range is a continuous block of covered time. Hours outside any range are MISSING.
     */
    private List<CoverageSlice> rangesToSlices(List<CoverageDao.CoverageRange> ranges) {
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
            for (CoverageDao.CoverageRange range : ranges) {
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
