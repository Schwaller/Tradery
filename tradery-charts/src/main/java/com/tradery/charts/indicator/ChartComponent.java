package com.tradery.charts.indicator;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.awt.*;

import static com.tradery.charts.util.ChartPanelFactory.configure;
import com.tradery.charts.util.ChartStyles;

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
    private final JButton closeButton;
    private JPanel wrapper;

    public ChartComponent(String title) {
        this(title, null);
    }

    public ChartComponent(String title, double[] yAxisRange) {
        chart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        ChartStyles.stylizeChart(chart, title);

        if (yAxisRange != null && yAxisRange.length == 2) {
            chart.getXYPlot().getRangeAxis().setRange(yAxisRange[0], yAxisRange[1]);
        }

        chartPanel = new ChartPanel(chart);
        configure(chartPanel, chart);

        zoomButton = new JButton("\u2922");
        zoomButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        zoomButton.setMargin(new Insets(2, 4, 1, 4));
        zoomButton.setFocusPainted(false);
        zoomButton.setToolTipText("Zoom chart");

        fullScreenButton = new JButton("\u25a1");
        fullScreenButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        fullScreenButton.setMargin(new Insets(2, 4, 1, 4));
        fullScreenButton.setFocusPainted(false);
        fullScreenButton.setToolTipText("Full screen (hide other charts)");
        fullScreenButton.setVisible(false);

        closeButton = new JButton("\u2715");
        closeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        closeButton.setMargin(new Insets(2, 4, 1, 4));
        closeButton.setFocusPainted(false);
        closeButton.setToolTipText("Show all charts");
        closeButton.setVisible(false);

        zoomButton.setVisible(false);
    }

    public JPanel createWrapper(Runnable onZoom) {
        return createWrapper(onZoom, null);
    }

    public JPanel createWrapper(Runnable onZoom, Runnable onFullScreen) {
        return createWrapper(onZoom, onFullScreen, null);
    }

    public JPanel createWrapper(Runnable onZoom, Runnable onFullScreen, Runnable exitFullScreen) {
        zoomButton.addActionListener(e -> onZoom.run());
        if (onFullScreen != null) {
            fullScreenButton.addActionListener(e -> onFullScreen.run());
        }
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
                zoomButton.setBounds(w - zbs.width - 12, 8, zbs.width, zbs.height);
                fullScreenButton.setBounds(w - zbs.width - 12 - fsbs.width - 4, 8, fsbs.width, fsbs.height);
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

    public void setZoomed(boolean zoomed) {
        if (zoomed) {
            zoomButton.setText("\u2921");
            zoomButton.setToolTipText("Restore chart size");
        } else {
            zoomButton.setText("\u2922");
            zoomButton.setToolTipText("Zoom chart");
        }
    }

    public void setFullScreen(boolean fullScreen) {
        if (fullScreen) {
            fullScreenButton.setText("\u25a0");
            fullScreenButton.setToolTipText("Exit full screen");
        } else {
            fullScreenButton.setText("\u25a1");
            fullScreenButton.setToolTipText("Full screen (hide other charts)");
        }
    }

    public void setCloseButtonVisible(boolean visible) {
        closeButton.setVisible(visible);
    }

    public JFreeChart getChart() { return chart; }
    public ChartPanel getChartPanel() { return chartPanel; }
    public JButton getZoomButton() { return zoomButton; }
    public JButton getFullScreenButton() { return fullScreenButton; }
    public JPanel getWrapper() { return wrapper; }
}
