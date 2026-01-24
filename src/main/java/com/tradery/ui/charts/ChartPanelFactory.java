package com.tradery.ui.charts;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ui.RectangleInsets;

/**
 * Factory for creating consistently configured chart panels.
 * Eliminates duplication of chart panel configuration across ChartsPanel,
 * ChartComponent, and ChartZoomManager.
 */
public final class ChartPanelFactory {

    private ChartPanelFactory() {} // Prevent instantiation

    /**
     * Create a new ChartPanel with standard configuration.
     */
    public static ChartPanel create(JFreeChart chart) {
        ChartPanel panel = new ChartPanel(chart);
        configure(panel, chart);
        return panel;
    }

    /**
     * Configure an existing ChartPanel with standard settings.
     */
    public static void configure(ChartPanel panel, JFreeChart chart) {
        // Disable default zoom behavior - we handle it manually
        panel.setMouseWheelEnabled(false);
        panel.setDomainZoomable(false);
        panel.setRangeZoomable(false);

        // Allow drawing at any size
        panel.setMinimumDrawWidth(0);
        panel.setMinimumDrawHeight(0);
        panel.setMaximumDrawWidth(Integer.MAX_VALUE);
        panel.setMaximumDrawHeight(Integer.MAX_VALUE);

        // Remove border
        panel.setBorder(null);

        // Remove chart and plot padding for tight layout
        if (chart != null) {
            chart.setPadding(new RectangleInsets(0, 0, 0, 0));
            if (chart.getPlot() != null) {
                chart.getXYPlot().setInsets(new RectangleInsets(0, 0, 0, 0));
            }
        }
    }
}
