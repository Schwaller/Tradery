package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.SupertrendCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.indicators.Supertrend;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.util.Date;
import java.util.List;

/**
 * Supertrend overlay.
 * Subscribes to SupertrendCompute for async background computation.
 */
public class SupertrendOverlay implements ChartOverlay {

    private static final Color UPTREND_COLOR = new Color(76, 175, 80);
    private static final Color DOWNTREND_COLOR = new Color(244, 67, 54);

    private final int period;
    private final double multiplier;
    private IndicatorSubscription<Supertrend.Result> subscription;

    public SupertrendOverlay() {
        this(10, 3.0);
    }

    public SupertrendOverlay(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new SupertrendCompute(period, multiplier));
        subscription.onReady(result -> {
            if (result == null) return;
            List<Candle> candles = provider.getCandles();
            if (candles == null || candles.isEmpty()) return;
            renderSupertrend(plot, datasetIndex, candles, result);
            plot.getChart().fireChartChanged();
        });
    }

    private void renderSupertrend(XYPlot plot, int datasetIndex, List<Candle> candles,
                                    Supertrend.Result result) {
        double[] upper = result.upperBand();
        double[] lower = result.lowerBand();
        double[] trend = result.trend();

        TimeSeries uptrendSeries = new TimeSeries("Supertrend Up");
        TimeSeries downtrendSeries = new TimeSeries("Supertrend Down");

        int warmup = period;
        for (int i = warmup; i < candles.size() && i < trend.length; i++) {
            Candle c = candles.get(i);
            Millisecond time = new Millisecond(new Date(c.timestamp()));
            if (trend[i] == 1) {
                if (!Double.isNaN(lower[i])) uptrendSeries.addOrUpdate(time, lower[i]);
            } else if (trend[i] == -1) {
                if (!Double.isNaN(upper[i])) downtrendSeries.addOrUpdate(time, upper[i]);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(uptrendSeries);
        dataset.addSeries(downtrendSeries);

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

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }
}
