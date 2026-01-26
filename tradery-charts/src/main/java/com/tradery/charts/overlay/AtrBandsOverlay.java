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
 * ATR Bands overlay.
 * Shows upper and lower bands calculated as:
 *   Upper = Close + (ATR * multiplier)
 *   Lower = Close - (ATR * multiplier)
 *
 * Useful for volatility-based stop placement and target setting.
 * Uses IndicatorEngine.getATR() for data.
 */
public class AtrBandsOverlay implements ChartOverlay {

    private static final Color UPPER_BAND_COLOR = new Color(76, 175, 80, 150);   // Green semi-transparent
    private static final Color LOWER_BAND_COLOR = new Color(244, 67, 54, 150);   // Red semi-transparent

    private final int period;
    private final double multiplier;

    public AtrBandsOverlay() {
        this(14, 2.0);  // Default: 14-period ATR with 2x multiplier
    }

    public AtrBandsOverlay(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        List<Candle> candles = provider.getCandles();
        if (candles.isEmpty()) return;

        // Get ATR from IndicatorEngine
        double[] atr = provider.getIndicatorEngine().getATR(period);
        if (atr == null || atr.length == 0) return;

        // Build upper band series
        TimeSeries upperSeries = new TimeSeries("ATR Upper");
        TimeSeries lowerSeries = new TimeSeries("ATR Lower");

        for (int i = period; i < candles.size() && i < atr.length; i++) {
            Candle c = candles.get(i);
            double atrVal = atr[i];
            if (!Double.isNaN(atrVal)) {
                double close = c.close();
                double offset = atrVal * multiplier;
                upperSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), close + offset);
                lowerSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), close - offset);
            }
        }

        // Add upper band
        TimeSeriesCollection upperDataset = new TimeSeriesCollection();
        upperDataset.addSeries(upperSeries);
        plot.setDataset(datasetIndex, upperDataset);
        plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(UPPER_BAND_COLOR, ChartStyles.DASHED_STROKE));

        // Add lower band
        TimeSeriesCollection lowerDataset = new TimeSeriesCollection();
        lowerDataset.addSeries(lowerSeries);
        plot.setDataset(datasetIndex + 1, lowerDataset);
        plot.setRenderer(datasetIndex + 1, RendererBuilder.lineRenderer(LOWER_BAND_COLOR, ChartStyles.DASHED_STROKE));
    }

    @Override
    public String getDisplayName() {
        return String.format("ATR Bands (%d, %.1f)", period, multiplier);
    }

    @Override
    public int getDatasetCount() {
        return 2;  // Upper and lower bands
    }
}
