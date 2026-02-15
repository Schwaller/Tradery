package com.tradery.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Cached theme colors derived from UIManager (FlatLaf).
 * Recomputes automatically on L&F changes.
 */
public final class ThemeColors {

    // Cached values — recomputed on L&F change
    private static boolean dark;
    private static Color background;
    private static Color canvas;
    private static Color separator;
    private static Color foreground;
    private static Color dimForeground;

    static {
        recompute();
        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                recompute();
            }
        });
    }

    private ThemeColors() {}

    private static void recompute() {
        background = ui("Panel.background", new Color(30, 30, 35));
        separator = ui("Separator.foreground", new Color(60, 60, 65));
        foreground = ui("Label.foreground", new Color(220, 220, 230));
        dimForeground = ui("Label.disabledForeground", new Color(150, 150, 155));

        double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue()) / 255;
        dark = luminance < 0.5;

        canvas = darker(background, dark ? 0.08f : 0.03f);
    }

    // ===== Cached getters =====

    public static boolean isDark()          { return dark; }
    public static Color background()        { return background; }
    public static Color canvas()            { return canvas; }
    public static Color separator()         { return separator; }
    public static Color foreground()        { return foreground; }
    public static Color dimForeground()     { return dimForeground; }

    // ===== Utilities =====

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

    /** Lighten a color by a fraction (0.0 = unchanged, 1.0 = white). */
    public static Color lighter(Color c, float amount) {
        return new Color(
            Math.min(255, (int) (c.getRed() + (255 - c.getRed()) * amount)),
            Math.min(255, (int) (c.getGreen() + (255 - c.getGreen()) * amount)),
            Math.min(255, (int) (c.getBlue() + (255 - c.getBlue()) * amount))
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
