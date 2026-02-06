package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.AdxCompute;
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
 * Renderer for ADX (Average Directional Index) indicator.
 * Subscribes to AdxCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 * Displays ADX line with +DI and -DI, plus trend strength threshold at 25.
 */
public class AdxRenderer implements IndicatorChartRenderer {

    private final int period;
    private final IndicatorSubscription<Indicators.ADXResult> subscription;

    public AdxRenderer(int period, XYPlot plot, ChartDataProvider provider) {
        this.period = period;

        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new AdxCompute(period));
        subscription.onReady(adxResult -> {
            if (adxResult == null || adxResult.adx() == null || adxResult.adx().length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "ADX");

            List<Candle> candles = provider.getCandles();
            double[] adxValues = adxResult.adx();
            double[] plusDI = adxResult.plusDI();
            double[] minusDI = adxResult.minusDI();

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries(
                "ADX(" + period + ")", candles, adxValues, period));
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries(
                "+DI(" + period + ")", candles, plusDI, period));
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries(
                "-DI(" + period + ")", candles, minusDI, period));

            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(
                ChartStyles.ADX_COLOR, ChartStyles.MEDIUM_STROKE,
                ChartStyles.PLUS_DI_COLOR, ChartStyles.THIN_STROKE,
                ChartStyles.MINUS_DI_COLOR, ChartStyles.THIN_STROKE));

            if (!candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                ChartAnnotationHelper.addAdxLines(plot, startTime, endTime);
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
