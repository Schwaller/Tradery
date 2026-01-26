package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Supertrend;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.Date;
import java.util.List;

/**
 * Supertrend overlay.
 * Uses IndicatorEngine.getSupertrend() for calculation.
 * Displays the active Supertrend line (upper when downtrend, lower when uptrend).
 */
public class SupertrendOverlay implements ChartOverlay {

    private static final Color UPTREND_COLOR = new Color(76, 175, 80);    // Green
    private static final Color DOWNTREND_COLOR = new Color(244, 67, 54);  // Red

    private final int period;
    private final double multiplier;

    /**
     * Create a Supertrend overlay with default parameters (10, 3).
     */
    public SupertrendOverlay() {
        this(10, 3.0);
    }

    /**
     * Create a Supertrend overlay with custom parameters.
     */
    public SupertrendOverlay(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        List<Candle> candles = provider.getCandles();
        IndicatorEngine engine = provider.getIndicatorEngine();

        // Get Supertrend from IndicatorEngine
        Supertrend.Result result = engine.getSupertrend(period, multiplier);
        if (result == null) return;

        double[] upper = result.upperBand();
        double[] lower = result.lowerBand();
        double[] trend = result.trend();

        // Create separate series for uptrend and downtrend segments
        TimeSeries uptrendSeries = new TimeSeries("Supertrend Up");
        TimeSeries downtrendSeries = new TimeSeries("Supertrend Down");

        int warmup = period;

        for (int i = warmup; i < candles.size() && i < trend.length; i++) {
            Candle c = candles.get(i);
            Millisecond time = new Millisecond(new Date(c.timestamp()));

            if (trend[i] == 1) {
                // Uptrend - show lower band
                if (!Double.isNaN(lower[i])) {
                    uptrendSeries.addOrUpdate(time, lower[i]);
                }
            } else if (trend[i] == -1) {
                // Downtrend - show upper band
                if (!Double.isNaN(upper[i])) {
                    downtrendSeries.addOrUpdate(time, upper[i]);
                }
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(uptrendSeries);
        dataset.addSeries(downtrendSeries);

        // Create renderer with colors
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, UPTREND_COLOR);
        renderer.setSeriesPaint(1, DOWNTREND_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        renderer.setSeriesStroke(1, ChartStyles.MEDIUM_STROKE);

        plot.setDataset(datasetIndex, dataset);
        plot.setRenderer(datasetIndex, renderer);
    }

    @Override
    public String getDisplayName() {
        return "ST(" + period + "," + multiplier + ")";
    }

    public int getPeriod() {
        return period;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
