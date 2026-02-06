package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.StochasticCompute;
import com.tradery.core.indicators.Indicators;
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
 * Subscribes to StochasticCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 * Displays %K and %D lines with overbought/oversold zones.
 */
public class StochasticRenderer implements IndicatorChartRenderer {

    private final int kPeriod;
    private final int dPeriod;
    private final IndicatorSubscription<Indicators.StochasticResult> subscription;

    public StochasticRenderer(int kPeriod, int dPeriod, XYPlot plot, ChartDataProvider provider) {
        this.kPeriod = kPeriod;
        this.dPeriod = dPeriod;

        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new StochasticCompute(kPeriod, dPeriod));
        subscription.onReady(stoch -> {
            if (stoch == null) return;

            double[] kValues = stoch.k();
            double[] dValues = stoch.d();
            if (kValues == null || kValues.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "Stochastic");

            List<Candle> candles = provider.getCandles();

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries(
                "%K(" + kPeriod + ")", candles, kValues, kPeriod));
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries(
                "%D(" + dPeriod + ")", candles, dValues, kPeriod + dPeriod - 1));

            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(
                ChartStyles.STOCHASTIC_K_COLOR, ChartStyles.MEDIUM_STROKE,
                ChartStyles.STOCHASTIC_D_COLOR, ChartStyles.MEDIUM_STROKE));

            if (!candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                ChartAnnotationHelper.addStochasticLines(plot, startTime, endTime);
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
        return kPeriod + "," + dPeriod;
    }

    public int getKPeriod() {
        return kPeriod;
    }

    public int getDPeriod() {
        return dPeriod;
    }

    private static void clearPlot(XYPlot plot) {
        for (int i = 0; i < plot.getDatasetCount(); i++) plot.setDataset(i, null);
        plot.clearAnnotations();
    }
}
