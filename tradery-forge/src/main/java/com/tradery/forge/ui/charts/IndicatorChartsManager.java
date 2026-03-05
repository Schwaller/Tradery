package com.tradery.forge.ui.charts;

import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;
import com.tradery.core.model.Trade;
import com.tradery.forge.ui.charts.indicator.*;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;

import javax.swing.*;
import java.util.*;

/**
 * Thin coordinator for indicator charts.
 * Each chart type is encapsulated in an {@link IndicatorChart} subclass.
 */
public class IndicatorChartsManager {

    private final EnumMap<IndicatorType, IndicatorChart> charts = new EnumMap<>(IndicatorType.class);
    private final EnumSet<IndicatorType> enabledIndicators = EnumSet.noneOf(IndicatorType.class);

    private final IndicatorDataService indicatorDataService = new IndicatorDataService();

    private IndicatorEngine indicatorEngine;
    private List<Trade> currentTrades;
    private String currentSymbol;
    private String currentTimeframe;

    private Runnable onLayoutChange;

    public IndicatorChartsManager() {
        registerCharts();
        indicatorDataService.addDataListener(this::onIndicatorDataReady);
    }

    private void registerCharts() {
        charts.put(IndicatorType.RSI, new RsiChart());
        charts.put(IndicatorType.MACD, new MacdChart());
        charts.put(IndicatorType.ATR, new AtrChart());
        charts.put(IndicatorType.DELTA, new DeltaChart());
        charts.put(IndicatorType.CVD, new CvdChart());
        charts.put(IndicatorType.VOLUME_RATIO, new VolumeRatioChart());
        charts.put(IndicatorType.WHALE, new WhaleChart());
        charts.put(IndicatorType.RETAIL, new RetailChart());
        charts.put(IndicatorType.FUNDING, new FundingChart());
        charts.put(IndicatorType.OI, new OiChart());
        charts.put(IndicatorType.STOCHASTIC, new StochasticChart());
        charts.put(IndicatorType.RANGE_POSITION, new RangePositionChart());
        charts.put(IndicatorType.ADX, new AdxChart());
        charts.put(IndicatorType.TRADE_COUNT, new TradeCountChart());
        charts.put(IndicatorType.PREMIUM, new PremiumChart());
        charts.put(IndicatorType.HOLDING_COST_CUMULATIVE, new HoldingCostCumulativeChart());
        charts.put(IndicatorType.HOLDING_COST_EVENTS, new HoldingCostEventsChart());
        charts.put(IndicatorType.FEAR_GREED, new FearGreedChart());
        charts.put(IndicatorType.SPECTRUM, new SpectrumChart());
    }

    // ===== Enable/disable =====

