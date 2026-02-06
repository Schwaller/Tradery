package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.AtrCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import java.util.List;

/**
 * Renderer for ATR (Average True Range) indicator.
 * Subscribes to AtrCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 */
public class AtrRenderer implements IndicatorChartRenderer {

    private final int period;
    private final IndicatorSubscription<double[]> subscription;

    public AtrRenderer(int period, XYPlot plot, ChartDataProvider provider) {
        this.period = period;

        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new AtrCompute(period));
        subscription.onReady(atr -> {
            if (atr == null || atr.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "ATR");

            List<Candle> candles = provider.getCandles();

            TimeSeriesCollection dataset = TimeSeriesBuilder.build(
                "ATR(" + period + ")", candles, atr, period - 1);

            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(ChartStyles.ATR_COLOR));

            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public void close() {
        subscription.close();
    }

    @Override
    public String getParameterString() {
        return String.valueOf(period);
    }

    public int getPeriod() {
        return period;
    }

    private static void clearPlot(XYPlot plot) {
        for (int i = 0; i < plot.getDatasetCount(); i++) plot.setDataset(i, null);
        plot.clearAnnotations();
    }
}
