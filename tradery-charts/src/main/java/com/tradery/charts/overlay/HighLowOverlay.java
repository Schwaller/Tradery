package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.List;

/**
 * High/Low range overlay.
 * Uses IndicatorEngine.getHighOf() and getLowOf() for calculation.
 * Displays the highest high and lowest low over the given period.
 */
public class HighLowOverlay implements ChartOverlay {

    private static final Color HIGH_COLOR = new Color(255, 87, 34, 180);  // Orange
    private static final Color LOW_COLOR = new Color(33, 150, 243, 180);  // Blue

    private final int period;

    /**
     * Create a High/Low overlay with default period (20).
     */
    public HighLowOverlay() {
        this(20);
    }

    /**
     * Create a High/Low overlay with custom period.
     */
    public HighLowOverlay(int period) {
        this.period = period;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        List<Candle> candles = provider.getCandles();
        IndicatorEngine engine = provider.getIndicatorEngine();

        // Get High/Low from IndicatorEngine
        double[] high = engine.getHighOf(period);
        double[] low = engine.getLowOf(period);
        if (high == null || low == null) return;

        int startIdx = period - 1;

        // Build time series for high and low
        TimeSeries highSeries = TimeSeriesBuilder.createTimeSeries("High(" + period + ")", candles, high, startIdx);
        TimeSeries lowSeries = TimeSeriesBuilder.createTimeSeries("Low(" + period + ")", candles, low, startIdx);

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(highSeries);
        dataset.addSeries(lowSeries);

        // Create renderer with colors
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, HIGH_COLOR);
        renderer.setSeriesPaint(1, LOW_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.DASHED_STROKE);
        renderer.setSeriesStroke(1, ChartStyles.DASHED_STROKE);

        plot.setDataset(datasetIndex, dataset);
        plot.setRenderer(datasetIndex, renderer);
    }

    @Override
    public String getDisplayName() {
        return "H/L(" + period + ")";
    }

    public int getPeriod() {
        return period;
    }
}
