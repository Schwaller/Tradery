package com.tradery.forge.ui.theme;

import java.awt.Color;

/**
 * Dark theme - the default theme with dark backgrounds.
 */
public class DarkTheme implements Theme {

    @Override
    public String getName() { return "Dark"; }

    // ===== Background Colors =====
    @Override public Color getBackgroundColor() { return new Color(30, 30, 35); }
    @Override public Color getPlotBackgroundColor() { return new Color(20, 20, 25); }
    @Override public Color getGridlineColor() { return new Color(60, 60, 65); }
    @Override public Color getTextColor() { return new Color(150, 150, 150); }
    @Override public Color getCrosshairColor() { return new Color(150, 150, 150, 180); }

    // ===== Price Chart Colors =====
    @Override public Color getPriceLineColor() { return new Color(255, 255, 255, 180); }
    @Override public Color getWinColor() { return new Color(76, 175, 80, 180); }
    @Override public Color getLossColor() { return new Color(244, 67, 54, 180); }

    // ===== Equity/Comparison Chart Colors =====
    @Override public Color getEquityColor() { return new Color(77, 77, 255); }
    @Override public Color getBuyHoldColor() { return new Color(255, 193, 7); }
    @Override public Color getCapitalUsageColor() { return new Color(57, 255, 20); }

    // ===== Overlay Colors =====
    @Override public Color getSmaColor() { return new Color(255, 193, 7, 200); }
    @Override public Color getEmaColor() { return new Color(0, 200, 255, 200); }
    @Override public Color getBbColor() { return new Color(180, 100, 255, 180); }
    @Override public Color getBbMiddleColor() { return new Color(180, 100, 255, 120); }

    // ===== RSI Chart Colors =====
    @Override public Color getRsiColor() { return new Color(255, 193, 7); }
    @Override public Color getRsiOverbought() { return new Color(255, 80, 80, 50); }
    @Override public Color getRsiOversold() { return new Color(80, 255, 80, 50); }

    // ===== MACD Chart Colors =====
    @Override public Color getMacdLineColor() { return new Color(0, 150, 255); }
    @Override public Color getMacdSignalColor() { return new Color(255, 140, 0); }
    @Override public Color getMacdHistPositive() { return new Color(76, 175, 80); }
    @Override public Color getMacdHistNegative() { return new Color(244, 67, 54); }

    // ===== ATR Chart Color =====
    @Override public Color getAtrColor() { return new Color(180, 100, 255); }

    // ===== Orderflow/Delta Colors =====
    @Override public Color getDeltaPositive() { return new Color(38, 166, 91); }
    @Override public Color getDeltaNegative() { return new Color(231, 76, 60); }
    @Override public Color getCvdColor() { return new Color(52, 152, 219); }
    @Override public Color getWhaleDeltaPositive() { return new Color(155, 89, 182); }
    @Override public Color getWhaleDeltaNegative() { return new Color(211, 84, 0); }
    @Override public Color getBuyVolumeColor() { return new Color(38, 166, 91); }
    @Override public Color getSellVolumeColor() { return new Color(231, 76, 60); }

    // ===== Funding Rate Colors =====
    @Override public Color getFundingPositive() { return new Color(230, 126, 34); }
    @Override public Color getFundingNegative() { return new Color(52, 152, 219); }

    // ===== Axis Colors =====
    @Override public Color getAxisLabelColor() { return Color.LIGHT_GRAY; }
}
