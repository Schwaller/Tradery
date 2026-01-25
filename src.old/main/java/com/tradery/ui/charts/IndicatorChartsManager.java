package com.tradery.ui.charts;

import com.tradery.ApplicationContext;
import com.tradery.data.PageState;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.data.page.PremiumPageManager;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.indicators.Indicators;
import com.tradery.model.Candle;
import com.tradery.model.PremiumIndex;
import com.tradery.model.Trade;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.tradery.ui.charts.ChartAnnotationHelper.*;
import static com.tradery.ui.charts.RendererBuilder.*;

/**
 * Manages optional indicator charts (RSI, MACD, ATR, Orderflow, Funding).
 */
public class IndicatorChartsManager {

    // Chart components indexed by type
    private final EnumMap<IndicatorType, ChartComponent> components = new EnumMap<>(IndicatorType.class);

    // Enable state
    private final EnumSet<IndicatorType> enabledIndicators = EnumSet.noneOf(IndicatorType.class);

    // Current trades for holding cost charts
    private List<Trade> currentTrades;

    // Indicator parameters
    private int rsiPeriod = 14;
    private int macdFast = 12;
    private int macdSlow = 26;
    private int macdSignal = 9;
    private int atrPeriod = 14;
    private double whaleThreshold = 50000;
    private double retailThreshold = 50000;
    private int stochasticKPeriod = 14;
    private int stochasticDPeriod = 3;
    private int rangePositionPeriod = 200;
    private int rangePositionSkip = 0;
    private int adxPeriod = 14;

    // IndicatorEngine for funding/OI data (only these charts still use it)
    // Delta, CVD, VolumeRatio, Whale, Retail, TradeCount, RangePosition now use IndicatorDataService
    private IndicatorEngine indicatorEngine;

    // IndicatorDataService for background indicator computation
    private final IndicatorDataService indicatorDataService = new IndicatorDataService();

    // Premium data tracking (market data, not computed indicator)
    private DataPageView<PremiumIndex> premiumPage;
    private DataPageListener<PremiumIndex> premiumListener;
    private List<PremiumIndex> currentPremiumData;
    private String currentSymbol;
    private String currentTimeframe;

    // Callback for layout updates
    private Runnable onLayoutChange;

    // Min chart height
    private static final int MIN_CHART_HEIGHT = 60;

    public IndicatorChartsManager() {
        initializeCharts();
        // Listen for indicator data changes to trigger chart repaints
        indicatorDataService.addDataListener(this::onIndicatorDataReady);
    }

    public void setOnLayoutChange(Runnable callback) {
        this.onLayoutChange = callback;
    }

    /**
     * Set the data context and subscribe to all enabled indicators.
     * Call this when candles change.
     */
    public void setDataContext(List<Candle> candles, String symbol, String timeframe,
                               long startTime, long endTime) {
        // Store current context for premium data loading
        this.currentSymbol = symbol;
        this.currentTimeframe = timeframe;

        // Update context in the service
        indicatorDataService.setDataContext(candles, symbol, timeframe, startTime, endTime);

        // Subscribe to enabled indicators
        if (isEnabled(IndicatorType.RSI)) {
            indicatorDataService.subscribeRSI(rsiPeriod);
        }
        if (isEnabled(IndicatorType.MACD)) {
            indicatorDataService.subscribeMACD(macdFast, macdSlow, macdSignal);
        }
        if (isEnabled(IndicatorType.ATR)) {
            indicatorDataService.subscribeATR(atrPeriod);
        }
        if (isEnabled(IndicatorType.STOCHASTIC)) {
            indicatorDataService.subscribeStochastic(stochasticKPeriod, stochasticDPeriod);
        }
        if (isEnabled(IndicatorType.ADX)) {
            indicatorDataService.subscribeADX(adxPeriod);
            indicatorDataService.subscribePlusDI(adxPeriod);
            indicatorDataService.subscribeMinusDI(adxPeriod);
        }
        if (isEnabled(IndicatorType.RANGE_POSITION)) {
            indicatorDataService.subscribeRangePosition(rangePositionPeriod, rangePositionSkip);
        }

        // Orderflow indicators (aggTrades-based)
        if (isEnabled(IndicatorType.DELTA)) {
            indicatorDataService.subscribeDelta();
        }
        if (isEnabled(IndicatorType.CVD)) {
            indicatorDataService.subscribeCumDelta();
        }
        if (isEnabled(IndicatorType.VOLUME_RATIO)) {
            indicatorDataService.subscribeBuyVolume();
            indicatorDataService.subscribeSellVolume();
        }
        if (isEnabled(IndicatorType.WHALE)) {
            indicatorDataService.subscribeWhaleDelta(whaleThreshold);
        }
        if (isEnabled(IndicatorType.RETAIL)) {
            indicatorDataService.subscribeRetailDelta(retailThreshold);
        }
        if (isEnabled(IndicatorType.TRADE_COUNT)) {
            indicatorDataService.subscribeTradeCount();
        }

        // Note: Footprint heatmap overlay manages its own IndicatorPage directly

        // Request premium data if chart is enabled
        if (isEnabled(IndicatorType.PREMIUM)) {
            requestPremiumData(symbol, timeframe, startTime, endTime);
        }
    }

    /**
     * Request premium data from PremiumPageManager.
     */
    private void requestPremiumData(String symbol, String timeframe, long startTime, long endTime) {
        PremiumPageManager premiumPageMgr = ApplicationContext.getInstance().getPremiumPageManager();
        if (premiumPageMgr == null) {
            return;
        }

        // Release previous page if any
        if (premiumPage != null && premiumListener != null) {
            premiumPageMgr.release(premiumPage, premiumListener);
        }

        // Create listener for premium data
        premiumListener = new DataPageListener<>() {
            @Override
            public void onStateChanged(DataPageView<PremiumIndex> page, PageState oldState, PageState newState) {
                if (newState == PageState.READY) {
                    currentPremiumData = page.getData();
                    // Refresh chart on EDT
                    SwingUtilities.invokeLater(() -> {
                        List<Candle> candles = indicatorDataService.getCandles();
                        if (candles != null && !candles.isEmpty()) {
                            updatePremiumChartFromData(candles);
                        }
                    });
                }
            }

            @Override
            public void onDataChanged(DataPageView<PremiumIndex> page) {
                if (page.isReady()) {
                    currentPremiumData = page.getData();
                    SwingUtilities.invokeLater(() -> {
                        List<Candle> candles = indicatorDataService.getCandles();
                        if (candles != null && !candles.isEmpty()) {
                            updatePremiumChartFromData(candles);
                        }
                    });
                }
            }
        };

        premiumPage = premiumPageMgr.request(symbol, timeframe, startTime, endTime, premiumListener, "PremiumChart");
    }

    /**
     * Called when indicator data becomes ready.
     */
    private void onIndicatorDataReady() {
        // Redraw all charts with the now-available data
        List<Candle> candles = indicatorDataService.getCandles();
        if (candles != null && !candles.isEmpty()) {
            redrawRsiChart(candles);
            redrawMacdChart(candles);
            redrawAtrChart(candles);
            redrawStochasticChart(candles);
            redrawAdxChart(candles);
            redrawRangePositionChart(candles);
            // Orderflow charts
            redrawDeltaChart(candles);
            redrawCvdChart(candles);
            redrawVolumeRatioChart(candles);
            redrawWhaleChart(candles);
            redrawRetailChart(candles);
            redrawTradeCountChart(candles);
        }
    }

