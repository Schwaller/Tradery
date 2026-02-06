package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.OpenInterestCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.util.List;

/**
 * Renderer for Open Interest indicator.
 * Uses IndicatorPool with OpenInterestCompute for async calculation.
 * Displays open interest as a line chart.
 */
public class OpenInterestRenderer implements IndicatorChartRenderer {

    private static final Color OI_COLOR = new Color(156, 39, 176);  // Purple

    private IndicatorSubscription<double[]> subscription;

    public OpenInterestRenderer() {
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new OpenInterestCompute());
        subscription.onReady(oi -> {
            if (oi == null || oi.length == 0) return;

            List<Candle> candles = provider.getCandles();

            // Build time series
            TimeSeriesCollection dataset = TimeSeriesBuilder.build("OI", candles, oi, 0);

            // Add to plot
            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(OI_COLOR, ChartStyles.MEDIUM_STROKE));

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
