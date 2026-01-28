package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.impl.StochasticCompute;
import com.tradery.charts.util.ChartAnnotationHelper;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import java.util.List;

/**
 * Renderer for Stochastic oscillator.
 * Uses IndicatorPool with StochasticCompute for async calculation.
 * Displays %K and %D lines with overbought/oversold zones.
 */
public class StochasticRenderer implements IndicatorChartRenderer {

    private final int kPeriod;
    private final int dPeriod;

    public StochasticRenderer(int kPeriod, int dPeriod) {
        this.kPeriod = kPeriod;
        this.dPeriod = dPeriod;
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        pool.subscribe(new StochasticCompute(kPeriod, dPeriod)).onReady(stoch -> {
            if (stoch == null) return;

            double[] kValues = stoch.k();
            double[] dValues = stoch.d();
            if (kValues == null || kValues.length == 0) return;

            List<Candle> candles = provider.getCandles();

            // Build time series for %K and %D
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries(
                "%K(" + kPeriod + ")", candles, kValues, kPeriod));
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries(
                "%D(" + dPeriod + ")", candles, dValues, kPeriod + dPeriod - 1));

            // Add to plot
            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(
                ChartStyles.STOCHASTIC_K_COLOR, ChartStyles.MEDIUM_STROKE,
                ChartStyles.STOCHASTIC_D_COLOR, ChartStyles.MEDIUM_STROKE));

            // Add reference lines (20, 50, 80)
            if (!candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                ChartAnnotationHelper.addStochasticLines(plot, startTime, endTime);
            }

            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public String getParameterString() {
        return kPeriod + "," + dPeriod;
    }

    public int getKPeriod() {
        return kPeriod;
    }

    public int getDPeriod() {
        return dPeriod;
    }
}
