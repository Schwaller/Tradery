package com.tradery.charts.util;

import com.tradery.charts.core.ChartTheme;
import com.tradery.charts.core.DarkChartTheme;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleAnchor;

import java.awt.*;
import java.text.DecimalFormat;

/**
 * Central styling constants and methods for charts.
 * Uses ChartTheme for dynamic theme colors.
 */
public final class ChartStyles {

    private ChartStyles() {} // Prevent instantiation

    // Theme provider - can be set by application
    private static ChartTheme currentTheme = DarkChartTheme.INSTANCE;

    /**
     * Set the current theme for all charts.
     */
    public static void setTheme(ChartTheme theme) {
        currentTheme = theme != null ? theme : DarkChartTheme.INSTANCE;
    }

    /**
     * Get the current theme.
     */
    public static ChartTheme getTheme() {
        return currentTheme;
    }

    // ===== Theme-aware color getters =====

    public static Color backgroundColor() { return currentTheme.getBackgroundColor(); }
    public static Color plotBackgroundColor() { return currentTheme.getPlotBackgroundColor(); }
    public static Color gridlineColor() { return currentTheme.getGridlineColor(); }
    public static Color textColor() { return currentTheme.getTextColor(); }
    public static Color crosshairColor() { return currentTheme.getCrosshairColor(); }
    public static Color axisLabelColor() { return currentTheme.getAxisLabelColor(); }

    // ===== Static color constants (for backward compatibility) =====

    public static final Color CANDLE_UP_COLOR = new Color(76, 175, 80);
    public static final Color CANDLE_DOWN_COLOR = new Color(244, 67, 54);
    public static final Color PRICE_LINE_COLOR = new Color(255, 255, 255, 180);
    public static final Color WIN_COLOR = new Color(76, 175, 80, 180);
    public static final Color LOSS_COLOR = new Color(244, 67, 54, 180);

    // ===== Overlay Colors =====
    public static final Color SMA_COLOR = new Color(255, 193, 7, 200);
    public static final Color EMA_COLOR = new Color(0, 200, 255, 200);
    public static final Color BB_COLOR = new Color(180, 100, 255, 180);
    public static final Color BB_MIDDLE_COLOR = new Color(180, 100, 255, 120);
    public static final Color VWAP_COLOR = new Color(255, 215, 0, 220);

    // ===== High/Low Range Cloud Colors =====
    public static final Color HL_CLOUD_COLOR = new Color(255, 255, 255, 42);

    // ===== RSI Chart Colors =====
    public static final Color RSI_COLOR = new Color(255, 193, 7);
    public static final Color RSI_OVERBOUGHT = new Color(255, 80, 80, 50);
    public static final Color RSI_OVERSOLD = new Color(80, 255, 80, 50);

    // ===== MACD Chart Colors =====
    public static final Color MACD_LINE_COLOR = new Color(0, 150, 255);
    public static final Color MACD_SIGNAL_COLOR = new Color(255, 140, 0);
    public static final Color MACD_HIST_POS = new Color(76, 175, 80);
    public static final Color MACD_HIST_NEG = new Color(244, 67, 54);

    // ===== ATR Chart Color =====
    public static final Color ATR_COLOR = new Color(180, 100, 255);

    // ===== ADX Chart Colors =====
    public static final Color ADX_COLOR = new Color(255, 165, 0);       // Orange
    public static final Color PLUS_DI_COLOR = new Color(76, 175, 80);   // Green
    public static final Color MINUS_DI_COLOR = new Color(244, 67, 54);  // Red

    // ===== Stochastic Chart Colors =====
    public static final Color STOCHASTIC_K_COLOR = new Color(0, 200, 255);
    public static final Color STOCHASTIC_D_COLOR = new Color(255, 100, 150);

    // ===== Orderflow/Delta Colors =====
    public static final Color DELTA_POSITIVE = new Color(38, 166, 91);
    public static final Color DELTA_NEGATIVE = new Color(231, 76, 60);
    public static final Color CVD_COLOR = new Color(52, 152, 219);
    public static final Color BUY_VOLUME_COLOR = new Color(38, 166, 91);
    public static final Color SELL_VOLUME_COLOR = new Color(231, 76, 60);