    private void initializeCharts() {
        for (IndicatorType type : IndicatorType.values()) {
            components.put(type, new ChartComponent(type.getTitle(), type.getYAxisRange()));
        }
    }

    /**
     * Get a chart component by type.
     */
    public ChartComponent getComponent(IndicatorType type) {
        return components.get(type);
    }

    /**
     * Check if an indicator chart is enabled.
     */
    public boolean isEnabled(IndicatorType type) {
        return enabledIndicators.contains(type);
    }

    /**
     * Set an indicator chart's enabled state.
     */
    private void setEnabled(IndicatorType type, boolean enabled) {
        if (enabled) {
            enabledIndicators.add(type);
        } else {
            enabledIndicators.remove(type);
        }
    }

    /**
     * Create wrapper panels with zoom buttons.
     * @param zoomCallback Callback to handle zoom toggle by indicator ordinal
     */
    public void createWrappers(java.util.function.IntConsumer zoomCallback) {
        createWrappers(zoomCallback, null, null);
    }

    /**
     * Create wrapper panels with zoom and full screen buttons.
     * @param zoomCallback Callback to handle zoom toggle by indicator ordinal
     * @param fullScreenCallback Callback to handle full screen toggle by indicator ordinal
     */
    public void createWrappers(java.util.function.IntConsumer zoomCallback, java.util.function.IntConsumer fullScreenCallback) {
        createWrappers(zoomCallback, fullScreenCallback, null);
    }

    /**
     * Create wrapper panels with zoom, full screen, and exit full screen buttons.
     * @param zoomCallback Callback to handle zoom toggle by indicator ordinal
     * @param fullScreenCallback Callback to handle full screen toggle by indicator ordinal
     * @param exitFullScreenCallback Callback to exit full screen mode (shared for all indicators)
     */
    public void createWrappers(java.util.function.IntConsumer zoomCallback, java.util.function.IntConsumer fullScreenCallback, Runnable exitFullScreenCallback) {
        for (IndicatorType type : IndicatorType.values()) {
            int ordinal = type.ordinal();
            Runnable fsCallback = fullScreenCallback != null ? () -> fullScreenCallback.accept(ordinal) : null;
            components.get(type).createWrapper(() -> zoomCallback.accept(ordinal), fsCallback, exitFullScreenCallback);
        }
    }

    // ===== RSI Methods =====

