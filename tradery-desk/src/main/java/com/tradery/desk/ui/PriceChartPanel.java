package com.tradery.desk.ui;

import com.tradery.charts.chart.CandlestickChart;
import com.tradery.charts.chart.VolumeChart;
import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.overlay.BollingerOverlay;
import com.tradery.charts.overlay.EmaOverlay;
import com.tradery.charts.overlay.SmaOverlay;
import com.tradery.core.model.Candle;
import com.tradery.desk.ui.charts.DeskDataProvider;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Price chart panel for Desk using tradery-charts CandlestickChart.
 * Displays candlestick chart with live updates.
 * Supports overlays (SMA, EMA, Bollinger) and optional volume chart.
 */
public class PriceChartPanel extends JPanel {

    private static final Color BACKGROUND_COLOR = new Color(30, 30, 30);

    private final DeskDataProvider dataProvider;
    private final ChartCoordinator coordinator;
    private final CandlestickChart candlestickChart;
    private VolumeChart volumeChart;
    private JSplitPane splitPane;
    private boolean volumeEnabled = false;

    public PriceChartPanel() {
        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);

        // Create data provider
        dataProvider = new DeskDataProvider();

        // Create coordinator for syncing charts
        coordinator = new ChartCoordinator();

        // Create candlestick chart
        candlestickChart = new CandlestickChart(coordinator, "");
        candlestickChart.initialize();
        candlestickChart.setCandlestickMode(true);

        // Add chart panel (volume chart added later if enabled)
        add(candlestickChart.getChartPanel(), BorderLayout.CENTER);
    }

    /**
     * Set historical candles.
     */
    public void setCandles(List<Candle> historicalCandles, String symbol, String timeframe) {
        dataProvider.setCandles(historicalCandles, symbol, timeframe);
        SwingUtilities.invokeLater(this::refreshCharts);
    }

    /**
     * Update the current (incomplete) candle.
     */
    public void updateCurrentCandle(Candle candle) {
        dataProvider.updateCandle(candle);
        SwingUtilities.invokeLater(this::refreshCharts);
    }

    /**
     * Add a completed candle.
     */
    public void addCandle(Candle candle) {
        dataProvider.updateCandle(candle);
        SwingUtilities.invokeLater(this::refreshCharts);
    }

    /**
     * Clear the chart.
     */
    public void clear() {
        dataProvider.setCandles(List.of(), "", "");
        SwingUtilities.invokeLater(this::refreshCharts);
    }

    /**
     * Get the data provider for adding overlays or indicators.
     */
    public DeskDataProvider getDataProvider() {
        return dataProvider;
    }

    /**
     * Get the underlying candlestick chart for customization.
     */
    public CandlestickChart getCandlestickChart() {
        return candlestickChart;
    }

    // ===== Overlay Support =====

    /**
     * Add an SMA overlay with the given period.
     */
    public void addSmaOverlay(int period) {
        candlestickChart.addOverlay(new SmaOverlay(period));
    }

    /**
     * Add an EMA overlay with the given period.
     */
    public void addEmaOverlay(int period) {
        candlestickChart.addOverlay(new EmaOverlay(period));
    }

    /**
     * Add a Bollinger Bands overlay.
     */
    public void addBollingerOverlay(int period, double stdDev) {
        candlestickChart.addOverlay(new BollingerOverlay(period, stdDev));
    }

    /**
     * Clear all overlays.
     */
    public void clearOverlays() {
        candlestickChart.clearOverlays();
    }

    // ===== Volume Chart Support =====

    /**
     * Enable or disable the volume chart.
     */
    public void setVolumeEnabled(boolean enabled) {
        if (enabled == volumeEnabled) return;
        volumeEnabled = enabled;

        if (enabled) {
            // Create volume chart
            volumeChart = new VolumeChart(coordinator, "");
            volumeChart.initialize();

            // Create split pane
            removeAll();
            splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            splitPane.setResizeWeight(0.8);  // Price chart gets 80%
            splitPane.setDividerSize(3);
            splitPane.setBorder(null);
            splitPane.setTopComponent(candlestickChart.getChartPanel());
            splitPane.setBottomComponent(volumeChart.getChartPanel());
            add(splitPane, BorderLayout.CENTER);

            // Update volume chart with current data
            if (!dataProvider.getCandles().isEmpty()) {
                volumeChart.updateData(dataProvider);
            }
        } else {
            // Remove volume chart
            if (volumeChart != null) {
                volumeChart.dispose();
                volumeChart = null;
            }
            removeAll();
            add(candlestickChart.getChartPanel(), BorderLayout.CENTER);
            splitPane = null;
        }

        revalidate();
        repaint();
    }

    /**
     * Check if volume chart is enabled.
     */
    public boolean isVolumeEnabled() {
        return volumeEnabled;
    }

    /**
     * Get the volume chart (may be null if not enabled).
     */
    public VolumeChart getVolumeChart() {
        return volumeChart;
    }

    /**
     * Refresh all charts with current data.
     */
    private void refreshCharts() {
        candlestickChart.updateData(dataProvider);
        if (volumeChart != null) {
            volumeChart.updateData(dataProvider);
        }
    }
}
