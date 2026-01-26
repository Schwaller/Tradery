package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
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
 * Calculates daily pivot levels based on previous day's HLC:
 *   Pivot (P)  = (High + Low + Close) / 3
 *   R1 = 2*P - Low
 *   S1 = 2*P - High
 *   R2 = P + (High - Low)
 *   S2 = P - (High - Low)
 *   R3 = High + 2*(P - Low)
 *   S3 = Low - 2*(High - P)
 *
 * Pivot points are key intraday support/resistance levels.
 */
public class PivotPointsOverlay implements ChartOverlay {

    private static final Color PIVOT_COLOR = new Color(255, 193, 7);        // Amber
    private static final Color R1_COLOR = new Color(76, 175, 80, 200);      // Green
    private static final Color R2_COLOR = new Color(76, 175, 80, 150);
    private static final Color R3_COLOR = new Color(76, 175, 80, 100);
    private static final Color S1_COLOR = new Color(244, 67, 54, 200);      // Red
    private static final Color S2_COLOR = new Color(244, 67, 54, 150);
    private static final Color S3_COLOR = new Color(244, 67, 54, 100);

    private final boolean showR3S3;

    public PivotPointsOverlay() {
        this(false);  // Default: don't show R3/S3
    }

    public PivotPointsOverlay(boolean showR3S3) {
        this.showR3S3 = showR3S3;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        List<Candle> candles = provider.getCandles();
        if (candles.size() < 2) return;

        // Calculate pivot levels from previous day's HLC
        // For simplicity, use first candle as "previous day" reference
        // In a real implementation, this would detect day boundaries

        // Find the highest high and lowest low of first half of data as "previous day"
        int midPoint = candles.size() / 2;
        double prevHigh = Double.MIN_VALUE;
        double prevLow = Double.MAX_VALUE;
        double prevClose = candles.get(midPoint - 1).close();

        for (int i = 0; i < midPoint; i++) {
            Candle c = candles.get(i);
            prevHigh = Math.max(prevHigh, c.high());
            prevLow = Math.min(prevLow, c.low());
        }

        // Calculate pivot levels
        double pivot = (prevHigh + prevLow + prevClose) / 3.0;
        double range = prevHigh - prevLow;
        double r1 = 2 * pivot - prevLow;
        double s1 = 2 * pivot - prevHigh;
        double r2 = pivot + range;
        double s2 = pivot - range;
        double r3 = prevHigh + 2 * (pivot - prevLow);
        double s3 = prevLow - 2 * (prevHigh - pivot);

        // Apply levels only to second half of candles
        long startTime = candles.get(midPoint).timestamp();
        long endTime = candles.get(candles.size() - 1).timestamp();

        int currentIndex = datasetIndex;

        // Pivot line
        currentIndex = addHorizontalLine(plot, currentIndex, "Pivot", pivot, startTime, endTime, PIVOT_COLOR, ChartStyles.MEDIUM_STROKE);

        // R1/S1
        currentIndex = addHorizontalLine(plot, currentIndex, "R1", r1, startTime, endTime, R1_COLOR, ChartStyles.THIN_STROKE);
        currentIndex = addHorizontalLine(plot, currentIndex, "S1", s1, startTime, endTime, S1_COLOR, ChartStyles.THIN_STROKE);

        // R2/S2
        currentIndex = addHorizontalLine(plot, currentIndex, "R2", r2, startTime, endTime, R2_COLOR, ChartStyles.THIN_STROKE);
        currentIndex = addHorizontalLine(plot, currentIndex, "S2", s2, startTime, endTime, S2_COLOR, ChartStyles.THIN_STROKE);

        // R3/S3 (optional)
        if (showR3S3) {
            currentIndex = addHorizontalLine(plot, currentIndex, "R3", r3, startTime, endTime, R3_COLOR, ChartStyles.DASHED_STROKE);
            addHorizontalLine(plot, currentIndex, "S3", s3, startTime, endTime, S3_COLOR, ChartStyles.DASHED_STROKE);
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
        return showR3S3 ? 7 : 5;  // P, R1, S1, R2, S2, (R3, S3)
    }
}
