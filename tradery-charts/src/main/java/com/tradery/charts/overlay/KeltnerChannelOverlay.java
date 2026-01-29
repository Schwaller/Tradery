package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.KeltnerCompute;
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
 * Subscribes to KeltnerCompute for async background computation.
 */
public class KeltnerChannelOverlay implements ChartOverlay {

    private static final Color UPPER_COLOR = new Color(156, 39, 176, 180);
    private static final Color MIDDLE_COLOR = new Color(156, 39, 176);
    private static final Color LOWER_COLOR = new Color(156, 39, 176, 180);

    private final int emaPeriod;
    private final int atrPeriod;
    private final double multiplier;
    private IndicatorSubscription<KeltnerCompute.Result> subscription;

    public KeltnerChannelOverlay() {
        this(20, 10, 2.0);
    }

    public KeltnerChannelOverlay(int emaPeriod, int atrPeriod, double multiplier) {
        this.emaPeriod = emaPeriod;
        this.atrPeriod = atrPeriod;
        this.multiplier = multiplier;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new KeltnerCompute(emaPeriod, atrPeriod, multiplier));
        subscription.onReady(result -> {
            if (result == null) return;
            List<Candle> candles = provider.getCandles();
            if (candles == null || candles.isEmpty()) return;
            renderChannel(plot, datasetIndex, candles, result);
            plot.getChart().fireChartChanged();
        });
    }

    private void renderChannel(XYPlot plot, int datasetIndex, List<Candle> candles,
                                KeltnerCompute.Result result) {
        TimeSeries upperSeries = new TimeSeries("KC Upper");
        TimeSeries middleSeries = new TimeSeries("KC Middle");
        TimeSeries lowerSeries = new TimeSeries("KC Lower");

        for (int i = result.warmup(); i < candles.size() && i < result.upper().length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(result.upper()[i])) {
                Millisecond time = new Millisecond(new Date(c.timestamp()));
                upperSeries.addOrUpdate(time, result.upper()[i]);
                middleSeries.addOrUpdate(time, result.middle()[i]);
                lowerSeries.addOrUpdate(time, result.lower()[i]);
            }
        }

        TimeSeriesCollection upperDataset = new TimeSeriesCollection();
        upperDataset.addSeries(upperSeries);
        plot.setDataset(datasetIndex, upperDataset);
        plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(UPPER_COLOR, ChartStyles.THIN_STROKE));

        TimeSeriesCollection middleDataset = new TimeSeriesCollection();
        middleDataset.addSeries(middleSeries);
        plot.setDataset(datasetIndex + 1, middleDataset);
        plot.setRenderer(datasetIndex + 1, RendererBuilder.lineRenderer(MIDDLE_COLOR, ChartStyles.MEDIUM_STROKE));

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
        return 3;
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }
}
