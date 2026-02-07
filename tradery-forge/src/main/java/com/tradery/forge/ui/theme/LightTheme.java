package com.tradery.forge.ui.theme;

import javax.swing.*;
import java.awt.*;

/**
 * Light theme - lighter backgrounds for daytime use.
 * Reads background colors from UIManager so charts adapt to the active FlatLaf theme.
 */
public class LightTheme implements Theme {

    @Override
    public String getName() { return "Light"; }

    // ===== Background Colors =====
    @Override public Color getBackgroundColor() { return ui("Panel.background", new Color(245, 245, 248)); }
    @Override public Color getPlotBackgroundColor() { return darker(getBackgroundColor(), 0.03f); }
    @Override public Color getGridlineColor() { return ui("Separator.foreground", new Color(220, 220, 225)); }
    @Override public Color getTextColor() { return ui("Label.disabledForeground", new Color(80, 80, 85)); }
    @Override public Color getCrosshairColor() {
        Color base = ui("Label.disabledForeground", new Color(100, 100, 100));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 180);
    }

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
    @Override public Color getAxisLabelColor() { return ui("Label.disabledForeground", new Color(60, 60, 65)); }

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
