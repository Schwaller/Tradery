package com.tradery.desk.ui;

import com.tradery.charts.chart.CandlestickChart;
import com.tradery.charts.chart.VolumeChart;
import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.ChartInteractionManager;
import com.tradery.charts.core.ChartLifecycleManager;
import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.indicator.*;
import com.tradery.charts.overlay.*;
import com.tradery.charts.util.ChartPanelFactory;
import com.tradery.core.model.Candle;
import com.tradery.desk.ui.charts.DeskDataProvider;
import com.tradery.desk.ui.charts.DeskIndicatorDataProvider;
import com.tradery.ui.ThemeHelper;
import com.tradery.ui.controls.ChartConfig;
import com.tradery.ui.controls.ThinSplitPane;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Price chart panel for Desk using tradery-charts CandlestickChart.
 * Displays candlestick chart with live updates.
 * Uses shared OverlayManager and IndicatorChartsManager for overlay/indicator management.
 * Persists chart settings via ChartConfig.
 */
public class PriceChartPanel extends JPanel {

    private final DeskDataProvider dataProvider;
    private final DeskIndicatorDataProvider indicatorDataProvider;
    private final IndicatorChartsManager indicatorManager;
    private final OverlayManager overlayManager;
    private final ChartCoordinator coordinator;
    private final CandlestickChart candlestickChart;
    private VolumeChart volumeChart;
    private boolean volumeEnabled = false;
    private LastPriceOverlay lastPriceOverlay;
    private boolean lastPriceEnabled = true;  // Enabled by default
    private ReferencePriceOverlay referencePriceOverlay;
    private boolean referencePriceEnabled = false;
    private final ChartInteractionManager interactionManager;
    private final ChartLifecycleManager lifecycleManager;

    public PriceChartPanel() {
        setLayout(new BorderLayout());
        setBackground(UIManager.getColor("Panel.background"));

        // Create data providers
        dataProvider = new DeskDataProvider();
        indicatorDataProvider = new DeskIndicatorDataProvider(dataProvider);

        // Create coordinator for syncing charts
        coordinator = new ChartCoordinator();

        // Create interaction manager for zoom/pan
        interactionManager = new ChartInteractionManager();

        // Create lifecycle manager composing coordinator + interaction manager
        lifecycleManager = new ChartLifecycleManager(coordinator, interactionManager);

        // Create indicator charts manager (no premium/spectrum for desk)
        indicatorManager = new IndicatorChartsManager(indicatorDataProvider, null, null, null);
        indicatorManager.setOnLayoutChange(this::rebuildLayout);

        // Create candlestick chart
        candlestickChart = new CandlestickChart(coordinator, "");
        candlestickChart.initialize();
        candlestickChart.setCandlestickMode(true);

        // Create shared overlay manager
        overlayManager = new OverlayManager(candlestickChart.getChart());
        overlayManager.setChartDataProvider(dataProvider);

        // Default price axis to right side, configurable via right-click menu
        ChartPanelFactory.setAxisPositionConfig("right", this::applyAxisPosition);
        candlestickChart.setRangeAxisPosition("right");
        interactionManager.setAxisPositionSupplier(ChartPanelFactory::getAxisPosition);

        // Add last price line overlay (enabled by default)
        lastPriceOverlay = new LastPriceOverlay();
        candlestickChart.addOverlay(lastPriceOverlay);

        // Register candlestick chart for synchronized zoom/pan and attach listeners
        lifecycleManager.addChart(candlestickChart.getChart(), candlestickChart.getChartPanel());

        // Add chart panel (volume/indicator charts added later if enabled)
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
        indicatorManager.refreshTheme();
        repaint();
    }

    // ===== Data Management =====

    /**
     * Set historical candles. Applies saved overlay and indicator config.
     */
    public void setCandles(List<Candle> historicalCandles, String symbol, String timeframe) {
        dataProvider.setCandles(historicalCandles, symbol, timeframe);
        overlayManager.setDataContext(symbol, timeframe, "perp");
        overlayManager.setCandles(historicalCandles);

        long start = historicalCandles.isEmpty() ? 0 : historicalCandles.get(0).timestamp();
        long end = historicalCandles.isEmpty() ? 0 : historicalCandles.get(historicalCandles.size() - 1).timestamp();
        indicatorManager.setDataContext(historicalCandles, symbol, timeframe, start, end);

        // Apply saved overlays and indicators from config
        ChartConfig config = ChartConfig.getInstance();
        overlayManager.applyConfig(config, historicalCandles);
        applyIndicatorConfig(config);

        SwingUtilities.invokeLater(this::refreshCharts);
    }

    /**
     * Update the current (incomplete) candle.
     */
    public void updateCurrentCandle(Candle candle) {
        dataProvider.updateCandle(candle);
        indicatorManager.updateCharts(dataProvider.getCandles());
        SwingUtilities.invokeLater(this::refreshCharts);
    }

