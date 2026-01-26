package com.tradery.forge.ui.charts;

import com.tradery.charts.core.ChartTheme;
import com.tradery.forge.ui.theme.Theme;
import com.tradery.forge.ui.theme.ThemeManager;

import java.awt.Color;

/**
 * Adapts forge's Theme to the ChartTheme interface.
 * This allows charts from tradery-charts to use forge's theme colors.
 */
public class ForgeChartTheme implements ChartTheme {

    private static final ForgeChartTheme INSTANCE = new ForgeChartTheme();

    private ForgeChartTheme() {}

    public static ForgeChartTheme getInstance() {
        return INSTANCE;
    }

    private Theme theme() {
        return ThemeManager.theme();
    }

    @Override
    public Color getBackgroundColor() {
        return theme().getBackgroundColor();
    }

    @Override
    public Color getPlotBackgroundColor() {
        return theme().getPlotBackgroundColor();
    }

    @Override
    public Color getGridlineColor() {
        return theme().getGridlineColor();
    }

    @Override
    public Color getTextColor() {
        return theme().getTextColor();
    }

    @Override
    public Color getCrosshairColor() {
        return theme().getCrosshairColor();
    }

    @Override
    public Color getAxisLabelColor() {
        return theme().getAxisLabelColor();
    }

    @Override
    public Color getPriceLineColor() {
        return theme().getPriceLineColor();
    }

    @Override
    public Color getWinColor() {
        return theme().getWinColor();
    }

    @Override
    public Color getLossColor() {
        return theme().getLossColor();
    }

    @Override
    public Color getBuyVolumeColor() {
        return theme().getBuyVolumeColor();
    }

    @Override
    public Color getSellVolumeColor() {
        return theme().getSellVolumeColor();
    }

    @Override
    public Color getSmaColor() {
        return theme().getSmaColor();
    }

    @Override
    public Color getEmaColor() {
        return theme().getEmaColor();
    }

    @Override
    public Color getBbColor() {
        return theme().getBbColor();
    }

    @Override
    public Color getBbMiddleColor() {
        return theme().getBbMiddleColor();
    }

    @Override
    public Color getRsiColor() {
        return theme().getRsiColor();
    }

    @Override
    public Color getRsiOverbought() {
        return theme().getRsiOverbought();
    }

    @Override
    public Color getRsiOversold() {
        return theme().getRsiOversold();
    }

    @Override
    public Color getMacdLineColor() {
        return theme().getMacdLineColor();
    }

    @Override
    public Color getMacdSignalColor() {
        return theme().getMacdSignalColor();
    }

    @Override
    public Color getMacdHistPositive() {
        return theme().getMacdHistPositive();
    }

    @Override
    public Color getMacdHistNegative() {
        return theme().getMacdHistNegative();
    }

    @Override
    public Color getAtrColor() {
        return theme().getAtrColor();
    }

    @Override
    public Color getDeltaPositive() {
        return theme().getDeltaPositive();
    }

    @Override
    public Color getDeltaNegative() {
        return theme().getDeltaNegative();
    }

    @Override
    public Color getCvdColor() {
        return theme().getCvdColor();
    }
}
