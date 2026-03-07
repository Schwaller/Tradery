package com.tradery.charts.core;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Unified chart lifecycle manager composing {@link ChartCoordinator} and
 * {@link ChartInteractionManager}. One {@code addChart()} call wires crosshair sync,
 * zoom/pan interaction, and listener attachment.
 */
public class ChartLifecycleManager {

    private final ChartCoordinator coordinator;
    private final ChartInteractionManager interactionManager;
    private final IdentityHashMap<ChartPanel, JFreeChart> tracked = new IdentityHashMap<>();

    public ChartLifecycleManager(ChartCoordinator coordinator, ChartInteractionManager interactionManager) {
        this.coordinator = coordinator;
        this.interactionManager = interactionManager;
    }

    /**
     * Register a chart for crosshair sync, zoom/pan interaction, and listener attachment.
     * Idempotent — repeated calls for the same panel are no-ops.
     */
    public void addChart(JFreeChart chart, ChartPanel panel) {
        if (panel == null || chart == null || tracked.containsKey(panel)) return;

        tracked.put(panel, chart);
        coordinator.register(panel);
        interactionManager.addChart(chart);
        interactionManager.attachListeners(panel);
    }

    /**
     * Unregister a chart from all managers.
     */
    public void removeChart(JFreeChart chart, ChartPanel panel) {
        if (panel == null || chart == null) return;
        if (tracked.remove(panel) == null) return;

        coordinator.unregister(panel);
        interactionManager.removeChart(chart);
        interactionManager.removeDoubleClickCallback(panel);
    }

    /**
     * Convenience overload — looks up the JFreeChart from the tracked map.
     */
    public void removeChart(ChartPanel panel) {
        if (panel == null) return;
        JFreeChart chart = tracked.get(panel);
        if (chart != null) {
            removeChart(chart, panel);
        }
    }

    /**
     * Set a double-click callback for a specific chart panel.
     */
    public void setDoubleClickCallback(ChartPanel panel, Runnable callback) {
        interactionManager.setDoubleClickCallback(panel, callback);
    }

    public boolean isRegistered(ChartPanel panel) {
        return tracked.containsKey(panel);
    }

    /**
     * Remove all tracked charts.
     */
    public void removeAll() {
        for (var entry : new IdentityHashMap<>(tracked).entrySet()) {
            removeChart(entry.getValue(), entry.getKey());
        }
    }

    /**
     * Update time axis label visibility based on visible panel order.
     * Shows labels on the first (top) and last (bottom) panels, hides on middle panels.
     *
     * <p>Call this from the parent container after layout changes (add/remove/toggle charts).
     * Not called automatically inside {@code addChart()}/{@code removeChart()} because the
     * parent knows the display order.</p>
     *
     * @param visiblePanels ordered list of currently visible chart panels (top to bottom)
     */
    public void updateTimeAxisVisibility(List<ChartPanel> visiblePanels) {
        if (visiblePanels == null || visiblePanels.isEmpty()) return;

        ChartPanel first = visiblePanels.get(0);
        ChartPanel last = visiblePanels.get(visiblePanels.size() - 1);

        for (ChartPanel panel : visiblePanels) {
            JFreeChart chart = panel.getChart();
            if (chart == null) continue;
            XYPlot plot = chart.getXYPlot();
            if (!(plot.getDomainAxis() instanceof DateAxis axis)) continue;

            boolean isFirst = (panel == first);
            boolean isLast = (panel == last);
            boolean showLabels = isFirst || isLast;
            axis.setTickLabelsVisible(showLabels);
            axis.setTickMarksVisible(showLabels);
            // First chart (top) has time labels at top, others at bottom
            plot.setDomainAxisLocation(isFirst
                    ? AxisLocation.TOP_OR_RIGHT
                    : AxisLocation.BOTTOM_OR_LEFT);
        }
    }

    public ChartCoordinator getCoordinator() {
        return coordinator;
    }

    public ChartInteractionManager getInteractionManager() {
        return interactionManager;
    }
}
