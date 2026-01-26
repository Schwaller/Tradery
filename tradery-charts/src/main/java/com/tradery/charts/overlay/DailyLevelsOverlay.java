package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.Date;
import java.util.List;

/**
 * Daily session levels overlay.
 * Shows Previous Day POC/VAH/VAL and Today's developing POC/VAH/VAL.
 * These are key support/resistance levels for intraday trading.
 *
 * Uses IndicatorEngine.getPrevDayPOCAt(), getTodayPOCAt(), etc.
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
        if (candles.isEmpty()) return;

        IndicatorEngine engine = provider.getIndicatorEngine();
        int currentIndex = datasetIndex;

        if (showPrevDay) {
            // Previous Day POC
            TimeSeries prevPocSeries = new TimeSeries("Prev POC");
            for (int i = 0; i < candles.size(); i++) {
                double poc = engine.getPrevDayPOCAt(i);
                if (!Double.isNaN(poc) && poc > 0) {
                    Candle c = candles.get(i);
                    prevPocSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), poc);
                }
            }
            if (prevPocSeries.getItemCount() > 0) {
                TimeSeriesCollection dataset = new TimeSeriesCollection();
                dataset.addSeries(prevPocSeries);
                plot.setDataset(currentIndex, dataset);
                plot.setRenderer(currentIndex, RendererBuilder.lineRenderer(PREV_POC_COLOR, ChartStyles.MEDIUM_STROKE));
                currentIndex++;
            }

            // Previous Day VAH
            TimeSeries prevVahSeries = new TimeSeries("Prev VAH");
            for (int i = 0; i < candles.size(); i++) {
                double vah = engine.getPrevDayVAHAt(i);
                if (!Double.isNaN(vah) && vah > 0) {
                    Candle c = candles.get(i);
                    prevVahSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), vah);
                }
            }
            if (prevVahSeries.getItemCount() > 0) {
                TimeSeriesCollection dataset = new TimeSeriesCollection();
                dataset.addSeries(prevVahSeries);
                plot.setDataset(currentIndex, dataset);
                plot.setRenderer(currentIndex, RendererBuilder.lineRenderer(PREV_VAH_COLOR, ChartStyles.THIN_STROKE));
                currentIndex++;
            }

            // Previous Day VAL
            TimeSeries prevValSeries = new TimeSeries("Prev VAL");
            for (int i = 0; i < candles.size(); i++) {
                double val = engine.getPrevDayVALAt(i);
                if (!Double.isNaN(val) && val > 0) {
                    Candle c = candles.get(i);
                    prevValSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), val);
                }
            }
            if (prevValSeries.getItemCount() > 0) {
                TimeSeriesCollection dataset = new TimeSeriesCollection();
                dataset.addSeries(prevValSeries);
                plot.setDataset(currentIndex, dataset);
                plot.setRenderer(currentIndex, RendererBuilder.lineRenderer(PREV_VAL_COLOR, ChartStyles.THIN_STROKE));
                currentIndex++;
            }
        }

        if (showToday) {
            // Today's developing POC
            TimeSeries todayPocSeries = new TimeSeries("Today POC");
            for (int i = 0; i < candles.size(); i++) {
                double poc = engine.getTodayPOCAt(i);
                if (!Double.isNaN(poc) && poc > 0) {
                    Candle c = candles.get(i);
                    todayPocSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), poc);
                }
            }
            if (todayPocSeries.getItemCount() > 0) {
                TimeSeriesCollection dataset = new TimeSeriesCollection();
                dataset.addSeries(todayPocSeries);
                plot.setDataset(currentIndex, dataset);
                plot.setRenderer(currentIndex, RendererBuilder.lineRenderer(TODAY_POC_COLOR, ChartStyles.DASHED_STROKE));
                currentIndex++;
            }

            // Today's developing VAH
            TimeSeries todayVahSeries = new TimeSeries("Today VAH");
            for (int i = 0; i < candles.size(); i++) {
                double vah = engine.getTodayVAHAt(i);
                if (!Double.isNaN(vah) && vah > 0) {
                    Candle c = candles.get(i);
                    todayVahSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), vah);
                }
            }
            if (todayVahSeries.getItemCount() > 0) {
                TimeSeriesCollection dataset = new TimeSeriesCollection();
                dataset.addSeries(todayVahSeries);
                plot.setDataset(currentIndex, dataset);
                plot.setRenderer(currentIndex, RendererBuilder.lineRenderer(TODAY_VAH_COLOR, ChartStyles.DASHED_STROKE));
                currentIndex++;
            }

            // Today's developing VAL
            TimeSeries todayValSeries = new TimeSeries("Today VAL");
            for (int i = 0; i < candles.size(); i++) {
                double val = engine.getTodayVALAt(i);
                if (!Double.isNaN(val) && val > 0) {
                    Candle c = candles.get(i);
                    todayValSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), val);
                }
            }
            if (todayValSeries.getItemCount() > 0) {
                TimeSeriesCollection dataset = new TimeSeriesCollection();
                dataset.addSeries(todayValSeries);
                plot.setDataset(currentIndex, dataset);
                plot.setRenderer(currentIndex, RendererBuilder.lineRenderer(TODAY_VAL_COLOR, ChartStyles.DASHED_STROKE));
            }
        }
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
}
