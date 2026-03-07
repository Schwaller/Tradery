package com.tradery.charts.util;

import com.tradery.charts.core.ChartTheme;
import com.tradery.charts.core.DefaultChartTheme;
import com.tradery.ui.controls.ChartConfig;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.axis.AxisLocation;
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
    private static ChartTheme currentTheme = DefaultChartTheme.INSTANCE;

    /**
     * Set the current theme for all charts.
     */
    public static void setTheme(ChartTheme theme) {
        currentTheme = theme != null ? theme : DefaultChartTheme.INSTANCE;
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

    // ===== Equity/Comparison Chart Colors =====
    public static final Color EQUITY_COLOR = new Color(77, 77, 255);
    public static final Color BUY_HOLD_COLOR = new Color(255, 193, 7);
    public static final Color CAPITAL_USAGE_COLOR = new Color(57, 255, 20);

    // ===== Overlay Colors =====
    public static final Color SMA_COLOR = new Color(255, 193, 7, 200);
    public static final Color EMA_COLOR = new Color(0, 200, 255, 200);
    public static final Color BB_COLOR = new Color(180, 100, 255, 180);
    public static final Color BB_MIDDLE_COLOR = new Color(180, 100, 255, 120);
    public static final Color VWAP_COLOR = new Color(255, 215, 0, 220);

    // ===== POC/VAH/VAL Overlay Colors =====
    public static final Color DAILY_POC_COLOR = new Color(0, 200, 200, 200);
    public static final Color DAILY_VAH_COLOR = new Color(0, 200, 200, 120);
    public static final Color DAILY_VAL_COLOR = new Color(0, 200, 200, 120);
    public static final Color FLOATING_POC_COLOR = new Color(255, 100, 200, 200);
    public static final Color FLOATING_VAH_COLOR = new Color(255, 100, 200, 120);
    public static final Color FLOATING_VAL_COLOR = new Color(255, 100, 200, 120);

    // ===== Ichimoku Cloud Colors =====
    public static final Color ICHIMOKU_TENKAN_COLOR = new Color(255, 87, 51, 200);
    public static final Color ICHIMOKU_KIJUN_COLOR = new Color(0, 150, 255, 200);
    public static final Color ICHIMOKU_CHIKOU_COLOR = new Color(255, 193, 7, 180);
    public static final Color ICHIMOKU_SPAN_A_COLOR = new Color(76, 175, 80, 180);
    public static final Color ICHIMOKU_SPAN_B_COLOR = new Color(244, 67, 54, 180);
    public static final Color ICHIMOKU_CLOUD_BULLISH = new Color(76, 175, 80, 50);
    public static final Color ICHIMOKU_CLOUD_BEARISH = new Color(244, 67, 54, 50);

    // ===== Mayer Multiple Zone Colors =====
    public static final Color MAYER_DEEP_UNDERVALUED = new Color(0, 200, 255);
    public static final Color MAYER_UNDERVALUED = new Color(0, 255, 100);
    public static final Color MAYER_NEUTRAL = new Color(255, 255, 0);
    public static final Color MAYER_OVERVALUED = new Color(255, 140, 0);
    public static final Color MAYER_DEEP_OVERVALUED = new Color(255, 0, 100);

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
    public static final Color WHALE_DELTA_POS = new Color(155, 89, 182);
    public static final Color WHALE_DELTA_NEG = new Color(211, 84, 0);
    public static final Color BUY_VOLUME_COLOR = new Color(38, 166, 91);
    public static final Color SELL_VOLUME_COLOR = new Color(231, 76, 60);
    public static final Color TRADE_COUNT_COLOR = new Color(149, 165, 166);
    public static final Color TRADE_COUNT_LINE_COLOR = new Color(100, 181, 246);

    // ===== Funding Rate Colors =====
    public static final Color FUNDING_POSITIVE = new Color(230, 126, 34);
    public static final Color FUNDING_NEGATIVE = new Color(52, 152, 219);
    public static final Color FUNDING_8H_COLOR = new Color(149, 165, 166);

    // ===== Open Interest Colors =====
    public static final Color OI_LINE_COLOR = new Color(155, 89, 182);
    public static final Color OI_POSITIVE = new Color(38, 166, 91);
    public static final Color OI_NEGATIVE = new Color(231, 76, 60);

    // ===== Premium Index Colors =====
    public static final Color PREMIUM_POSITIVE = new Color(46, 204, 113);
    public static final Color PREMIUM_NEGATIVE = new Color(231, 76, 60);
    public static final Color PREMIUM_AVG_COLOR = new Color(241, 196, 15);

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

    // ===== Hoop Pattern Colors =====
    public static final Color[] HOOP_COLORS = {
        new Color(76, 175, 80),
        new Color(33, 150, 243),
        new Color(255, 152, 0),
        new Color(156, 39, 176),
        new Color(0, 188, 212),
        new Color(255, 87, 34),
    };
    public static final Color HOOP_MATCH_COLOR = new Color(76, 175, 80, 200);
    public static final Color HOOP_ANCHOR_COLOR = new Color(255, 215, 0);

    // ===== Rainbow Colors for Trades =====
    public static final Color[] RAINBOW_COLORS = {
        new Color(255, 12, 18, 180),
        new Color(253, 174, 50, 180),
        new Color(253, 251, 0, 180),
        new Color(92, 255, 0, 180),
        new Color(0, 207, 251, 180),
        new Color(143, 0, 242, 180)
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
    public static final BasicStroke DASHED_MAYER_STROKE = new BasicStroke(
        LINE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, new float[]{6.0f, 4.0f}, 0.0f);
    public static final BasicStroke TRADE_LINE_STROKE = new BasicStroke(
        LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

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

        // Apply axis position from config
        applyAxisPosition(plot, ChartConfig.getInstance().getPriceAxisPosition());

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
     * Apply range axis position to a plot: "left", "right", or "both".
     * In "both" mode, creates a mirrored right axis synced to the left axis.
     * Cleans up any stale mirror axis from a previous "both" mode.
     */
    public static void applyAxisPosition(XYPlot plot, String position) {
        // Remove any stale mirror axis from a previous "both" mode
        if (plot.getRangeAxisCount() > 1) {
            plot.setRangeAxis(1, null);
        }

        if ("right".equals(position)) {
            plot.setRangeAxisLocation(AxisLocation.TOP_OR_RIGHT);
        } else if ("both".equals(position)) {
            plot.setRangeAxisLocation(AxisLocation.TOP_OR_LEFT);
            if (plot.getRangeAxis() instanceof NumberAxis leftAxis) {
                NumberAxis rightAxis = new NumberAxis();
                styleNumberAxis(rightAxis, currentTheme);
                rightAxis.setAutoRange(false);
                rightAxis.setRange(leftAxis.getRange());
                plot.setRangeAxis(1, rightAxis);
                plot.setRangeAxisLocation(1, AxisLocation.TOP_OR_RIGHT);
                plot.addChangeListener(event -> {
                    org.jfree.data.Range leftRange = leftAxis.getRange();
                    org.jfree.data.Range rightRange = rightAxis.getRange();
                    if (!leftRange.equals(rightRange)) {
                        rightAxis.setRange(leftRange);
                    }
                });
            }
        } else {
            plot.setRangeAxisLocation(AxisLocation.TOP_OR_LEFT);
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
     * Get color for Mayer Multiple value using smooth gradient.
     */
    public static Color getMayerColor(double mayer) {
        if (mayer < 0.6) {
            return MAYER_DEEP_UNDERVALUED;
        } else if (mayer < 0.8) {
            float t = (float) ((mayer - 0.6) / 0.2);
            return interpolateColor(MAYER_DEEP_UNDERVALUED, MAYER_UNDERVALUED, t);
        } else if (mayer < 1.0) {
            float t = (float) ((mayer - 0.8) / 0.2);
            return interpolateColor(MAYER_UNDERVALUED, MAYER_NEUTRAL, t);
        } else if (mayer < 1.5) {
            float t = (float) ((mayer - 1.0) / 0.5);
            return interpolateColor(MAYER_NEUTRAL, MAYER_OVERVALUED, t);
        } else if (mayer < 2.4) {
            float t = (float) ((mayer - 1.5) / 0.9);
            return interpolateColor(MAYER_OVERVALUED, MAYER_DEEP_OVERVALUED, t);
        } else {
            return MAYER_DEEP_OVERVALUED;
        }
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
