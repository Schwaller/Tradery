package com.tradery.ui.charts;

import com.tradery.indicators.IndicatorEngine;
import com.tradery.indicators.Indicators;
import com.tradery.model.Candle;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
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
import java.util.Date;
import java.util.List;

/**
 * Manages optional indicator charts (RSI, MACD, ATR, Orderflow, Funding).
 */
public class IndicatorChartsManager {

    // Chart components (each contains chart, panel, wrapper, zoom button)
    private ChartComponent rsiComponent;
    private ChartComponent macdComponent;
    private ChartComponent atrComponent;
    private ChartComponent deltaComponent;
    private ChartComponent cvdComponent;
    private ChartComponent volumeRatioComponent;
    private ChartComponent whaleComponent;
    private ChartComponent retailComponent;
    private ChartComponent fundingComponent;
    private ChartComponent oiComponent;
    private ChartComponent stochasticComponent;
    private ChartComponent rangePositionComponent;
    private ChartComponent adxComponent;
    private ChartComponent tradeCountComponent;
    private ChartComponent premiumComponent;

    // Enable state
    private boolean rsiChartEnabled = false;
    private boolean macdChartEnabled = false;
    private boolean atrChartEnabled = false;
    private boolean deltaChartEnabled = false;
    private boolean cvdChartEnabled = false;
    private boolean volumeRatioChartEnabled = false;
    private boolean whaleChartEnabled = false;
    private boolean retailChartEnabled = false;
    private boolean fundingChartEnabled = false;
    private boolean oiChartEnabled = false;
    private boolean stochasticChartEnabled = false;
    private boolean rangePositionChartEnabled = false;
    private boolean adxChartEnabled = false;
    private boolean tradeCountChartEnabled = false;
    private boolean premiumChartEnabled = false;

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

    // IndicatorEngine for orderflow/funding data
    private IndicatorEngine indicatorEngine;

    // IndicatorDataService for background indicator computation
    private final IndicatorDataService indicatorDataService = new IndicatorDataService();

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
        // Update context in the service
        indicatorDataService.setDataContext(candles, symbol, timeframe, startTime, endTime);

