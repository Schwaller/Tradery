package com.tradery.desk.ui;

import com.tradery.charts.chart.CandlestickChart;
import com.tradery.charts.chart.IndicatorChart;
import com.tradery.charts.chart.SyncedChart;
import com.tradery.charts.chart.VolumeChart;
import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.ChartInteractionManager;
import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.overlay.*;
import com.tradery.charts.renderer.*;
import com.tradery.charts.util.ChartPanelFactory;
import com.tradery.core.model.Candle;
import com.tradery.desk.ui.charts.DeskDataProvider;
import com.tradery.ui.ThemeHelper;
import com.tradery.ui.controls.ThinSplitPane;

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

    private final DeskDataProvider dataProvider;
    private final ChartCoordinator coordinator;
    private final CandlestickChart candlestickChart;
    private VolumeChart volumeChart;
    private IndicatorChart rsiChart;
    private IndicatorChart macdChart;
    private IndicatorChart atrChart;
    private IndicatorChart stochasticChart;
    private IndicatorChart adxChart;
    private IndicatorChart deltaChart;
    private IndicatorChart rangePositionChart;
    private IndicatorChart tradeCountChart;
    private IndicatorChart volumeRatioChart;
    private final List<IndicatorChart> indicatorCharts = new ArrayList<>();
    private boolean volumeEnabled = false;
    private boolean rsiEnabled = false;
    private boolean macdEnabled = false;
    private boolean atrEnabled = false;
    private boolean stochasticEnabled = false;
    private boolean adxEnabled = false;
    private boolean deltaEnabled = false;
    private boolean rangePositionEnabled = false;
    private boolean tradeCountEnabled = false;
    private boolean volumeRatioEnabled = false;
    private LastPriceOverlay lastPriceOverlay;
    private boolean lastPriceEnabled = true;  // Enabled by default
    private ReferencePriceOverlay referencePriceOverlay;
    private boolean referencePriceEnabled = false;
    private final ChartInteractionManager interactionManager;

    public PriceChartPanel() {
        setLayout(new BorderLayout());
        setBackground(UIManager.getColor("Panel.background"));

        // Create data provider
        dataProvider = new DeskDataProvider();

        // Create coordinator for syncing charts
        coordinator = new ChartCoordinator();

        // Create interaction manager for zoom/pan
        interactionManager = new ChartInteractionManager();

        // Create candlestick chart
        candlestickChart = new CandlestickChart(coordinator, "");
        candlestickChart.initialize();
        candlestickChart.setCandlestickMode(true);

        // Default price axis to right side, configurable via right-click menu
        ChartPanelFactory.setAxisPositionConfig("right", this::applyAxisPosition);
        candlestickChart.setRangeAxisPosition("right");

        // Add last price line overlay (enabled by default)
        lastPriceOverlay = new LastPriceOverlay();
        candlestickChart.addOverlay(lastPriceOverlay);

        // Register chart for synchronized zoom/pan and attach listeners
        interactionManager.addChart(candlestickChart.getChart());
        interactionManager.attachListeners(candlestickChart.getChartPanel());

        // Add chart panel (volume chart added later if enabled)
        add(candlestickChart.getChartPanel(), BorderLayout.CENTER);

        // Refresh chart colors when theme changes
        ThemeHelper.addThemeChangeListener(() -> SwingUtilities.invokeLater(this::refreshTheme));
    }

    /**
     * Re-apply theme styling to all charts.
     */
    private void refreshTheme() {
        setBackground(UIManager.getColor("Panel.background"));
        candlestickChart.refreshTheme();
        if (volumeChart != null) volumeChart.refreshTheme();
        for (IndicatorChart ic : indicatorCharts) {
            ic.refreshTheme();
        }
        repaint();
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

    /**
     * Set candlestick mode (true) or line mode (false) and refresh.
     */
    public void setCandlestickMode(boolean candlestick) {
        candlestickChart.setCandlestickMode(candlestick);
        if (!dataProvider.getCandles().isEmpty()) {
            refreshCharts();
        }
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
     * Add a VWAP overlay.
     */
    public void addVwapOverlay() {
        candlestickChart.addOverlay(new VwapOverlay());
    }

    /**
     * Add an Ichimoku Cloud overlay with default parameters.
     */
    public void addIchimokuOverlay() {
        candlestickChart.addOverlay(new IchimokuOverlay());
    }

    /**
     * Add an Ichimoku Cloud overlay with custom parameters.
     */
    public void addIchimokuOverlay(int conversion, int base, int spanB, int displacement) {
        candlestickChart.addOverlay(new IchimokuOverlay(conversion, base, spanB, displacement));
    }

    /**
     * Add a Supertrend overlay with default parameters.
     */
    public void addSupertrendOverlay() {
        candlestickChart.addOverlay(new SupertrendOverlay());
    }

    /**
     * Add a Supertrend overlay with custom parameters.
     */
    public void addSupertrendOverlay(int period, double multiplier) {
        candlestickChart.addOverlay(new SupertrendOverlay(period, multiplier));
    }

    /**
     * Add a High/Low range overlay with default period.
     */
    public void addHighLowOverlay() {
        candlestickChart.addOverlay(new HighLowOverlay());
    }

    /**
     * Add a High/Low range overlay with custom period.
     */
    public void addHighLowOverlay(int period) {
        candlestickChart.addOverlay(new HighLowOverlay(period));
    }

    /**
     * Add a Mayer Multiple overlay (200 SMA).
     */
    public void addMayerMultipleOverlay() {
        candlestickChart.addOverlay(new MayerMultipleOverlay());
    }

    /**
     * Add a Mayer Multiple overlay with custom period.
     */
    public void addMayerMultipleOverlay(int period) {
        candlestickChart.addOverlay(new MayerMultipleOverlay(period));
    }

    /**
     * Add a POC (Point of Control) overlay.
     */
    public void addPocOverlay() {
        candlestickChart.addOverlay(new PocOverlay());
    }

    /**
     * Add a POC overlay with custom period.
     */
    public void addPocOverlay(int period) {
        candlestickChart.addOverlay(new PocOverlay(period));
    }

    /**
     * Add Daily Levels overlay (Prev Day POC/VAH/VAL + Today's developing levels).
     */
    public void addDailyLevelsOverlay() {
        candlestickChart.addOverlay(new DailyLevelsOverlay());
    }

    /**
     * Add Daily Levels overlay with options.
     */
    public void addDailyLevelsOverlay(boolean showPrevDay, boolean showToday) {
        candlestickChart.addOverlay(new DailyLevelsOverlay(showPrevDay, showToday));
    }

    /**
     * Add Keltner Channel overlay.
     */
    public void addKeltnerOverlay() {
        candlestickChart.addOverlay(new KeltnerChannelOverlay());
    }

    /**
     * Add Keltner Channel overlay with custom parameters.
     */
    public void addKeltnerOverlay(int emaPeriod, int atrPeriod, double multiplier) {
        candlestickChart.addOverlay(new KeltnerChannelOverlay(emaPeriod, atrPeriod, multiplier));
    }

    /**
     * Add Donchian Channel overlay.
     */
    public void addDonchianOverlay() {
        candlestickChart.addOverlay(new DonchianChannelOverlay());
    }

    /**
     * Add Donchian Channel overlay with custom period.
     */
    public void addDonchianOverlay(int period) {
        candlestickChart.addOverlay(new DonchianChannelOverlay(period));
    }

    /**
     * Add Rotating Ray trendlines overlay (resistance + support).
     */
    public void addRayOverlay() {
        candlestickChart.addOverlay(new RayOverlay());
    }

    /**
     * Add Rotating Ray overlay with custom lookback and skip.
     */
    public void addRayOverlay(int lookback, int skip) {
        candlestickChart.addOverlay(new RayOverlay(lookback, skip));
    }

    /**
     * Add Rotating Ray overlay with full customization.
     */
    public void addRayOverlay(int lookback, int skip, int maxRays, boolean showResistance, boolean showSupport) {
        candlestickChart.addOverlay(new RayOverlay(lookback, skip, maxRays, showResistance, showSupport));
    }

    /**
     * Add ATR Bands overlay (volatility envelope around price).
     */
    public void addAtrBandsOverlay() {
        candlestickChart.addOverlay(new AtrBandsOverlay());
    }

    /**
     * Add ATR Bands overlay with custom parameters.
     */
    public void addAtrBandsOverlay(int period, double multiplier) {
        candlestickChart.addOverlay(new AtrBandsOverlay(period, multiplier));
    }

    /**
     * Add Pivot Points overlay (classic daily pivot levels).
     */
    public void addPivotPointsOverlay() {
        candlestickChart.addOverlay(new PivotPointsOverlay());
    }

    /**
     * Add Pivot Points overlay with R3/S3 levels.
     */
    public void addPivotPointsOverlay(boolean showR3S3) {
        candlestickChart.addOverlay(new PivotPointsOverlay(showR3S3));
    }

    /**
     * Clear all overlays.
     */
    public void clearOverlays() {
        candlestickChart.clearOverlays();
        // Re-add last price overlay if enabled
        if (lastPriceEnabled && lastPriceOverlay != null) {
            candlestickChart.addOverlay(lastPriceOverlay);
        }
    }

    /**
     * Enable or disable the last price line overlay.
     */
    public void setLastPriceEnabled(boolean enabled) {
        if (enabled == lastPriceEnabled) return;
        lastPriceEnabled = enabled;

        if (enabled) {
            if (lastPriceOverlay == null) {
                lastPriceOverlay = new LastPriceOverlay();
            }
            candlestickChart.addOverlay(lastPriceOverlay);
        } else {
            if (lastPriceOverlay != null) {
                candlestickChart.removeOverlay(lastPriceOverlay);
            }
        }
    }

    /**
     * Check if last price overlay is enabled.
     */
    public boolean isLastPriceEnabled() {
        return lastPriceEnabled;
    }

    /**
     * Enable or disable the reference price overlay (e.g., spot price for comparison).
     */
    public void setReferencePriceEnabled(boolean enabled) {
        if (enabled == referencePriceEnabled) return;
        referencePriceEnabled = enabled;

        if (enabled) {
            if (referencePriceOverlay == null) {
                referencePriceOverlay = new ReferencePriceOverlay("SPOT");
            }
            candlestickChart.addOverlay(referencePriceOverlay);
        } else {
            if (referencePriceOverlay != null) {
                candlestickChart.removeOverlay(referencePriceOverlay);
            }
        }
    }

    /**
     * Check if reference price overlay is enabled.
     */
    public boolean isReferencePriceEnabled() {
        return referencePriceEnabled;
    }

    /**
     * Update the reference price value.
     */
    public void updateReferencePrice(double price) {
        if (referencePriceOverlay != null) {
            referencePriceOverlay.setReferencePrice(price);
            // Trigger chart refresh to update the marker
            SwingUtilities.invokeLater(this::refreshCharts);
        }
    }

    /**
     * Get the reference price overlay (for direct access if needed).
     */
    public ReferencePriceOverlay getReferencePriceOverlay() {
        return referencePriceOverlay;
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
            interactionManager.addChart(volumeChart.getChart());
            interactionManager.attachListeners(volumeChart.getChartPanel());
        } else {
            if (volumeChart != null) {
                interactionManager.removeChart(volumeChart.getChart());
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
        if (adxChart != null) {
            adxChart.updateData(dataProvider);
        }
        if (deltaChart != null) {
            deltaChart.updateData(dataProvider);
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
            rsiChart = new IndicatorChart(coordinator, IndicatorType.RSI,
                (plot, prov) -> new RsiRenderer(period, plot, prov));
            rsiChart.initialize();
            indicatorCharts.add(rsiChart);
            interactionManager.addChart(rsiChart.getChart());
            interactionManager.attachListeners(rsiChart.getChartPanel());
        } else {
            if (rsiChart != null) {
                indicatorCharts.remove(rsiChart);
                interactionManager.removeChart(rsiChart.getChart());
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
            macdChart = new IndicatorChart(coordinator, IndicatorType.MACD,
                (plot, prov) -> new MacdRenderer(fast, slow, signal, plot, prov));
            macdChart.initialize();
            indicatorCharts.add(macdChart);
            interactionManager.addChart(macdChart.getChart());
            interactionManager.attachListeners(macdChart.getChartPanel());
        } else {
            if (macdChart != null) {
                indicatorCharts.remove(macdChart);
                interactionManager.removeChart(macdChart.getChart());
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
            atrChart = new IndicatorChart(coordinator, IndicatorType.ATR,
                (plot, prov) -> new AtrRenderer(period, plot, prov));
            atrChart.initialize();
            indicatorCharts.add(atrChart);
            interactionManager.addChart(atrChart.getChart());
            interactionManager.attachListeners(atrChart.getChartPanel());
        } else {
            if (atrChart != null) {
                indicatorCharts.remove(atrChart);
                interactionManager.removeChart(atrChart.getChart());
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
            stochasticChart = new IndicatorChart(coordinator, IndicatorType.STOCHASTIC,
                (plot, prov) -> new StochasticRenderer(kPeriod, dPeriod, plot, prov));
            stochasticChart.initialize();
            indicatorCharts.add(stochasticChart);
            interactionManager.addChart(stochasticChart.getChart());
            interactionManager.attachListeners(stochasticChart.getChartPanel());
        } else {
            if (stochasticChart != null) {
                indicatorCharts.remove(stochasticChart);
                interactionManager.removeChart(stochasticChart.getChart());
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
     * Enable or disable ADX chart.
     */
    public void setAdxEnabled(boolean enabled) {
        setAdxEnabled(enabled, 14);  // Default period
    }

    /**
     * Enable or disable ADX chart with custom period.
     */
    public void setAdxEnabled(boolean enabled, int period) {
        if (enabled == adxEnabled) return;
        adxEnabled = enabled;

        if (enabled) {
            adxChart = new IndicatorChart(coordinator, IndicatorType.ADX,
                (plot, prov) -> new AdxRenderer(period, plot, prov));
            adxChart.initialize();
            indicatorCharts.add(adxChart);
            interactionManager.addChart(adxChart.getChart());
            interactionManager.attachListeners(adxChart.getChartPanel());
        } else {
            if (adxChart != null) {
                indicatorCharts.remove(adxChart);
                interactionManager.removeChart(adxChart.getChart());
                adxChart.dispose();
                adxChart = null;
            }
        }

        rebuildLayout();
    }

    /**
     * Check if ADX chart is enabled.
     */
    public boolean isAdxEnabled() {
        return adxEnabled;
    }

    /**
     * Enable or disable Delta chart.
     */
    public void setDeltaEnabled(boolean enabled) {
        setDeltaEnabled(enabled, true);  // Default: show CVD
    }

    /**
     * Enable or disable Delta chart with optional CVD line.
     */
    public void setDeltaEnabled(boolean enabled, boolean showCvd) {
        if (enabled == deltaEnabled) return;
        deltaEnabled = enabled;

        if (enabled) {
            deltaChart = new IndicatorChart(coordinator, IndicatorType.DELTA,
                (plot, prov) -> new DeltaRenderer(showCvd, plot, prov));
            deltaChart.initialize();
            indicatorCharts.add(deltaChart);
            interactionManager.addChart(deltaChart.getChart());
            interactionManager.attachListeners(deltaChart.getChartPanel());
        } else {
            if (deltaChart != null) {
                indicatorCharts.remove(deltaChart);
                interactionManager.removeChart(deltaChart.getChart());
                deltaChart.dispose();
                deltaChart = null;
            }
        }

        rebuildLayout();
    }

    /**
     * Check if Delta chart is enabled.
     */
    public boolean isDeltaEnabled() {
        return deltaEnabled;
    }

    /**
     * Enable or disable Range Position chart.
     */
    public void setRangePositionEnabled(boolean enabled) {
        setRangePositionEnabled(enabled, 200, 0);
    }

    /**
     * Enable or disable Range Position chart with custom parameters.
     */
    public void setRangePositionEnabled(boolean enabled, int period, int skip) {
        if (enabled == rangePositionEnabled) return;
        rangePositionEnabled = enabled;

        if (enabled) {
            rangePositionChart = new IndicatorChart(coordinator, IndicatorType.RANGE_POSITION,
                (plot, prov) -> new RangePositionRenderer(period, skip, plot, prov));
            rangePositionChart.initialize();
            indicatorCharts.add(rangePositionChart);
            interactionManager.addChart(rangePositionChart.getChart());
            interactionManager.attachListeners(rangePositionChart.getChartPanel());
        } else {
            if (rangePositionChart != null) {
                indicatorCharts.remove(rangePositionChart);
                interactionManager.removeChart(rangePositionChart.getChart());
                rangePositionChart.dispose();
                rangePositionChart = null;
            }
        }

        rebuildLayout();
    }

    /**
     * Check if Range Position chart is enabled.
     */
    public boolean isRangePositionEnabled() {
        return rangePositionEnabled;
    }

    /**
     * Enable or disable Trade Count chart.
     */
    public void setTradeCountEnabled(boolean enabled) {
        if (enabled == tradeCountEnabled) return;
        tradeCountEnabled = enabled;

        if (enabled) {
            tradeCountChart = new IndicatorChart(coordinator, IndicatorType.TRADE_COUNT,
                (plot, prov) -> new TradeCountRenderer(plot, prov));
            tradeCountChart.initialize();
            indicatorCharts.add(tradeCountChart);
            interactionManager.addChart(tradeCountChart.getChart());
            interactionManager.attachListeners(tradeCountChart.getChartPanel());
        } else {
            if (tradeCountChart != null) {
                indicatorCharts.remove(tradeCountChart);
                interactionManager.removeChart(tradeCountChart.getChart());
                tradeCountChart.dispose();
                tradeCountChart = null;
            }
        }

        rebuildLayout();
    }

    /**
     * Check if Trade Count chart is enabled.
     */
    public boolean isTradeCountEnabled() {
        return tradeCountEnabled;
    }

    /**
     * Enable or disable Volume Ratio chart.
     */
    public void setVolumeRatioEnabled(boolean enabled) {
        if (enabled == volumeRatioEnabled) return;
        volumeRatioEnabled = enabled;

        if (enabled) {
            volumeRatioChart = new IndicatorChart(coordinator, IndicatorType.VOLUME_RATIO,
                (plot, prov) -> new VolumeRatioRenderer(plot, prov));
            volumeRatioChart.initialize();
            indicatorCharts.add(volumeRatioChart);
            interactionManager.addChart(volumeRatioChart.getChart());
            interactionManager.attachListeners(volumeRatioChart.getChartPanel());
        } else {
            if (volumeRatioChart != null) {
                indicatorCharts.remove(volumeRatioChart);
                interactionManager.removeChart(volumeRatioChart.getChart());
                volumeRatioChart.dispose();
                volumeRatioChart = null;
            }
        }

        rebuildLayout();
    }

    /**
     * Check if Volume Ratio chart is enabled.
     */
    public boolean isVolumeRatioEnabled() {
        return volumeRatioEnabled;
    }

    /**
     * Apply axis position to all charts.
     */
    private void applyAxisPosition(String position) {
        SwingUtilities.invokeLater(() -> {
            candlestickChart.setRangeAxisPosition(position);
            if (volumeChart != null) volumeChart.setRangeAxisPosition(position);
            for (IndicatorChart ic : indicatorCharts) {
                ic.setRangeAxisPosition(position);
            }
        });
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

        // Apply current axis position to all charts
        applyAxisPosition(ChartPanelFactory.getAxisPosition());

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
            ThinSplitPane split = new ThinSplitPane(JSplitPane.VERTICAL_SPLIT);

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
