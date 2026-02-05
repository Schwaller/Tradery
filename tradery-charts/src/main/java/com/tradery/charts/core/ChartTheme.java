package com.tradery.charts.core;

import java.awt.*;

/**
 * Theme interface for chart styling.
 * Applications implement this to provide custom color schemes.
 */
public interface ChartTheme {

    // ===== Background Colors =====
    Color getBackgroundColor();
    Color getPlotBackgroundColor();
    Color getGridlineColor();
    Color getTextColor();
    Color getCrosshairColor();
    Color getAxisLabelColor();

    // ===== Price Chart Colors =====
    default Color getCandleUpColor() { return new Color(76, 175, 80); }
    default Color getCandleDownColor() { return new Color(244, 67, 54); }
    default Color getPriceLineColor() { return new Color(255, 255, 255, 180); }
    default Color getWinColor() { return new Color(76, 175, 80, 180); }
    default Color getLossColor() { return new Color(244, 67, 54, 180); }

    // ===== Volume Colors =====
    default Color getBuyVolumeColor() { return new Color(38, 166, 91); }
    default Color getSellVolumeColor() { return new Color(231, 76, 60); }

    // ===== Overlay Colors =====
    default Color getSmaColor() { return new Color(255, 193, 7, 200); }
    default Color getEmaColor() { return new Color(0, 200, 255, 200); }
    default Color getBbColor() { return new Color(180, 100, 255, 180); }
    default Color getBbMiddleColor() { return new Color(180, 100, 255, 120); }
    default Color getVwapColor() { return new Color(255, 215, 0, 220); }

    // ===== RSI Chart Colors =====
    default Color getRsiColor() { return new Color(255, 193, 7); }
    default Color getRsiOverbought() { return new Color(255, 80, 80, 50); }
    default Color getRsiOversold() { return new Color(80, 255, 80, 50); }

    // ===== MACD Chart Colors =====
    default Color getMacdLineColor() { return new Color(0, 150, 255); }
    default Color getMacdSignalColor() { return new Color(255, 140, 0); }
    default Color getMacdHistPositive() { return new Color(76, 175, 80); }
    default Color getMacdHistNegative() { return new Color(244, 67, 54); }

    // ===== ATR Chart Color =====
    default Color getAtrColor() { return new Color(180, 100, 255); }

    // ===== ADX Chart Color =====
    default Color getAdxColor() { return new Color(255, 165, 0); }

    // ===== Stochastic Chart Colors =====
    default Color getStochasticKColor() { return new Color(0, 200, 255); }
    default Color getStochasticDColor() { return new Color(255, 100, 150); }

    // ===== Orderflow/Delta Colors =====
    default Color getDeltaPositive() { return new Color(38, 166, 91); }
    default Color getDeltaNegative() { return new Color(231, 76, 60); }
    default Color getCvdColor() { return new Color(52, 152, 219); }

    /**
     * Get overlay color by index for multiple overlays of the same type.
     */
    default Color getOverlayColor(int index) {
        Color[] palette = {
            new Color(255, 193, 7, 200),   // Gold
            new Color(0, 200, 255, 200),   // Cyan
            new Color(255, 87, 34, 200),   // Deep Orange
            new Color(156, 39, 176, 200),  // Purple
            new Color(76, 175, 80, 200),   // Green
            new Color(233, 30, 99, 200),   // Pink
            new Color(63, 81, 181, 200),   // Indigo
            new Color(255, 235, 59, 200),  // Yellow
        };
        return palette[index % palette.length];
    }
}
