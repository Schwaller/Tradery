package com.tradery.charts.core;

import javax.swing.*;
import java.awt.*;

/**
 * Reads background/foreground colors from UIManager (FlatLaf)
 * and provides helpers to compute derived colors.
 */
public final class ThemeColors {

    private ThemeColors() {}

    /** Panel background from the active L&F. */
    public static Color background() {
        return ui("Panel.background", new Color(30, 30, 35));
    }

    /** Gridline / separator color from the active L&F. */
    public static Color separator() {
        return ui("Separator.foreground", new Color(60, 60, 65));
    }

    /** Label foreground from the active L&F. */
    public static Color foreground() {
        return ui("Label.foreground", new Color(220, 220, 230));
    }

    /** Dim/secondary foreground from the active L&F. */
    public static Color dimForeground() {
        return ui("Label.disabledForeground", new Color(150, 150, 155));
    }

    /** True if the current theme background is dark. */
    public static boolean isDark() {
        Color bg = background();
        double luminance = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return luminance < 0.5;
    }

    /** Read a color from UIManager, with fallback. */
    public static Color ui(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    /** Darken a color by a fraction (0.0 = unchanged, 1.0 = black). */
    public static Color darker(Color c, float amount) {
        return new Color(
            Math.max(0, (int) (c.getRed() * (1 - amount))),
            Math.max(0, (int) (c.getGreen() * (1 - amount))),
            Math.max(0, (int) (c.getBlue() * (1 - amount)))
        );
    }

    /** Offset a color's RGB channels (positive = lighter, negative = darker). */
    public static Color offset(Color c, int amount) {
        return new Color(
            clamp(c.getRed() + amount),
            clamp(c.getGreen() + amount),
            clamp(c.getBlue() + amount)
        );
    }

    /** Offset with alpha. */
    public static Color offset(Color c, int amount, int alpha) {
        return new Color(
            clamp(c.getRed() + amount),
            clamp(c.getGreen() + amount),
            clamp(c.getBlue() + amount),
            clamp(alpha)
        );
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
