package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.DonchianCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.List;

/**
 * High/Low range overlay.
 * Uses DonchianCompute (same data: highest high / lowest low) for async computation.
 */
public class HighLowOverlay implements ChartOverlay {

    private static final Color HIGH_COLOR = new Color(255, 87, 34, 180);
    private static final Color LOW_COLOR = new Color(33, 150, 243, 180);

    private final int period;
    private IndicatorSubscription<DonchianCompute.Result> subscription;

    public HighLowOverlay() {
        this(20);
    }

    public HighLowOverlay(int period) {
        this.period = period;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new DonchianCompute(period));
        subscription.onReady(result -> {
            if (result == null) return;
            List<Candle> candles = provider.getCandles();
            if (candles == null || candles.isEmpty()) return;
            renderHighLow(plot, datasetIndex, candles, result.highOf(), result.lowOf());
            plot.getChart().fireChartChanged();
        });
    }

    private void renderHighLow(XYPlot plot, int datasetIndex, List<Candle> candles,
                                double[] high, double[] low) {
        int startIdx = period - 1;
        TimeSeries highSeries = TimeSeriesBuilder.createTimeSeries("High(" + period + ")", candles, high, startIdx);
        TimeSeries lowSeries = TimeSeriesBuilder.createTimeSeries("Low(" + period + ")", candles, low, startIdx);

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(highSeries);
        dataset.addSeries(lowSeries);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, HIGH_COLOR);
        renderer.setSeriesPaint(1, LOW_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.DASHED_STROKE);
        renderer.setSeriesStroke(1, ChartStyles.DASHED_STROKE);

        plot.setDataset(datasetIndex, dataset);
        plot.setRenderer(datasetIndex, renderer);
    }

    @Override
    public String getDisplayName() {
        return "H/L(" + period + ")";
    }

    public int getPeriod() {
        return period;
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }
}
