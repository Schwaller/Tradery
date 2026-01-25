package com.tradery.forge.ui.charts;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Centralized zoom/pan/Y-axis drag interaction manager for all charts.
 * Provides unified interaction behavior across ChartsPanel and PhasePreviewChart.
 */
public class ChartInteractionManager {

    // Pan state
    private Point panStart = null;
    private double panStartDomainMin, panStartDomainMax;
    private double panStartRangeMin, panStartRangeMax;
    private ChartPanel activeChartPanel = null;
    private XYPlot activePlot = null;

    // Y-axis drag state
    private boolean draggingYAxis = false;
    private int yAxisDragStartY;
    private double yAxisDragStartRangeMin, yAxisDragStartRangeMax;

    // Registry of synced charts
    private final List<JFreeChart> syncedCharts = new ArrayList<>();

    // Fixed-width mode support
    private boolean fixedWidthMode = false;
    private Supplier<JScrollBar> scrollBarSupplier;

    // Double-click callbacks per chart panel for full-screen toggle
    private java.util.Map<ChartPanel, Runnable> doubleClickCallbacks = new java.util.HashMap<>();

    /**
     * Add a chart to the synchronization registry.
     * When zooming/panning, all synced charts will have their domain axes updated together.
     */
    public void addChart(JFreeChart chart) {
        if (chart != null && !syncedCharts.contains(chart)) {
            syncedCharts.add(chart);
        }
    }

    /**
     * Remove a chart from the synchronization registry.
     */
    public void removeChart(JFreeChart chart) {
        syncedCharts.remove(chart);
    }

    /**
     * Clear all synced charts.
     */
    public void clearCharts() {
        syncedCharts.clear();
    }

    /**
     * Set fixed-width mode configuration.
     * In fixed-width mode, mouse wheel scrolls the scrollbar instead of zooming.
     */
    public void setFixedWidthMode(boolean enabled, Supplier<JScrollBar> scrollBarSupplier) {
        this.fixedWidthMode = enabled;
        this.scrollBarSupplier = scrollBarSupplier;
    }

    /**
     * Set a callback to be invoked on double-click for a specific chart panel.
     * Used for full-screen toggle per chart.
     */
    public void setDoubleClickCallback(ChartPanel panel, Runnable callback) {
        if (panel != null && callback != null) {
            doubleClickCallbacks.put(panel, callback);
        }
    }

    /**
     * Remove double-click callback for a chart panel.
     */
    public void removeDoubleClickCallback(ChartPanel panel) {
        doubleClickCallbacks.remove(panel);
    }

    /**
     * Attach interaction listeners (zoom, pan, Y-axis drag) to a chart panel.
     */
    public void attachListeners(ChartPanel panel) {
        attachListeners(panel, false);
    }

    /**
     * Attach interaction listeners with optional Y-axis position on right side.
     */
    public void attachListeners(ChartPanel panel, boolean yAxisOnRight) {
        panel.addMouseWheelListener(createMouseWheelListener(panel));
        panel.addMouseListener(createMouseListener(panel, yAxisOnRight));
        panel.addMouseMotionListener(createMouseMotionListener(panel));
    }

    /**
     * Set domain range on all synced charts.
     */
    public void setAllDomainRange(double min, double max) {
        for (JFreeChart chart : syncedCharts) {
            if (chart != null) {
                DateAxis axis = (DateAxis) chart.getXYPlot().getDomainAxis();
                axis.setRange(min, max);
            }
        }
    }

    // ===== Internal Mouse Handlers =====

    private MouseWheelListener createMouseWheelListener(ChartPanel panel) {
        return e -> {
            // In fixed-width mode, scroll the scrollbar instead of zooming
            if (fixedWidthMode && scrollBarSupplier != null) {
                JScrollBar scrollBar = scrollBarSupplier.get();
                if (scrollBar != null && scrollBar.isVisible()) {
                    int scrollAmount = e.getWheelRotation() * scrollBar.getUnitIncrement();
                    int newValue = scrollBar.getValue() + scrollAmount;
                    newValue = Math.max(scrollBar.getMinimum(),
                        Math.min(newValue, scrollBar.getMaximum() - scrollBar.getVisibleAmount()));
                    scrollBar.setValue(newValue);
                    return;
                }
            }

            // Zoom-to-cursor on X-axis
            if (panel.getChartRenderingInfo() == null) return;
            Rectangle2D dataArea = panel.getChartRenderingInfo().getPlotInfo().getDataArea();
            if (dataArea == null) return;

            XYPlot plot = panel.getChart().getXYPlot();
            DateAxis domainAxis = (DateAxis) plot.getDomainAxis();

            double zoomFactor = e.getWheelRotation() < 0 ? 0.9 : 1.1;
            double mouseX = domainAxis.java2DToValue(e.getPoint().getX(), dataArea, plot.getDomainAxisEdge());

            double domainMin = domainAxis.getLowerBound();
            double domainMax = domainAxis.getUpperBound();
            double domainRange = domainMax - domainMin;
            double newRange = domainRange * zoomFactor;

            double mouseRatio = (mouseX - domainMin) / domainRange;
            double newMin = mouseX - mouseRatio * newRange;
            double newMax = newMin + newRange;

            setAllDomainRange(newMin, newMax);
        };
    }

