package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
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
 * Uses IndicatorPool with RsiCompute for async calculation.
 */
public class RsiRenderer implements IndicatorChartRenderer {

    private final int period;

    public RsiRenderer(int period) {
        this.period = period;
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        pool.subscribe(new RsiCompute(period)).onReady(rsi -> {
            if (rsi == null || rsi.length == 0) return;

            List<Candle> candles = provider.getCandles();

            // Build time series
            TimeSeriesCollection dataset = TimeSeriesBuilder.build(
                "RSI(" + period + ")", candles, rsi, period);

            // Add to plot
            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(ChartStyles.RSI_COLOR));

            // Add reference lines (30, 50, 70)
            if (!candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                ChartAnnotationHelper.addRsiLines(plot, startTime, endTime);
            }

            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public String getParameterString() {
        return String.valueOf(period);
    }

    public int getPeriod() {
        return period;
    }
}
