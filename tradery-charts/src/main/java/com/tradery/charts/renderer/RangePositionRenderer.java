package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.RangePositionCompute;
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
 * Renderer for Range Position indicator.
 * Subscribes to RangePositionCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 * Values range from -1 (at range low) to +1 (at range high).
 */
public class RangePositionRenderer implements IndicatorChartRenderer {

    private static final Color LINE_COLOR = new Color(33, 150, 243);

    private final int period;
    private final int skip;
    private final IndicatorSubscription<double[]> subscription;

    public RangePositionRenderer(int period, int skip, XYPlot plot, ChartDataProvider provider) {
        this.period = period;
        this.skip = skip;

        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new RangePositionCompute(period, skip));
        subscription.onReady(rangePos -> {
            if (rangePos == null || rangePos.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "Range Position");

            List<Candle> candles = provider.getCandles();

            TimeSeries series = new TimeSeries("Range Position");
            int warmup = period + skip;
            for (int i = warmup; i < candles.size() && i < rangePos.length; i++) {
                Candle c = candles.get(i);
                if (!Double.isNaN(rangePos[i])) {
                    series.addOrUpdate(new Millisecond(new Date(c.timestamp())), rangePos[i]);
                }
            }

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);

            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(LINE_COLOR, ChartStyles.MEDIUM_STROKE));

            plot.getRangeAxis().setRange(-1.0, 1.0);

            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public void close() {
        subscription.close();
    }

    @Override
    public String getParameterString() {
        if (skip > 0) {
            return String.format("(%d,%d)", period, skip);
        }
        return String.format("(%d)", period);
    }

    private static void clearPlot(XYPlot plot) {
        for (int i = 0; i < plot.getDatasetCount(); i++) plot.setDataset(i, null);
        plot.clearAnnotations();
    }
}
