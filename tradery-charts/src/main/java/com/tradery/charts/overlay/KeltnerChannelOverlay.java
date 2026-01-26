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
 * Keltner Channel overlay.
 * Shows channels around an EMA using ATR for the band width:
 *   Upper = EMA + (ATR * multiplier)
 *   Middle = EMA
 *   Lower = EMA - (ATR * multiplier)
 *
 * Keltner Channels are smoother than Bollinger Bands since ATR
 * is less volatile than standard deviation.
 *
 * Uses IndicatorEngine.getEMA() and getATR() for data.
 */
public class KeltnerChannelOverlay implements ChartOverlay {

    private static final Color UPPER_COLOR = new Color(156, 39, 176, 180);    // Purple semi-transparent
    private static final Color MIDDLE_COLOR = new Color(156, 39, 176);        // Purple solid
    private static final Color LOWER_COLOR = new Color(156, 39, 176, 180);    // Purple semi-transparent

    private final int emaPeriod;
    private final int atrPeriod;
    private final double multiplier;

    public KeltnerChannelOverlay() {
        this(20, 10, 2.0);  // Default: 20-EMA, 10-ATR, 2x multiplier
    }

    public KeltnerChannelOverlay(int emaPeriod, int atrPeriod, double multiplier) {
        this.emaPeriod = emaPeriod;
        this.atrPeriod = atrPeriod;
        this.multiplier = multiplier;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        List<Candle> candles = provider.getCandles();
        if (candles.isEmpty()) return;

        // Get EMA and ATR from IndicatorEngine
        double[] ema = provider.getIndicatorEngine().getEMA(emaPeriod);
        double[] atr = provider.getIndicatorEngine().getATR(atrPeriod);
        if (ema == null || atr == null || ema.length == 0 || atr.length == 0) return;

        int warmup = Math.max(emaPeriod, atrPeriod);

        // Build channel series
        TimeSeries upperSeries = new TimeSeries("KC Upper");
        TimeSeries middleSeries = new TimeSeries("KC Middle");
        TimeSeries lowerSeries = new TimeSeries("KC Lower");

        for (int i = warmup; i < candles.size() && i < ema.length && i < atr.length; i++) {
            Candle c = candles.get(i);
            double emaVal = ema[i];
            double atrVal = atr[i];
            if (!Double.isNaN(emaVal) && !Double.isNaN(atrVal)) {
                double offset = atrVal * multiplier;
                Millisecond time = new Millisecond(new Date(c.timestamp()));
                upperSeries.addOrUpdate(time, emaVal + offset);
                middleSeries.addOrUpdate(time, emaVal);
                lowerSeries.addOrUpdate(time, emaVal - offset);
            }
        }

        // Add upper band
        TimeSeriesCollection upperDataset = new TimeSeriesCollection();
        upperDataset.addSeries(upperSeries);
        plot.setDataset(datasetIndex, upperDataset);
        plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(UPPER_COLOR, ChartStyles.THIN_STROKE));

        // Add middle line (EMA)
        TimeSeriesCollection middleDataset = new TimeSeriesCollection();
        middleDataset.addSeries(middleSeries);
        plot.setDataset(datasetIndex + 1, middleDataset);
        plot.setRenderer(datasetIndex + 1, RendererBuilder.lineRenderer(MIDDLE_COLOR, ChartStyles.MEDIUM_STROKE));

        // Add lower band
        TimeSeriesCollection lowerDataset = new TimeSeriesCollection();
        lowerDataset.addSeries(lowerSeries);
        plot.setDataset(datasetIndex + 2, lowerDataset);
        plot.setRenderer(datasetIndex + 2, RendererBuilder.lineRenderer(LOWER_COLOR, ChartStyles.THIN_STROKE));
    }

    @Override
    public String getDisplayName() {
        return String.format("Keltner (%d, %d, %.1f)", emaPeriod, atrPeriod, multiplier);
    }

    @Override
    public int getDatasetCount() {
        return 3;  // Upper, middle, lower
    }
}
