package com.tradery.ui.charts;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.awt.*;

/**
 * Reusable chart component that encapsulates a JFreeChart with its panel,
 * wrapper, and zoom button for consistent chart creation across the application.
 */
public class ChartComponent {

    private static final int MIN_CHART_HEIGHT = 60;

    private final JFreeChart chart;
    private final ChartPanel chartPanel;
    private final JButton zoomButton;
    private final JButton fullScreenButton;
    private JPanel wrapper;

    /**
     * Create a new chart component with the given title.
     */
    public ChartComponent(String title) {
        this(title, null);
    }

    /**
     * Create a new chart component with the given title and optional fixed Y-axis range.
     */
    public ChartComponent(String title, double[] yAxisRange) {
        // Create time series chart
        chart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        ChartStyles.stylizeChart(chart, title);

        // Apply fixed Y-axis range if specified
        if (yAxisRange != null && yAxisRange.length == 2) {
            chart.getXYPlot().getRangeAxis().setRange(yAxisRange[0], yAxisRange[1]);
        }

        // Create and configure chart panel
        chartPanel = new ChartPanel(chart);
        configureChartPanel();

        // Create zoom button
        zoomButton = new JButton("\u2922"); // ⤢
        zoomButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        zoomButton.setMargin(new Insets(2, 4, 1, 4));
        zoomButton.setFocusPainted(false);
        zoomButton.setToolTipText("Zoom chart");

        // Create full screen button
        fullScreenButton = new JButton("\u25a1"); // □ (empty square)
        fullScreenButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        fullScreenButton.setMargin(new Insets(2, 4, 1, 4));
        fullScreenButton.setFocusPainted(false);
        fullScreenButton.setToolTipText("Full screen (hide other charts)");
    }

    private void configureChartPanel() {
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        chartPanel.setMinimumDrawWidth(0);
        chartPanel.setMinimumDrawHeight(0);
        chartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        chartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);
        chartPanel.setBorder(null);
        // Remove chart padding
        chart.setPadding(new org.jfree.chart.ui.RectangleInsets(0, 0, 0, 0));
        chart.getXYPlot().setInsets(new org.jfree.chart.ui.RectangleInsets(0, 0, 0, 0));
    }

    /**
     * Create the wrapper panel with zoom button overlay.
     * Must be called after construction to set up the zoom callback.
     */
    public JPanel createWrapper(Runnable onZoom) {
        return createWrapper(onZoom, null);
    }

    /**
     * Create the wrapper panel with zoom and full screen button overlays.
     * Must be called after construction to set up the callbacks.
     */
    public JPanel createWrapper(Runnable onZoom, Runnable onFullScreen) {
        zoomButton.addActionListener(e -> onZoom.run());
        if (onFullScreen != null) {
            fullScreenButton.addActionListener(e -> onFullScreen.run());
        }

        JLayeredPane layeredPane = new JLayeredPane();
        chartPanel.setBounds(0, 0, 100, 100);
        layeredPane.add(chartPanel, JLayeredPane.DEFAULT_LAYER);

        Dimension zoomBtnSize = zoomButton.getPreferredSize();
        Dimension fsBtnSize = fullScreenButton.getPreferredSize();
        zoomButton.setBounds(0, 5, zoomBtnSize.width, zoomBtnSize.height);
        fullScreenButton.setBounds(0, 5, fsBtnSize.width, fsBtnSize.height);
        layeredPane.add(zoomButton, JLayeredPane.PALETTE_LAYER);
        if (onFullScreen != null) {
            layeredPane.add(fullScreenButton, JLayeredPane.PALETTE_LAYER);
        }

        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                chartPanel.setBounds(0, 0, w, h);
                Dimension zbs = zoomButton.getPreferredSize();
                Dimension fsbs = fullScreenButton.getPreferredSize();
                // Position zoom button at right edge
                zoomButton.setBounds(w - zbs.width - 12, 8, zbs.width, zbs.height);
                // Position full screen button to the left of zoom button
                fullScreenButton.setBounds(w - zbs.width - 12 - fsbs.width - 4, 8, fsbs.width, fsbs.height);
            }
        });

        wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setBorder(null);
        wrapper.add(layeredPane, BorderLayout.CENTER);
        wrapper.setMinimumSize(new Dimension(100, MIN_CHART_HEIGHT));
        wrapper.setPreferredSize(new Dimension(100, MIN_CHART_HEIGHT));
        return wrapper;
    }

    /**
     * Update zoom button state (zoomed vs normal).
     */
    public void setZoomed(boolean zoomed) {
        if (zoomed) {
            zoomButton.setText("\u2921"); // ⤡
            zoomButton.setToolTipText("Restore chart size");
        } else {
            zoomButton.setText("\u2922"); // ⤢
            zoomButton.setToolTipText("Zoom chart");
        }
    }

    /**
     * Update full screen button state.
     */
    public void setFullScreen(boolean fullScreen) {
        if (fullScreen) {
            fullScreenButton.setText("\u25a0"); // ■ (filled square)
            fullScreenButton.setToolTipText("Exit full screen");
        } else {
            fullScreenButton.setText("\u25a1"); // □ (empty square)
            fullScreenButton.setToolTipText("Full screen (hide other charts)");
        }
    }

    // Accessors

    public JFreeChart getChart() {
        return chart;
    }

    public ChartPanel getChartPanel() {
        return chartPanel;
    }

    public JButton getZoomButton() {
        return zoomButton;
    }

    public JButton getFullScreenButton() {
        return fullScreenButton;
    }

    public JPanel getWrapper() {
        return wrapper;
    }
}
