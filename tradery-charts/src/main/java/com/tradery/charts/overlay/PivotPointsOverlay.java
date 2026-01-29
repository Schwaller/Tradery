package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.PivotPointsCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.Date;
import java.util.List;

/**
 * Classic Pivot Points overlay.
 * Uses PivotPointsCompute for async background computation.
 */
public class PivotPointsOverlay implements ChartOverlay {

    private static final Color PIVOT_COLOR = new Color(255, 193, 7);
    private static final Color R1_COLOR = new Color(76, 175, 80, 200);
    private static final Color R2_COLOR = new Color(76, 175, 80, 150);
    private static final Color R3_COLOR = new Color(76, 175, 80, 100);
    private static final Color S1_COLOR = new Color(244, 67, 54, 200);
    private static final Color S2_COLOR = new Color(244, 67, 54, 150);
    private static final Color S3_COLOR = new Color(244, 67, 54, 100);

    private final boolean showR3S3;
    private IndicatorSubscription<PivotPointsCompute.Result> subscription;

    public PivotPointsOverlay() {
        this(false);
    }

    public PivotPointsOverlay(boolean showR3S3) {
        this.showR3S3 = showR3S3;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        List<Candle> candles = provider.getCandles();
        if (candles == null || candles.size() < 2) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new PivotPointsCompute());
        subscription.onReady(result -> {
            if (result == null) return;
            List<Candle> c = provider.getCandles();
            if (c == null || c.size() < 2) return;
            renderPivots(plot, datasetIndex, c, result);
            plot.getChart().fireChartChanged();
        });
    }

    private void renderPivots(XYPlot plot, int datasetIndex, List<Candle> candles,
                               PivotPointsCompute.Result result) {
        long startTime = candles.get(result.startIndex()).timestamp();
        long endTime = candles.get(candles.size() - 1).timestamp();

        int currentIndex = datasetIndex;
        currentIndex = addHorizontalLine(plot, currentIndex, "Pivot", result.pivot(), startTime, endTime, PIVOT_COLOR, ChartStyles.MEDIUM_STROKE);
        currentIndex = addHorizontalLine(plot, currentIndex, "R1", result.r1(), startTime, endTime, R1_COLOR, ChartStyles.THIN_STROKE);
        currentIndex = addHorizontalLine(plot, currentIndex, "S1", result.s1(), startTime, endTime, S1_COLOR, ChartStyles.THIN_STROKE);
        currentIndex = addHorizontalLine(plot, currentIndex, "R2", result.r2(), startTime, endTime, R2_COLOR, ChartStyles.THIN_STROKE);
        currentIndex = addHorizontalLine(plot, currentIndex, "S2", result.s2(), startTime, endTime, S2_COLOR, ChartStyles.THIN_STROKE);
        if (showR3S3) {
            currentIndex = addHorizontalLine(plot, currentIndex, "R3", result.r3(), startTime, endTime, R3_COLOR, ChartStyles.DASHED_STROKE);
            addHorizontalLine(plot, currentIndex, "S3", result.s3(), startTime, endTime, S3_COLOR, ChartStyles.DASHED_STROKE);
        }
    }

    private int addHorizontalLine(XYPlot plot, int index, String name, double value,
                                   long startTime, long endTime, Color color, java.awt.Stroke stroke) {
        TimeSeries series = new TimeSeries(name);
        series.addOrUpdate(new Millisecond(new Date(startTime)), value);
        series.addOrUpdate(new Millisecond(new Date(endTime)), value);

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(series);
        plot.setDataset(index, dataset);
        plot.setRenderer(index, RendererBuilder.lineRenderer(color, stroke));

        return index + 1;
    }

    @Override
    public String getDisplayName() {
        return "Pivot Points";
    }

    @Override
    public int getDatasetCount() {
        return showR3S3 ? 7 : 5;
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }
}