    /**
     * Add a completed candle.
     */
    public void addCandle(Candle candle) {
        dataProvider.updateCandle(candle);
        indicatorManager.updateCharts(dataProvider.getCandles());
        SwingUtilities.invokeLater(this::refreshCharts);
    }

    /**
     * Clear the chart.
     */
    public void clear() {
        overlayManager.clearAll();
        dataProvider.setCandles(List.of(), "", "");
        indicatorManager.setDataContext(List.of(), "", "", 0, 0);
        SwingUtilities.invokeLater(this::refreshCharts);
    }

    /**
     * Wire data service client for footprint and DVP overlay support.
     * Call this after the DataServiceClient becomes available.
     */
    public void setDataServiceClient(com.tradery.dataclient.DataServiceClient client) {
        if (client != null) {
            overlayManager.setFootprintProfileProvider(
                new com.tradery.desk.ui.charts.DeskFootprintProfileProvider(client));
            overlayManager.setDailyProfileProvider(
                new com.tradery.desk.ui.charts.DeskDailyProfileProvider(client));
        }
    }

    /**
     * Get the data provider.
     */
    public DeskDataProvider getDataProvider() {
        return dataProvider;
    }

    /**
     * Get the overlay manager for external control (e.g., side panel).
     */
    public OverlayManager getOverlayManager() {
        return overlayManager;
    }