    public void setRsiChartEnabled(boolean enabled, int period) {
        setEnabled(IndicatorType.RSI, enabled);
        this.rsiPeriod = period;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isRsiChartEnabled() {
        return isEnabled(IndicatorType.RSI);
    }

    public void updateRsiChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.RSI) || candles == null || candles.size() < rsiPeriod + 1) {
            return;
        }
        // Subscribe to RSI data (will arrive via callback if not ready)
        indicatorDataService.subscribeRSI(rsiPeriod);
        // Try to render with available data
        redrawRsiChart(candles);
    }

    private void redrawRsiChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.RSI) || candles == null || candles.size() < rsiPeriod + 1) {
            return;
        }

        // Get pre-computed RSI data from service
        double[] rsiValues = indicatorDataService.getRSI(rsiPeriod);
        if (rsiValues == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.RSI).getChart().getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries rsiSeries = new TimeSeries("RSI(" + rsiPeriod + ")");

        for (int i = rsiPeriod; i < candles.size() && i < rsiValues.length; i++) {
            if (!Double.isNaN(rsiValues[i])) {
                Candle c = candles.get(i);
                rsiSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), rsiValues[i]);
            }
        }

        dataset.addSeries(rsiSeries);
        plot.setDataset(dataset);

        // Style the RSI line
        plot.setRenderer(lineRenderer(ChartStyles.RSI_COLOR));

        // Add reference lines at 30, 50, 70
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "RSI");

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addRsiLines(plot, startTime, endTime);
        }
    }

    // ===== MACD Methods =====

    public void setMacdChartEnabled(boolean enabled, int fast, int slow, int signal) {
        setEnabled(IndicatorType.MACD, enabled);
        this.macdFast = fast;
        this.macdSlow = slow;
        this.macdSignal = signal;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isMacdChartEnabled() {
        return isEnabled(IndicatorType.MACD);
    }

    public void updateMacdChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.MACD) || candles == null || candles.size() < macdSlow + macdSignal) {
            return;
        }
        // Subscribe to MACD data (will arrive via callback if not ready)
        indicatorDataService.subscribeMACD(macdFast, macdSlow, macdSignal);
        // Try to render with available data
        redrawMacdChart(candles);
    }

    private void redrawMacdChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.MACD) || candles == null || candles.size() < macdSlow + macdSignal) {
            return;
        }

        // Get pre-computed MACD data from service
        Indicators.MACDResult macdResult = indicatorDataService.getMACD(macdFast, macdSlow, macdSignal);
        if (macdResult == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.MACD).getChart().getXYPlot();
        TimeSeriesCollection lineDataset = new TimeSeriesCollection();
        TimeSeries macdLine = new TimeSeries("MACD");
        TimeSeries signalLine = new TimeSeries("Signal");

        double[] macdValues = macdResult.line();
        double[] signalValues = macdResult.signal();
        double[] histogramValues = macdResult.histogram();

        // Build series
        XYSeriesCollection histogramDataset = new XYSeriesCollection();
        XYSeries histogramSeries = new XYSeries("Histogram");

        for (int i = macdSlow + macdSignal - 1; i < candles.size() && i < macdValues.length; i++) {
            if (!Double.isNaN(macdValues[i]) && !Double.isNaN(signalValues[i])) {
                Candle c = candles.get(i);
                Millisecond ms = new Millisecond(new Date(c.timestamp()));
                macdLine.addOrUpdate(ms, macdValues[i]);
                signalLine.addOrUpdate(ms, signalValues[i]);
                histogramSeries.add(c.timestamp(), histogramValues[i]);
            }
        }

        lineDataset.addSeries(macdLine);
        lineDataset.addSeries(signalLine);
        histogramDataset.addSeries(histogramSeries);

        // Set up renderers
        plot.setDataset(0, histogramDataset);
        plot.setDataset(1, lineDataset);

        // Histogram renderer with color based on value
        plot.setRenderer(0, colorCodedBarRenderer(histogramDataset, ChartStyles.MACD_HIST_POS, ChartStyles.MACD_HIST_NEG));

        // Line renderer for MACD and signal
        plot.setRenderer(1, lineRenderer(
            ChartStyles.MACD_LINE_COLOR, ChartStyles.MEDIUM_STROKE,
            ChartStyles.MACD_SIGNAL_COLOR, ChartStyles.MEDIUM_STROKE));

        // Add zero line
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "MACD");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addZeroLine(plot, startTime, endTime);
        }
    }

    // ===== ATR Methods =====

    public void setAtrChartEnabled(boolean enabled, int period) {
        setEnabled(IndicatorType.ATR, enabled);
        this.atrPeriod = period;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isAtrChartEnabled() {
        return isEnabled(IndicatorType.ATR);
    }

    public void updateAtrChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.ATR) || candles == null || candles.size() < atrPeriod + 1) {
            return;
        }
        // Subscribe to ATR data (will arrive via callback if not ready)
        indicatorDataService.subscribeATR(atrPeriod);
        // Try to render with available data
        redrawAtrChart(candles);
    }

    private void redrawAtrChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.ATR) || candles == null || candles.size() < atrPeriod + 1) {
            return;
        }

        // Get pre-computed ATR data from service
        double[] atrValues = indicatorDataService.getATR(atrPeriod);
        if (atrValues == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.ATR).getChart().getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries atrSeries = new TimeSeries("ATR(" + atrPeriod + ")");

        for (int i = atrPeriod; i < candles.size() && i < atrValues.length; i++) {
            if (!Double.isNaN(atrValues[i])) {
                Candle c = candles.get(i);
                atrSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), atrValues[i]);
            }
        }

        dataset.addSeries(atrSeries);
        plot.setDataset(dataset);

        // Style the ATR line
        plot.setRenderer(lineRenderer(ChartStyles.ATR_COLOR));

        // Clear annotations and re-add title
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "ATR");
    }

    // ===== Delta Methods =====

    /**
     * Set the indicator engine. Still needed for Funding and OI charts.
     * Most indicator charts now use IndicatorDataService for background computation.
     */
    public void setIndicatorEngine(IndicatorEngine engine) {
        this.indicatorEngine = engine;
    }

    public void setDeltaChartEnabled(boolean enabled) {
        setEnabled(IndicatorType.DELTA, enabled);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isDeltaChartEnabled() {
        return isEnabled(IndicatorType.DELTA);
    }

    public void setCvdChartEnabled(boolean enabled) {
        setEnabled(IndicatorType.CVD, enabled);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isCvdChartEnabled() {
        return isEnabled(IndicatorType.CVD);
    }

    public void setVolumeRatioChartEnabled(boolean enabled) {
        setEnabled(IndicatorType.VOLUME_RATIO, enabled);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isVolumeRatioChartEnabled() {
        return isEnabled(IndicatorType.VOLUME_RATIO);
    }

    public void setWhaleChartEnabled(boolean enabled, double threshold) {
        setEnabled(IndicatorType.WHALE, enabled);
        this.whaleThreshold = threshold;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isWhaleChartEnabled() {
        return isEnabled(IndicatorType.WHALE);
    }

    public void setWhaleThreshold(double threshold) {
        this.whaleThreshold = threshold;
    }

    public double getWhaleThreshold() {
        return whaleThreshold;
    }

    public void setRetailChartEnabled(boolean enabled) {
        setEnabled(IndicatorType.RETAIL, enabled);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public void setRetailChartEnabled(boolean enabled, double threshold) {
        setEnabled(IndicatorType.RETAIL, enabled);
        this.retailThreshold = threshold;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public void setRetailThreshold(double threshold) {
        this.retailThreshold = threshold;
    }

    public double getRetailThreshold() {
        return retailThreshold;
    }

    public boolean isRetailChartEnabled() {
        return isEnabled(IndicatorType.RETAIL);
    }

    /**
     * Check if any orderflow chart is enabled.
     */
    public boolean isAnyOrderflowEnabled() {
        return isEnabled(IndicatorType.DELTA) || isEnabled(IndicatorType.CVD) || isEnabled(IndicatorType.VOLUME_RATIO)
            || isEnabled(IndicatorType.WHALE) || isEnabled(IndicatorType.RETAIL) || isEnabled(IndicatorType.TRADE_COUNT);
    }

    public void updateDeltaChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.DELTA) || candles == null || candles.isEmpty()) {
            return;
        }
        // Subscribe to Delta data (will arrive via callback if not ready)
        indicatorDataService.subscribeDelta();
        // Try to render with available data
        redrawDeltaChart(candles);
    }

    private void redrawDeltaChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.DELTA) || candles == null || candles.isEmpty()) {
            return;
        }

        // Get pre-computed Delta data from service
        double[] delta = indicatorDataService.getDelta();
        if (delta == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.DELTA).getChart().getXYPlot();
        XYSeriesCollection deltaDataset = new XYSeriesCollection();
        XYSeries deltaSeries = new XYSeries("Delta");

        for (int i = 0; i < candles.size() && i < delta.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(delta[i])) {
                deltaSeries.add(c.timestamp(), delta[i]);
            }
        }
        deltaDataset.addSeries(deltaSeries);
        plot.setDataset(0, deltaDataset);

        // Color-coded bar renderer
        plot.setRenderer(0, colorCodedBarRenderer(deltaDataset, ChartStyles.DELTA_POSITIVE, ChartStyles.DELTA_NEGATIVE));

        // Add zero line
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Delta");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addZeroLine(plot, startTime, endTime);
        }
    }

    public void updateCvdChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.CVD) || candles == null || candles.isEmpty()) {
            return;
        }
        // Subscribe to CVD data (will arrive via callback if not ready)
        indicatorDataService.subscribeCumDelta();
        // Try to render with available data
        redrawCvdChart(candles);
    }

    private void redrawCvdChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.CVD) || candles == null || candles.isEmpty()) {
            return;
        }

        // Get pre-computed CVD data from service
        double[] cumDelta = indicatorDataService.getCumDelta();
        if (cumDelta == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.CVD).getChart().getXYPlot();
        TimeSeriesCollection cvdDataset = new TimeSeriesCollection();
        TimeSeries cvdSeries = new TimeSeries("CVD");

        for (int i = 0; i < candles.size() && i < cumDelta.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(cumDelta[i])) {
                cvdSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), cumDelta[i]);
            }
        }
        cvdDataset.addSeries(cvdSeries);
        plot.setDataset(0, cvdDataset);

        plot.setRenderer(0, lineRenderer(ChartStyles.CVD_COLOR));

        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "CVD (Cumulative Delta)");
    }

    public void updateVolumeRatioChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.VOLUME_RATIO) || candles == null || candles.isEmpty()) {
            return;
        }
        // Subscribe to volume data (will arrive via callback if not ready)
        indicatorDataService.subscribeBuyVolume();
        indicatorDataService.subscribeSellVolume();
        // Try to render with available data
        redrawVolumeRatioChart(candles);
    }

    private void redrawVolumeRatioChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.VOLUME_RATIO) || candles == null || candles.isEmpty()) {
            return;
        }

        // Get pre-computed volume data from service
        double[] buyVolume = indicatorDataService.getBuyVolume();
        double[] sellVolume = indicatorDataService.getSellVolume();
        if (buyVolume == null || sellVolume == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.VOLUME_RATIO).getChart().getXYPlot();

        // Create single series with net volume (buy - sell)
        // Positive = more buying, Negative = more selling
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries buySeries = new TimeSeries("Buy");
        TimeSeries sellSeries = new TimeSeries("Sell");

        double maxVolume = 0;
        for (int i = 0; i < candles.size() && i < buyVolume.length && i < sellVolume.length; i++) {
            Candle c = candles.get(i);
            double buy = buyVolume[i];
            double sell = sellVolume[i];
            if (!Double.isNaN(buy) && !Double.isNaN(sell)) {
                maxVolume = Math.max(maxVolume, Math.max(buy, sell));
                // Buy volume as positive, sell volume as negative
                buySeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), buy);
                sellSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), -sell);
            }
        }

        dataset.addSeries(buySeries);
        dataset.addSeries(sellSeries);
        plot.setDataset(0, dataset);

        // Two series renderer (green for buy, red for sell)
        plot.setRenderer(0, barRenderer(new Color(38, 166, 91, 200), new Color(231, 76, 60, 200)));

        // Map dataset to range axis
        plot.mapDatasetToRangeAxis(0, 0);

        // Set symmetric Y axis range around zero
        double padding = maxVolume * 1.1;
        if (padding > 0) {
            plot.getRangeAxis().setRange(-padding, padding);
        }

        // Add zero reference line
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Buy/Sell Volume");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addSimpleZeroLine(plot, startTime, endTime);
        }
    }

    public void updateWhaleChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.WHALE) || candles == null || candles.isEmpty()) {
            return;
        }
        // Subscribe to Whale Delta data (will arrive via callback if not ready)
        indicatorDataService.subscribeWhaleDelta(whaleThreshold);
        // Try to render with available data
        redrawWhaleChart(candles);
    }

    private void redrawWhaleChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.WHALE) || candles == null || candles.isEmpty()) {
            return;
        }

        // Get pre-computed Whale Delta data from service
        double[] whaleDelta = indicatorDataService.getWhaleDelta(whaleThreshold);
        if (whaleDelta == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.WHALE).getChart().getXYPlot();
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("Whale Delta");

        double maxDelta = 0;
        for (int i = 0; i < candles.size() && i < whaleDelta.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(whaleDelta[i])) {
                series.add(c.timestamp(), whaleDelta[i]);
                maxDelta = Math.max(maxDelta, Math.abs(whaleDelta[i]));
            }
        }
        dataset.addSeries(series);
        plot.setDataset(0, dataset);

        // Color-coded bar renderer (purple for whale trades)
        plot.setRenderer(0, colorCodedBarRendererNoMargin(dataset, ChartStyles.WHALE_DELTA_POS, ChartStyles.WHALE_DELTA_NEG));

        // Set symmetric Y axis range around zero
        double padding = maxDelta * 1.1;
        if (padding > 0) {
            plot.getRangeAxis().setRange(-padding, padding);
        }

        // Add zero reference line and title
        plot.clearAnnotations();
        String title = String.format("Whale Delta ($%.0fK+)", whaleThreshold / 1000);
        ChartStyles.addChartTitleAnnotation(plot, title);
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addSimpleZeroLine(plot, startTime, endTime);
        }
    }

    public void updateRetailChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.RETAIL) || candles == null || candles.isEmpty()) {
            return;
        }
        // Subscribe to Retail Delta data (will arrive via callback if not ready)
        indicatorDataService.subscribeRetailDelta(retailThreshold);
        // Try to render with available data
        redrawRetailChart(candles);
    }

    private void redrawRetailChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.RETAIL) || candles == null || candles.isEmpty()) {
            return;
        }

        // Get pre-computed Retail Delta data from service
        double[] retailDelta = indicatorDataService.getRetailDelta(retailThreshold);
        if (retailDelta == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.RETAIL).getChart().getXYPlot();
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("Retail Delta");

        double maxDelta = 0;
        for (int i = 0; i < candles.size() && i < retailDelta.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(retailDelta[i])) {
                series.add(c.timestamp(), retailDelta[i]);
                maxDelta = Math.max(maxDelta, Math.abs(retailDelta[i]));
            }
        }
        dataset.addSeries(series);
        plot.setDataset(0, dataset);

        // Color-coded bar renderer (blue/red for retail trades)
        plot.setRenderer(0, colorCodedBarRendererNoMargin(dataset, new Color(52, 152, 219), new Color(231, 76, 60)));

        // Set symmetric Y axis range around zero
        double padding = maxDelta * 1.1;
        if (padding > 0) {
            plot.getRangeAxis().setRange(-padding, padding);
        }

        // Add zero reference line and title
        plot.clearAnnotations();
        String title = String.format("Retail Delta (<$%.0fK)", retailThreshold / 1000);
        ChartStyles.addChartTitleAnnotation(plot, title);
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addSimpleZeroLine(plot, startTime, endTime);
        }
    }

    // ===== Funding Methods =====

    public void setFundingChartEnabled(boolean enabled) {
        setEnabled(IndicatorType.FUNDING, enabled);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isFundingChartEnabled() {
        return isEnabled(IndicatorType.FUNDING);
    }

    public void updateFundingChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.FUNDING) || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = components.get(IndicatorType.FUNDING).getChart().getXYPlot();

        double[] funding = indicatorEngine.getFunding();
        double[] funding8H = indicatorEngine.getFunding8H();

        // Check if funding data is available
        if (funding == null || funding.length == 0) {
            return;
        }

        // Build datasets
        XYSeriesCollection fundingDataset = new XYSeriesCollection();
        XYSeries fundingSeries = new XYSeries("Funding");

        TimeSeriesCollection avgDataset = new TimeSeriesCollection();
        TimeSeries avgSeries = new TimeSeries("Funding 8H Avg");

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(funding[i])) {
                fundingSeries.add(c.timestamp(), funding[i]);
            }
            if (!Double.isNaN(funding8H[i])) {
                avgSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), funding8H[i]);
            }
        }

        fundingDataset.addSeries(fundingSeries);
        avgDataset.addSeries(avgSeries);

        plot.setDataset(0, fundingDataset);
        plot.setDataset(1, avgDataset);

        // Funding line renderer (colored segments based on sign)
        plot.setRenderer(0, signColoredLineRenderer(fundingDataset, ChartStyles.FUNDING_POSITIVE, ChartStyles.FUNDING_NEGATIVE));

        // 8H average line renderer
        plot.setRenderer(1, lineRenderer(ChartStyles.FUNDING_8H_COLOR));

        // Format Y-axis to avoid scientific notation for small values
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setNumberFormatOverride(new java.text.DecimalFormat("0.####"));

        // Add reference lines at 0, 0.01 (typical high funding), -0.01 (negative funding)
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Funding Rate (%)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addFundingLines(plot, startTime, endTime);
        }
    }

    // ===== Open Interest Methods =====

    public void setOiChartEnabled(boolean enabled) {
        setEnabled(IndicatorType.OI, enabled);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isOiChartEnabled() {
        return isEnabled(IndicatorType.OI);
    }

    public void updateOiChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.OI) || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = components.get(IndicatorType.OI).getChart().getXYPlot();

        double[] oi = indicatorEngine.getOI();
        double[] oiChange = indicatorEngine.getOIChange();

        // Build datasets - OI value as line, OI change as bars
        TimeSeriesCollection oiLineDataset = new TimeSeriesCollection();
        TimeSeries oiSeries = new TimeSeries("Open Interest");

        XYSeriesCollection oiChangeDataset = new XYSeriesCollection();
        XYSeries oiChangeSeries = new XYSeries("OI Change");

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (i < oi.length && !Double.isNaN(oi[i])) {
                oiSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), oi[i]);
            }
            if (i < oiChange.length && !Double.isNaN(oiChange[i])) {
                oiChangeSeries.add(c.timestamp(), oiChange[i]);
            }
        }

        oiLineDataset.addSeries(oiSeries);
        oiChangeDataset.addSeries(oiChangeSeries);

        // OI change bars on primary dataset
        plot.setDataset(0, oiChangeDataset);
        // OI line on secondary dataset
        plot.setDataset(1, oiLineDataset);

        // OI change bar renderer (green for increase, red for decrease)
        final XYSeriesCollection finalOiChangeDataset = oiChangeDataset;
        XYBarRenderer changeRenderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = finalOiChangeDataset.getYValue(series, item);
                return value >= 0 ? ChartStyles.OI_POSITIVE : ChartStyles.OI_NEGATIVE;
            }
        };
        changeRenderer.setShadowVisible(false);
        changeRenderer.setBarPainter(new StandardXYBarPainter());
        plot.setRenderer(0, changeRenderer);

        // OI line renderer
        XYLineAndShapeRenderer oiLineRenderer = new XYLineAndShapeRenderer(true, false);
        oiLineRenderer.setSeriesPaint(0, ChartStyles.OI_LINE_COLOR);
        oiLineRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(1, oiLineRenderer);

        // Primary Y-axis for OI change bars (centered around zero, hidden)
        NumberAxis changeAxis = (NumberAxis) plot.getRangeAxis();
        changeAxis.setAutoRangeIncludesZero(true);
        changeAxis.setAutoRange(true);
        changeAxis.setVisible(false);

        // Secondary Y-axis for OI line (scale to data, not zero) - on LEFT side
        NumberAxis oiAxis = new NumberAxis();
        oiAxis.setAutoRangeIncludesZero(false);
        oiAxis.setAutoRange(true);
        oiAxis.setLabelPaint(ChartStyles.TEXT_COLOR);
        oiAxis.setTickLabelPaint(ChartStyles.TEXT_COLOR);
        oiAxis.setFixedDimension(60); // Match width with other chart axes
        oiAxis.setAxisLineVisible(false); // Hide the axis line
        oiAxis.setTickMarksVisible(false); // Hide tick marks
        plot.setRangeAxis(1, oiAxis);
        plot.setRangeAxisLocation(1, org.jfree.chart.axis.AxisLocation.BOTTOM_OR_LEFT);
        plot.mapDatasetToRangeAxis(1, 1);

        // Add zero line and title
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Open Interest (B)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addZeroLine(plot, startTime, endTime);
        }
    }

    // ===== Premium Index Methods =====

    public void setPremiumChartEnabled(boolean enabled) {
        setEnabled(IndicatorType.PREMIUM, enabled);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isPremiumChartEnabled() {
        return isEnabled(IndicatorType.PREMIUM);
    }

    public void updatePremiumChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.PREMIUM) || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = components.get(IndicatorType.PREMIUM).getChart().getXYPlot();

        double[] premium = indicatorEngine.getPremium();
        double[] premiumAvg = indicatorEngine.getPremiumAvg(24); // 24-period average

        // Check if premium data is available
        if (premium == null || premium.length == 0) {
            return;
        }

        // Build datasets
        XYSeriesCollection premiumDataset = new XYSeriesCollection();
        XYSeries premiumSeries = new XYSeries("Premium");

        TimeSeriesCollection avgDataset = new TimeSeriesCollection();
        TimeSeries avgSeries = new TimeSeries("Premium 24 Avg");

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (i < premium.length && !Double.isNaN(premium[i])) {
                premiumSeries.add(c.timestamp(), premium[i]);
            }
            if (i < premiumAvg.length && !Double.isNaN(premiumAvg[i])) {
                avgSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), premiumAvg[i]);
            }
        }

        premiumDataset.addSeries(premiumSeries);
        avgDataset.addSeries(avgSeries);

        // Premium bars (green for positive, red for negative)
        plot.setDataset(0, premiumDataset);
        plot.setDataset(1, avgDataset);

        plot.setRenderer(0, colorCodedBarRenderer(premiumDataset, ChartStyles.PREMIUM_POSITIVE, ChartStyles.PREMIUM_NEGATIVE));
        plot.setRenderer(1, lineRenderer(ChartStyles.PREMIUM_AVG_COLOR));

        // Add zero line and title
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Premium Index (%)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addZeroLine(plot, startTime, endTime);
        }
    }

    /**
     * Update premium chart using directly loaded premium data.
     * This is called when premium data is loaded from PremiumPageManager.
     */
    private void updatePremiumChartFromData(List<Candle> candles) {
        if (!isEnabled(IndicatorType.PREMIUM) || candles == null || candles.isEmpty() || currentPremiumData == null) {
            return;
        }

        XYPlot plot = components.get(IndicatorType.PREMIUM).getChart().getXYPlot();

        // Build timestamp -> premium map for efficient lookup
        java.util.Map<Long, PremiumIndex> premiumMap = new java.util.HashMap<>();
        for (PremiumIndex p : currentPremiumData) {
            premiumMap.put(p.openTime(), p);
        }

        // Build datasets
        XYSeriesCollection premiumDataset = new XYSeriesCollection();
        XYSeries premiumSeries = new XYSeries("Premium");

        TimeSeriesCollection avgDataset = new TimeSeriesCollection();
        TimeSeries avgSeries = new TimeSeries("Premium 24 Avg");

        // Calculate premium values and 24-period average
        double[] premiumValues = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            PremiumIndex p = premiumMap.get(c.timestamp());
            if (p != null) {
                // Convert to percentage (close is the premium value at bar close)
                premiumValues[i] = p.close() * 100.0;
            } else {
                premiumValues[i] = Double.NaN;
            }
        }

        // Build series
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(premiumValues[i])) {
                premiumSeries.add(c.timestamp(), premiumValues[i]);
            }

            // Calculate 24-period average
            if (i >= 23) {
                double sum = 0;
                int count = 0;
                for (int j = i - 23; j <= i; j++) {
                    if (!Double.isNaN(premiumValues[j])) {
                        sum += premiumValues[j];
                        count++;
                    }
                }
                if (count > 0) {
                    avgSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), sum / count);
                }
            }
        }

        premiumDataset.addSeries(premiumSeries);
        avgDataset.addSeries(avgSeries);

        plot.setDataset(0, premiumDataset);
        plot.setDataset(1, avgDataset);

        plot.setRenderer(0, colorCodedBarRenderer(premiumDataset, ChartStyles.PREMIUM_POSITIVE, ChartStyles.PREMIUM_NEGATIVE));
        plot.setRenderer(1, lineRenderer(ChartStyles.PREMIUM_AVG_COLOR));

        // Add zero line and title
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Premium Index (%)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addZeroLine(plot, startTime, endTime);
        }
    }

    // ===== Holding Cost Chart Methods =====

    /**
     * Set trades for holding cost charts.
     * Call this when trades are available (after backtest).
     */
    public void setTrades(List<Trade> trades) {
        this.currentTrades = trades;
    }

    public boolean isHoldingCostCumulativeChartEnabled() {
        return isEnabled(IndicatorType.HOLDING_COST_CUMULATIVE);
    }

    public boolean isHoldingCostEventsChartEnabled() {
        return isEnabled(IndicatorType.HOLDING_COST_EVENTS);
    }

    public void setHoldingCostCumulativeChartEnabled(boolean enabled) {
        setEnabled(IndicatorType.HOLDING_COST_CUMULATIVE, enabled);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public void setHoldingCostEventsChartEnabled(boolean enabled) {
        setEnabled(IndicatorType.HOLDING_COST_EVENTS, enabled);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    /**
     * Update cumulative holding costs chart.
     * Shows running total of holding costs over time.
     */
    public void updateHoldingCostCumulativeChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.HOLDING_COST_CUMULATIVE) || candles == null || candles.isEmpty() || currentTrades == null) {
            return;
        }

        XYPlot plot = components.get(IndicatorType.HOLDING_COST_CUMULATIVE).getChart().getXYPlot();

        // Build timestamp -> holding cost map from trades
        java.util.Map<Long, Double> holdingCostByTime = new java.util.TreeMap<>();
        for (Trade t : currentTrades) {
            if (t.exitTime() != null && t.holdingCosts() != null) {
                holdingCostByTime.merge(t.exitTime(), t.holdingCosts(), Double::sum);
            }
        }

        // Build cumulative series
        TimeSeries cumulativeSeries = new TimeSeries("Cumulative Holding Costs");
        double cumulative = 0;

        for (Candle c : candles) {
            if (holdingCostByTime.containsKey(c.timestamp())) {
                cumulative += holdingCostByTime.get(c.timestamp());
            }
            cumulativeSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), cumulative);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(cumulativeSeries);

        plot.setDataset(dataset);

        // Line renderer - red for costs, green for earnings
        plot.setRenderer(lineRenderer(cumulative >= 0 ? ChartStyles.LOSS_COLOR : ChartStyles.WIN_COLOR));

        // Add zero line
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Cumulative Holding Costs ($)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addZeroLine(plot, startTime, endTime);
        }
    }

    /**
     * Update holding cost events chart.
     * Shows individual holding cost spikes at trade exit times.
     */
    public void updateHoldingCostEventsChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.HOLDING_COST_EVENTS) || candles == null || candles.isEmpty() || currentTrades == null) {
            return;
        }

        XYPlot plot = components.get(IndicatorType.HOLDING_COST_EVENTS).getChart().getXYPlot();

        // Build separate series for costs (positive) and earnings (negative)
        XYSeries costsSeries = new XYSeries("Costs");
        XYSeries earningsSeries = new XYSeries("Earnings");

        for (Trade t : currentTrades) {
            if (t.exitTime() != null && t.holdingCosts() != null && t.holdingCosts() != 0) {
                if (t.holdingCosts() > 0) {
                    costsSeries.add(t.exitTime(), t.holdingCosts());
                } else {
                    earningsSeries.add(t.exitTime(), t.holdingCosts());
                }
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(costsSeries);
        dataset.addSeries(earningsSeries);

        plot.setDataset(dataset);

        // Bar renderer - costs red, earnings green
        plot.setRenderer(barRenderer(ChartStyles.LOSS_COLOR, ChartStyles.WIN_COLOR));

        // Add zero line
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Holding Cost Events ($)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addZeroLine(plot, startTime, endTime);
        }
    }

    // ===== Stochastic Methods =====

    public void setStochasticChartEnabled(boolean enabled, int kPeriod, int dPeriod) {
        setEnabled(IndicatorType.STOCHASTIC, enabled);
        this.stochasticKPeriod = kPeriod;
        this.stochasticDPeriod = dPeriod;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isStochasticChartEnabled() {
        return isEnabled(IndicatorType.STOCHASTIC);
    }

    public void updateStochasticChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.STOCHASTIC) || candles == null || candles.size() < stochasticKPeriod + stochasticDPeriod) {
            return;
        }
        // Subscribe to Stochastic data (will arrive via callback if not ready)
        indicatorDataService.subscribeStochastic(stochasticKPeriod, stochasticDPeriod);
        // Try to render with available data
        redrawStochasticChart(candles);
    }

    private void redrawStochasticChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.STOCHASTIC) || candles == null || candles.size() < stochasticKPeriod + stochasticDPeriod) {
            return;
        }

        // Get pre-computed Stochastic data from service
        Indicators.StochasticResult result = indicatorDataService.getStochastic(stochasticKPeriod, stochasticDPeriod);
        if (result == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.STOCHASTIC).getChart().getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries kSeries = new TimeSeries("%K(" + stochasticKPeriod + ")");
        TimeSeries dSeries = new TimeSeries("%D(" + stochasticDPeriod + ")");

        double[] kValues = result.k();
        double[] dValues = result.d();

        for (int i = stochasticKPeriod - 1; i < candles.size() && i < kValues.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(kValues[i])) {
                kSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), kValues[i]);
            }
            if (!Double.isNaN(dValues[i])) {
                dSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), dValues[i]);
            }
        }

        dataset.addSeries(kSeries);
        dataset.addSeries(dSeries);
        plot.setDataset(dataset);

        // Style the lines with distinct colors
        plot.setRenderer(lineRenderer(
            ChartStyles.STOCHASTIC_K_COLOR, ChartStyles.MEDIUM_STROKE,
            ChartStyles.STOCHASTIC_D_COLOR, ChartStyles.MEDIUM_STROKE));

        // Add reference lines at 20, 50, 80
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Stochastic");

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addStochasticLines(plot, startTime, endTime);
        }
    }

    // ===== Range Position Methods =====

    public void setRangePositionChartEnabled(boolean enabled, int period, int skip) {
        setEnabled(IndicatorType.RANGE_POSITION, enabled);
        this.rangePositionPeriod = period;
        this.rangePositionSkip = skip;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isRangePositionChartEnabled() {
        return isEnabled(IndicatorType.RANGE_POSITION);
    }

    public void updateRangePositionChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.RANGE_POSITION) || candles == null || candles.size() < rangePositionPeriod + rangePositionSkip + 1) {
            return;
        }
        // Subscribe to Range Position data (will arrive via callback if not ready)
        indicatorDataService.subscribeRangePosition(rangePositionPeriod, rangePositionSkip);
        // Try to render with available data
        redrawRangePositionChart(candles);
    }

    private void redrawRangePositionChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.RANGE_POSITION) || candles == null || candles.size() < rangePositionPeriod + rangePositionSkip + 1) {
            return;
        }

        // Get pre-computed Range Position data from service
        double[] values = indicatorDataService.getRangePosition(rangePositionPeriod, rangePositionSkip);
        if (values == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.RANGE_POSITION).getChart().getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        String label = rangePositionSkip > 0
            ? "RANGE_POSITION(" + rangePositionPeriod + "," + rangePositionSkip + ")"
            : "RANGE_POSITION(" + rangePositionPeriod + ")";
        TimeSeries series = new TimeSeries(label);

        for (int i = rangePositionPeriod + rangePositionSkip; i < candles.size() && i < values.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(values[i])) {
                series.addOrUpdate(new Millisecond(new Date(c.timestamp())), values[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(dataset);

        // Style the line - color changes based on position (breakout = highlighted)
        plot.setRenderer(colorCodedLineRenderer(dataset,
            ChartStyles.DELTA_POSITIVE,  // Green - above range (breakout)
            ChartStyles.DELTA_NEGATIVE,  // Red - below range (breakdown)
            ChartStyles.ATR_COLOR,       // Normal - within range
            1.0));

        // Add reference lines at -1, 0, +1
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Range Position");

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addRangePositionLines(plot, startTime, endTime);
        }
    }

    // ===== ADX Methods =====

    public void setAdxChartEnabled(boolean enabled, int period) {
        setEnabled(IndicatorType.ADX, enabled);
        this.adxPeriod = period;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isAdxChartEnabled() {
        return isEnabled(IndicatorType.ADX);
    }

    public void updateAdxChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.ADX) || candles == null || candles.size() < adxPeriod * 2) {
            return;
        }
        // Subscribe to ADX, +DI, -DI data (will arrive via callback if not ready)
        indicatorDataService.subscribeADX(adxPeriod);
        indicatorDataService.subscribePlusDI(adxPeriod);
        indicatorDataService.subscribeMinusDI(adxPeriod);
        // Try to render with available data
        redrawAdxChart(candles);
    }

    private void redrawAdxChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.ADX) || candles == null || candles.size() < adxPeriod * 2) {
            return;
        }

        // Get pre-computed data from service
        double[] adxValues = indicatorDataService.getADX(adxPeriod);
        double[] plusDIValues = indicatorDataService.getPlusDI(adxPeriod);
        double[] minusDIValues = indicatorDataService.getMinusDI(adxPeriod);
        if (adxValues == null || plusDIValues == null || minusDIValues == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.ADX).getChart().getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries adxSeries = new TimeSeries("ADX(" + adxPeriod + ")");
        TimeSeries plusDISeries = new TimeSeries("+DI(" + adxPeriod + ")");
        TimeSeries minusDISeries = new TimeSeries("-DI(" + adxPeriod + ")");

        for (int i = adxPeriod * 2 - 1; i < candles.size() && i < adxValues.length; i++) {
            Candle c = candles.get(i);
            Millisecond ms = new Millisecond(new Date(c.timestamp()));
            if (!Double.isNaN(adxValues[i])) {
                adxSeries.addOrUpdate(ms, adxValues[i]);
            }
            if (!Double.isNaN(plusDIValues[i])) {
                plusDISeries.addOrUpdate(ms, plusDIValues[i]);
            }
            if (!Double.isNaN(minusDIValues[i])) {
                minusDISeries.addOrUpdate(ms, minusDIValues[i]);
            }
        }

        dataset.addSeries(adxSeries);
        dataset.addSeries(plusDISeries);
        dataset.addSeries(minusDISeries);
        plot.setDataset(dataset);

        // Style the lines
        plot.setRenderer(lineRenderer(
            ChartStyles.ADX_COLOR, ChartStyles.MEDIUM_STROKE,
            ChartStyles.DELTA_POSITIVE, ChartStyles.THIN_STROKE,
            ChartStyles.DELTA_NEGATIVE, ChartStyles.THIN_STROKE));

        // Add reference lines at 20, 25 (common ADX thresholds)
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "ADX");

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addAdxLines(plot, startTime, endTime);
        }
    }

    // ===== Trade Count Methods =====

    public void setTradeCountChartEnabled(boolean enabled) {
        setEnabled(IndicatorType.TRADE_COUNT, enabled);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isTradeCountChartEnabled() {
        return isEnabled(IndicatorType.TRADE_COUNT);
    }

    public void updateTradeCountChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.TRADE_COUNT) || candles == null || candles.isEmpty()) {
            return;
        }
        // Subscribe to Trade Count data (will arrive via callback if not ready)
        indicatorDataService.subscribeTradeCount();
        // Try to render with available data
        redrawTradeCountChart(candles);
    }

    private void redrawTradeCountChart(List<Candle> candles) {
        if (!isEnabled(IndicatorType.TRADE_COUNT) || candles == null || candles.isEmpty()) {
            return;
        }

        // Get pre-computed Trade Count data from service
        double[] tradeCount = indicatorDataService.getTradeCount();
        if (tradeCount == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = components.get(IndicatorType.TRADE_COUNT).getChart().getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries series = new TimeSeries("Trade Count");

        for (int i = 0; i < candles.size() && i < tradeCount.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(tradeCount[i])) {
                series.addOrUpdate(new Millisecond(new Date(c.timestamp())), tradeCount[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(0, dataset);

        plot.setRenderer(0, lineRenderer(ChartStyles.TRADE_COUNT_LINE_COLOR));

        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Trade Count");
    }

    // ===== Accessors =====

    // Generic accessors (preferred for new code)
    public JFreeChart getChart(IndicatorType type) { return components.get(type).getChart(); }
    public org.jfree.chart.ChartPanel getChartPanel(IndicatorType type) { return components.get(type).getChartPanel(); }
    public JPanel getChartWrapper(IndicatorType type) { return components.get(type).getWrapper(); }

    // Legacy accessors for backward compatibility
    public JFreeChart getRsiChart() { return getChart(IndicatorType.RSI); }
    public JFreeChart getMacdChart() { return getChart(IndicatorType.MACD); }
    public JFreeChart getAtrChart() { return getChart(IndicatorType.ATR); }
    public JFreeChart getDeltaChart() { return getChart(IndicatorType.DELTA); }
    public JFreeChart getCvdChart() { return getChart(IndicatorType.CVD); }
    public JFreeChart getVolumeRatioChart() { return getChart(IndicatorType.VOLUME_RATIO); }
    public JFreeChart getFundingChart() { return getChart(IndicatorType.FUNDING); }
    public JFreeChart getWhaleChart() { return getChart(IndicatorType.WHALE); }
    public JFreeChart getRetailChart() { return getChart(IndicatorType.RETAIL); }
    public JFreeChart getOiChart() { return getChart(IndicatorType.OI); }
    public JFreeChart getStochasticChart() { return getChart(IndicatorType.STOCHASTIC); }
    public JFreeChart getRangePositionChart() { return getChart(IndicatorType.RANGE_POSITION); }
    public JFreeChart getAdxChart() { return getChart(IndicatorType.ADX); }
    public JFreeChart getTradeCountChart() { return getChart(IndicatorType.TRADE_COUNT); }
    public JFreeChart getPremiumChart() { return getChart(IndicatorType.PREMIUM); }
    public JFreeChart getHoldingCostCumulativeChart() { return getChart(IndicatorType.HOLDING_COST_CUMULATIVE); }
    public JFreeChart getHoldingCostEventsChart() { return getChart(IndicatorType.HOLDING_COST_EVENTS); }

    public org.jfree.chart.ChartPanel getRsiChartPanel() { return getChartPanel(IndicatorType.RSI); }
    public org.jfree.chart.ChartPanel getMacdChartPanel() { return getChartPanel(IndicatorType.MACD); }
    public org.jfree.chart.ChartPanel getAtrChartPanel() { return getChartPanel(IndicatorType.ATR); }
    public org.jfree.chart.ChartPanel getDeltaChartPanel() { return getChartPanel(IndicatorType.DELTA); }
    public org.jfree.chart.ChartPanel getCvdChartPanel() { return getChartPanel(IndicatorType.CVD); }
    public org.jfree.chart.ChartPanel getVolumeRatioChartPanel() { return getChartPanel(IndicatorType.VOLUME_RATIO); }
    public org.jfree.chart.ChartPanel getFundingChartPanel() { return getChartPanel(IndicatorType.FUNDING); }
    public org.jfree.chart.ChartPanel getWhaleChartPanel() { return getChartPanel(IndicatorType.WHALE); }
    public org.jfree.chart.ChartPanel getRetailChartPanel() { return getChartPanel(IndicatorType.RETAIL); }
    public org.jfree.chart.ChartPanel getOiChartPanel() { return getChartPanel(IndicatorType.OI); }
    public org.jfree.chart.ChartPanel getStochasticChartPanel() { return getChartPanel(IndicatorType.STOCHASTIC); }
    public org.jfree.chart.ChartPanel getRangePositionChartPanel() { return getChartPanel(IndicatorType.RANGE_POSITION); }
    public org.jfree.chart.ChartPanel getAdxChartPanel() { return getChartPanel(IndicatorType.ADX); }
    public org.jfree.chart.ChartPanel getTradeCountChartPanel() { return getChartPanel(IndicatorType.TRADE_COUNT); }
    public org.jfree.chart.ChartPanel getPremiumChartPanel() { return getChartPanel(IndicatorType.PREMIUM); }
    public org.jfree.chart.ChartPanel getHoldingCostCumulativeChartPanel() { return getChartPanel(IndicatorType.HOLDING_COST_CUMULATIVE); }
    public org.jfree.chart.ChartPanel getHoldingCostEventsChartPanel() { return getChartPanel(IndicatorType.HOLDING_COST_EVENTS); }

    public JPanel getRsiChartWrapper() { return getChartWrapper(IndicatorType.RSI); }
    public JPanel getMacdChartWrapper() { return getChartWrapper(IndicatorType.MACD); }
    public JPanel getAtrChartWrapper() { return getChartWrapper(IndicatorType.ATR); }
    public JPanel getDeltaChartWrapper() { return getChartWrapper(IndicatorType.DELTA); }
    public JPanel getCvdChartWrapper() { return getChartWrapper(IndicatorType.CVD); }
    public JPanel getVolumeRatioChartWrapper() { return getChartWrapper(IndicatorType.VOLUME_RATIO); }
    public JPanel getWhaleChartWrapper() { return getChartWrapper(IndicatorType.WHALE); }
    public JPanel getRetailChartWrapper() { return getChartWrapper(IndicatorType.RETAIL); }
    public JPanel getFundingChartWrapper() { return getChartWrapper(IndicatorType.FUNDING); }
    public JPanel getOiChartWrapper() { return getChartWrapper(IndicatorType.OI); }
    public JPanel getStochasticChartWrapper() { return getChartWrapper(IndicatorType.STOCHASTIC); }
    public JPanel getRangePositionChartWrapper() { return getChartWrapper(IndicatorType.RANGE_POSITION); }
    public JPanel getAdxChartWrapper() { return getChartWrapper(IndicatorType.ADX); }
    public JPanel getTradeCountChartWrapper() { return getChartWrapper(IndicatorType.TRADE_COUNT); }
    public JPanel getPremiumChartWrapper() { return getChartWrapper(IndicatorType.PREMIUM); }
    public JPanel getHoldingCostCumulativeChartWrapper() { return getChartWrapper(IndicatorType.HOLDING_COST_CUMULATIVE); }
    public JPanel getHoldingCostEventsChartWrapper() { return getChartWrapper(IndicatorType.HOLDING_COST_EVENTS); }

    public JButton getZoomButton(IndicatorType type) { return components.get(type).getZoomButton(); }

    // Legacy zoom button accessors
    public JButton getRsiZoomBtn() { return getZoomButton(IndicatorType.RSI); }
    public JButton getMacdZoomBtn() { return getZoomButton(IndicatorType.MACD); }
    public JButton getAtrZoomBtn() { return getZoomButton(IndicatorType.ATR); }
    public JButton getDeltaZoomBtn() { return getZoomButton(IndicatorType.DELTA); }
    public JButton getCvdZoomBtn() { return getZoomButton(IndicatorType.CVD); }
    public JButton getVolumeRatioZoomBtn() { return getZoomButton(IndicatorType.VOLUME_RATIO); }
    public JButton getWhaleZoomBtn() { return getZoomButton(IndicatorType.WHALE); }
    public JButton getRetailZoomBtn() { return getZoomButton(IndicatorType.RETAIL); }
    public JButton getFundingZoomBtn() { return getZoomButton(IndicatorType.FUNDING); }
    public JButton getOiZoomBtn() { return getZoomButton(IndicatorType.OI); }
    public JButton getStochasticZoomBtn() { return getZoomButton(IndicatorType.STOCHASTIC); }
    public JButton getRangePositionZoomBtn() { return getZoomButton(IndicatorType.RANGE_POSITION); }
    public JButton getAdxZoomBtn() { return getZoomButton(IndicatorType.ADX); }
    public JButton getTradeCountZoomBtn() { return getZoomButton(IndicatorType.TRADE_COUNT); }

    /**
     * Update zoom button states.
     * @param zoomedIndex Index of zoomed indicator (-1 for none, uses IndicatorType ordinal)
     */
    public void updateZoomButtonStates(int zoomedIndex) {
        for (IndicatorType type : IndicatorType.values()) {
            components.get(type).setZoomed(type.ordinal() == zoomedIndex);
        }
    }

    /**
     * Update full screen button states.
     * @param fullScreenIndex Index of full screen indicator (-1 for none, uses IndicatorType ordinal)
     */
    public void updateFullScreenButtonStates(int fullScreenIndex) {
        for (IndicatorType type : IndicatorType.values()) {
            components.get(type).setFullScreen(type.ordinal() == fullScreenIndex);
        }
    }

    /**
     * Update close button visibility (only visible for the full-screen chart).
     * @param fullScreenIndex Index of full screen indicator (-1 for none, uses IndicatorType ordinal)
     */
    public void updateCloseButtonVisibility(int fullScreenIndex) {
        for (IndicatorType type : IndicatorType.values()) {
            components.get(type).setCloseButtonVisible(type.ordinal() == fullScreenIndex);
        }
    }

    /**
     * Add mouse wheel listener to all chart panels.
     */
    public void addMouseWheelListener(java.awt.event.MouseWheelListener listener) {
        for (ChartComponent comp : components.values()) {
            comp.getChartPanel().addMouseWheelListener(listener);
        }
    }

    /**
     * Remove mouse wheel listener from all chart panels.
     */
    public void removeMouseWheelListener(java.awt.event.MouseWheelListener listener) {
        for (ChartComponent comp : components.values()) {
            comp.getChartPanel().removeMouseWheelListener(listener);
        }
    }

    /**
     * Dispose of all resources and clear callbacks.
     */
    public void dispose() {
        this.onLayoutChange = null;
        this.indicatorEngine = null;
    }

    /**
     * Update all enabled indicator charts with new candle data.
     */
    public void updateCharts(List<Candle> candles) {
        updateRsiChart(candles);
        updateMacdChart(candles);
        updateAtrChart(candles);
        updateDeltaChart(candles);
        updateCvdChart(candles);
        updateVolumeRatioChart(candles);
        updateWhaleChart(candles);
        updateRetailChart(candles);
        updateFundingChart(candles);
        updateOiChart(candles);
        // Premium chart updated via PremiumPageManager callback - not here (EDT would block)
        updateStochasticChart(candles);
        updateRangePositionChart(candles);
        updateAdxChart(candles);
        updateTradeCountChart(candles);
        updateHoldingCostCumulativeChart(candles);
        updateHoldingCostEventsChart(candles);
    }

    /**
     * Update Y-axis auto-range for indicator charts.
     */
    public void updateYAxisAutoRange(boolean fitYAxisToVisible) {
        // Standard auto-range indicators
        IndicatorType[] standardTypes = {IndicatorType.MACD, IndicatorType.ATR, IndicatorType.DELTA, IndicatorType.FUNDING};
        for (IndicatorType type : standardTypes) {
            JFreeChart chart = components.get(type).getChart();
            XYPlot plot = chart.getXYPlot();
            plot.getRangeAxis().setAutoRange(true);
            if (fitYAxisToVisible) {
                plot.configureRangeAxes();
            }
        }

        // RSI: fixed 0-100 range in Full Y mode, auto in Fit Y mode
        ChartComponent rsiComp = components.get(IndicatorType.RSI);
        if (fitYAxisToVisible) {
            rsiComp.getChart().getXYPlot().getRangeAxis().setAutoRange(true);
            rsiComp.getChart().getXYPlot().configureRangeAxes();
        } else {
            rsiComp.getChart().getXYPlot().getRangeAxis().setAutoRange(false);
            rsiComp.getChart().getXYPlot().getRangeAxis().setRange(0, 100);
        }
    }
}
