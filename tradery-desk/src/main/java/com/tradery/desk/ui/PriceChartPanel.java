package com.tradery.desk.ui;

import com.tradery.charts.chart.CandlestickChart;
import com.tradery.charts.chart.IndicatorChart;
import com.tradery.charts.chart.VolumeChart;
import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.overlay.BollingerOverlay;
import com.tradery.charts.overlay.EmaOverlay;
import com.tradery.charts.overlay.SmaOverlay;
import com.tradery.charts.renderer.AtrRenderer;
import com.tradery.charts.renderer.MacdRenderer;
import com.tradery.charts.renderer.RsiRenderer;
import com.tradery.charts.renderer.StochasticRenderer;
import com.tradery.core.model.Candle;
import com.tradery.desk.ui.charts.DeskDataProvider;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
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
    private IndicatorChart rsiChart;
    private IndicatorChart macdChart;
    private IndicatorChart atrChart;
    private IndicatorChart stochasticChart;
    private final List<IndicatorChart> indicatorCharts = new ArrayList<>();
    private boolean volumeEnabled = false;
    private boolean rsiEnabled = false;
    private boolean macdEnabled = false;
    private boolean atrEnabled = false;
    private boolean stochasticEnabled = false;

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
            volumeChart = new VolumeChart(coordinator, "");
            volumeChart.initialize();
        } else {
            if (volumeChart != null) {
                volumeChart.dispose();
                volumeChart = null;
            }
        }

        rebuildLayout();
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
        if (rsiChart != null) {
            rsiChart.updateData(dataProvider);
        }
        if (macdChart != null) {
            macdChart.updateData(dataProvider);
        }
        if (atrChart != null) {
            atrChart.updateData(dataProvider);
        }
        if (stochasticChart != null) {
            stochasticChart.updateData(dataProvider);
        }
    }

    // ===== Indicator Chart Support =====

    /**
     * Enable or disable RSI chart.
     */
    public void setRsiEnabled(boolean enabled) {
        setRsiEnabled(enabled, 14);  // Default period
    }

    /**
     * Enable or disable RSI chart with custom period.
     */
    public void setRsiEnabled(boolean enabled, int period) {
        if (enabled == rsiEnabled) return;
        rsiEnabled = enabled;

        if (enabled) {
            rsiChart = new IndicatorChart(coordinator, IndicatorType.RSI, new RsiRenderer(period));
            rsiChart.initialize();
            indicatorCharts.add(rsiChart);
        } else {
            if (rsiChart != null) {
                indicatorCharts.remove(rsiChart);
                rsiChart.dispose();
                rsiChart = null;
            }
        }

        rebuildLayout();
    }

    /**
     * Enable or disable MACD chart.
     */
    public void setMacdEnabled(boolean enabled) {
        setMacdEnabled(enabled, 12, 26, 9);  // Default periods
    }

    /**
     * Enable or disable MACD chart with custom periods.
     */
    public void setMacdEnabled(boolean enabled, int fast, int slow, int signal) {
        if (enabled == macdEnabled) return;
        macdEnabled = enabled;

        if (enabled) {
            macdChart = new IndicatorChart(coordinator, IndicatorType.MACD, new MacdRenderer(fast, slow, signal));
            macdChart.initialize();
            indicatorCharts.add(macdChart);
        } else {
            if (macdChart != null) {
                indicatorCharts.remove(macdChart);
                macdChart.dispose();
                macdChart = null;
            }
        }

        rebuildLayout();
    }

    /**
     * Check if RSI chart is enabled.
     */
    public boolean isRsiEnabled() {
        return rsiEnabled;
    }

    /**
     * Check if MACD chart is enabled.
     */
    public boolean isMacdEnabled() {
        return macdEnabled;
    }

    /**
     * Enable or disable ATR chart.
     */
    public void setAtrEnabled(boolean enabled) {
        setAtrEnabled(enabled, 14);  // Default period
    }

    /**
     * Enable or disable ATR chart with custom period.
     */
    public void setAtrEnabled(boolean enabled, int period) {
        if (enabled == atrEnabled) return;
        atrEnabled = enabled;

        if (enabled) {
            atrChart = new IndicatorChart(coordinator, IndicatorType.ATR, new AtrRenderer(period));
            atrChart.initialize();
            indicatorCharts.add(atrChart);
        } else {
            if (atrChart != null) {
                indicatorCharts.remove(atrChart);
                atrChart.dispose();
                atrChart = null;
            }
        }

        rebuildLayout();
    }

    /**
     * Check if ATR chart is enabled.
     */
    public boolean isAtrEnabled() {
        return atrEnabled;
    }

    /**
     * Enable or disable Stochastic chart.
     */
    public void setStochasticEnabled(boolean enabled) {
        setStochasticEnabled(enabled, 14, 3);  // Default periods
    }

    /**
     * Enable or disable Stochastic chart with custom periods.
     */
    public void setStochasticEnabled(boolean enabled, int kPeriod, int dPeriod) {
        if (enabled == stochasticEnabled) return;
        stochasticEnabled = enabled;

        if (enabled) {
            stochasticChart = new IndicatorChart(coordinator, IndicatorType.STOCHASTIC, new StochasticRenderer(kPeriod, dPeriod));
            stochasticChart.initialize();
            indicatorCharts.add(stochasticChart);
        } else {
            if (stochasticChart != null) {
                indicatorCharts.remove(stochasticChart);
                stochasticChart.dispose();
                stochasticChart = null;
            }
        }

        rebuildLayout();
    }

    /**
     * Check if Stochastic chart is enabled.
     */
    public boolean isStochasticEnabled() {
        return stochasticEnabled;
    }

    /**
     * Rebuild the layout with all enabled charts.
     */
    private void rebuildLayout() {
        removeAll();

        // Build list of panels to show
        List<JComponent> panels = new ArrayList<>();
        panels.add(candlestickChart.getChartPanel());

        if (volumeChart != null) {
            panels.add(volumeChart.getChartPanel());
        }

        for (IndicatorChart ic : indicatorCharts) {
            panels.add(ic.getChartPanel());
        }

        if (panels.size() == 1) {
            // Just price chart
            add(panels.get(0), BorderLayout.CENTER);
        } else {
            // Multiple charts - use nested split panes
            JComponent combined = createNestedSplitPanes(panels);
            add(combined, BorderLayout.CENTER);
        }

        // Update all charts with current data
        if (!dataProvider.getCandles().isEmpty()) {
            refreshCharts();
        }

        revalidate();
        repaint();
    }

    /**
     * Create nested split panes for multiple charts.
     * Price chart gets more space, indicator charts get less.
     */
    private JComponent createNestedSplitPanes(List<JComponent> panels) {
        if (panels.size() == 1) {
            return panels.get(0);
        }

        // Calculate resize weights: price chart gets more space
        // Price: 0.6, others: split remaining 0.4
        JComponent current = panels.get(panels.size() - 1);

        for (int i = panels.size() - 2; i >= 0; i--) {
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            split.setBorder(null);
            split.setDividerSize(3);

            // Price chart (index 0) gets more space
            if (i == 0) {
                split.setResizeWeight(0.6);
            } else {
                split.setResizeWeight(0.5);
            }

            split.setTopComponent(panels.get(i));
            split.setBottomComponent(current);
            current = split;
        }

        return current;
    }
}
