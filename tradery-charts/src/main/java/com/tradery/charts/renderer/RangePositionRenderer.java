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
 * Shows where the current price is within the high-low range of the last N bars.
 * Values range from -1 (at range low) to +1 (at range high).
 * Uses IndicatorPool with RangePositionCompute for async calculation.
 */
public class RangePositionRenderer implements IndicatorChartRenderer {

    private static final Color HIGH_ZONE_COLOR = new Color(76, 175, 80, 50);   // Green transparent
    private static final Color LOW_ZONE_COLOR = new Color(244, 67, 54, 50);    // Red transparent
    private static final Color LINE_COLOR = new Color(33, 150, 243);           // Blue

    private final int period;
    private final int skip;
    private IndicatorSubscription<double[]> subscription;

    public RangePositionRenderer(int period) {
        this(period, 0);
    }

    public RangePositionRenderer(int period, int skip) {
        this.period = period;
        this.skip = skip;
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new RangePositionCompute(period, skip));
        subscription.onReady(rangePos -> {
            if (rangePos == null || rangePos.length == 0) return;

            List<Candle> candles = provider.getCandles();

            // Build time series
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

            // Add to plot
            plot.setDataset(0, dataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(LINE_COLOR, ChartStyles.MEDIUM_STROKE));

            // Set Y axis range to -1 to +1
            plot.getRangeAxis().setRange(-1.0, 1.0);

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
        if (skip > 0) {
            return String.format("(%d,%d)", period, skip);
        }
        return String.format("(%d)", period);
    }
}
