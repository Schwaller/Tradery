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
 * Subscribes to OpenInterestCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 */
public class OpenInterestRenderer implements IndicatorChartRenderer {

    private static final Color OI_COLOR = new Color(156, 39, 176);

    private final IndicatorSubscription<double[]> subscription;

    public OpenInterestRenderer(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new OpenInterestCompute());
        subscription.onReady(oi -> {
            if (oi == null || oi.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "OI");

            List<Candle> candles = provider.getCandles();

            TimeSeriesCollection dataset = TimeSeriesBuilder.build("OI", candles, oi, 0);

            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(OI_COLOR, ChartStyles.MEDIUM_STROKE));

            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public void close() {
        subscription.close();
    }

    @Override
    public String getParameterString() {
        return "";
    }

    private static void clearPlot(XYPlot plot) {
        for (int i = 0; i < plot.getDatasetCount(); i++) plot.setDataset(i, null);
        plot.clearAnnotations();
    }
}
