package com.tradery.ui.charts;

import com.tradery.indicators.IndicatorEngine;
import com.tradery.model.Candle;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.BasicStroke;
import java.util.Date;
import java.util.List;

/**
 * Manages overlay indicators on the price chart (SMA, EMA, Bollinger Bands, High/Low, Mayer Multiple).
 */
public class OverlayManager {

    private final JFreeChart priceChart;

    // Overlay dataset indices
    private int smaDatasetIndex = -1;
    private int emaDatasetIndex = -1;
    private int bbDatasetIndex = -1;
    private int hlDatasetIndex = -1;
    private int dailyPocDatasetIndex = -1;
    private int floatingPocDatasetIndex = -1;

    // Overlay series references
    private TimeSeries smaSeries;
    private TimeSeries emaSeries;

    // Mayer Multiple state
    private boolean mayerMultipleEnabled = false;
    private int mayerPeriod = 200;

    // IndicatorEngine for POC calculations
    private IndicatorEngine indicatorEngine;

    public OverlayManager(JFreeChart priceChart) {
        this.priceChart = priceChart;
    }

    // ===== SMA Overlay =====

    public void setSmaOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearSmaOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        // Calculate SMA values
        smaSeries = new TimeSeries("SMA(" + period + ")");

        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += candles.get(i - j).close();
            }
            double sma = sum / period;
            smaSeries.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), sma);
        }

        // Add as secondary dataset
        TimeSeriesCollection smaDataset = new TimeSeriesCollection(smaSeries);

        if (smaDatasetIndex < 0) {
            smaDatasetIndex = findNextAvailableDatasetIndex(plot, 1);
        }

        plot.setDataset(smaDatasetIndex, smaDataset);

        // Style the SMA line
        XYLineAndShapeRenderer smaRenderer = new XYLineAndShapeRenderer(true, false);
        smaRenderer.setSeriesPaint(0, ChartStyles.SMA_COLOR);
        smaRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(smaDatasetIndex, smaRenderer);
    }

    public void clearSmaOverlay() {
        if (smaDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(smaDatasetIndex, null);
        }
        smaSeries = null;
    }

    public boolean isSmaEnabled() {
        return smaSeries != null;
    }

    // ===== EMA Overlay =====

    public void setEmaOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearEmaOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        // Calculate EMA values
        emaSeries = new TimeSeries("EMA(" + period + ")");
        double multiplier = 2.0 / (period + 1);
        double ema = 0;

        for (int i = 0; i < candles.size(); i++) {
            if (i < period - 1) {
                continue;
            } else if (i == period - 1) {
                // First EMA is SMA of first 'period' values
                double sum = 0;
                for (int j = 0; j < period; j++) {
                    sum += candles.get(j).close();
                }
                ema = sum / period;
            } else {
                ema = (candles.get(i).close() - ema) * multiplier + ema;
            }
            emaSeries.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), ema);
        }

        // Add as secondary dataset
        TimeSeriesCollection emaDataset = new TimeSeriesCollection(emaSeries);

        if (emaDatasetIndex < 0) {
            emaDatasetIndex = findNextAvailableDatasetIndex(plot, Math.max(2, smaDatasetIndex + 1));
        }

        plot.setDataset(emaDatasetIndex, emaDataset);

        // Style the EMA line
        XYLineAndShapeRenderer emaRenderer = new XYLineAndShapeRenderer(true, false);
        emaRenderer.setSeriesPaint(0, ChartStyles.EMA_COLOR);
        emaRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(emaDatasetIndex, emaRenderer);
    }

    public void clearEmaOverlay() {
        if (emaDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(emaDatasetIndex, null);
        }
        emaSeries = null;
    }

    public boolean isEmaEnabled() {
        return emaSeries != null;
    }

    // ===== Bollinger Bands Overlay =====

    public void setBollingerOverlay(int period, double stdDevMultiplier, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearBollingerOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        TimeSeries upperSeries = new TimeSeries("BB Upper");
        TimeSeries middleSeries = new TimeSeries("BB Middle");
        TimeSeries lowerSeries = new TimeSeries("BB Lower");

        for (int i = period - 1; i < candles.size(); i++) {
            // Calculate SMA (middle band)
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += candles.get(i - j).close();
            }
            double sma = sum / period;

            // Calculate standard deviation
            double sumSquaredDiff = 0;
            for (int j = 0; j < period; j++) {
                double diff = candles.get(i - j).close() - sma;
                sumSquaredDiff += diff * diff;
            }
            double stdDev = Math.sqrt(sumSquaredDiff / period);

            // Calculate bands
            double upper = sma + (stdDevMultiplier * stdDev);
            double lower = sma - (stdDevMultiplier * stdDev);

            Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));
            upperSeries.addOrUpdate(time, upper);
            middleSeries.addOrUpdate(time, sma);
            lowerSeries.addOrUpdate(time, lower);
        }

        TimeSeriesCollection bbDataset = new TimeSeriesCollection();
        bbDataset.addSeries(upperSeries);
        bbDataset.addSeries(middleSeries);
        bbDataset.addSeries(lowerSeries);

        if (bbDatasetIndex < 0) {
            bbDatasetIndex = findNextAvailableDatasetIndex(plot,
                Math.max(3, Math.max(smaDatasetIndex, emaDatasetIndex) + 1));
        }

        plot.setDataset(bbDatasetIndex, bbDataset);

        // Style: purple bands with semi-transparent fill effect
        XYLineAndShapeRenderer bbRenderer = new XYLineAndShapeRenderer(true, false);
        bbRenderer.setSeriesPaint(0, ChartStyles.BB_COLOR);
        bbRenderer.setSeriesPaint(1, ChartStyles.BB_MIDDLE_COLOR);
        bbRenderer.setSeriesPaint(2, ChartStyles.BB_COLOR);
        bbRenderer.setSeriesStroke(0, ChartStyles.THIN_STROKE);
        bbRenderer.setSeriesStroke(1, new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[]{4.0f}, 0.0f)); // Dashed middle
        bbRenderer.setSeriesStroke(2, ChartStyles.THIN_STROKE);
        plot.setRenderer(bbDatasetIndex, bbRenderer);
    }

    public void clearBollingerOverlay() {
        if (bbDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(bbDatasetIndex, null);
            bbDatasetIndex = -1;
        }
    }

    public boolean isBollingerEnabled() {
        return bbDatasetIndex >= 0 && priceChart.getXYPlot().getDataset(bbDatasetIndex) != null;
    }

    // ===== High/Low Overlay =====

    public void setHighLowOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearHighLowOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        TimeSeries highSeries = new TimeSeries("High(" + period + ")");
        TimeSeries lowSeries = new TimeSeries("Low(" + period + ")");

        for (int i = period - 1; i < candles.size(); i++) {
            double high = Double.MIN_VALUE;
            double low = Double.MAX_VALUE;

            for (int j = 0; j < period; j++) {
                Candle c = candles.get(i - j);
                high = Math.max(high, c.high());
                low = Math.min(low, c.low());
            }

            Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));
            highSeries.addOrUpdate(time, high);
            lowSeries.addOrUpdate(time, low);
        }

        TimeSeriesCollection hlDataset = new TimeSeriesCollection();
        hlDataset.addSeries(highSeries);
        hlDataset.addSeries(lowSeries);

        if (hlDatasetIndex < 0) {
            hlDatasetIndex = findNextAvailableDatasetIndex(plot,
                Math.max(4, Math.max(Math.max(smaDatasetIndex, emaDatasetIndex), bbDatasetIndex) + 1));
        }

        plot.setDataset(hlDatasetIndex, hlDataset);

        // Style: green for high (resistance), red for low (support)
        XYLineAndShapeRenderer hlRenderer = new XYLineAndShapeRenderer(true, false);
        hlRenderer.setSeriesPaint(0, ChartStyles.WIN_COLOR);
        hlRenderer.setSeriesPaint(1, ChartStyles.LOSS_COLOR);
        hlRenderer.setSeriesStroke(0, new BasicStroke(1.2f));
        hlRenderer.setSeriesStroke(1, new BasicStroke(1.2f));
        plot.setRenderer(hlDatasetIndex, hlRenderer);
    }

    public void clearHighLowOverlay() {
        if (hlDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(hlDatasetIndex, null);
            hlDatasetIndex = -1;
        }
    }

    public boolean isHighLowEnabled() {
        return hlDatasetIndex >= 0 && priceChart.getXYPlot().getDataset(hlDatasetIndex) != null;
    }

    // ===== Mayer Multiple =====

    public void setMayerMultipleEnabled(boolean enabled, int period) {
        this.mayerMultipleEnabled = enabled;
        this.mayerPeriod = period;
    }

    public boolean isMayerMultipleEnabled() {
        return mayerMultipleEnabled;
    }

    public int getMayerPeriod() {
        return mayerPeriod;
    }

    /**
     * Add Mayer Multiple threshold lines to the price chart.
     * Should be called during price chart update when Mayer is enabled.
     */
    public void addMayerMultipleAnnotations(XYPlot plot, List<Candle> candles) {
        if (!mayerMultipleEnabled || candles.size() < mayerPeriod) {
            return;
        }

        // Calculate SMA for Mayer Multiple
        double[] sma = new double[candles.size()];
        for (int i = mayerPeriod - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = 0; j < mayerPeriod; j++) {
                sum += candles.get(i - j).close();
            }
            sma[i] = sum / mayerPeriod;
        }

        // Draw threshold lines at Mayer levels
        double[] thresholds = {0.95, 0.98, 1.0, 1.02, 1.05};
        java.awt.Color[] colors = {
            ChartStyles.MAYER_DEEP_UNDERVALUED,
            ChartStyles.MAYER_UNDERVALUED,
            ChartStyles.MAYER_NEUTRAL,
            ChartStyles.MAYER_OVERVALUED,
            ChartStyles.MAYER_DEEP_OVERVALUED
        };

        for (int t = 0; t < thresholds.length; t++) {
            double mult = thresholds[t];
            java.awt.Color color = colors[t];

            for (int i = mayerPeriod; i < candles.size(); i++) {
                Candle prev = candles.get(i - 1);
                Candle curr = candles.get(i);

                double prevPrice = sma[i - 1] * mult;
                double currPrice = sma[i] * mult;

                XYLineAnnotation segment = new XYLineAnnotation(
                    prev.timestamp(), prevPrice,
                    curr.timestamp(), currPrice,
                    ChartStyles.DASHED_MAYER_STROKE, color
                );
                plot.addAnnotation(segment);
            }
        }
    }

    // ===== IndicatorEngine Setter =====

    public void setIndicatorEngine(IndicatorEngine engine) {
        this.indicatorEngine = engine;
    }

    // ===== Daily POC Overlay =====

    /**
     * Shows previous day's POC as a horizontal line extending across the current day.
     */
    public void setDailyPocOverlay(List<Candle> candles) {
        if (candles == null || candles.isEmpty() || indicatorEngine == null) {
            clearDailyPocOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        TimeSeries pocSeries = new TimeSeries("Daily POC");

        for (int i = 0; i < candles.size(); i++) {
            double poc = indicatorEngine.getPrevDayPOCAt(i);
            if (!Double.isNaN(poc)) {
                pocSeries.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), poc);
            }
        }

        if (pocSeries.isEmpty()) {
            clearDailyPocOverlay();
            return;
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(pocSeries);

        if (dailyPocDatasetIndex < 0) {
            dailyPocDatasetIndex = findNextAvailableDatasetIndex(plot, 10);
        }

        plot.setDataset(dailyPocDatasetIndex, dataset);

        // Style: cyan dashed line for previous day's POC
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.DAILY_POC_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.DASHED_STROKE);
        plot.setRenderer(dailyPocDatasetIndex, renderer);
    }

    public void clearDailyPocOverlay() {
        if (dailyPocDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(dailyPocDatasetIndex, null);
        }
    }

    public boolean isDailyPocEnabled() {
        return dailyPocDatasetIndex >= 0 && priceChart.getXYPlot().getDataset(dailyPocDatasetIndex) != null;
    }

    // ===== Floating POC Overlay =====

    /**
     * Shows the developing (floating) POC that updates throughout the current day.
     */
    public void setFloatingPocOverlay(List<Candle> candles) {
        if (candles == null || candles.isEmpty() || indicatorEngine == null) {
            clearFloatingPocOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        TimeSeries pocSeries = new TimeSeries("Floating POC");

        for (int i = 0; i < candles.size(); i++) {
            double poc = indicatorEngine.getTodayPOCAt(i);
            if (!Double.isNaN(poc)) {
                pocSeries.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), poc);
            }
        }

        if (pocSeries.isEmpty()) {
            clearFloatingPocOverlay();
            return;
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(pocSeries);

        if (floatingPocDatasetIndex < 0) {
            floatingPocDatasetIndex = findNextAvailableDatasetIndex(plot, 11);
        }

        plot.setDataset(floatingPocDatasetIndex, dataset);

        // Style: magenta solid line for floating/developing POC
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.FLOATING_POC_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(floatingPocDatasetIndex, renderer);
    }

    public void clearFloatingPocOverlay() {
        if (floatingPocDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(floatingPocDatasetIndex, null);
        }
    }

    public boolean isFloatingPocEnabled() {
        return floatingPocDatasetIndex >= 0 && priceChart.getXYPlot().getDataset(floatingPocDatasetIndex) != null;
    }

    // ===== Clear All =====

    public void clearAll() {
        clearSmaOverlay();
        clearEmaOverlay();
        clearBollingerOverlay();
        clearHighLowOverlay();
        clearDailyPocOverlay();
        clearFloatingPocOverlay();
    }

    // ===== Helper Methods =====

    private int findNextAvailableDatasetIndex(XYPlot plot, int startIndex) {
        int index = startIndex;
        while (plot.getDataset(index) != null) {
            index++;
        }
        return index;
    }
}
