package com.tradery.ui.charts;

import com.tradery.indicators.IndicatorEngine;
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

    // Indicator parameters
    private int rsiPeriod = 14;
    private int macdFast = 12;
    private int macdSlow = 26;
    private int macdSignal = 9;
    private int atrPeriod = 14;
    private double whaleThreshold = 50000;

    // IndicatorEngine for orderflow/funding data
    private IndicatorEngine indicatorEngine;

    // Callback for layout updates
    private Runnable onLayoutChange;

    // Min chart height
    private static final int MIN_CHART_HEIGHT = 60;

    public IndicatorChartsManager() {
        initializeCharts();
    }

    public void setOnLayoutChange(Runnable callback) {
        this.onLayoutChange = callback;
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
    }

    /**
     * Create wrapper panels with zoom buttons.
     * @param zoomCallback Callback to handle zoom toggle (index: 0=RSI, 1=MACD, 2=ATR, 3=Delta, 4=CVD, 5=VolumeRatio, 6=Whale, 7=Retail, 8=Funding, 9=OI)
     */
    public void createWrappers(java.util.function.IntConsumer zoomCallback) {
        rsiComponent.createWrapper(() -> zoomCallback.accept(0));
        macdComponent.createWrapper(() -> zoomCallback.accept(1));
        atrComponent.createWrapper(() -> zoomCallback.accept(2));
        deltaComponent.createWrapper(() -> zoomCallback.accept(3));
        cvdComponent.createWrapper(() -> zoomCallback.accept(4));
        volumeRatioComponent.createWrapper(() -> zoomCallback.accept(5));
        whaleComponent.createWrapper(() -> zoomCallback.accept(6));
        retailComponent.createWrapper(() -> zoomCallback.accept(7));
        fundingComponent.createWrapper(() -> zoomCallback.accept(8));
        oiComponent.createWrapper(() -> zoomCallback.accept(9));
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

        XYPlot plot = rsiComponent.getChart().getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries rsiSeries = new TimeSeries("RSI(" + rsiPeriod + ")");

        // Calculate RSI
        double[] gains = new double[candles.size()];
        double[] losses = new double[candles.size()];

        for (int i = 1; i < candles.size(); i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            gains[i] = change > 0 ? change : 0;
            losses[i] = change < 0 ? -change : 0;
        }

        // Calculate initial averages
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= rsiPeriod; i++) {
            avgGain += gains[i];
            avgLoss += losses[i];
        }
        avgGain /= rsiPeriod;
        avgLoss /= rsiPeriod;

        // Calculate RSI values
        for (int i = rsiPeriod; i < candles.size(); i++) {
            if (i > rsiPeriod) {
                avgGain = (avgGain * (rsiPeriod - 1) + gains[i]) / rsiPeriod;
                avgLoss = (avgLoss * (rsiPeriod - 1) + losses[i]) / rsiPeriod;
            }

            double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            double rsi = 100 - (100 / (1 + rs));

            Candle c = candles.get(i);
            rsiSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), rsi);
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

        XYPlot plot = macdComponent.getChart().getXYPlot();
        TimeSeriesCollection lineDataset = new TimeSeriesCollection();
        TimeSeries macdLine = new TimeSeries("MACD");
        TimeSeries signalLine = new TimeSeries("Signal");

        // Calculate EMAs
        double[] fastEma = calculateEMA(candles, macdFast);
        double[] slowEma = calculateEMA(candles, macdSlow);
        double[] macdValues = new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            macdValues[i] = fastEma[i] - slowEma[i];
        }

        // Calculate signal line (EMA of MACD)
        double[] signalValues = new double[candles.size()];
        double multiplier = 2.0 / (macdSignal + 1);
        signalValues[macdSlow - 1] = macdValues[macdSlow - 1];
        for (int i = macdSlow; i < candles.size(); i++) {
            signalValues[i] = (macdValues[i] - signalValues[i - 1]) * multiplier + signalValues[i - 1];
        }

        // Build series and histogram data
        XYSeriesCollection histogramDataset = new XYSeriesCollection();
        XYSeries histogramSeries = new XYSeries("Histogram");

        for (int i = macdSlow + macdSignal - 1; i < candles.size(); i++) {
            Candle c = candles.get(i);
            Millisecond ms = new Millisecond(new Date(c.timestamp()));
            macdLine.addOrUpdate(ms, macdValues[i]);
            signalLine.addOrUpdate(ms, signalValues[i]);
            histogramSeries.add(c.timestamp(), macdValues[i] - signalValues[i]);
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

    private double[] calculateEMA(List<Candle> candles, int period) {
        double[] ema = new double[candles.size()];
        double multiplier = 2.0 / (period + 1);

        // Start with SMA for first value
        double sum = 0;
        for (int i = 0; i < period && i < candles.size(); i++) {
            sum += candles.get(i).close();
        }
        ema[period - 1] = sum / period;

        // Calculate EMA
        for (int i = period; i < candles.size(); i++) {
            ema[i] = (candles.get(i).close() - ema[i - 1]) * multiplier + ema[i - 1];
        }

        return ema;
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

        XYPlot plot = atrComponent.getChart().getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries atrSeries = new TimeSeries("ATR(" + atrPeriod + ")");

        // Calculate True Range and ATR
        double[] tr = new double[candles.size()];
        tr[0] = candles.get(0).high() - candles.get(0).low();

        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            double highLow = curr.high() - curr.low();
            double highClose = Math.abs(curr.high() - prev.close());
            double lowClose = Math.abs(curr.low() - prev.close());
            tr[i] = Math.max(highLow, Math.max(highClose, lowClose));
        }

        // Calculate ATR using Wilder's smoothing
        double atr = 0;
        for (int i = 0; i < atrPeriod; i++) {
            atr += tr[i];
        }
        atr /= atrPeriod;

        for (int i = atrPeriod; i < candles.size(); i++) {
            if (i > atrPeriod) {
                atr = (atr * (atrPeriod - 1) + tr[i]) / atrPeriod;
            }
            Candle c = candles.get(i);
            atrSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), atr);
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

    public void setDeltaChartEnabled(boolean enabled, double threshold) {
        this.deltaChartEnabled = enabled;
        this.whaleThreshold = threshold;
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

    public void setRetailChartEnabled(boolean enabled, double threshold) {
        this.retailChartEnabled = enabled;
        this.whaleThreshold = threshold;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isRetailChartEnabled() {
        return retailChartEnabled;
    }

    /**
     * Check if any orderflow chart is enabled.
     */
    public boolean isAnyOrderflowEnabled() {
        return deltaChartEnabled || cvdChartEnabled || volumeRatioChartEnabled || whaleChartEnabled || retailChartEnabled;
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
        double[] retailDelta = indicatorEngine.getRetailDelta(whaleThreshold);
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

    /**
     * Update zoom button states.
     * @param zoomedIndex Index of zoomed indicator (-1 for none, 0=RSI, 1=MACD, 2=ATR, 3=Delta, 4=CVD, 5=VolumeRatio, 6=Whale, 7=Retail, 8=Funding, 9=OI)
     */
    public void updateZoomButtonStates(int zoomedIndex) {
        ChartComponent[] components = {rsiComponent, macdComponent, atrComponent, deltaComponent, cvdComponent, volumeRatioComponent, whaleComponent, retailComponent, fundingComponent, oiComponent};
        for (int i = 0; i < components.length; i++) {
            components[i].setZoomed(zoomedIndex == i);
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
