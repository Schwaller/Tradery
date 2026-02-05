package com.tradery.charts.util;

import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.plot.XYPlot;

import java.awt.*;

/**
 * Centralized helper for creating chart reference lines and annotations.
 * Eliminates duplication of reference line creation.
 */
public final class ChartAnnotationHelper {

    private ChartAnnotationHelper() {} // Prevent instantiation

    /**
     * Add a horizontal line annotation to the plot.
     */
    public static void addHorizontalLine(XYPlot plot, double y, Stroke stroke, Paint color,
                                         long startTime, long endTime) {
        plot.addAnnotation(new XYLineAnnotation(startTime, y, endTime, y, stroke, color));
    }

    /**
     * Add a zero reference line to the plot.
     */
    public static void addZeroLine(XYPlot plot, long startTime, long endTime) {
        addHorizontalLine(plot, 0, ChartStyles.DASHED_STROKE, ChartStyles.textColor(), startTime, endTime);
    }

    /**
     * Add a zero reference line with custom color.
     */
    public static void addZeroLine(XYPlot plot, long startTime, long endTime, Paint color) {
        addHorizontalLine(plot, 0, ChartStyles.DASHED_STROKE, color, startTime, endTime);
    }

    /**
     * Add a simple zero line with thin solid stroke.
     */
    public static void addSimpleZeroLine(XYPlot plot, long startTime, long endTime) {
        addHorizontalLine(plot, 0, new BasicStroke(1.0f), new Color(149, 165, 166, 200), startTime, endTime);
    }

    /**
     * Add RSI reference lines at 30 (oversold), 50 (neutral), and 70 (overbought).
     */
    public static void addRsiLines(XYPlot plot, long startTime, long endTime) {
        addHorizontalLine(plot, 30, ChartStyles.DASHED_STROKE, ChartStyles.RSI_OVERSOLD, startTime, endTime);
        addHorizontalLine(plot, 50, ChartStyles.DASHED_STROKE, ChartStyles.textColor(), startTime, endTime);
        addHorizontalLine(plot, 70, ChartStyles.DASHED_STROKE, ChartStyles.RSI_OVERBOUGHT, startTime, endTime);
    }

    /**
     * Add Stochastic reference lines at 20 (oversold), 50 (neutral), and 80 (overbought).
     */
    public static void addStochasticLines(XYPlot plot, long startTime, long endTime) {
        addHorizontalLine(plot, 20, ChartStyles.DASHED_STROKE, ChartStyles.RSI_OVERSOLD, startTime, endTime);
        addHorizontalLine(plot, 50, ChartStyles.DASHED_STROKE, ChartStyles.textColor(), startTime, endTime);
        addHorizontalLine(plot, 80, ChartStyles.DASHED_STROKE, ChartStyles.RSI_OVERBOUGHT, startTime, endTime);
    }

    /**
     * Add ADX reference lines at 20 (weak trend) and 25 (strong trend).
     */
    public static void addAdxLines(XYPlot plot, long startTime, long endTime) {
        addHorizontalLine(plot, 20, ChartStyles.DASHED_STROKE, ChartStyles.textColor(), startTime, endTime);
        addHorizontalLine(plot, 25, ChartStyles.DASHED_STROKE, new Color(230, 126, 34, 150), startTime, endTime);
    }

    /**
     * Add Range Position reference lines at -1 (breakdown), 0 (center), and +1 (breakout).
     */
    public static void addRangePositionLines(XYPlot plot, long startTime, long endTime) {
        addHorizontalLine(plot, 1, ChartStyles.DASHED_STROKE, ChartStyles.DELTA_POSITIVE, startTime, endTime);
        addHorizontalLine(plot, -1, ChartStyles.DASHED_STROKE, ChartStyles.DELTA_NEGATIVE, startTime, endTime);
        addHorizontalLine(plot, 0, ChartStyles.DASHED_STROKE, ChartStyles.textColor(), startTime, endTime);
    }

    /**
     * Add Funding Rate reference lines at 0, 0.01 (high), and -0.01 (negative).
     */
    public static void addFundingLines(XYPlot plot, long startTime, long endTime) {
        addHorizontalLine(plot, 0, ChartStyles.DASHED_STROKE, ChartStyles.textColor(), startTime, endTime);
        addHorizontalLine(plot, 0.01, ChartStyles.DASHED_STROKE, new Color(230, 126, 34, 100), startTime, endTime);
        addHorizontalLine(plot, -0.01, ChartStyles.DASHED_STROKE, new Color(52, 152, 219, 100), startTime, endTime);
    }
}
