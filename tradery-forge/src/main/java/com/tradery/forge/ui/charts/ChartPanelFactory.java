package com.tradery.forge.ui.charts;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ui.RectangleInsets;

import javax.swing.*;

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

        // Custom simplified popup menu
        configurePopupMenu(panel);
    }

    /**
     * Client property key for storing the full-screen toggle callback on a ChartPanel.
     */
    public static final String FULL_SCREEN_CALLBACK_KEY = "fullScreenCallback";

    /**
     * Set the full-screen toggle callback for a chart panel.
     * This enables the "Show Only This Chart" context menu item.
     */
    public static void setFullScreenCallback(ChartPanel panel, Runnable callback) {
        panel.putClientProperty(FULL_SCREEN_CALLBACK_KEY, callback);
    }

    private static void configurePopupMenu(ChartPanel panel) {
        JPopupMenu popup = new JPopupMenu();

        // Show Only This Chart - at the top for visibility
        JMenuItem showOnlyThis = new JMenuItem("Show Only This Chart (double-click)");
        showOnlyThis.addActionListener(e -> {
            Runnable callback = (Runnable) panel.getClientProperty(FULL_SCREEN_CALLBACK_KEY);
            if (callback != null) {
                callback.run();
            }
        });
        popup.add(showOnlyThis);

        popup.addSeparator();

        JMenuItem fitHorizontal = new JMenuItem("Fit Horizontal");
        fitHorizontal.addActionListener(e -> panel.restoreAutoDomainBounds());
        popup.add(fitHorizontal);

        JMenuItem fitVertical = new JMenuItem("Fit Vertical");
        fitVertical.addActionListener(e -> panel.restoreAutoRangeBounds());
        popup.add(fitVertical);

        JMenuItem fitBoth = new JMenuItem("Fit Both");
        fitBoth.addActionListener(e -> panel.restoreAutoBounds());
        popup.add(fitBoth);

        popup.addSeparator();

        // Price axis position submenu
        JMenu axisMenu = new JMenu("Price Axis");
        ButtonGroup axisGroup = new ButtonGroup();

        JRadioButtonMenuItem leftItem = new JRadioButtonMenuItem("Left");
        JRadioButtonMenuItem rightItem = new JRadioButtonMenuItem("Right");
        JRadioButtonMenuItem bothItem = new JRadioButtonMenuItem("Both");

        // Set initial selection based on current config
        String currentPos = ChartConfig.getInstance().getPriceAxisPosition();
        leftItem.setSelected("left".equals(currentPos));
        rightItem.setSelected("right".equals(currentPos));
        bothItem.setSelected("both".equals(currentPos));

        leftItem.addActionListener(e -> ChartConfig.getInstance().setPriceAxisPosition("left"));
        rightItem.addActionListener(e -> ChartConfig.getInstance().setPriceAxisPosition("right"));
        bothItem.addActionListener(e -> ChartConfig.getInstance().setPriceAxisPosition("both"));

        axisGroup.add(leftItem);
        axisGroup.add(rightItem);
        axisGroup.add(bothItem);

        axisMenu.add(leftItem);
        axisMenu.add(rightItem);
        axisMenu.add(bothItem);
        popup.add(axisMenu);

        popup.addSeparator();

        JMenuItem resetLayout = new JMenuItem("Reset Layout");
        resetLayout.addActionListener(e -> {
            ChartConfig.getInstance().clearChartDividerPositions();
        });
        popup.add(resetLayout);

        popup.addSeparator();

        JMenuItem copyImage = new JMenuItem("Copy");
        copyImage.addActionListener(e -> panel.doCopy());
        popup.add(copyImage);

        JMenuItem saveAs = new JMenuItem("Save As...");
        saveAs.addActionListener(e -> {
            try {
                panel.doSaveAs();
            } catch (Exception ex) {
                // Ignore save errors
            }
        });
        popup.add(saveAs);

        panel.setPopupMenu(popup);
    }
}
