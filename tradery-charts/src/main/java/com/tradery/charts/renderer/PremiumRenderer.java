package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.PremiumCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.util.Date;
import java.util.List;

/**
 * Renderer for Premium/Basis indicator.
 * Shows the difference between futures and spot price (as percentage).
 * Positive values indicate futures trading at a premium (contango).
 * Negative values indicate futures trading at a discount (backwardation).
 * Uses IndicatorPool with PremiumCompute for async calculation.
 */
public class PremiumRenderer implements IndicatorChartRenderer {

    private static final Color PREMIUM_COLOR = new Color(76, 175, 80);    // Green (contango)
    private static final Color DISCOUNT_COLOR = new Color(244, 67, 54);   // Red (backwardation)
    private static final Color LINE_COLOR = new Color(156, 39, 176);      // Purple

    private IndicatorSubscription<double[]> subscription;

    public PremiumRenderer() {
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new PremiumCompute());
        subscription.onReady(premium -> {
            if (premium == null || premium.length == 0) return;

            List<Candle> candles = provider.getCandles();

            // Build time series
            TimeSeries series = new TimeSeries("Premium");
            for (int i = 0; i < candles.size() && i < premium.length; i++) {
                Candle c = candles.get(i);
                if (!Double.isNaN(premium[i])) {
                    series.addOrUpdate(new Millisecond(new Date(c.timestamp())), premium[i]);
                }
            }

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);

            // Add to plot
            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(LINE_COLOR, ChartStyles.MEDIUM_STROKE));

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
        return "";
    }
}
