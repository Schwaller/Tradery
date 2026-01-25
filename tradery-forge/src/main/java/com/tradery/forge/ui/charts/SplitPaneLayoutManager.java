package com.tradery.forge.ui.charts;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

/**
 * Manages resizable split pane layout for chart panels.
 * Creates nested vertical JSplitPanes for visible charts with persistent divider positions.
 */
public class SplitPaneLayoutManager {

    private static final int DIVIDER_SIZE = 8;

    // Root split pane (null when no split panes needed)
    private JSplitPane rootSplitPane;

    // Track all split panes and their chart keys for position persistence
    private final List<SplitPaneEntry> splitPanes = new ArrayList<>();

    // Chart identifier for each panel (for persistence key generation)
    private final Map<JPanel, String> panelIds = new IdentityHashMap<>();

    // Flag to suppress saves during layout rebuilding
    private boolean isRebuilding = false;

    // Listener for divider changes to save positions
    private final PropertyChangeListener dividerListener = evt -> {
        if ("dividerLocation".equals(evt.getPropertyName()) && !isRebuilding) {
            saveDividerPositions();
        }
    };

    /**
     * Register a panel with a chart identifier for persistence.
     */
    public void registerPanel(JPanel panel, String chartId) {
        panelIds.put(panel, chartId);
    }

    /**
     * Build nested split pane layout from visible chart panels.
     * Returns the root component to add to the container.
     *
     * @param visibleCharts List of chart wrapper panels in display order
     * @return Root component (single panel or nested split pane structure)
     */
    public Component buildLayout(List<JPanel> visibleCharts) {
        // Suppress saves during rebuild to avoid saving intermediate positions
        isRebuilding = true;

        // Clean up old split panes
        cleanup();

        if (visibleCharts == null || visibleCharts.isEmpty()) {
            isRebuilding = false;
            return new JPanel();
        }

        if (visibleCharts.size() == 1) {
            // Single chart - no split pane needed
            isRebuilding = false;
            return visibleCharts.get(0);
        }

        // Build nested split panes from bottom to top
        // Structure: price -|- (volume -|- (rsi -|- (macd -|- ...)))
        Component bottomComponent = visibleCharts.get(visibleCharts.size() - 1);

        for (int i = visibleCharts.size() - 2; i >= 0; i--) {
            JPanel topPanel = visibleCharts.get(i);
            String topId = panelIds.getOrDefault(topPanel, "chart_" + i);
            String bottomId = getBottomComponentId(bottomComponent, i + 1);
            // Use both position-based key and chart-based key for flexibility
            String splitKey = topId + "_" + bottomId;

            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true);
            splitPane.setDividerSize(DIVIDER_SIZE);
            splitPane.setBorder(null);
            splitPane.setTopComponent(topPanel);
            splitPane.setBottomComponent((Component) bottomComponent);

            // Set resize weight - top component gets proportional share
            double weight = 1.0 / (visibleCharts.size() - i);
            splitPane.setResizeWeight(weight);

            // Track for position persistence - also store top chart ID for matching
            SplitPaneEntry entry = new SplitPaneEntry(splitPane, splitKey, i, topId);
            splitPanes.add(entry);

            // Listen for divider changes
            splitPane.addPropertyChangeListener("dividerLocation", dividerListener);

            bottomComponent = splitPane;
        }

        rootSplitPane = (JSplitPane) bottomComponent;

        // Restore saved positions after layout is ready, then re-enable saves
        SwingUtilities.invokeLater(() -> {
            restoreDividerPositions();
            isRebuilding = false;
        });

        return rootSplitPane;
    }

    private String getBottomComponentId(Component component, int index) {
        if (component instanceof JPanel panel) {
            return panelIds.getOrDefault(panel, "chart_" + index);
        } else if (component instanceof JSplitPane) {
            return "split_" + index;
        }
        return "component_" + index;
    }

    /**
     * Save current divider positions to ChartConfig.
     */
    public void saveDividerPositions() {
        if (splitPanes.isEmpty()) return;

        ChartConfig config = ChartConfig.getInstance();
        Map<String, Double> positions = new LinkedHashMap<>();

        for (SplitPaneEntry entry : splitPanes) {
            JSplitPane sp = entry.splitPane;
            int height = sp.getHeight();
            if (height > 0) {
                double proportion = (double) sp.getDividerLocation() / height;
                // Clamp to valid range
                proportion = Math.max(0.05, Math.min(0.95, proportion));
                positions.put(entry.key, proportion);
            }
        }

        // Only save if we have valid positions
        if (!positions.isEmpty()) {
            config.setChartDividerPositions(positions);
        }
    }

    /**
     * Restore saved divider positions from ChartConfig.
     * Uses flexible matching: exact key first, then by top chart ID prefix.
     */
    public void restoreDividerPositions() {
        if (splitPanes.isEmpty()) return;

        Map<String, Double> positions = ChartConfig.getInstance().getChartDividerPositions();
        if (positions.isEmpty()) return;

        for (SplitPaneEntry entry : splitPanes) {
            Double proportion = positions.get(entry.key);

            // If exact key not found, try to find a saved position with matching top chart ID
            if (proportion == null && entry.topChartId != null) {
                for (Map.Entry<String, Double> saved : positions.entrySet()) {
                    if (saved.getKey().startsWith(entry.topChartId + "_")) {
                        proportion = saved.getValue();
                        break;
                    }
                }
            }

            if (proportion != null) {
                int height = entry.splitPane.getHeight();
                if (height > 0) {
                    int location = (int) (height * proportion);
                    entry.splitPane.setDividerLocation(location);
                }
            }
        }
    }

    /**
     * Reset all dividers to equal distribution.
     */
    public void resetToDefaults() {
        if (splitPanes.isEmpty()) return;

        // Set each split pane to give equal space to all charts
        for (SplitPaneEntry entry : splitPanes) {
            JSplitPane sp = entry.splitPane;
            int totalCharts = splitPanes.size() + 1;
            int chartsInTop = entry.index + 1;
            double proportion = (double) chartsInTop / totalCharts;
            sp.setDividerLocation(proportion);
        }

        // Clear saved positions
        ChartConfig.getInstance().clearChartDividerPositions();
    }

    /**
     * Clean up split panes and listeners.
     */
    public void cleanup() {
        for (SplitPaneEntry entry : splitPanes) {
            entry.splitPane.removePropertyChangeListener("dividerLocation", dividerListener);
        }
        splitPanes.clear();
        rootSplitPane = null;
    }

    /**
     * Get the root split pane (null if layout hasn't been built or single chart).
     */
    public JSplitPane getRootSplitPane() {
        return rootSplitPane;
    }

    /**
     * Entry tracking a split pane for persistence.
     */
    private static class SplitPaneEntry {
        final JSplitPane splitPane;
        final String key;
        final int index;
        final String topChartId;

        SplitPaneEntry(JSplitPane splitPane, String key, int index, String topChartId) {
            this.splitPane = splitPane;
            this.key = key;
            this.index = index;
            this.topChartId = topChartId;
        }
    }
}