    // ===== Volume Colors (Wyckoff-style: cool to warm) =====
    public static final Color[] VOLUME_COLORS = {
        new Color(100, 100, 100),  // Ultra Low - grey
        new Color(0, 100, 255),    // Very Low - blue
        new Color(0, 200, 200),    // Low - cyan
        new Color(100, 200, 100),  // Average - green
        new Color(255, 180, 0),    // High - orange
        new Color(255, 80, 80),    // Very High - red
        new Color(255, 0, 200)     // Ultra High - magenta
    };

    // ===== Multiple Overlay Color Palette =====
    public static final Color[] OVERLAY_PALETTE = {
        new Color(255, 193, 7, 200),   // Gold
        new Color(0, 200, 255, 200),   // Cyan
        new Color(255, 87, 34, 200),   // Deep Orange
        new Color(156, 39, 176, 200),  // Purple
        new Color(76, 175, 80, 200),   // Green
        new Color(233, 30, 99, 200),   // Pink
        new Color(63, 81, 181, 200),   // Indigo
        new Color(255, 235, 59, 200),  // Yellow
    };

    // ===== Line Strokes =====
    public static final float LINE_WIDTH = 0.6f;
    public static final BasicStroke LINE_STROKE = new BasicStroke(
        LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final BasicStroke THIN_STROKE = new BasicStroke(LINE_WIDTH);
    public static final BasicStroke MEDIUM_STROKE = new BasicStroke(LINE_WIDTH);
    public static final BasicStroke DASHED_STROKE = new BasicStroke(
        LINE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, new float[]{4.0f, 4.0f}, 0.0f);

    // Consistent axis tick label font
    private static final Font AXIS_TICK_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

    /**
     * Apply theme styling to a chart.
     */
    public static void stylizeChart(JFreeChart chart, String title) {
        ChartTheme theme = currentTheme;
        chart.setBackgroundPaint(theme.getBackgroundColor());

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(theme.getPlotBackgroundColor());
        plot.setDomainGridlinePaint(theme.getGridlineColor());
        plot.setRangeGridlinePaint(theme.getGridlineColor());
        plot.setOutlineVisible(false);

        // Date axis formatting with adaptive format based on visible range
        if (plot.getDomainAxis() instanceof DateAxis dateAxis) {
            dateAxis.setDateFormatOverride(new AdaptiveDateFormat(dateAxis));
            dateAxis.setTickLabelPaint(theme.getAxisLabelColor());
            dateAxis.setTickLabelFont(AXIS_TICK_FONT);
            dateAxis.setAxisLineVisible(false);
        }

        // Configure range axis with consistent styling
        if (plot.getRangeAxis() instanceof NumberAxis rangeAxis) {
            styleNumberAxis(rangeAxis, theme);
        }

        // Set thin line stroke with rounded joins for all series
        if (plot.getRenderer() != null) {
            plot.getRenderer().setDefaultStroke(LINE_STROKE);
        }

        // Add title as annotation only if chart has no legend
        if (chart.getLegend() == null) {
            addChartTitleAnnotation(plot, title);
        }
    }

    /**
     * Apply consistent styling to a NumberAxis.
     */
    public static void styleNumberAxis(NumberAxis axis, ChartTheme theme) {
        axis.setTickLabelPaint(theme.getAxisLabelColor());
        axis.setTickLabelFont(AXIS_TICK_FONT);
        axis.setAxisLineVisible(false);
        axis.setFixedDimension(60);
        axis.setNumberFormatOverride(new DecimalFormat("#,##0.####"));
        axis.setTickMarksVisible(false);
    }

    /**
     * Add title annotation to chart plot.
     */
    public static void addChartTitleAnnotation(XYPlot plot, String title) {
        TextTitle textTitle = new TextTitle(title, new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        textTitle.setPaint(currentTheme.getTextColor());
        textTitle.setBackgroundPaint(null);
        XYTitleAnnotation titleAnnotation = new XYTitleAnnotation(0.01, 0.98, textTitle, RectangleAnchor.TOP_LEFT);
        plot.addAnnotation(titleAnnotation);
    }

    /**
     * Interpolate between two colors.
     */
    public static Color interpolateColor(Color c1, Color c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (c1.getRed() + t * (c2.getRed() - c1.getRed()));
        int g = (int) (c1.getGreen() + t * (c2.getGreen() - c1.getGreen()));
        int b = (int) (c1.getBlue() + t * (c2.getBlue() - c1.getBlue()));
        return new Color(r, g, b);
    }
}
