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
 * Shows the proportion of buying vs selling pressure.
 * Values above 0.5 indicate more buying, below 0.5 more selling.
 * Uses IndicatorPool with VolumeRatioCompute for async calculation.
 */
public class VolumeRatioRenderer implements IndicatorChartRenderer {

    private static final Color BUY_COLOR = new Color(76, 175, 80);     // Green
    private static final Color SELL_COLOR = new Color(244, 67, 54);    // Red
    private static final Color NEUTRAL_COLOR = new Color(158, 158, 158); // Gray
    private static final Color RATIO_LINE_COLOR = new Color(255, 193, 7); // Amber

    private IndicatorSubscription<VolumeRatioCompute.Result> subscription;

    public VolumeRatioRenderer() {
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new VolumeRatioCompute());
        subscription.onReady(result -> {
            if (result == null) return;

            double[] buyVol = result.buyVolume();
            double[] sellVol = result.sellVolume();
            if (buyVol == null || sellVol == null || buyVol.length == 0) return;

            List<Candle> candles = provider.getCandles();

            // Calculate ratio: buyVol / (buyVol + sellVol)
            double[] ratio = new double[buyVol.length];
            for (int i = 0; i < buyVol.length; i++) {
                double total = buyVol[i] + sellVol[i];
                ratio[i] = total > 0 ? buyVol[i] / total : 0.5;
            }

            // Build time series
            TimeSeries ratioSeries = new TimeSeries("Volume Ratio");
            for (int i = 0; i < candles.size() && i < ratio.length; i++) {
                Candle c = candles.get(i);
                if (!Double.isNaN(ratio[i])) {
                    ratioSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), ratio[i]);
                }
            }

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(ratioSeries);

            // Add to plot with line renderer
            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(RATIO_LINE_COLOR, ChartStyles.MEDIUM_STROKE));

            // Set Y axis range to 0-1
            plot.getRangeAxis().setRange(0.0, 1.0);

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
