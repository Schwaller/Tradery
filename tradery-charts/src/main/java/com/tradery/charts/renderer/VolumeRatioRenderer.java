package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.VolumeRatioCompute;
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
 * Renderer for Volume Ratio indicator (Buy Volume / Total Volume).
 * Subscribes to VolumeRatioCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 * Values above 0.5 indicate more buying, below 0.5 more selling.
 */
public class VolumeRatioRenderer implements IndicatorChartRenderer {

    private static final Color RATIO_LINE_COLOR = new Color(255, 193, 7);

    private final IndicatorSubscription<VolumeRatioCompute.Result> subscription;

    public VolumeRatioRenderer(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new VolumeRatioCompute());
        subscription.onReady(result -> {
            if (result == null) return;

            double[] buyVol = result.buyVolume();
            double[] sellVol = result.sellVolume();
            if (buyVol == null || sellVol == null || buyVol.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "Volume Ratio");

            List<Candle> candles = provider.getCandles();

            double[] ratio = new double[buyVol.length];
            for (int i = 0; i < buyVol.length; i++) {
                double total = buyVol[i] + sellVol[i];
                ratio[i] = total > 0 ? buyVol[i] / total : 0.5;
            }

            TimeSeries ratioSeries = new TimeSeries("Volume Ratio");
            for (int i = 0; i < candles.size() && i < ratio.length; i++) {
                Candle c = candles.get(i);
                if (!Double.isNaN(ratio[i])) {
                    ratioSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), ratio[i]);
                }
            }

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(ratioSeries);

            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(RATIO_LINE_COLOR, ChartStyles.MEDIUM_STROKE));

            plot.getRangeAxis().setRange(0.0, 1.0);

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
