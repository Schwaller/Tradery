package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.Date;
import java.util.List;

/**
 * Donchian Channel overlay.
 * Shows the highest high and lowest low over a lookback period:
 *   Upper = Highest high of last N bars
 *   Middle = (Upper + Lower) / 2
 *   Lower = Lowest low of last N bars
 *
 * Donchian Channels are used for trend following and breakout trading.
 * A close above the upper channel is a buy signal; below lower is a sell.
 *
 * Uses IndicatorEngine.getHighOf() and getLowOf() for data.
 */
public class DonchianChannelOverlay implements ChartOverlay {

    private static final Color UPPER_COLOR = new Color(0, 150, 136);          // Teal
    private static final Color MIDDLE_COLOR = new Color(0, 150, 136, 128);    // Teal semi-transparent
    private static final Color LOWER_COLOR = new Color(0, 150, 136);          // Teal

    private final int period;
    private final boolean showMiddle;

    public DonchianChannelOverlay() {
        this(20, true);  // Default: 20-bar Donchian with middle line
    }

    public DonchianChannelOverlay(int period) {
        this(period, true);
    }

    public DonchianChannelOverlay(int period, boolean showMiddle) {
        this.period = period;
        this.showMiddle = showMiddle;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        List<Candle> candles = provider.getCandles();
        if (candles.isEmpty()) return;

        // Get high and low from IndicatorEngine
        double[] highOf = provider.getIndicatorEngine().getHighOf(period);
        double[] lowOf = provider.getIndicatorEngine().getLowOf(period);
        if (highOf == null || lowOf == null || highOf.length == 0 || lowOf.length == 0) return;

        // Build channel series
        TimeSeries upperSeries = new TimeSeries("DC Upper");
        TimeSeries middleSeries = new TimeSeries("DC Middle");
        TimeSeries lowerSeries = new TimeSeries("DC Lower");

        for (int i = period; i < candles.size() && i < highOf.length && i < lowOf.length; i++) {
            Candle c = candles.get(i);
            double high = highOf[i];
            double low = lowOf[i];
            if (!Double.isNaN(high) && !Double.isNaN(low)) {
                Millisecond time = new Millisecond(new Date(c.timestamp()));
                upperSeries.addOrUpdate(time, high);
                lowerSeries.addOrUpdate(time, low);
                if (showMiddle) {
                    middleSeries.addOrUpdate(time, (high + low) / 2);
                }
            }
        }

        // Add upper band
        TimeSeriesCollection upperDataset = new TimeSeriesCollection();
        upperDataset.addSeries(upperSeries);
        plot.setDataset(datasetIndex, upperDataset);
        plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(UPPER_COLOR, ChartStyles.MEDIUM_STROKE));

        int currentIndex = datasetIndex + 1;

        // Add middle line if enabled
        if (showMiddle) {
            TimeSeriesCollection middleDataset = new TimeSeriesCollection();
            middleDataset.addSeries(middleSeries);
            plot.setDataset(currentIndex, middleDataset);
            plot.setRenderer(currentIndex, RendererBuilder.lineRenderer(MIDDLE_COLOR, ChartStyles.DASHED_STROKE));
            currentIndex++;
        }

        // Add lower band
        TimeSeriesCollection lowerDataset = new TimeSeriesCollection();
        lowerDataset.addSeries(lowerSeries);
        plot.setDataset(currentIndex, lowerDataset);
        plot.setRenderer(currentIndex, RendererBuilder.lineRenderer(LOWER_COLOR, ChartStyles.MEDIUM_STROKE));
    }

    @Override
    public String getDisplayName() {
        return String.format("Donchian (%d)", period);
    }

    @Override
    public int getDatasetCount() {
        return showMiddle ? 3 : 2;
    }
}
