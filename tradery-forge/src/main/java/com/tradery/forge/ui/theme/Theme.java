package com.tradery.forge.ui.theme;

import java.awt.*;

/**
 * Defines all colors for a UI theme.
 */
public interface Theme {

    String getName();

    // ===== Background Colors =====
    Color getBackgroundColor();
    Color getPlotBackgroundColor();
    Color getGridlineColor();
    Color getTextColor();
    Color getCrosshairColor();

    // ===== Price Chart Colors =====
    Color getPriceLineColor();
    Color getWinColor();
    Color getLossColor();

    // ===== Equity/Comparison Chart Colors =====
    Color getEquityColor();
    Color getBuyHoldColor();
    Color getCapitalUsageColor();

    // ===== Overlay Colors =====
    Color getSmaColor();
    Color getEmaColor();
    Color getBbColor();
    Color getBbMiddleColor();

    // ===== RSI Chart Colors =====
    Color getRsiColor();
    Color getRsiOverbought();
    Color getRsiOversold();

    // ===== MACD Chart Colors =====
    Color getMacdLineColor();
    Color getMacdSignalColor();
    Color getMacdHistPositive();
    Color getMacdHistNegative();

    // ===== ATR Chart Color =====
    Color getAtrColor();

    // ===== Orderflow/Delta Colors =====
    Color getDeltaPositive();
    Color getDeltaNegative();
    Color getCvdColor();
    Color getWhaleDeltaPositive();
    Color getWhaleDeltaNegative();
    Color getBuyVolumeColor();
    Color getSellVolumeColor();

    // ===== Funding Rate Colors =====
    Color getFundingPositive();
    Color getFundingNegative();

    // ===== Axis Colors =====
    Color getAxisLabelColor();
}
