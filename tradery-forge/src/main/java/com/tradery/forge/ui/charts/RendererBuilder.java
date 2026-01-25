package com.tradery.forge.ui.charts;

import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;

import java.awt.*;

/**
 * Fluent builder for creating consistently configured chart renderers.
 * Eliminates duplication of renderer setup across IndicatorChartsManager.
 */
public final class RendererBuilder {

    private RendererBuilder() {} // Prevent instantiation

    // ===== Line Renderer Factory Methods =====

    /**
     * Create a simple line renderer with one series.
     */
    public static XYLineAndShapeRenderer lineRenderer(Paint color, Stroke stroke) {
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, color);
        renderer.setSeriesStroke(0, stroke);
        return renderer;
    }

    /**
     * Create a line renderer with one series using default medium stroke.
     */
    public static XYLineAndShapeRenderer lineRenderer(Paint color) {
        return lineRenderer(color, ChartStyles.MEDIUM_STROKE);
    }

    /**
     * Create a line renderer with two series (e.g., MACD line + signal).
     */
    public static XYLineAndShapeRenderer lineRenderer(
            Paint color1, Stroke stroke1,
            Paint color2, Stroke stroke2) {
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, color1);
        renderer.setSeriesStroke(0, stroke1);
        renderer.setSeriesPaint(1, color2);
        renderer.setSeriesStroke(1, stroke2);
        return renderer;
    }

    /**
     * Create a line renderer with three series (e.g., ADX, +DI, -DI).
     */
    public static XYLineAndShapeRenderer lineRenderer(
            Paint color1, Stroke stroke1,
            Paint color2, Stroke stroke2,
            Paint color3, Stroke stroke3) {
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, color1);
        renderer.setSeriesStroke(0, stroke1);
        renderer.setSeriesPaint(1, color2);
        renderer.setSeriesStroke(1, stroke2);
        renderer.setSeriesPaint(2, color3);
        renderer.setSeriesStroke(2, stroke3);
        return renderer;
    }

    // ===== Bar Renderer Factory Methods =====

    /**
     * Create a simple bar renderer with one series.
     */
    public static XYBarRenderer barRenderer(Paint color) {
        XYBarRenderer renderer = new XYBarRenderer();
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setSeriesPaint(0, color);
        return renderer;
    }

    /**
     * Create a bar renderer with two series (e.g., buy/sell).
     */
    public static XYBarRenderer barRenderer(Paint color1, Paint color2) {
        XYBarRenderer renderer = new XYBarRenderer(0.0);
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setDrawBarOutline(false);
        renderer.setSeriesPaint(0, color1);
        renderer.setSeriesPaint(1, color2);
        return renderer;
    }

    /**
     * Create a color-coded bar renderer that colors bars based on positive/negative values.
     * Positive values use positiveColor, negative values use negativeColor.
     */
    public static XYBarRenderer colorCodedBarRenderer(XYDataset dataset, Paint positiveColor, Paint negativeColor) {
        XYBarRenderer renderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = dataset.getYValue(series, item);
                return value >= 0 ? positiveColor : negativeColor;
            }
        };
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        return renderer;
    }

    /**
     * Create a color-coded bar renderer with no margin between bars.
     */
    public static XYBarRenderer colorCodedBarRendererNoMargin(XYDataset dataset, Paint positiveColor, Paint negativeColor) {
        XYBarRenderer renderer = new XYBarRenderer(0.0) {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = dataset.getYValue(series, item);
                return value >= 0 ? positiveColor : negativeColor;
            }
        };
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setDrawBarOutline(false);
        return renderer;
    }

    // ===== Specialized Renderers =====

    /**
     * Create a color-coded line renderer where color depends on value.
     * Useful for Range Position chart where color indicates breakout/breakdown.
     */
    public static XYLineAndShapeRenderer colorCodedLineRenderer(
            XYDataset dataset,
            Paint aboveColor,    // value > threshold
            Paint belowColor,    // value < -threshold
            Paint normalColor,   // value in between
            double threshold) {
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false) {
            @Override
            public Paint getItemPaint(int series, int item) {
                double val = dataset.getYValue(series, item);
                if (val > threshold) {
                    return aboveColor;
                } else if (val < -threshold) {
                    return belowColor;
                } else {
                    return normalColor;
                }
            }
        };
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        return renderer;
    }

    /**
     * Create a funding-style line renderer with colors based on positive/negative.
     */
    public static XYLineAndShapeRenderer signColoredLineRenderer(XYDataset dataset, Paint positiveColor, Paint negativeColor) {
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false) {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = dataset.getYValue(series, item);
                return value >= 0 ? positiveColor : negativeColor;
            }
        };
        renderer.setSeriesStroke(0, ChartStyles.THIN_STROKE);
        return renderer;
    }
}
