package com.tradery.desk.ui.charts;

import com.tradery.charts.core.ChartTheme;

import java.awt.Color;

/**
 * Dark theme for Desk charts.
 * Provides consistent styling for all chart components in the Desk application.
 */
public class DeskChartTheme implements ChartTheme {

    private static final DeskChartTheme INSTANCE = new DeskChartTheme();

    private DeskChartTheme() {}

    public static DeskChartTheme getInstance() {
        return INSTANCE;
    }

    // ===== Base Colors =====

    @Override
    public Color getBackgroundColor() {
        return new Color(30, 30, 30);
    }

    @Override
    public Color getPlotBackgroundColor() {
        return new Color(30, 30, 30);
    }

    @Override
    public Color getGridlineColor() {
        return new Color(60, 60, 60);
    }

    @Override
    public Color getTextColor() {
        return new Color(200, 200, 200);
    }

    @Override
    public Color getCrosshairColor() {
        return new Color(150, 150, 150);
    }

    @Override
    public Color getAxisLabelColor() {
        return new Color(200, 200, 200);
    }

    @Override
    public Color getPriceLineColor() {
        return new Color(100, 100, 100);
    }

    // ===== Candlestick Colors =====

    @Override
    public Color getWinColor() {
        return new Color(38, 166, 154);  // Teal green
    }

    @Override
    public Color getLossColor() {
        return new Color(239, 83, 80);   // Red
    }

    // ===== Volume Colors =====

    @Override
    public Color getBuyVolumeColor() {
        return new Color(38, 166, 154, 180);
    }

    @Override
    public Color getSellVolumeColor() {
        return new Color(239, 83, 80, 180);
    }

    // ===== Overlay Colors =====

    @Override
    public Color getSmaColor() {
        return new Color(255, 193, 7);   // Amber
    }

    @Override
    public Color getEmaColor() {
        return new Color(33, 150, 243);  // Blue
    }

    @Override
    public Color getBbColor() {
        return new Color(156, 39, 176);  // Purple
    }

    @Override
    public Color getBbMiddleColor() {
        return new Color(186, 104, 200); // Light purple
    }

    // ===== RSI Colors =====

    @Override
    public Color getRsiColor() {
        return new Color(255, 193, 7);   // Amber
    }

    @Override
    public Color getRsiOverbought() {
        return new Color(239, 83, 80, 100);
    }

    @Override
    public Color getRsiOversold() {
        return new Color(38, 166, 154, 100);
    }

    // ===== MACD Colors =====

    @Override
    public Color getMacdLineColor() {
        return new Color(33, 150, 243);  // Blue
    }

    @Override
    public Color getMacdSignalColor() {
        return new Color(255, 152, 0);   // Orange
    }

    @Override
    public Color getMacdHistPositive() {
        return new Color(38, 166, 154, 200);
    }

    @Override
    public Color getMacdHistNegative() {
        return new Color(239, 83, 80, 200);
    }

    // ===== ATR Color =====

    @Override
    public Color getAtrColor() {
        return new Color(255, 193, 7);   // Amber
    }

    // ===== Delta/CVD Colors =====

    @Override
    public Color getDeltaPositive() {
        return new Color(38, 166, 154);
    }

    @Override
    public Color getDeltaNegative() {
        return new Color(239, 83, 80);
    }

    @Override
    public Color getCvdColor() {
        return new Color(33, 150, 243);
    }
}