        // Subscribe to enabled indicators
        if (rsiChartEnabled) {
            indicatorDataService.subscribeRSI(rsiPeriod);
        }
        if (macdChartEnabled) {
            indicatorDataService.subscribeMACD(macdFast, macdSlow, macdSignal);
        }
        if (atrChartEnabled) {
            indicatorDataService.subscribeATR(atrPeriod);
        }
        if (stochasticChartEnabled) {
            indicatorDataService.subscribeStochastic(stochasticKPeriod, stochasticDPeriod);
        }
        if (adxChartEnabled) {
            indicatorDataService.subscribeADX(adxPeriod);
            indicatorDataService.subscribePlusDI(adxPeriod);
            indicatorDataService.subscribeMinusDI(adxPeriod);
        }
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
        }
    }

    private void initializeCharts() {
        rsiComponent = new ChartComponent("RSI", new double[]{0, 100});
        macdComponent = new ChartComponent("MACD");
        atrComponent = new ChartComponent("ATR");
        deltaComponent = new ChartComponent("Delta");
        cvdComponent = new ChartComponent("CVD");
        volumeRatioComponent = new ChartComponent("Buy/Sell Volume");
        whaleComponent = new ChartComponent("Whale Delta");
        retailComponent = new ChartComponent("Retail Delta");
        fundingComponent = new ChartComponent("Funding");
        oiComponent = new ChartComponent("Open Interest");
        stochasticComponent = new ChartComponent("Stochastic", new double[]{0, 100});
        rangePositionComponent = new ChartComponent("Range Position", new double[]{-2, 2});
        adxComponent = new ChartComponent("ADX", new double[]{0, 100});
        tradeCountComponent = new ChartComponent("Trade Count");
        premiumComponent = new ChartComponent("Premium Index");
    }

    /**
     * Create wrapper panels with zoom buttons.
     * @param zoomCallback Callback to handle zoom toggle (index: 0=RSI, 1=MACD, 2=ATR, 3=Delta, 4=CVD, 5=VolumeRatio, 6=Whale, 7=Retail, 8=Funding, 9=OI, 10=Stochastic, 11=RangePosition, 12=ADX, 13=TradeCount)
     */
    public void createWrappers(java.util.function.IntConsumer zoomCallback) {
        createWrappers(zoomCallback, null);
    }

    /**
     * Create wrapper panels with zoom and full screen buttons.
     * @param zoomCallback Callback to handle zoom toggle
     * @param fullScreenCallback Callback to handle full screen toggle
     */
    public void createWrappers(java.util.function.IntConsumer zoomCallback, java.util.function.IntConsumer fullScreenCallback) {
        Runnable fs0 = fullScreenCallback != null ? () -> fullScreenCallback.accept(0) : null;
        Runnable fs1 = fullScreenCallback != null ? () -> fullScreenCallback.accept(1) : null;
        Runnable fs2 = fullScreenCallback != null ? () -> fullScreenCallback.accept(2) : null;
        Runnable fs3 = fullScreenCallback != null ? () -> fullScreenCallback.accept(3) : null;
        Runnable fs4 = fullScreenCallback != null ? () -> fullScreenCallback.accept(4) : null;
        Runnable fs5 = fullScreenCallback != null ? () -> fullScreenCallback.accept(5) : null;
        Runnable fs6 = fullScreenCallback != null ? () -> fullScreenCallback.accept(6) : null;
        Runnable fs7 = fullScreenCallback != null ? () -> fullScreenCallback.accept(7) : null;
        Runnable fs8 = fullScreenCallback != null ? () -> fullScreenCallback.accept(8) : null;
        Runnable fs9 = fullScreenCallback != null ? () -> fullScreenCallback.accept(9) : null;
        Runnable fs10 = fullScreenCallback != null ? () -> fullScreenCallback.accept(10) : null;
        Runnable fs11 = fullScreenCallback != null ? () -> fullScreenCallback.accept(11) : null;
        Runnable fs12 = fullScreenCallback != null ? () -> fullScreenCallback.accept(12) : null;
        Runnable fs13 = fullScreenCallback != null ? () -> fullScreenCallback.accept(13) : null;
        Runnable fs14 = fullScreenCallback != null ? () -> fullScreenCallback.accept(14) : null;

        rsiComponent.createWrapper(() -> zoomCallback.accept(0), fs0);
        macdComponent.createWrapper(() -> zoomCallback.accept(1), fs1);
        atrComponent.createWrapper(() -> zoomCallback.accept(2), fs2);
        deltaComponent.createWrapper(() -> zoomCallback.accept(3), fs3);
        cvdComponent.createWrapper(() -> zoomCallback.accept(4), fs4);
        volumeRatioComponent.createWrapper(() -> zoomCallback.accept(5), fs5);
        whaleComponent.createWrapper(() -> zoomCallback.accept(6), fs6);
        retailComponent.createWrapper(() -> zoomCallback.accept(7), fs7);
        fundingComponent.createWrapper(() -> zoomCallback.accept(8), fs8);
        oiComponent.createWrapper(() -> zoomCallback.accept(9), fs9);
        stochasticComponent.createWrapper(() -> zoomCallback.accept(10), fs10);
        rangePositionComponent.createWrapper(() -> zoomCallback.accept(11), fs11);
        adxComponent.createWrapper(() -> zoomCallback.accept(12), fs12);
        tradeCountComponent.createWrapper(() -> zoomCallback.accept(13), fs13);
        premiumComponent.createWrapper(() -> zoomCallback.accept(14), fs14);
    }

    // ===== RSI Methods =====

    public void setRsiChartEnabled(boolean enabled, int period) {
        this.rsiChartEnabled = enabled;
        this.rsiPeriod = period;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isRsiChartEnabled() {
        return rsiChartEnabled;
    }

    public void updateRsiChart(List<Candle> candles) {
        if (!rsiChartEnabled || candles == null || candles.size() < rsiPeriod + 1) {
            return;
        }
        // Subscribe to RSI data (will arrive via callback if not ready)
        indicatorDataService.subscribeRSI(rsiPeriod);
        // Try to render with available data
        redrawRsiChart(candles);
    }

    private void redrawRsiChart(List<Candle> candles) {
        if (!rsiChartEnabled || candles == null || candles.size() < rsiPeriod + 1) {
            return;
        }

        // Get pre-computed RSI data from service
        double[] rsiValues = indicatorDataService.getRSI(rsiPeriod);
        if (rsiValues == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = rsiComponent.getChart().getXYPlot();
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
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.RSI_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(renderer);

        // Add reference lines at 30, 50, 70
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "RSI");

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();

            plot.addAnnotation(new XYLineAnnotation(startTime, 30, endTime, 30,
                ChartStyles.DASHED_STROKE, ChartStyles.RSI_OVERSOLD));
            plot.addAnnotation(new XYLineAnnotation(startTime, 50, endTime, 50,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
            plot.addAnnotation(new XYLineAnnotation(startTime, 70, endTime, 70,
                ChartStyles.DASHED_STROKE, ChartStyles.RSI_OVERBOUGHT));
        }
    }

    // ===== MACD Methods =====

    public void setMacdChartEnabled(boolean enabled, int fast, int slow, int signal) {
        this.macdChartEnabled = enabled;
        this.macdFast = fast;
        this.macdSlow = slow;
        this.macdSignal = signal;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isMacdChartEnabled() {
        return macdChartEnabled;
    }

    public void updateMacdChart(List<Candle> candles) {
        if (!macdChartEnabled || candles == null || candles.size() < macdSlow + macdSignal) {
            return;
        }
        // Subscribe to MACD data (will arrive via callback if not ready)
        indicatorDataService.subscribeMACD(macdFast, macdSlow, macdSignal);
        // Try to render with available data
        redrawMacdChart(candles);
    }

    private void redrawMacdChart(List<Candle> candles) {
        if (!macdChartEnabled || candles == null || candles.size() < macdSlow + macdSignal) {
            return;
        }

        // Get pre-computed MACD data from service
        Indicators.MACDResult macdResult = indicatorDataService.getMACD(macdFast, macdSlow, macdSignal);
        if (macdResult == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = macdComponent.getChart().getXYPlot();
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
        XYBarRenderer histRenderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = histogramDataset.getYValue(series, item);
                return value >= 0 ? ChartStyles.MACD_HIST_POS : ChartStyles.MACD_HIST_NEG;
            }
        };
        histRenderer.setShadowVisible(false);
        histRenderer.setBarPainter(new StandardXYBarPainter());
        plot.setRenderer(0, histRenderer);

        // Line renderer for MACD and signal
        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        lineRenderer.setSeriesPaint(0, ChartStyles.MACD_LINE_COLOR);
        lineRenderer.setSeriesPaint(1, ChartStyles.MACD_SIGNAL_COLOR);
        lineRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        lineRenderer.setSeriesStroke(1, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(1, lineRenderer);

        // Add zero line
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "MACD");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
        }
    }

    // ===== ATR Methods =====

    public void setAtrChartEnabled(boolean enabled, int period) {
        this.atrChartEnabled = enabled;
        this.atrPeriod = period;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isAtrChartEnabled() {
        return atrChartEnabled;
    }

    public void updateAtrChart(List<Candle> candles) {
        if (!atrChartEnabled || candles == null || candles.size() < atrPeriod + 1) {
            return;
        }
        // Subscribe to ATR data (will arrive via callback if not ready)
        indicatorDataService.subscribeATR(atrPeriod);
        // Try to render with available data
        redrawAtrChart(candles);
    }

    private void redrawAtrChart(List<Candle> candles) {
        if (!atrChartEnabled || candles == null || candles.size() < atrPeriod + 1) {
            return;
        }

        // Get pre-computed ATR data from service
        double[] atrValues = indicatorDataService.getATR(atrPeriod);
        if (atrValues == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = atrComponent.getChart().getXYPlot();
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
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.ATR_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(renderer);

        // Clear annotations and re-add title
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "ATR");
    }

    // ===== Delta Methods =====

    public void setIndicatorEngine(IndicatorEngine engine) {
        this.indicatorEngine = engine;
    }

    public void setDeltaChartEnabled(boolean enabled) {
        this.deltaChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isDeltaChartEnabled() {
        return deltaChartEnabled;
    }

    public void setCvdChartEnabled(boolean enabled) {
        this.cvdChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isCvdChartEnabled() {
        return cvdChartEnabled;
    }

    public void setVolumeRatioChartEnabled(boolean enabled) {
        this.volumeRatioChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isVolumeRatioChartEnabled() {
        return volumeRatioChartEnabled;
    }

    public void setWhaleChartEnabled(boolean enabled, double threshold) {
        this.whaleChartEnabled = enabled;
        this.whaleThreshold = threshold;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isWhaleChartEnabled() {
        return whaleChartEnabled;
    }

    public void setWhaleThreshold(double threshold) {
        this.whaleThreshold = threshold;
    }

    public double getWhaleThreshold() {
        return whaleThreshold;
    }

    public void setRetailChartEnabled(boolean enabled) {
        this.retailChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public void setRetailChartEnabled(boolean enabled, double threshold) {
        this.retailChartEnabled = enabled;
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
        return retailChartEnabled;
    }

    /**
     * Check if any orderflow chart is enabled.
     */
    public boolean isAnyOrderflowEnabled() {
        return deltaChartEnabled || cvdChartEnabled || volumeRatioChartEnabled || whaleChartEnabled || retailChartEnabled || tradeCountChartEnabled;
    }

    public void updateDeltaChart(List<Candle> candles) {
        if (!deltaChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = deltaComponent.getChart().getXYPlot();
        double[] delta = indicatorEngine.getDelta();
        if (delta == null) return;

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
        final XYSeriesCollection finalDeltaDataset = deltaDataset;
        XYBarRenderer deltaRenderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = finalDeltaDataset.getYValue(series, item);
                return value >= 0 ? ChartStyles.DELTA_POSITIVE : ChartStyles.DELTA_NEGATIVE;
            }
        };
        deltaRenderer.setShadowVisible(false);
        deltaRenderer.setBarPainter(new StandardXYBarPainter());
        plot.setRenderer(0, deltaRenderer);

        // Add zero line
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Delta");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
        }
    }

    public void updateCvdChart(List<Candle> candles) {
        if (!cvdChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = cvdComponent.getChart().getXYPlot();
        double[] cumDelta = indicatorEngine.getCumulativeDelta();
        if (cumDelta == null) return;

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

        XYLineAndShapeRenderer cvdRenderer = new XYLineAndShapeRenderer(true, false);
        cvdRenderer.setSeriesPaint(0, ChartStyles.CVD_COLOR);
        cvdRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(0, cvdRenderer);

        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "CVD (Cumulative Delta)");
    }

    public void updateVolumeRatioChart(List<Candle> candles) {
        if (!volumeRatioChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = volumeRatioComponent.getChart().getXYPlot();
        double[] buyVolume = indicatorEngine.getBuyVolume();
        double[] sellVolume = indicatorEngine.getSellVolume();
        if (buyVolume == null || sellVolume == null) return;

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
            maxVolume = Math.max(maxVolume, Math.max(buy, sell));

            // Buy volume as positive, sell volume as negative
            buySeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), buy);
            sellSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), -sell);
        }

        dataset.addSeries(buySeries);
        dataset.addSeries(sellSeries);
        plot.setDataset(0, dataset);

        // Single renderer with two series (green for buy, red for sell)
        XYBarRenderer renderer = new XYBarRenderer(0.0);
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setSeriesPaint(0, new Color(38, 166, 91, 200));  // Buy - green
        renderer.setSeriesPaint(1, new Color(231, 76, 60, 200));  // Sell - red
        renderer.setDrawBarOutline(false);
        plot.setRenderer(0, renderer);

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
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                new BasicStroke(1.0f), new Color(149, 165, 166, 200)));
        }
    }

    public void updateWhaleChart(List<Candle> candles) {
        if (!whaleChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = whaleComponent.getChart().getXYPlot();
        double[] whaleDelta = indicatorEngine.getWhaleDelta(whaleThreshold);
        if (whaleDelta == null) return;

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
        final XYSeriesCollection finalDataset = dataset;
        XYBarRenderer renderer = new XYBarRenderer(0.0) {
            @Override
            public java.awt.Paint getItemPaint(int seriesIdx, int item) {
                double val = finalDataset.getYValue(seriesIdx, item);
                if (val >= 0) {
                    return ChartStyles.WHALE_DELTA_POS;  // Purple for buy
                } else {
                    return ChartStyles.WHALE_DELTA_NEG;  // Orange for sell
                }
            }
        };
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setDrawBarOutline(false);
        plot.setRenderer(0, renderer);

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
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                new BasicStroke(1.0f), new Color(149, 165, 166, 200)));
        }
    }

    public void updateRetailChart(List<Candle> candles) {
        if (!retailChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = retailComponent.getChart().getXYPlot();
        double[] retailDelta = indicatorEngine.getRetailDelta(retailThreshold);
        if (retailDelta == null) return;

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

        // Color-coded bar renderer (blue/cyan for retail trades)
        final XYSeriesCollection finalDataset = dataset;
        XYBarRenderer renderer = new XYBarRenderer(0.0) {
            @Override
            public java.awt.Paint getItemPaint(int seriesIdx, int item) {
                double val = finalDataset.getYValue(seriesIdx, item);
                if (val >= 0) {
                    return new Color(52, 152, 219);   // Blue for buy
                } else {
                    return new Color(231, 76, 60);    // Red for sell
                }
            }
        };
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setDrawBarOutline(false);
        plot.setRenderer(0, renderer);

        // Set symmetric Y axis range around zero
        double padding = maxDelta * 1.1;
        if (padding > 0) {
            plot.getRangeAxis().setRange(-padding, padding);
        }

        // Add zero reference line and title
        plot.clearAnnotations();
        String title = String.format("Retail Delta (<$%.0fK)", whaleThreshold / 1000);
        ChartStyles.addChartTitleAnnotation(plot, title);
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                new BasicStroke(1.0f), new Color(149, 165, 166, 200)));
        }
    }

    // ===== Funding Methods =====

    public void setFundingChartEnabled(boolean enabled) {
        this.fundingChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isFundingChartEnabled() {
        return fundingChartEnabled;
    }

    public void updateFundingChart(List<Candle> candles) {
        if (!fundingChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = fundingComponent.getChart().getXYPlot();

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
        XYLineAndShapeRenderer fundingRenderer = new XYLineAndShapeRenderer(true, false) {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = fundingDataset.getYValue(series, item);
                return value >= 0 ? ChartStyles.FUNDING_POSITIVE : ChartStyles.FUNDING_NEGATIVE;
            }
        };
        fundingRenderer.setSeriesStroke(0, ChartStyles.THIN_STROKE);
        plot.setRenderer(0, fundingRenderer);

        // 8H average line renderer
        XYLineAndShapeRenderer avgRenderer = new XYLineAndShapeRenderer(true, false);
        avgRenderer.setSeriesPaint(0, ChartStyles.FUNDING_8H_COLOR);
        avgRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(1, avgRenderer);

        // Format Y-axis to avoid scientific notation for small values
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setNumberFormatOverride(new java.text.DecimalFormat("0.####"));

        // Add reference lines at 0, 0.01 (typical high funding), -0.01 (negative funding)
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Funding Rate (%)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
            plot.addAnnotation(new XYLineAnnotation(startTime, 0.01, endTime, 0.01,
                ChartStyles.DASHED_STROKE, new Color(230, 126, 34, 100)));
            plot.addAnnotation(new XYLineAnnotation(startTime, -0.01, endTime, -0.01,
                ChartStyles.DASHED_STROKE, new Color(52, 152, 219, 100)));
        }
    }

    // ===== Open Interest Methods =====

    public void setOiChartEnabled(boolean enabled) {
        this.oiChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isOiChartEnabled() {
        return oiChartEnabled;
    }

    public void updateOiChart(List<Candle> candles) {
        if (!oiChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = oiComponent.getChart().getXYPlot();

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
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
        }
    }

    // ===== Premium Index Methods =====

    public void setPremiumChartEnabled(boolean enabled) {
        this.premiumChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isPremiumChartEnabled() {
        return premiumChartEnabled;
    }

    public void updatePremiumChart(List<Candle> candles) {
        if (!premiumChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = premiumComponent.getChart().getXYPlot();

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
        // Average line on secondary dataset
        plot.setDataset(1, avgDataset);

        // Bar renderer with colors based on premium sign
        final XYSeriesCollection finalPremiumDataset = premiumDataset;
        XYBarRenderer barRenderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = finalPremiumDataset.getYValue(series, item);
                return value >= 0 ? ChartStyles.PREMIUM_POSITIVE : ChartStyles.PREMIUM_NEGATIVE;
            }
        };
        barRenderer.setShadowVisible(false);
        barRenderer.setBarPainter(new StandardXYBarPainter());
        plot.setRenderer(0, barRenderer);

        // Average line renderer
        XYLineAndShapeRenderer avgRenderer = new XYLineAndShapeRenderer(true, false);
        avgRenderer.setSeriesPaint(0, ChartStyles.PREMIUM_AVG_COLOR);
        avgRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(1, avgRenderer);

        // Add zero line and title
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Premium Index (%)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
        }
    }

    // ===== Stochastic Methods =====

    public void setStochasticChartEnabled(boolean enabled, int kPeriod, int dPeriod) {
        this.stochasticChartEnabled = enabled;
        this.stochasticKPeriod = kPeriod;
        this.stochasticDPeriod = dPeriod;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isStochasticChartEnabled() {
        return stochasticChartEnabled;
    }

    public void updateStochasticChart(List<Candle> candles) {
        if (!stochasticChartEnabled || candles == null || candles.size() < stochasticKPeriod + stochasticDPeriod) {
            return;
        }
        // Subscribe to Stochastic data (will arrive via callback if not ready)
        indicatorDataService.subscribeStochastic(stochasticKPeriod, stochasticDPeriod);
        // Try to render with available data
        redrawStochasticChart(candles);
    }

    private void redrawStochasticChart(List<Candle> candles) {
        if (!stochasticChartEnabled || candles == null || candles.size() < stochasticKPeriod + stochasticDPeriod) {
            return;
        }

        // Get pre-computed Stochastic data from service
        Indicators.StochasticResult result = indicatorDataService.getStochastic(stochasticKPeriod, stochasticDPeriod);
        if (result == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = stochasticComponent.getChart().getXYPlot();
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
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.STOCHASTIC_K_COLOR);  // %K - cyan (fast line)
        renderer.setSeriesPaint(1, ChartStyles.STOCHASTIC_D_COLOR);  // %D - pink (slow/signal line)
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        renderer.setSeriesStroke(1, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(renderer);

        // Add reference lines at 20, 50, 80
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Stochastic");

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();

            plot.addAnnotation(new XYLineAnnotation(startTime, 20, endTime, 20,
                ChartStyles.DASHED_STROKE, ChartStyles.RSI_OVERSOLD));
            plot.addAnnotation(new XYLineAnnotation(startTime, 50, endTime, 50,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
            plot.addAnnotation(new XYLineAnnotation(startTime, 80, endTime, 80,
                ChartStyles.DASHED_STROKE, ChartStyles.RSI_OVERBOUGHT));
        }
    }

    // ===== Range Position Methods =====

    public void setRangePositionChartEnabled(boolean enabled, int period, int skip) {
        this.rangePositionChartEnabled = enabled;
        this.rangePositionPeriod = period;
        this.rangePositionSkip = skip;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isRangePositionChartEnabled() {
        return rangePositionChartEnabled;
    }

    public void updateRangePositionChart(List<Candle> candles) {
        if (!rangePositionChartEnabled || candles == null || candles.size() < rangePositionPeriod + rangePositionSkip + 1) {
            return;
        }

        XYPlot plot = rangePositionComponent.getChart().getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        String label = rangePositionSkip > 0
            ? "RANGE_POSITION(" + rangePositionPeriod + "," + rangePositionSkip + ")"
            : "RANGE_POSITION(" + rangePositionPeriod + ")";
        TimeSeries series = new TimeSeries(label);

        double[] values = Indicators.rangePosition(candles, rangePositionPeriod, rangePositionSkip);

        for (int i = rangePositionPeriod + rangePositionSkip; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(values[i])) {
                series.addOrUpdate(new Millisecond(new Date(c.timestamp())), values[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(dataset);

        // Style the line - color changes based on position (breakout = highlighted)
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false) {
            @Override
            public Paint getItemPaint(int seriesIdx, int item) {
                double val = dataset.getYValue(seriesIdx, item);
                if (val > 1.0) {
                    return ChartStyles.DELTA_POSITIVE;  // Green - above range (breakout)
                } else if (val < -1.0) {
                    return ChartStyles.DELTA_NEGATIVE;  // Red - below range (breakdown)
                } else {
                    return ChartStyles.ATR_COLOR;       // Normal - within range
                }
            }
        };
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(renderer);

        // Add reference lines at -1, 0, +1
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Range Position");

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();

            // Breakout levels
            plot.addAnnotation(new XYLineAnnotation(startTime, 1, endTime, 1,
                ChartStyles.DASHED_STROKE, ChartStyles.DELTA_POSITIVE));
            plot.addAnnotation(new XYLineAnnotation(startTime, -1, endTime, -1,
                ChartStyles.DASHED_STROKE, ChartStyles.DELTA_NEGATIVE));
            // Center line
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
        }
    }

    // ===== ADX Methods =====

    public void setAdxChartEnabled(boolean enabled, int period) {
        this.adxChartEnabled = enabled;
        this.adxPeriod = period;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isAdxChartEnabled() {
        return adxChartEnabled;
    }

    public void updateAdxChart(List<Candle> candles) {
        if (!adxChartEnabled || candles == null || candles.size() < adxPeriod * 2) {
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
        if (!adxChartEnabled || candles == null || candles.size() < adxPeriod * 2) {
            return;
        }

        // Get pre-computed data from service
        double[] adxValues = indicatorDataService.getADX(adxPeriod);
        double[] plusDIValues = indicatorDataService.getPlusDI(adxPeriod);
        double[] minusDIValues = indicatorDataService.getMinusDI(adxPeriod);
        if (adxValues == null || plusDIValues == null || minusDIValues == null) {
            return; // Data not ready yet, will be called again via callback
        }

        XYPlot plot = adxComponent.getChart().getXYPlot();
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
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.ADX_COLOR);         // ADX - main trend line
        renderer.setSeriesPaint(1, ChartStyles.DELTA_POSITIVE);    // +DI - green
        renderer.setSeriesPaint(2, ChartStyles.DELTA_NEGATIVE);    // -DI - red
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        renderer.setSeriesStroke(1, ChartStyles.THIN_STROKE);
        renderer.setSeriesStroke(2, ChartStyles.THIN_STROKE);
        plot.setRenderer(renderer);

        // Add reference lines at 20, 25 (common ADX thresholds)
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "ADX");

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();

            // 20 - weak trend threshold
            plot.addAnnotation(new XYLineAnnotation(startTime, 20, endTime, 20,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
            // 25 - strong trend threshold
            plot.addAnnotation(new XYLineAnnotation(startTime, 25, endTime, 25,
                ChartStyles.DASHED_STROKE, new Color(230, 126, 34, 150)));
        }
    }

    // ===== Trade Count Methods =====

    public void setTradeCountChartEnabled(boolean enabled) {
        this.tradeCountChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isTradeCountChartEnabled() {
        return tradeCountChartEnabled;
    }

    public void updateTradeCountChart(List<Candle> candles) {
        if (!tradeCountChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = tradeCountComponent.getChart().getXYPlot();
        double[] tradeCount = indicatorEngine.getTradeCount();
        if (tradeCount == null) return;

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

        // Line renderer for trade count
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.TRADE_COUNT_LINE_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(0, renderer);

        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Trade Count");
    }

    // ===== Accessors =====

    public JFreeChart getRsiChart() { return rsiComponent.getChart(); }
    public JFreeChart getMacdChart() { return macdComponent.getChart(); }
    public JFreeChart getAtrChart() { return atrComponent.getChart(); }
    public JFreeChart getDeltaChart() { return deltaComponent.getChart(); }
    public JFreeChart getCvdChart() { return cvdComponent.getChart(); }
    public JFreeChart getVolumeRatioChart() { return volumeRatioComponent.getChart(); }
    public JFreeChart getFundingChart() { return fundingComponent.getChart(); }
    public JFreeChart getWhaleChart() { return whaleComponent.getChart(); }
    public JFreeChart getRetailChart() { return retailComponent.getChart(); }
    public JFreeChart getOiChart() { return oiComponent.getChart(); }
    public JFreeChart getStochasticChart() { return stochasticComponent.getChart(); }
    public JFreeChart getRangePositionChart() { return rangePositionComponent.getChart(); }
    public JFreeChart getAdxChart() { return adxComponent.getChart(); }
    public JFreeChart getTradeCountChart() { return tradeCountComponent.getChart(); }
    public JFreeChart getPremiumChart() { return premiumComponent.getChart(); }

    public org.jfree.chart.ChartPanel getRsiChartPanel() { return rsiComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getMacdChartPanel() { return macdComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getAtrChartPanel() { return atrComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getDeltaChartPanel() { return deltaComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getCvdChartPanel() { return cvdComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getVolumeRatioChartPanel() { return volumeRatioComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getFundingChartPanel() { return fundingComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getWhaleChartPanel() { return whaleComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getRetailChartPanel() { return retailComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getOiChartPanel() { return oiComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getStochasticChartPanel() { return stochasticComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getRangePositionChartPanel() { return rangePositionComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getAdxChartPanel() { return adxComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getTradeCountChartPanel() { return tradeCountComponent.getChartPanel(); }
    public org.jfree.chart.ChartPanel getPremiumChartPanel() { return premiumComponent.getChartPanel(); }

    public JPanel getRsiChartWrapper() { return rsiComponent.getWrapper(); }
    public JPanel getMacdChartWrapper() { return macdComponent.getWrapper(); }
    public JPanel getAtrChartWrapper() { return atrComponent.getWrapper(); }
    public JPanel getDeltaChartWrapper() { return deltaComponent.getWrapper(); }
    public JPanel getCvdChartWrapper() { return cvdComponent.getWrapper(); }
    public JPanel getVolumeRatioChartWrapper() { return volumeRatioComponent.getWrapper(); }
    public JPanel getWhaleChartWrapper() { return whaleComponent.getWrapper(); }
    public JPanel getRetailChartWrapper() { return retailComponent.getWrapper(); }
    public JPanel getFundingChartWrapper() { return fundingComponent.getWrapper(); }
    public JPanel getOiChartWrapper() { return oiComponent.getWrapper(); }
    public JPanel getStochasticChartWrapper() { return stochasticComponent.getWrapper(); }
    public JPanel getRangePositionChartWrapper() { return rangePositionComponent.getWrapper(); }
    public JPanel getAdxChartWrapper() { return adxComponent.getWrapper(); }
    public JPanel getTradeCountChartWrapper() { return tradeCountComponent.getWrapper(); }
    public JPanel getPremiumChartWrapper() { return premiumComponent.getWrapper(); }

    public JButton getRsiZoomBtn() { return rsiComponent.getZoomButton(); }
    public JButton getMacdZoomBtn() { return macdComponent.getZoomButton(); }
    public JButton getAtrZoomBtn() { return atrComponent.getZoomButton(); }
    public JButton getDeltaZoomBtn() { return deltaComponent.getZoomButton(); }
    public JButton getCvdZoomBtn() { return cvdComponent.getZoomButton(); }
    public JButton getVolumeRatioZoomBtn() { return volumeRatioComponent.getZoomButton(); }
    public JButton getWhaleZoomBtn() { return whaleComponent.getZoomButton(); }
    public JButton getRetailZoomBtn() { return retailComponent.getZoomButton(); }
    public JButton getFundingZoomBtn() { return fundingComponent.getZoomButton(); }
    public JButton getOiZoomBtn() { return oiComponent.getZoomButton(); }
    public JButton getStochasticZoomBtn() { return stochasticComponent.getZoomButton(); }
    public JButton getRangePositionZoomBtn() { return rangePositionComponent.getZoomButton(); }
    public JButton getAdxZoomBtn() { return adxComponent.getZoomButton(); }
    public JButton getTradeCountZoomBtn() { return tradeCountComponent.getZoomButton(); }

    /**
     * Update zoom button states.
     * @param zoomedIndex Index of zoomed indicator (-1 for none, 0=RSI, 1=MACD, 2=ATR, 3=Delta, 4=CVD, 5=VolumeRatio, 6=Whale, 7=Retail, 8=Funding, 9=OI, 10=Stochastic, 11=RangePosition, 12=ADX, 13=TradeCount)
     */
    public void updateZoomButtonStates(int zoomedIndex) {
        ChartComponent[] components = {rsiComponent, macdComponent, atrComponent, deltaComponent, cvdComponent, volumeRatioComponent, whaleComponent, retailComponent, fundingComponent, oiComponent, stochasticComponent, rangePositionComponent, adxComponent, tradeCountComponent};
        for (int i = 0; i < components.length; i++) {
            components[i].setZoomed(zoomedIndex == i);
        }
    }

    /**
     * Update full screen button states.
     * @param fullScreenIndex Index of full screen indicator (-1 for none)
     */
    public void updateFullScreenButtonStates(int fullScreenIndex) {
        ChartComponent[] components = {rsiComponent, macdComponent, atrComponent, deltaComponent, cvdComponent, volumeRatioComponent, whaleComponent, retailComponent, fundingComponent, oiComponent, stochasticComponent, rangePositionComponent, adxComponent, tradeCountComponent};
        for (int i = 0; i < components.length; i++) {
            components[i].setFullScreen(fullScreenIndex == i);
        }
    }

    /**
     * Add mouse wheel listener to all chart panels.
     */
    public void addMouseWheelListener(java.awt.event.MouseWheelListener listener) {
        rsiComponent.getChartPanel().addMouseWheelListener(listener);
        macdComponent.getChartPanel().addMouseWheelListener(listener);
        atrComponent.getChartPanel().addMouseWheelListener(listener);
        deltaComponent.getChartPanel().addMouseWheelListener(listener);
        cvdComponent.getChartPanel().addMouseWheelListener(listener);
        volumeRatioComponent.getChartPanel().addMouseWheelListener(listener);
        whaleComponent.getChartPanel().addMouseWheelListener(listener);
        retailComponent.getChartPanel().addMouseWheelListener(listener);
        fundingComponent.getChartPanel().addMouseWheelListener(listener);
        oiComponent.getChartPanel().addMouseWheelListener(listener);
        stochasticComponent.getChartPanel().addMouseWheelListener(listener);
        rangePositionComponent.getChartPanel().addMouseWheelListener(listener);
        adxComponent.getChartPanel().addMouseWheelListener(listener);
        tradeCountComponent.getChartPanel().addMouseWheelListener(listener);
    }

    /**
     * Remove mouse wheel listener from all chart panels.
     */
    public void removeMouseWheelListener(java.awt.event.MouseWheelListener listener) {
        rsiComponent.getChartPanel().removeMouseWheelListener(listener);
        macdComponent.getChartPanel().removeMouseWheelListener(listener);
        atrComponent.getChartPanel().removeMouseWheelListener(listener);
        deltaComponent.getChartPanel().removeMouseWheelListener(listener);
        cvdComponent.getChartPanel().removeMouseWheelListener(listener);
        volumeRatioComponent.getChartPanel().removeMouseWheelListener(listener);
        whaleComponent.getChartPanel().removeMouseWheelListener(listener);
        retailComponent.getChartPanel().removeMouseWheelListener(listener);
        fundingComponent.getChartPanel().removeMouseWheelListener(listener);
        oiComponent.getChartPanel().removeMouseWheelListener(listener);
        stochasticComponent.getChartPanel().removeMouseWheelListener(listener);
        rangePositionComponent.getChartPanel().removeMouseWheelListener(listener);
        adxComponent.getChartPanel().removeMouseWheelListener(listener);
        tradeCountComponent.getChartPanel().removeMouseWheelListener(listener);
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
        updatePremiumChart(candles);
        updateStochasticChart(candles);
        updateRangePositionChart(candles);
        updateAdxChart(candles);
        updateTradeCountChart(candles);
    }

    /**
     * Update Y-axis auto-range for indicator charts.
     */
    public void updateYAxisAutoRange(boolean fitYAxisToVisible) {
        // MACD, ATR, Delta, Funding follow standard auto-range
        ChartComponent[] standardCharts = {macdComponent, atrComponent, deltaComponent, fundingComponent};
        for (ChartComponent comp : standardCharts) {
            JFreeChart chart = comp.getChart();
            XYPlot plot = chart.getXYPlot();
            plot.getRangeAxis().setAutoRange(true);
            if (fitYAxisToVisible) {
                plot.configureRangeAxes();
            }
        }

        // RSI: fixed 0-100 range in Full Y mode, auto in Fit Y mode
        if (fitYAxisToVisible) {
            rsiComponent.getChart().getXYPlot().getRangeAxis().setAutoRange(true);
            rsiComponent.getChart().getXYPlot().configureRangeAxes();
        } else {
            rsiComponent.getChart().getXYPlot().getRangeAxis().setAutoRange(false);
            rsiComponent.getChart().getXYPlot().getRangeAxis().setRange(0, 100);
        }
    }
}