    /**
     * Get the indicator charts manager for external control (e.g., side panel).
     */
    public IndicatorChartsManager getIndicatorManager() {
        return indicatorManager;
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

    /**
     * Refresh candlestick and volume charts with current data.
     */
    private void refreshCharts() {
        candlestickChart.updateData(dataProvider);
        if (volumeChart != null) {
            volumeChart.updateData(dataProvider);
        }
    }

    // ===== Last Price / Reference Price Overlays =====

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

    public boolean isLastPriceEnabled() {
        return lastPriceEnabled;
    }

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

    public boolean isReferencePriceEnabled() {
        return referencePriceEnabled;
    }

    public void updateReferencePrice(double price) {
        if (referencePriceOverlay != null) {
            referencePriceOverlay.setReferencePrice(price);
            SwingUtilities.invokeLater(this::refreshCharts);
        }
    }

    public ReferencePriceOverlay getReferencePriceOverlay() {
        return referencePriceOverlay;
    }

    // ===== Volume Chart Support =====

    public void setVolumeEnabled(boolean enabled) {
        if (enabled == volumeEnabled) return;
        volumeEnabled = enabled;

        if (enabled) {
            volumeChart = new VolumeChart(coordinator, "");
            volumeChart.initialize();
            lifecycleManager.addChart(volumeChart.getChart(), volumeChart.getChartPanel());
        } else {
            if (volumeChart != null) {
                lifecycleManager.removeChart(volumeChart.getChart(), volumeChart.getChartPanel());
                volumeChart.dispose();
                volumeChart = null;
            }
        }

        rebuildLayout();
    }

    public boolean isVolumeEnabled() {
        return volumeEnabled;
    }

    public VolumeChart getVolumeChart() {
        return volumeChart;
    }

    // ===== Indicator Chart Support =====

    /**
     * Enable or disable an indicator chart type, registering/unregistering
     * with the coordinator and interaction manager.
     */
    public void setIndicatorEnabled(IndicatorType type, boolean enabled) {
        boolean wasEnabled = indicatorManager.isEnabled(type);
        if (enabled == wasEnabled) return;

        if (enabled) {
            lifecycleManager.addChart(indicatorManager.getChart(type), indicatorManager.getChartPanel(type));
        } else {
            lifecycleManager.removeChart(indicatorManager.getChart(type), indicatorManager.getChartPanel(type));
        }

        indicatorManager.setEnabled(type, enabled);

        // Trigger data update for newly enabled chart
        if (enabled && !dataProvider.getCandles().isEmpty()) {
            indicatorManager.updateCharts(dataProvider.getCandles(), type);
        }
    }

    /**
     * Apply indicator config from ChartConfig, handling coordinator/interactionManager registration.
     */
    public void applyIndicatorConfig(ChartConfig config) {
        // Set parameters before enabling
        indicatorManager.<RsiChart>getChartImpl(IndicatorType.RSI).setPeriod(config.getRsiPeriod());
        setIndicatorEnabled(IndicatorType.RSI, config.isRsiEnabled());

        MacdChart macd = indicatorManager.getChartImpl(IndicatorType.MACD);
        macd.setFast(config.getMacdFast());
        macd.setSlow(config.getMacdSlow());
        macd.setSignal(config.getMacdSignal());
        setIndicatorEnabled(IndicatorType.MACD, config.isMacdEnabled());

        indicatorManager.<AtrChart>getChartImpl(IndicatorType.ATR).setPeriod(config.getAtrPeriod());
        setIndicatorEnabled(IndicatorType.ATR, config.isAtrEnabled());

        StochasticChart stoch = indicatorManager.getChartImpl(IndicatorType.STOCHASTIC);
        stoch.setKPeriod(config.getStochasticKPeriod());
        stoch.setDPeriod(config.getStochasticDPeriod());
        setIndicatorEnabled(IndicatorType.STOCHASTIC, config.isStochasticEnabled());

        indicatorManager.<RangePositionChart>getChartImpl(IndicatorType.RANGE_POSITION).setPeriod(config.getRangePositionPeriod());
        setIndicatorEnabled(IndicatorType.RANGE_POSITION, config.isRangePositionEnabled());

        indicatorManager.<AdxChart>getChartImpl(IndicatorType.ADX).setPeriod(config.getAdxPeriod());
        setIndicatorEnabled(IndicatorType.ADX, config.isAdxEnabled());

        setIndicatorEnabled(IndicatorType.DELTA, config.isDeltaEnabled());
        setIndicatorEnabled(IndicatorType.CVD, config.isCvdEnabled());
        setIndicatorEnabled(IndicatorType.VOLUME_RATIO, config.isVolumeRatioEnabled());

        WhaleChart whale = indicatorManager.getChartImpl(IndicatorType.WHALE);
        whale.setThreshold(config.getWhaleThreshold());
        setIndicatorEnabled(IndicatorType.WHALE, config.isWhaleEnabled());

        RetailChart retail = indicatorManager.getChartImpl(IndicatorType.RETAIL);
        retail.setThreshold(config.getRetailThreshold());
        setIndicatorEnabled(IndicatorType.RETAIL, config.isRetailEnabled());

        setIndicatorEnabled(IndicatorType.TRADE_COUNT, config.isTradeCountEnabled());
        setIndicatorEnabled(IndicatorType.FUNDING, config.isFundingEnabled());
        setIndicatorEnabled(IndicatorType.OI, config.isOiEnabled());
        setIndicatorEnabled(IndicatorType.PREMIUM, config.isPremiumEnabled());
        setIndicatorEnabled(IndicatorType.FEAR_GREED, config.isFearGreedEnabled());
        setIndicatorEnabled(IndicatorType.SPECTRUM, config.isSpectrumEnabled());
        setIndicatorEnabled(IndicatorType.HOLDING_COST_CUMULATIVE, config.isHoldingCostCumulativeEnabled());
        setIndicatorEnabled(IndicatorType.HOLDING_COST_EVENTS, config.isHoldingCostEventsEnabled());

        // Volume chart
        setVolumeEnabled(config.isVolumeChartEnabled());
    }

    // ===== Layout =====

    /**
     * Apply axis position to all charts.
     */
    private void applyAxisPosition(String position) {
        SwingUtilities.invokeLater(() -> {
            candlestickChart.setRangeAxisPosition(position);
            if (volumeChart != null) volumeChart.setRangeAxisPosition(position);
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

        // Add enabled indicator chart panels
        for (IndicatorType type : IndicatorType.values()) {
            if (indicatorManager.isEnabled(type)) {
                panels.add(indicatorManager.getChartPanel(type));
            }
        }

        if (panels.size() == 1) {
            add(panels.get(0), BorderLayout.CENTER);
        } else {
            JComponent combined = createNestedSplitPanes(panels);
            add(combined, BorderLayout.CENTER);
        }

        // Apply current axis position to all charts
        applyAxisPosition(ChartPanelFactory.getAxisPosition());

        // Update time axis visibility — labels on first/last chart only
        List<org.jfree.chart.ChartPanel> visibleChartPanels = new ArrayList<>();
        visibleChartPanels.add(candlestickChart.getChartPanel());
        if (volumeChart != null) {
            visibleChartPanels.add(volumeChart.getChartPanel());
        }
        for (IndicatorType type : IndicatorType.values()) {
            if (indicatorManager.isEnabled(type)) {
                visibleChartPanels.add(indicatorManager.getChartPanel(type));
            }
        }
        lifecycleManager.updateTimeAxisVisibility(visibleChartPanels);

        // Update all charts with current data
        if (!dataProvider.getCandles().isEmpty()) {
            refreshCharts();
        }

        revalidate();
        repaint();
    }

    /**
     * Create nested split panes for multiple charts.
     */
    private JComponent createNestedSplitPanes(List<JComponent> panels) {
        if (panels.size() == 1) {
            return panels.get(0);
        }

        JComponent current = panels.get(panels.size() - 1);

        for (int i = panels.size() - 2; i >= 0; i--) {
            ThinSplitPane split = new ThinSplitPane(JSplitPane.VERTICAL_SPLIT);

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
