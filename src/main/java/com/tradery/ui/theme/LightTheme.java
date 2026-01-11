package com.tradery.ui.theme;

import java.awt.Color;

/**
 * Light theme - lighter backgrounds for daytime use.
 */
public class LightTheme implements Theme {

    @Override
    public String getName() { return "Light"; }

    // ===== Background Colors =====
    @Override public Color getBackgroundColor() { return new Color(245, 245, 248); }
    @Override public Color getPlotBackgroundColor() { return new Color(255, 255, 255); }
    @Override public Color getGridlineColor() { return new Color(220, 220, 225); }
    @Override public Color getTextColor() { return new Color(80, 80, 85); }
    @Override public Color getCrosshairColor() { return new Color(100, 100, 100, 180); }

    // ===== Price Chart Colors =====
    @Override public Color getPriceLineColor() { return new Color(50, 50, 55, 200); }
    @Override public Color getWinColor() { return new Color(46, 125, 50, 200); }
    @Override public Color getLossColor() { return new Color(198, 40, 40, 200); }

    // ===== Equity/Comparison Chart Colors =====
    @Override public Color getEquityColor() { return new Color(63, 81, 181); }
    @Override public Color getBuyHoldColor() { return new Color(255, 160, 0); }
    @Override public Color getCapitalUsageColor() { return new Color(67, 160, 71); }

    // ===== Overlay Colors =====
    @Override public Color getSmaColor() { return new Color(255, 160, 0, 220); }
    @Override public Color getEmaColor() { return new Color(0, 150, 200, 220); }
    @Override public Color getBbColor() { return new Color(140, 80, 200, 200); }
    @Override public Color getBbMiddleColor() { return new Color(140, 80, 200, 140); }

    // ===== RSI Chart Colors =====
    @Override public Color getRsiColor() { return new Color(255, 160, 0); }
    @Override public Color getRsiOverbought() { return new Color(255, 80, 80, 40); }
    @Override public Color getRsiOversold() { return new Color(80, 200, 80, 40); }

    // ===== MACD Chart Colors =====
    @Override public Color getMacdLineColor() { return new Color(0, 120, 215); }
    @Override public Color getMacdSignalColor() { return new Color(230, 120, 0); }
    @Override public Color getMacdHistPositive() { return new Color(46, 125, 50); }
    @Override public Color getMacdHistNegative() { return new Color(198, 40, 40); }

    // ===== ATR Chart Color =====
    @Override public Color getAtrColor() { return new Color(140, 80, 200); }

    // ===== Orderflow/Delta Colors =====
    @Override public Color getDeltaPositive() { return new Color(46, 125, 50); }
    @Override public Color getDeltaNegative() { return new Color(198, 40, 40); }
    @Override public Color getCvdColor() { return new Color(33, 150, 243); }
    @Override public Color getWhaleDeltaPositive() { return new Color(123, 31, 162); }
    @Override public Color getWhaleDeltaNegative() { return new Color(191, 54, 12); }
    @Override public Color getBuyVolumeColor() { return new Color(46, 125, 50); }
    @Override public Color getSellVolumeColor() { return new Color(198, 40, 40); }

    // ===== Funding Rate Colors =====
    @Override public Color getFundingPositive() { return new Color(230, 120, 0); }
    @Override public Color getFundingNegative() { return new Color(33, 150, 243); }

    // ===== Axis Colors =====
    @Override public Color getAxisLabelColor() { return new Color(60, 60, 65); }
}
