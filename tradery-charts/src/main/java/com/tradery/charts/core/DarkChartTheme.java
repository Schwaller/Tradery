package com.tradery.charts.core;

import javax.swing.*;
import java.awt.*;

/**
 * Adaptive chart theme that reads background colors from UIManager
 * and detects light/dark mode from the active FlatLaf theme.
 * Returns indicator colors appropriate for the current background brightness.
 */
public class DarkChartTheme implements ChartTheme {

    public static final DarkChartTheme INSTANCE = new DarkChartTheme();

    private DarkChartTheme() {}

    private boolean isDark() {
        Color bg = ui("Panel.background", new Color(30, 30, 35));
        double luminance = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return luminance < 0.5;
    }

    // ===== Background Colors (from UIManager) =====

    @Override
    public Color getBackgroundColor() {
        return ui("Panel.background", new Color(30, 30, 35));
    }

    @Override
    public Color getPlotBackgroundColor() {
        return darker(getBackgroundColor(), isDark() ? 0.08f : 0.03f);
    }

    @Override
    public Color getGridlineColor() {
        return ui("Separator.foreground", new Color(60, 60, 65));
    }

    @Override
    public Color getDimForeground() {
        return ui("Label.disabledForeground", isDark() ? new Color(150, 150, 150) : new Color(80, 80, 85));
    }

    @Override
    public Color getStrongForeground() {
        return isDark() ? new Color(240, 240, 245) : new Color(30, 30, 35);
    }

    // ===== Price Chart Colors =====

    @Override public Color getCandleUpColor() { return isDark() ? new Color(76, 175, 80) : new Color(46, 125, 50); }
    @Override public Color getCandleDownColor() { return isDark() ? new Color(244, 67, 54) : new Color(198, 40, 40); }
    // getPriceLineColor() inherited from ChartTheme → getStrongForeground()
    @Override public Color getWinColor() { return isDark() ? new Color(76, 175, 80, 180) : new Color(46, 125, 50, 200); }
    @Override public Color getLossColor() { return isDark() ? new Color(244, 67, 54, 180) : new Color(198, 40, 40, 200); }

    // ===== Volume Colors =====

    @Override public Color getBuyVolumeColor() { return isDark() ? new Color(38, 166, 91) : new Color(46, 125, 50); }
    @Override public Color getSellVolumeColor() { return isDark() ? new Color(231, 76, 60) : new Color(198, 40, 40); }

    // ===== Overlay Colors =====

    @Override public Color getSmaColor() { return isDark() ? new Color(255, 193, 7, 200) : new Color(255, 160, 0, 220); }
    @Override public Color getEmaColor() { return isDark() ? new Color(0, 200, 255, 200) : new Color(0, 150, 200, 220); }
    @Override public Color getBbColor() { return isDark() ? new Color(180, 100, 255, 180) : new Color(140, 80, 200, 200); }
    @Override public Color getBbMiddleColor() { return isDark() ? new Color(180, 100, 255, 120) : new Color(140, 80, 200, 140); }

    // ===== RSI Chart Colors =====

    @Override public Color getRsiColor() { return isDark() ? new Color(255, 193, 7) : new Color(255, 160, 0); }
    @Override public Color getRsiOverbought() { return isDark() ? new Color(255, 80, 80, 50) : new Color(255, 80, 80, 40); }
    @Override public Color getRsiOversold() { return isDark() ? new Color(80, 255, 80, 50) : new Color(80, 200, 80, 40); }

    // ===== MACD Chart Colors =====

    @Override public Color getMacdLineColor() { return isDark() ? new Color(0, 150, 255) : new Color(0, 120, 215); }
    @Override public Color getMacdSignalColor() { return isDark() ? new Color(255, 140, 0) : new Color(230, 120, 0); }
    @Override public Color getMacdHistPositive() { return isDark() ? new Color(76, 175, 80) : new Color(46, 125, 50); }
    @Override public Color getMacdHistNegative() { return isDark() ? new Color(244, 67, 54) : new Color(198, 40, 40); }

    // ===== ATR Chart Color =====

    @Override public Color getAtrColor() { return isDark() ? new Color(180, 100, 255) : new Color(140, 80, 200); }

    // ===== Orderflow/Delta Colors =====

    @Override public Color getDeltaPositive() { return isDark() ? new Color(38, 166, 91) : new Color(46, 125, 50); }
    @Override public Color getDeltaNegative() { return isDark() ? new Color(231, 76, 60) : new Color(198, 40, 40); }
    @Override public Color getCvdColor() { return isDark() ? new Color(52, 152, 219) : new Color(33, 150, 243); }

    // ===== Helpers =====

    private static Color ui(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Color darker(Color c, float amount) {
        return new Color(
            Math.max(0, (int)(c.getRed() * (1 - amount))),
            Math.max(0, (int)(c.getGreen() * (1 - amount))),
            Math.max(0, (int)(c.getBlue() * (1 - amount)))
        );
    }
}
