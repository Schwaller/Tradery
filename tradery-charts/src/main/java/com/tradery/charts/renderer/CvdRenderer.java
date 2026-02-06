package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.CumulativeDeltaCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.util.List;

/**
 * Renderer for Cumulative Volume Delta (CVD) indicator.
 * Subscribes to CumulativeDeltaCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 */
public class CvdRenderer implements IndicatorChartRenderer {

    private static final Color CVD_LINE_COLOR = new Color(33, 150, 243);

    private final IndicatorSubscription<double[]> subscription;

    public CvdRenderer(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new CumulativeDeltaCompute());
        subscription.onReady(cvd -> {
            if (cvd == null || cvd.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "CVD");

            List<Candle> candles = provider.getCandles();

            TimeSeriesCollection dataset = TimeSeriesBuilder.build("CVD", candles, cvd, 0);

            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(CVD_LINE_COLOR, ChartStyles.MEDIUM_STROKE));

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
