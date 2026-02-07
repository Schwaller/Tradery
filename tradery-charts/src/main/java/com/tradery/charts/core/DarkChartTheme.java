package com.tradery.charts.core;

import javax.swing.*;
import java.awt.*;

/**
 * Default dark theme for charts.
 * Reads background colors from UIManager so charts adapt to the active FlatLaf theme.
 */
public class DarkChartTheme implements ChartTheme {

    public static final DarkChartTheme INSTANCE = new DarkChartTheme();

    private DarkChartTheme() {}

    @Override
    public Color getBackgroundColor() {
        return ui("Panel.background", new Color(30, 30, 35));
    }

    @Override
    public Color getPlotBackgroundColor() {
        return darker(getBackgroundColor(), 0.08f);
    }

    @Override
    public Color getGridlineColor() {
        return ui("Separator.foreground", new Color(60, 60, 65));
    }

    @Override
    public Color getTextColor() {
        return ui("Label.disabledForeground", new Color(150, 150, 150));
    }

    @Override
    public Color getCrosshairColor() {
        Color base = ui("Label.disabledForeground", new Color(150, 150, 150));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 180);
    }

    @Override
    public Color getAxisLabelColor() {
        return ui("Label.disabledForeground", Color.LIGHT_GRAY);
    }

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
