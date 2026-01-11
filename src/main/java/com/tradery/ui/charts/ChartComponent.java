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
    }

    private void configureChartPanel() {
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        chartPanel.setMinimumDrawWidth(0);
        chartPanel.setMinimumDrawHeight(0);
        chartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        chartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);
    }

    /**
     * Create the wrapper panel with zoom button overlay.
     * Must be called after construction to set up the zoom callback.
     */
    public JPanel createWrapper(Runnable onZoom) {
        zoomButton.addActionListener(e -> onZoom.run());

        JLayeredPane layeredPane = new JLayeredPane();
        chartPanel.setBounds(0, 0, 100, 100);
        layeredPane.add(chartPanel, JLayeredPane.DEFAULT_LAYER);

        Dimension btnSize = zoomButton.getPreferredSize();
        zoomButton.setBounds(0, 5, btnSize.width, btnSize.height);
        layeredPane.add(zoomButton, JLayeredPane.PALETTE_LAYER);

        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                chartPanel.setBounds(0, 0, w, h);
                Dimension bs = zoomButton.getPreferredSize();
                zoomButton.setBounds(w - bs.width - 12, 8, bs.width, bs.height);
            }
        });

        wrapper = new JPanel(new BorderLayout());
        wrapper.add(layeredPane, BorderLayout.CENTER);
        wrapper.setMinimumSize(new Dimension(100, MIN_CHART_HEIGHT));
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

    public JPanel getWrapper() {
        return wrapper;
    }
}