    public void setEnabled(IndicatorType type, boolean enabled) {
        if (enabled) {
            enabledIndicators.add(type);
        } else {
            enabledIndicators.remove(type);
        }
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isEnabled(IndicatorType type) {
        return enabledIndicators.contains(type);
    }

    public boolean isAnyOrderflowEnabled() {
        for (IndicatorType type : enabledIndicators) {
            if (charts.get(type).requiresOrderflow()) return true;
        }
        return false;
    }

    // ===== Data context =====

    public void setOnLayoutChange(Runnable callback) {
        this.onLayoutChange = callback;
    }

    public void setIndicatorEngine(IndicatorEngine engine) {
        this.indicatorEngine = engine;
    }

    public void setTrades(List<Trade> trades) {
        this.currentTrades = trades;
    }

    /**
     * Set the data context and subscribe all enabled charts.
     */
    public void setDataContext(List<Candle> candles, String symbol, String timeframe,
                               long startTime, long endTime) {
        this.currentSymbol = symbol;
        this.currentTimeframe = timeframe;

        indicatorDataService.setDataContext(candles, symbol, timeframe, startTime, endTime);

        ChartDataContext ctx = new ChartDataContext(
            symbol, timeframe, startTime, endTime,
            indicatorDataService, indicatorEngine, currentTrades
        );

        for (IndicatorType type : enabledIndicators) {
            charts.get(type).subscribe(ctx);
        }
    }

    /**
     * Called when indicator data becomes ready (callback from IndicatorDataService).
     */
    private void onIndicatorDataReady() {
        List<Candle> candles = indicatorDataService.getCandles();
        if (candles == null || candles.isEmpty()) return;

        ChartDataContext ctx = new ChartDataContext(
            currentSymbol, currentTimeframe,
            candles.get(0).timestamp(), candles.get(candles.size() - 1).timestamp(),
            indicatorDataService, indicatorEngine, currentTrades
        );

        for (IndicatorType type : enabledIndicators) {
            charts.get(type).redraw(candles, ctx);
        }
    }

    /**
     * Update all enabled indicator charts with new candle data.
     */
    public void updateCharts(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        ChartDataContext ctx = new ChartDataContext(
            currentSymbol, currentTimeframe,
            candles.get(0).timestamp(), candles.get(candles.size() - 1).timestamp(),
            indicatorDataService, indicatorEngine, currentTrades
        );

        for (IndicatorType type : enabledIndicators) {
            IndicatorChart chart = charts.get(type);
            chart.subscribe(ctx);
            chart.redraw(candles, ctx);
        }
    }

    /**
     * Update a single chart type (for async data arrival — funding, OI, premium, etc.).
     */
    public void updateChart(IndicatorType type, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;
        if (!enabledIndicators.contains(type)) return;

        ChartDataContext ctx = new ChartDataContext(
            currentSymbol, currentTimeframe,
            candles.get(0).timestamp(), candles.get(candles.size() - 1).timestamp(),
            indicatorDataService, indicatorEngine, currentTrades
        );

        IndicatorChart chart = charts.get(type);
        chart.subscribe(ctx);
        chart.redraw(candles, ctx);
    }

    /**
     * Update multiple chart types at once (for orderflow refresh).
     */
    public void updateCharts(List<Candle> candles, IndicatorType... types) {
        if (candles == null || candles.isEmpty()) return;

        ChartDataContext ctx = new ChartDataContext(
            currentSymbol, currentTimeframe,
            candles.get(0).timestamp(), candles.get(candles.size() - 1).timestamp(),
            indicatorDataService, indicatorEngine, currentTrades
        );

        for (IndicatorType type : types) {
            if (enabledIndicators.contains(type)) {
                IndicatorChart chart = charts.get(type);
                chart.subscribe(ctx);
                chart.redraw(candles, ctx);
            }
        }
    }

    /**
     * Re-render spectrum chart (e.g., after color/bucket mode change).
     */
    public void refreshSpectrumChart() {
        IndicatorChart spectrum = charts.get(IndicatorType.SPECTRUM);
        if (spectrum instanceof SpectrumChart spectrumChart) {
            List<Candle> candles = indicatorDataService.getCandles();
            spectrumChart.refresh(currentSymbol, candles);
        }
    }

    // ===== Chart access =====

    @SuppressWarnings("unchecked")
    public <T extends IndicatorChart> T getChartImpl(IndicatorType type) {
        return (T) charts.get(type);
    }

    public ChartComponent getComponent(IndicatorType type) {
        return charts.get(type).getComponent();
    }

    public JFreeChart getChart(IndicatorType type) {
        return charts.get(type).getChart();
    }

    public org.jfree.chart.ChartPanel getChartPanel(IndicatorType type) {
        return charts.get(type).getChartPanel();
    }

    public JPanel getChartWrapper(IndicatorType type) {
        return charts.get(type).getChartWrapper();
    }

    public JButton getZoomButton(IndicatorType type) {
        return charts.get(type).getZoomButton();
    }

    public IndicatorDataService getIndicatorDataService() {
        return indicatorDataService;
    }

    // ===== Iteration helpers =====

    /**
     * Get all chart panels (for crosshair setup, mouse listeners, etc.).
     */
    public List<org.jfree.chart.ChartPanel> getAllChartPanels() {
        List<org.jfree.chart.ChartPanel> panels = new ArrayList<>();
        for (IndicatorType type : IndicatorType.values()) {
            panels.add(charts.get(type).getChartPanel());
        }
        return panels;
    }

    /**
     * Get all JFreeCharts (for theme refresh, etc.).
     */
    public List<JFreeChart> getAllCharts() {
        List<JFreeChart> result = new ArrayList<>();
        for (IndicatorType type : IndicatorType.values()) {
            result.add(charts.get(type).getChart());
        }
        return result;
    }

    /**
     * Get wrappers for ALL indicators (for zoom/layout detection), in enum order.
     */
    public List<JPanel> getAllWrappers() {
        List<JPanel> wrappers = new ArrayList<>();
        for (IndicatorType type : IndicatorType.values()) {
            wrappers.add(charts.get(type).getChartWrapper());
        }
        return wrappers;
    }

    /**
     * Get wrappers for all enabled (visible) indicators, in enum order.
     */
    public List<JPanel> getVisibleWrappers() {
        List<JPanel> wrappers = new ArrayList<>();
        for (IndicatorType type : IndicatorType.values()) {
            if (enabledIndicators.contains(type)) {
                wrappers.add(charts.get(type).getChartWrapper());
            }
        }
        return wrappers;
    }

    // ===== Wrapper/button management =====

    public void createWrappers(java.util.function.IntConsumer zoomCallback) {
        createWrappers(zoomCallback, null, null);
    }

    public void createWrappers(java.util.function.IntConsumer zoomCallback,
                                java.util.function.IntConsumer fullScreenCallback) {
        createWrappers(zoomCallback, fullScreenCallback, null);
    }

    public void createWrappers(java.util.function.IntConsumer zoomCallback,
                                java.util.function.IntConsumer fullScreenCallback,
                                Runnable exitFullScreenCallback) {
        for (IndicatorType type : IndicatorType.values()) {
            int ordinal = type.ordinal();
            Runnable fsCallback = fullScreenCallback != null ? () -> fullScreenCallback.accept(ordinal) : null;
            charts.get(type).getComponent().createWrapper(() -> zoomCallback.accept(ordinal), fsCallback, exitFullScreenCallback);
        }
    }

    public void updateZoomButtonStates(int zoomedIndex) {
        for (IndicatorType type : IndicatorType.values()) {
            charts.get(type).getComponent().setZoomed(type.ordinal() == zoomedIndex);
        }
    }

    public void updateFullScreenButtonStates(int fullScreenIndex) {
        for (IndicatorType type : IndicatorType.values()) {
            charts.get(type).getComponent().setFullScreen(type.ordinal() == fullScreenIndex);
        }
    }

    public void updateCloseButtonVisibility(int fullScreenIndex) {
        for (IndicatorType type : IndicatorType.values()) {
            charts.get(type).getComponent().setCloseButtonVisible(type.ordinal() == fullScreenIndex);
        }
    }

    public void addMouseWheelListener(java.awt.event.MouseWheelListener listener) {
        for (IndicatorChart chart : charts.values()) {
            chart.getChartPanel().addMouseWheelListener(listener);
        }
    }

    public void removeMouseWheelListener(java.awt.event.MouseWheelListener listener) {
        for (IndicatorChart chart : charts.values()) {
            chart.getChartPanel().removeMouseWheelListener(listener);
        }
    }

    public void updateYAxisAutoRange(boolean fitYAxisToVisible) {
        for (IndicatorType type : IndicatorType.values()) {
            IndicatorChart chart = charts.get(type);
            XYPlot plot = chart.getChart().getXYPlot();
            ValueAxis rangeAxis = plot.getRangeAxis();
            double[] fixedRange = type.getYAxisRange();

            if (fitYAxisToVisible) {
                rangeAxis.setAutoRange(true);
                plot.configureRangeAxes();
            } else if (fixedRange != null) {
                rangeAxis.setAutoRange(false);
                rangeAxis.setRange(fixedRange[0], fixedRange[1]);
            } else {
                rangeAxis.setAutoRange(true);
            }
        }
    }

    /**
     * Refresh theme on all charts.
     */
    public void refreshTheme() {
        for (IndicatorChart chart : charts.values()) {
            chart.refreshTheme();
        }
    }

    /**
     * Dispose of all resources.
     */
    public void dispose() {
        for (IndicatorChart chart : charts.values()) {
            chart.dispose();
        }
        this.onLayoutChange = null;
        this.indicatorEngine = null;
    }
}
