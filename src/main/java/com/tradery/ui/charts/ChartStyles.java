package com.tradery.ui.charts;

import com.tradery.ui.theme.Theme;
import com.tradery.ui.theme.ThemeManager;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleAnchor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

/**
 * Central styling constants and methods for charts.
 * Uses ThemeManager for dynamic theme colors.
 */
public final class ChartStyles {

    private ChartStyles() {} // Prevent instantiation

    // ===== Theme-aware color getters =====
    private static Theme theme() { return ThemeManager.theme(); }

    // ===== Background Colors (dynamic) =====
    public static Color BACKGROUND_COLOR() { return theme().getBackgroundColor(); }
    public static Color PLOT_BACKGROUND_COLOR() { return theme().getPlotBackgroundColor(); }
    public static Color GRIDLINE_COLOR() { return theme().getGridlineColor(); }
    public static Color TEXT_COLOR() { return theme().getTextColor(); }
    public static Color CROSSHAIR_COLOR() { return theme().getCrosshairColor(); }

    // ===== Legacy static fields (for compatibility) =====
    public static final Color BACKGROUND_COLOR = new Color(30, 30, 35);
    public static final Color PLOT_BACKGROUND_COLOR = new Color(20, 20, 25);
    public static final Color GRIDLINE_COLOR = new Color(60, 60, 65);
    public static final Color TEXT_COLOR = new Color(150, 150, 150);
    public static final Color CROSSHAIR_COLOR = new Color(150, 150, 150, 180);

    // ===== Price Chart Colors =====
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

    // ===== POC Overlay Colors =====
    public static final Color DAILY_POC_COLOR = new Color(0, 200, 200, 200);      // Cyan for previous day POC
    public static final Color FLOATING_POC_COLOR = new Color(255, 100, 200, 200); // Magenta for floating POC

    // ===== Mayer Multiple Zone Colors (rainbow spectrum) =====
    public static final Color MAYER_DEEP_UNDERVALUED = new Color(0, 200, 255);    // Cyan
    public static final Color MAYER_UNDERVALUED = new Color(0, 255, 100);         // Green
    public static final Color MAYER_NEUTRAL = new Color(255, 255, 0);             // Yellow
    public static final Color MAYER_OVERVALUED = new Color(255, 140, 0);          // Orange
    public static final Color MAYER_DEEP_OVERVALUED = new Color(255, 0, 100);     // Magenta

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

    // ===== Orderflow/Delta Colors =====
    public static final Color DELTA_POSITIVE = new Color(38, 166, 91);      // Green
    public static final Color DELTA_NEGATIVE = new Color(231, 76, 60);      // Red
    public static final Color CVD_COLOR = new Color(52, 152, 219);          // Blue
    public static final Color WHALE_DELTA_POS = new Color(155, 89, 182);    // Purple
    public static final Color WHALE_DELTA_NEG = new Color(211, 84, 0);      // Dark Orange
    public static final Color BUY_VOLUME_COLOR = new Color(38, 166, 91);    // Green
    public static final Color SELL_VOLUME_COLOR = new Color(231, 76, 60);   // Red
    public static final Color TRADE_COUNT_COLOR = new Color(149, 165, 166); // Gray

    // ===== Funding Rate Colors =====
    public static final Color FUNDING_POSITIVE = new Color(230, 126, 34);   // Orange (longs pay)
    public static final Color FUNDING_NEGATIVE = new Color(52, 152, 219);   // Blue (shorts pay)
    public static final Color FUNDING_8H_COLOR = new Color(149, 165, 166);  // Gray

    // ===== Open Interest Colors =====
    public static final Color OI_LINE_COLOR = new Color(155, 89, 182);      // Purple for OI line
    public static final Color OI_POSITIVE = new Color(38, 166, 91);         // Green (OI increasing)
    public static final Color OI_NEGATIVE = new Color(231, 76, 60);         // Red (OI decreasing)

    // ===== Hoop Pattern Colors =====
    public static final Color[] HOOP_COLORS = {
        new Color(76, 175, 80),   // Green
        new Color(33, 150, 243),  // Blue
        new Color(255, 152, 0),   // Orange
        new Color(156, 39, 176),  // Purple
        new Color(0, 188, 212),   // Cyan
        new Color(255, 87, 34),   // Deep Orange
    };
    public static final Color HOOP_MATCH_COLOR = new Color(76, 175, 80, 200);  // Green for matches
    public static final Color HOOP_ANCHOR_COLOR = new Color(255, 215, 0);  // Gold for anchor point

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

    // ===== Rainbow Colors for Trades (with transparency) =====
    public static final Color[] RAINBOW_COLORS = {
        new Color(255, 12, 18, 180),   // Vivid Red
        new Color(253, 174, 50, 180),  // Yellow Orange
        new Color(253, 251, 0, 180),   // Lemon Glacier
        new Color(92, 255, 0, 180),    // Bright Green
        new Color(0, 207, 251, 180),   // Vivid Sky Blue
        new Color(143, 0, 242, 180)    // Electric Violet
    };

    // ===== Line Strokes =====
    public static final float LINE_WIDTH = 1.1f;
    public static final BasicStroke LINE_STROKE = new BasicStroke(
        LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final BasicStroke TRADE_LINE_STROKE = new BasicStroke(
        LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final BasicStroke THIN_STROKE = new BasicStroke(1.0f);
    public static final BasicStroke MEDIUM_STROKE = new BasicStroke(1.5f);
    public static final BasicStroke DASHED_STROKE = new BasicStroke(
        1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{4.0f, 4.0f}, 0.0f);
    public static final BasicStroke DASHED_MAYER_STROKE = new BasicStroke(
        1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{6.0f, 4.0f}, 0.0f);

    // ===== Styling Methods =====

    /**
     * Apply theme styling to a chart.
     */
    public static void stylizeChart(JFreeChart chart, String title) {
        Theme t = theme();
        chart.setBackgroundPaint(t.getBackgroundColor());

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(t.getPlotBackgroundColor());
        plot.setDomainGridlinePaint(t.getGridlineColor());
        plot.setRangeGridlinePaint(t.getGridlineColor());
        plot.setOutlineVisible(false);

        // Date axis formatting
        if (plot.getDomainAxis() instanceof DateAxis dateAxis) {
            dateAxis.setDateFormatOverride(new SimpleDateFormat("MMM d"));
            dateAxis.setTickLabelPaint(t.getAxisLabelColor());
            dateAxis.setAxisLineVisible(false);
        }

        plot.getRangeAxis().setTickLabelPaint(t.getAxisLabelColor());
        plot.getRangeAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setFixedDimension(60);  // Fixed width for alignment

        // Avoid scientific notation on Y-axis (e.g., 1E4 instead of 10000)
        if (plot.getRangeAxis() instanceof NumberAxis numberAxis) {
            numberAxis.setNumberFormatOverride(new DecimalFormat("#,##0.####"));
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
     * Add title annotation to chart plot (for re-adding after clearAnnotations).
     */
    public static void addChartTitleAnnotation(XYPlot plot, String title) {
        TextTitle textTitle = new TextTitle(title, new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        textTitle.setPaint(TEXT_COLOR);
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
