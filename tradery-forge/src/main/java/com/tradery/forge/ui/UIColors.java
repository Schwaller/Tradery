package com.tradery.forge.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Centralized UI color constants for non-chart UI components.
 * For chart-specific colors, see {@link com.tradery.forge.ui.charts.ChartStyles}.
 *
 * Theme-aware colors use UIManager and adapt to light/dark themes.
 * Semantic colors (status, badges) are fixed across themes for consistency.
 */
public final class UIColors {

    private UIColors() {} // Prevent instantiation

    // ===== Theme-Aware Colors (use methods, not constants) =====

    /** Main panel background - adapts to theme */
    public static Color background() { return UIManager.getColor("Panel.background"); }

    /** Slightly darker background for cards/sections */
    public static Color backgroundDark() {
        Color bg = background();
        return darker(bg, 0.05f);
    }

    /** Header/toolbar background */
    public static Color backgroundHeader() {
        Color bg = background();
        return darker(bg, 0.08f);
    }

    /** Hover state background */
    public static Color backgroundHover() {
        Color bg = background();
        return brighter(bg, 0.08f);
    }

    /** Primary text color */
    public static Color textPrimary() { return UIManager.getColor("Label.foreground"); }

    /** Secondary/muted text color */
    public static Color textSecondary() { return UIManager.getColor("Label.disabledForeground"); }

    /** Border/separator color */
    public static Color border() { return UIManager.getColor("Separator.foreground"); }

    // ===== Semantic Status Colors (fixed across themes) =====
    /** Status unknown or not loaded */
    public static final Color STATUS_UNKNOWN = new Color(150, 150, 150);
    /** Status ready/loaded successfully */
    public static final Color STATUS_READY = new Color(60, 140, 60);
    /** Status loading/in progress */
    public static final Color STATUS_LOADING = new Color(180, 120, 40);
    /** Status loading with accent (blue) */
    public static final Color STATUS_LOADING_ACCENT = new Color(100, 100, 180);
    /** Status error */
    public static final Color STATUS_ERROR = new Color(180, 60, 60);

    // ===== Semantic Badge Colors (fixed across themes) =====
    /** Info badge color (blue) */
    public static final Color BADGE_INFO = new Color(0, 100, 200);
    /** Warning badge color (orange) */
    public static final Color BADGE_WARNING = new Color(200, 120, 0);
    /** Success badge color (green) */
    public static final Color BADGE_SUCCESS = new Color(60, 140, 60);

    // ===== Accent Colors =====
    /** Primary accent (blue) */
    public static final Color ACCENT_PRIMARY = new Color(100, 140, 180);
    /** Hover accent (steel blue) */
    public static final Color ACCENT_HOVER = new Color(70, 130, 180);
    /** Link color */
    public static final Color LINK = new Color(100, 150, 220);

    // ===== Trading Semantic Colors (fixed - represent meaning) =====
    /** Profit/win/long - green */
    public static final Color TRADE_PROFIT = new Color(76, 175, 80);
    /** Loss/short - red */
    public static final Color TRADE_LOSS = new Color(244, 67, 54);
    /** Warning/caution - yellow */
    public static final Color TRADE_WARNING = new Color(255, 193, 7);
    /** Phase tag - blue */
    public static final Color TAG_PHASE = new Color(70, 130, 180);
    /** Phase tag alt - purple */
    public static final Color TAG_PHASE_ALT = new Color(130, 100, 180);
    /** Entry indicator - light blue */
    public static final Color IND_ENTRY = new Color(100, 180, 255);
    /** Exit indicator - tan */
    public static final Color IND_EXIT = new Color(180, 130, 100);
    /** MFE indicator - light green */
    public static final Color IND_MFE = new Color(100, 180, 100);
    /** MAE indicator - light red */
    public static final Color IND_MAE = new Color(180, 100, 100);

    // ===== Misc UI Colors =====
    /** Arrow/connector color */
    public static final Color ARROW_COLOR = new Color(150, 150, 150, 180);

    // ===== Legacy Constants (deprecated - use methods instead) =====
    @Deprecated public static final Color DIALOG_BACKGROUND = new Color(40, 40, 45);
    @Deprecated public static final Color PANEL_BACKGROUND_DARK = new Color(35, 35, 40);
    @Deprecated public static final Color TERMINAL_BACKGROUND = new Color(30, 30, 30);
    @Deprecated public static final Color HEADER_BACKGROUND = new Color(45, 45, 45);
    @Deprecated public static final Color AXIS_COLOR = new Color(180, 180, 180);
    @Deprecated public static final Color OVERLAY_BACKGROUND = new Color(255, 255, 255, 180);
    @Deprecated public static final Color DIVIDER = new Color(80, 80, 85);

    // ===== Helper Methods =====

    private static Color darker(Color c, float factor) {
        return new Color(
            Math.max(0, (int)(c.getRed() * (1 - factor))),
            Math.max(0, (int)(c.getGreen() * (1 - factor))),
            Math.max(0, (int)(c.getBlue() * (1 - factor)))
        );
    }

    private static Color brighter(Color c, float factor) {
        return new Color(
            Math.min(255, (int)(c.getRed() + (255 - c.getRed()) * factor)),
            Math.min(255, (int)(c.getGreen() + (255 - c.getGreen()) * factor)),
            Math.min(255, (int)(c.getBlue() + (255 - c.getBlue()) * factor))
        );
    }
}
