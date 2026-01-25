package com.tradery.ui;

import java.awt.Color;

/**
 * Centralized UI color constants for non-chart UI components.
 * For chart-specific colors, see {@link com.tradery.ui.charts.ChartStyles}.
 */
public final class UIColors {

    private UIColors() {} // Prevent instantiation

    // ===== Data Status Colors =====
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

    // ===== Badge Colors =====
    /** Info badge color (blue) */
    public static final Color BADGE_INFO = new Color(0, 100, 200);
    /** Warning badge color (orange) */
    public static final Color BADGE_WARNING = new Color(200, 120, 0);
    /** Success badge color (green) */
    public static final Color BADGE_SUCCESS = new Color(60, 140, 60);

    // ===== Background Colors =====
    /** Dark dialog background */
    public static final Color DIALOG_BACKGROUND = new Color(40, 40, 45);
    /** Darker panel background */
    public static final Color PANEL_BACKGROUND_DARK = new Color(35, 35, 40);
    /** Terminal background */
    public static final Color TERMINAL_BACKGROUND = new Color(30, 30, 30);
    /** Header/toolbar background */
    public static final Color HEADER_BACKGROUND = new Color(45, 45, 45);

    // ===== Accent Colors =====
    /** Primary accent (blue) */
    public static final Color ACCENT_PRIMARY = new Color(100, 140, 180);
    /** Hover accent (steel blue) */
    public static final Color ACCENT_HOVER = new Color(70, 130, 180);

    // ===== Misc UI Colors =====
    /** Arrow/connector color */
    public static final Color ARROW_COLOR = new Color(150, 150, 150, 180);
    /** Axis color */
    public static final Color AXIS_COLOR = new Color(180, 180, 180);
    /** Tooltip overlay background */
    public static final Color OVERLAY_BACKGROUND = new Color(255, 255, 255, 180);
    /** Divider/separator color */
    public static final Color DIVIDER = new Color(80, 80, 85);
}
