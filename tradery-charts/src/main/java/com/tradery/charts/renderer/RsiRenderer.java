package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.RsiCompute;
import com.tradery.charts.util.ChartAnnotationHelper;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import java.util.List;

/**
 * Renderer for RSI (Relative Strength Index) indicator.
 * Subscribes to RsiCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 */
public class RsiRenderer implements IndicatorChartRenderer {

    private final int period;
    private final IndicatorSubscription<double[]> subscription;

    public RsiRenderer(int period, XYPlot plot, ChartDataProvider provider) {
        this.period = period;

        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new RsiCompute(period));
        subscription.onReady(rsi -> {
            if (rsi == null || rsi.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "RSI");

            List<Candle> candles = provider.getCandles();

            TimeSeriesCollection dataset = TimeSeriesBuilder.build(
                "RSI(" + period + ")", candles, rsi, period);

            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(ChartStyles.RSI_COLOR));

            if (!candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                ChartAnnotationHelper.addRsiLines(plot, startTime, endTime);
            }

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
