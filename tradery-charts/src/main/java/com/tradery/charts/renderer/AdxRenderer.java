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
 * Uses IndicatorPool with AdxCompute for async calculation.
 * Displays ADX line with +DI and -DI, plus trend strength threshold at 25.
 */
public class AdxRenderer implements IndicatorChartRenderer {

    private final int period;
    private IndicatorSubscription<Indicators.ADXResult> subscription;

    public AdxRenderer(int period) {
        this.period = period;
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new AdxCompute(period));
        subscription.onReady(adxResult -> {
            if (adxResult == null || adxResult.adx() == null || adxResult.adx().length == 0) return;

            List<Candle> candles = provider.getCandles();
            double[] adxValues = adxResult.adx();
            double[] plusDI = adxResult.plusDI();
            double[] minusDI = adxResult.minusDI();

            // Build time series for ADX, +DI, -DI
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries(
                "ADX(" + period + ")", candles, adxValues, period));
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries(
                "+DI(" + period + ")", candles, plusDI, period));
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries(
                "-DI(" + period + ")", candles, minusDI, period));

            // Add to plot
            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(
                ChartStyles.ADX_COLOR, ChartStyles.MEDIUM_STROKE,       // ADX - orange
                ChartStyles.PLUS_DI_COLOR, ChartStyles.THIN_STROKE,     // +DI - green
                ChartStyles.MINUS_DI_COLOR, ChartStyles.THIN_STROKE));  // -DI - red

            // Add reference lines (25 = trend threshold)
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
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    @Override
    public String getParameterString() {
        return String.valueOf(period);
    }

    public int getPeriod() {
        return period;
    }
}