    private MouseAdapter createMouseListener(ChartPanel panel, boolean yAxisOnRight) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    Runnable callback = doubleClickCallbacks.get(panel);
                    if (callback != null) {
                        callback.run();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (fixedWidthMode) return;

                    if (isOnYAxis(e.getPoint(), panel, yAxisOnRight)) {
                        startYAxisDrag(e.getY(), panel);
                    } else {
                        startPan(e.getPoint(), panel);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                panStart = null;
                activeChartPanel = null;
                activePlot = null;
                draggingYAxis = false;
            }
        };
    }

    private MouseMotionAdapter createMouseMotionListener(ChartPanel panel) {
        return new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingYAxis && activePlot != null) {
                    handleYAxisDrag(e.getY());
                } else if (panStart != null && activeChartPanel == panel) {
                    handlePan(e.getPoint(), panel);
                }
            }
        };
    }

    private boolean isOnYAxis(Point point, ChartPanel panel, boolean yAxisOnRight) {
        if (panel.getChartRenderingInfo() == null) return false;
        Rectangle2D dataArea = panel.getChartRenderingInfo().getPlotInfo().getDataArea();
        if (dataArea == null) return false;

        // Check current axis position from config
        String axisPosition = ChartConfig.getInstance().getPriceAxisPosition();

        boolean onLeft = point.x < dataArea.getMinX();
        boolean onRight = point.x > dataArea.getMaxX() && point.x < dataArea.getMaxX() + 60;

        if ("both".equals(axisPosition)) {
            return onLeft || onRight;
        } else if ("right".equals(axisPosition)) {
            return onRight;
        } else {
            return onLeft;
        }
    }

    private void startYAxisDrag(int y, ChartPanel panel) {
        draggingYAxis = true;
        yAxisDragStartY = y;
        activePlot = panel.getChart().getXYPlot();
        yAxisDragStartRangeMin = activePlot.getRangeAxis().getLowerBound();
        yAxisDragStartRangeMax = activePlot.getRangeAxis().getUpperBound();
    }

    private void handleYAxisDrag(int currentY) {
        if (activePlot == null) return;
        int dy = currentY - yAxisDragStartY;
        double scaleFactor = Math.pow(1.01, dy);

        double originalRange = yAxisDragStartRangeMax - yAxisDragStartRangeMin;
        double originalCenter = (yAxisDragStartRangeMax + yAxisDragStartRangeMin) / 2.0;

        double newRange = originalRange * scaleFactor;
        double newMin = originalCenter - newRange / 2.0;
        double newMax = originalCenter + newRange / 2.0;

        activePlot.getRangeAxis().setRange(newMin, newMax);
    }

    private void startPan(Point point, ChartPanel panel) {
        panStart = point;
        activeChartPanel = panel;
        activePlot = panel.getChart().getXYPlot();

        DateAxis domainAxis = (DateAxis) activePlot.getDomainAxis();
        panStartDomainMin = domainAxis.getLowerBound();
        panStartDomainMax = domainAxis.getUpperBound();
        panStartRangeMin = activePlot.getRangeAxis().getLowerBound();
        panStartRangeMax = activePlot.getRangeAxis().getUpperBound();
    }

    private void handlePan(Point currentPoint, ChartPanel panel) {
        if (panel.getChartRenderingInfo() == null) return;
        Rectangle2D dataArea = panel.getChartRenderingInfo().getPlotInfo().getDataArea();
        if (dataArea == null) return;

        int dx = currentPoint.x - panStart.x;
        int dy = currentPoint.y - panStart.y;

        double domainRange = panStartDomainMax - panStartDomainMin;
        double domainDelta = -dx * domainRange / dataArea.getWidth();

        // Pan X-axis on all charts
        setAllDomainRange(panStartDomainMin + domainDelta, panStartDomainMax + domainDelta);

        // Pan Y-axis only on the active chart
        if (activePlot != null) {
            double rangeRange = panStartRangeMax - panStartRangeMin;
            double rangeDelta = dy * rangeRange / dataArea.getHeight();
            activePlot.getRangeAxis().setRange(panStartRangeMin + rangeDelta, panStartRangeMax + rangeDelta);
        }
    }
}
