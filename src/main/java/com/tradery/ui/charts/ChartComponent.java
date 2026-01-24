package com.tradery.ui.charts;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.awt.*;

import static com.tradery.ui.charts.ChartPanelFactory.configure;

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
    private final JButton closeButton; // Close button for exiting full-screen mode
    private JPanel wrapper;
    private Runnable exitFullScreenCallback;

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

        // Create full screen button (hidden by default)
        fullScreenButton = new JButton("\u25a1"); // □ (empty square)
        fullScreenButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        fullScreenButton.setMargin(new Insets(2, 4, 1, 4));
        fullScreenButton.setFocusPainted(false);
        fullScreenButton.setToolTipText("Full screen (hide other charts)");
        fullScreenButton.setVisible(false); // Hidden by default

        // Create close button for exiting full-screen mode (top-left)
        closeButton = new JButton("\u2715"); // ✕
        closeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        closeButton.setMargin(new Insets(2, 4, 1, 4));
        closeButton.setFocusPainted(false);
        closeButton.setToolTipText("Show all charts");
        closeButton.setVisible(false); // Only visible in full-screen mode

        // Hide zoom button by default too
        zoomButton.setVisible(false);
    }

    private void configureChartPanel() {
        configure(chartPanel, chart);
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
        return createWrapper(onZoom, onFullScreen, null);
    }

    /**
     * Create the wrapper panel with zoom, full screen, and exit full screen button overlays.
     * Must be called after construction to set up the callbacks.
     */
    public JPanel createWrapper(Runnable onZoom, Runnable onFullScreen, Runnable exitFullScreen) {
        zoomButton.addActionListener(e -> onZoom.run());
        if (onFullScreen != null) {
            fullScreenButton.addActionListener(e -> onFullScreen.run());
        }
        this.exitFullScreenCallback = exitFullScreen;
        if (exitFullScreen != null) {
            closeButton.addActionListener(e -> exitFullScreen.run());
        }

        JLayeredPane layeredPane = new JLayeredPane();
        chartPanel.setBounds(0, 0, 100, 100);
        layeredPane.add(chartPanel, JLayeredPane.DEFAULT_LAYER);

        Dimension zoomBtnSize = zoomButton.getPreferredSize();
        Dimension fsBtnSize = fullScreenButton.getPreferredSize();
        Dimension closeBtnSize = closeButton.getPreferredSize();
        zoomButton.setBounds(0, 5, zoomBtnSize.width, zoomBtnSize.height);
        fullScreenButton.setBounds(0, 5, fsBtnSize.width, fsBtnSize.height);
        closeButton.setBounds(8, 8, closeBtnSize.width, closeBtnSize.height);
        layeredPane.add(zoomButton, JLayeredPane.PALETTE_LAYER);
        if (onFullScreen != null) {
            layeredPane.add(fullScreenButton, JLayeredPane.PALETTE_LAYER);
        }
        layeredPane.add(closeButton, JLayeredPane.PALETTE_LAYER);

        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                chartPanel.setBounds(0, 0, w, h);
                Dimension zbs = zoomButton.getPreferredSize();
                Dimension fsbs = fullScreenButton.getPreferredSize();
                Dimension cbs = closeButton.getPreferredSize();
                // Position zoom button at right edge
                zoomButton.setBounds(w - zbs.width - 12, 8, zbs.width, zbs.height);
                // Position full screen button to the left of zoom button
                fullScreenButton.setBounds(w - zbs.width - 12 - fsbs.width - 4, 8, fsbs.width, fsbs.height);
                // Position close button at top-left
                closeButton.setBounds(8, 8, cbs.width, cbs.height);
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

    /**
     * Set close button visibility (only visible in full-screen mode).
     */
    public void setCloseButtonVisible(boolean visible) {
        closeButton.setVisible(visible);
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
