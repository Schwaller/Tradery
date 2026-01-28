package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.DonchianCompute;
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
 * Subscribes to DonchianCompute for async background computation.
 */
public class DonchianChannelOverlay implements ChartOverlay {

    private static final Color UPPER_COLOR = new Color(0, 150, 136);
    private static final Color MIDDLE_COLOR = new Color(0, 150, 136, 128);
    private static final Color LOWER_COLOR = new Color(0, 150, 136);

    private final int period;
    private final boolean showMiddle;
    private IndicatorSubscription<DonchianCompute.Result> subscription;

    public DonchianChannelOverlay() {
        this(20, true);
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
        if (!provider.hasCandles()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new DonchianCompute(period));
        subscription.onReady(result -> {
            if (result == null) return;
            List<Candle> candles = provider.getCandles();
            if (candles == null || candles.isEmpty()) return;
            renderChannel(plot, datasetIndex, candles, result.highOf(), result.lowOf());
            plot.getChart().fireChartChanged();
        });
    }

    private void renderChannel(XYPlot plot, int datasetIndex, List<Candle> candles,
                                double[] highOf, double[] lowOf) {
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

        plot.setDataset(datasetIndex, new TimeSeriesCollection(upperSeries));
        plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(UPPER_COLOR, ChartStyles.MEDIUM_STROKE));

        int currentIndex = datasetIndex + 1;
        if (showMiddle) {
            plot.setDataset(currentIndex, new TimeSeriesCollection(middleSeries));
            plot.setRenderer(currentIndex, RendererBuilder.lineRenderer(MIDDLE_COLOR, ChartStyles.DASHED_STROKE));
            currentIndex++;
        }

        plot.setDataset(currentIndex, new TimeSeriesCollection(lowerSeries));
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
