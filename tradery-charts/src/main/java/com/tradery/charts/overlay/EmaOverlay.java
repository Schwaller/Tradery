package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.EmaCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.List;

/**
 * Exponential Moving Average overlay.
 * Subscribes to EmaCompute for async background computation.
 */
public class EmaOverlay implements ChartOverlay {

    private final int period;
    private final Color color;
    private IndicatorSubscription<double[]> subscription;

    public EmaOverlay(int period) {
        this(period, ChartStyles.EMA_COLOR);
    }

    public EmaOverlay(int period, Color color) {
        this.period = period;
        this.color = color;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new EmaCompute(period));
        subscription.onReady(ema -> {
            if (ema == null || ema.length == 0) return;
            List<Candle> candles = provider.getCandles();
            if (candles == null || candles.isEmpty()) return;
            TimeSeriesCollection dataset = TimeSeriesBuilder.build(
                    getDisplayName(), candles, ema, period - 1);
            plot.setDataset(datasetIndex, dataset);
            plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(color));
            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public String getDisplayName() {
        return "EMA(" + period + ")";
    }

    public int getPeriod() {
        return period;
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }
}
