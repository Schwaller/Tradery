package com.tradery.charts.core;

import java.awt.Color;

/**
 * Default dark theme for charts.
 * Provides a professional trading platform look.
 */
public class DarkChartTheme implements ChartTheme {

    public static final DarkChartTheme INSTANCE = new DarkChartTheme();

    private DarkChartTheme() {}

    @Override
    public Color getBackgroundColor() {
        return new Color(30, 30, 35);
    }

    @Override
    public Color getPlotBackgroundColor() {
        return new Color(20, 20, 25);
    }

    @Override
    public Color getGridlineColor() {
        return new Color(60, 60, 65);
    }

    @Override
    public Color getTextColor() {
        return new Color(150, 150, 150);
    }

    @Override
    public Color getCrosshairColor() {
        return new Color(150, 150, 150, 180);
    }

    @Override
    public Color getAxisLabelColor() {
        return new Color(150, 150, 150);
    }
}
