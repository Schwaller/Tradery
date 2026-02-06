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
 * Subscribes to PremiumCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 */
public class PremiumRenderer implements IndicatorChartRenderer {

    private static final Color LINE_COLOR = new Color(156, 39, 176);

    private final IndicatorSubscription<double[]> subscription;

    public PremiumRenderer(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new PremiumCompute());
        subscription.onReady(premium -> {
            if (premium == null || premium.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "Premium");

            List<Candle> candles = provider.getCandles();

            TimeSeries series = new TimeSeries("Premium");
            for (int i = 0; i < candles.size() && i < premium.length; i++) {
                Candle c = candles.get(i);
                if (!Double.isNaN(premium[i])) {
                    series.addOrUpdate(new Millisecond(new Date(c.timestamp())), premium[i]);
                }
            }

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);

            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(LINE_COLOR, ChartStyles.MEDIUM_STROKE));

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
