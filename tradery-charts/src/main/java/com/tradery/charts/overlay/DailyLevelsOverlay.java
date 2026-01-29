package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.DailyLevelsCompute;
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
 * Daily session levels overlay.
 * Shows Previous Day POC/VAH/VAL and Today's developing POC/VAH/VAL.
 * These are key support/resistance levels for intraday trading.
 *
 * Subscribes to DailyLevelsCompute for async background computation.
 */
public class DailyLevelsOverlay implements ChartOverlay {

    // Previous day colors (solid)
    private static final Color PREV_POC_COLOR = new Color(255, 193, 7);       // Amber
    private static final Color PREV_VAH_COLOR = new Color(76, 175, 80);       // Green
    private static final Color PREV_VAL_COLOR = new Color(244, 67, 54);       // Red

    // Today colors (lighter/dashed)
    private static final Color TODAY_POC_COLOR = new Color(255, 193, 7, 180); // Amber semi-transparent
    private static final Color TODAY_VAH_COLOR = new Color(76, 175, 80, 180); // Green semi-transparent
    private static final Color TODAY_VAL_COLOR = new Color(244, 67, 54, 180); // Red semi-transparent

    private final boolean showPrevDay;
    private final boolean showToday;
    private IndicatorSubscription<DailyLevelsCompute.Result> subscription;

    public DailyLevelsOverlay() {
        this(true, true);  // Show both by default
    }

    public DailyLevelsOverlay(boolean showPrevDay, boolean showToday) {
        this.showPrevDay = showPrevDay;
        this.showToday = showToday;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        List<Candle> candles = provider.getCandles();
        if (candles == null || candles.isEmpty()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new DailyLevelsCompute(showPrevDay, showToday));
        subscription.onReady(result -> {
            if (result == null) return;
            List<Candle> c = provider.getCandles();
            if (c == null || c.isEmpty()) return;
            renderLevels(plot, datasetIndex, c, result);
            plot.getChart().fireChartChanged();
        });
    }

    private void renderLevels(XYPlot plot, int datasetIndex, List<Candle> candles,
                               DailyLevelsCompute.Result result) {
        int currentIndex = datasetIndex;

        if (showPrevDay) {
            currentIndex = addLevelSeries(plot, currentIndex, candles, "Prev POC", result.prevPoc(), PREV_POC_COLOR, ChartStyles.MEDIUM_STROKE);
            currentIndex = addLevelSeries(plot, currentIndex, candles, "Prev VAH", result.prevVah(), PREV_VAH_COLOR, ChartStyles.THIN_STROKE);
            currentIndex = addLevelSeries(plot, currentIndex, candles, "Prev VAL", result.prevVal(), PREV_VAL_COLOR, ChartStyles.THIN_STROKE);
        }

        if (showToday) {
            currentIndex = addLevelSeries(plot, currentIndex, candles, "Today POC", result.todayPoc(), TODAY_POC_COLOR, ChartStyles.DASHED_STROKE);
            currentIndex = addLevelSeries(plot, currentIndex, candles, "Today VAH", result.todayVah(), TODAY_VAH_COLOR, ChartStyles.DASHED_STROKE);
            addLevelSeries(plot, currentIndex, candles, "Today VAL", result.todayVal(), TODAY_VAL_COLOR, ChartStyles.DASHED_STROKE);
        }
    }

    private int addLevelSeries(XYPlot plot, int index, List<Candle> candles, String name,
                                double[] values, Color color, java.awt.Stroke stroke) {
        if (values == null) return index;

        TimeSeries series = new TimeSeries(name);
        for (int i = 0; i < candles.size() && i < values.length; i++) {
            double v = values[i];
            if (!Double.isNaN(v) && v > 0) {
                Candle c = candles.get(i);
                series.addOrUpdate(new Millisecond(new Date(c.timestamp())), v);
            }
        }

        if (series.getItemCount() > 0) {
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            plot.setDataset(index, dataset);
            plot.setRenderer(index, RendererBuilder.lineRenderer(color, stroke));
            return index + 1;
        }
        return index;
    }

    @Override
    public String getDisplayName() {
        if (showPrevDay && showToday) {
            return "Daily Levels";
        } else if (showPrevDay) {
            return "Prev Day Levels";
        } else {
            return "Today Levels";
        }
    }

    @Override
    public int getDatasetCount() {
        int count = 0;
        if (showPrevDay) count += 3;  // POC, VAH, VAL
        if (showToday) count += 3;
        return count;
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }
}
