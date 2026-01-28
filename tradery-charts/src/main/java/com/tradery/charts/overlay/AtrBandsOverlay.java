package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.AtrBandsCompute;
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
 * Subscribes to AtrBandsCompute for async background computation.
 */
public class AtrBandsOverlay implements ChartOverlay {

    private static final Color UPPER_BAND_COLOR = new Color(76, 175, 80, 150);
    private static final Color LOWER_BAND_COLOR = new Color(244, 67, 54, 150);

    private final int period;
    private final double multiplier;
    private IndicatorSubscription<AtrBandsCompute.Result> subscription;

    public AtrBandsOverlay() {
        this(14, 2.0);
    }

    public AtrBandsOverlay(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new AtrBandsCompute(period, multiplier));
        subscription.onReady(result -> {
            if (result == null) return;
            List<Candle> candles = provider.getCandles();
            if (candles == null || candles.isEmpty()) return;
            renderBands(plot, datasetIndex, candles, result);
            plot.getChart().fireChartChanged();
        });
    }

    private void renderBands(XYPlot plot, int datasetIndex, List<Candle> candles,
                              AtrBandsCompute.Result result) {
        TimeSeries upperSeries = new TimeSeries("ATR Upper");
        TimeSeries lowerSeries = new TimeSeries("ATR Lower");

        for (int i = result.warmup(); i < candles.size() && i < result.upper().length; i++) {
            if (!Double.isNaN(result.upper()[i])) {
                Candle c = candles.get(i);
                Millisecond time = new Millisecond(new Date(c.timestamp()));
                upperSeries.addOrUpdate(time, result.upper()[i]);
                lowerSeries.addOrUpdate(time, result.lower()[i]);
            }
        }

        TimeSeriesCollection upperDataset = new TimeSeriesCollection();
        upperDataset.addSeries(upperSeries);
        plot.setDataset(datasetIndex, upperDataset);
        plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(UPPER_BAND_COLOR, ChartStyles.DASHED_STROKE));

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
        return 2;
    }
}
