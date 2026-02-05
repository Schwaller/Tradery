package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.SmaCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.util.List;

/**
 * Simple Moving Average overlay.
 * Subscribes to SmaCompute for async background computation.
 */
public class SmaOverlay implements ChartOverlay {

    private final int period;
    private final Color color;
    private IndicatorSubscription<double[]> subscription;

    public SmaOverlay(int period) {
        this(period, ChartStyles.SMA_COLOR);
    }

    public SmaOverlay(int period, Color color) {
        this.period = period;
        this.color = color;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new SmaCompute(period));
        subscription.onReady(sma -> {
            if (sma == null || sma.length == 0) return;
            List<Candle> candles = provider.getCandles();
            if (candles == null || candles.isEmpty()) return;
            TimeSeriesCollection dataset = TimeSeriesBuilder.build(
                    getDisplayName(), candles, sma, period - 1);
            plot.setDataset(datasetIndex, dataset);
            plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(color));
            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public String getDisplayName() {
        return "SMA(" + period + ")";
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
