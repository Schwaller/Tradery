package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.List;

/**
 * Bollinger Bands overlay.
 * Uses IndicatorEngine.getBollingerBands() for calculation.
 */
public class BollingerOverlay implements ChartOverlay {

    private final int period;
    private final double stdDev;
    private final Color bandColor;
    private final Color middleColor;

    /**
     * Create a Bollinger Bands overlay with default colors.
     */
    public BollingerOverlay(int period, double stdDev) {
        this(period, stdDev, ChartStyles.BB_COLOR, ChartStyles.BB_MIDDLE_COLOR);
    }

    /**
     * Create a Bollinger Bands overlay with custom colors.
     */
    public BollingerOverlay(int period, double stdDev, Color bandColor, Color middleColor) {
        this.period = period;
        this.stdDev = stdDev;
        this.bandColor = bandColor;
        this.middleColor = middleColor;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        List<Candle> candles = provider.getCandles();
        IndicatorEngine engine = provider.getIndicatorEngine();

        // Get Bollinger Bands from IndicatorEngine - NOT inline calculation
        Indicators.BollingerResult bb = engine.getBollingerBands(period, stdDev);
        if (bb == null) return;

        double[] upper = bb.upper();
        double[] middle = bb.middle();
        double[] lower = bb.lower();

        // Build time series for each band
        int startIdx = period - 1;

        TimeSeries upperSeries = TimeSeriesBuilder.createTimeSeries("Upper", candles, upper, startIdx);
        TimeSeries middleSeries = TimeSeriesBuilder.createTimeSeries("Middle", candles, middle, startIdx);
        TimeSeries lowerSeries = TimeSeriesBuilder.createTimeSeries("Lower", candles, lower, startIdx);

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(upperSeries);
        dataset.addSeries(middleSeries);
        dataset.addSeries(lowerSeries);

        // Create renderer with different colors for bands and middle
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, bandColor);       // Upper
        renderer.setSeriesPaint(1, middleColor);     // Middle
        renderer.setSeriesPaint(2, bandColor);       // Lower
        renderer.setSeriesStroke(0, ChartStyles.THIN_STROKE);
        renderer.setSeriesStroke(1, ChartStyles.DASHED_STROKE);
        renderer.setSeriesStroke(2, ChartStyles.THIN_STROKE);

        plot.setDataset(datasetIndex, dataset);
        plot.setRenderer(datasetIndex, renderer);
    }

    @Override
    public String getDisplayName() {
        return "BB(" + period + "," + stdDev + ")";
    }

    @Override
    public int getDatasetCount() {
        return 1;  // Single dataset with 3 series
    }

    public int getPeriod() {
        return period;
    }

    public double getStdDev() {
        return stdDev;
    }
}
