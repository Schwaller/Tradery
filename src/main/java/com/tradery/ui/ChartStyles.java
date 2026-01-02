package com.tradery.ui;

import java.awt.BasicStroke;
import java.awt.Color;

/**
 * Central styling constants for charts.
 */
public final class ChartStyles {

    private ChartStyles() {} // Prevent instantiation

    // Line styling
    public static final float LINE_WIDTH = 1.1f;
    public static final BasicStroke LINE_STROKE = new BasicStroke(
        LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    // Trade annotation line
    public static final BasicStroke TRADE_LINE_STROKE = new BasicStroke(
        LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    // Rainbow colors for trades (with transparency)
    public static final Color[] RAINBOW_COLORS = {
        new Color(255, 12, 18, 180),   // Vivid Red
        new Color(253, 174, 50, 180),  // Yellow Orange
        new Color(253, 251, 0, 180),   // Lemon Glacier
        new Color(92, 255, 0, 180),    // Bright Green
        new Color(0, 207, 251, 180),   // Vivid Sky Blue
        new Color(143, 0, 242, 180)    // Electric Violet
    };
}
