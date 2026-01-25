package com.tradery.forge.ui.charts.sourceable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tradery.forge.TraderyApp;
import com.tradery.core.model.DataSourceSelection;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages multiple sourceable chart instances.
 * Handles persistence, ordering, and lifecycle of charts.
 */
public class SourceableChartsManager {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final List<SourceableChart> charts = new CopyOnWriteArrayList<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private File configFile;
    private ChartDataContext currentContext;

    public SourceableChartsManager() {
        load();
    }

    // ===== Chart Management =====

    /**
     * Add a new chart instance.
     */
    public void addChart(SourceableChart chart) {
        charts.add(chart);
        if (currentContext != null) {
            chart.updateData(currentContext);
        }
        save();
        notifyListeners();
    }

    /**
     * Create and add a new chart with default settings.
     */
    public SourceableChartInstance createChart(SourceableChartType type, String name, DataSourceSelection sources) {
        SourceableChartInstance chart = new SourceableChartInstance(type, name, sources);
        addChart(chart);
        return chart;
    }

    /**
     * Remove a chart by ID.
     */
    public void removeChart(String chartId) {
        charts.removeIf(c -> c.getId().equals(chartId));
        save();
        notifyListeners();
    }

    /**
     * Remove a chart instance.
     */
    public void removeChart(SourceableChart chart) {
        charts.remove(chart);
        save();
        notifyListeners();
    }

    /**
     * Get a chart by ID.
     */
    public SourceableChart getChart(String chartId) {
        return charts.stream()
            .filter(c -> c.getId().equals(chartId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get all charts.
     */
    public List<SourceableChart> getCharts() {
        return new ArrayList<>(charts);
    }

    /**
     * Get visible charts only.
     */
    public List<SourceableChart> getVisibleCharts() {
        return charts.stream()
            .filter(SourceableChart::isVisible)
            .toList();
    }

    /**
     * Get charts by type.
     */
    public List<SourceableChart> getChartsByType(SourceableChartType type) {
        return charts.stream()
            .filter(c -> c.getChartType() == type)
            .toList();
    }

    /**
     * Reorder charts.
     */
    public void reorderCharts(List<String> chartIds) {
        Map<String, SourceableChart> byId = new HashMap<>();
        for (SourceableChart chart : charts) {
            byId.put(chart.getId(), chart);
        }

        List<SourceableChart> reordered = new ArrayList<>();
        for (String id : chartIds) {
            SourceableChart chart = byId.remove(id);
            if (chart != null) {
                reordered.add(chart);
            }
        }

        // Add any remaining charts that weren't in the order list
        reordered.addAll(byId.values());

        charts.clear();
        charts.addAll(reordered);
        save();
        notifyListeners();
    }

    /**
     * Move a chart up in the order.
     */
    public void moveChartUp(String chartId) {
        int index = -1;
        for (int i = 0; i < charts.size(); i++) {
            if (charts.get(i).getId().equals(chartId)) {
                index = i;
                break;
            }
        }

        if (index > 0) {
            SourceableChart chart = charts.remove(index);
            charts.add(index - 1, chart);
            save();
            notifyListeners();
        }
    }

    /**
     * Move a chart down in the order.
     */
    public void moveChartDown(String chartId) {
        int index = -1;
        for (int i = 0; i < charts.size(); i++) {
            if (charts.get(i).getId().equals(chartId)) {
                index = i;
                break;
            }
        }

        if (index >= 0 && index < charts.size() - 1) {
            SourceableChart chart = charts.remove(index);
            charts.add(index + 1, chart);
            save();
            notifyListeners();
        }
    }

    /**
     * Toggle chart visibility.
     */
    public void toggleVisibility(String chartId) {
        SourceableChart chart = getChart(chartId);
        if (chart != null) {
            chart.setVisible(!chart.isVisible());
            save();
            notifyListeners();
        }
    }

    /**
     * Set chart visibility.
     */
    public void setVisibility(String chartId, boolean visible) {
        SourceableChart chart = getChart(chartId);
        if (chart != null) {
            chart.setVisible(visible);
            save();
            notifyListeners();
        }
    }

    // ===== Data Updates =====

    /**
     * Update the data context for all charts.
     */
    public void setDataContext(ChartDataContext context) {
        this.currentContext = context;
        updateAllCharts();
    }

    /**
     * Update all charts with current context.
     */
    public void updateAllCharts() {
        if (currentContext == null) return;

        for (SourceableChart chart : charts) {
            if (chart.isVisible()) {
                chart.updateData(currentContext);
            }
        }
    }

    /**
     * Update a specific chart.
     */
    public void updateChart(String chartId) {
        if (currentContext == null) return;

        SourceableChart chart = getChart(chartId);
        if (chart != null && chart.isVisible()) {
            chart.updateData(currentContext);
        }
    }

    // ===== Listeners =====

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    // ===== Persistence =====

    private File getConfigFile() {
        if (configFile == null) {
            configFile = new File(TraderyApp.USER_DIR, "sourceable-charts.json");
        }
        return configFile;
    }

    private void load() {
        try {
            File file = getConfigFile();
            if (file.exists()) {
                SourceableChartsConfig config = MAPPER.readValue(file, SourceableChartsConfig.class);
                if (config.charts != null) {
                    charts.clear();
                    charts.addAll(config.charts);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load sourceable charts config: " + e.getMessage());
        }
    }

    private void save() {
        try {
            File file = getConfigFile();
            file.getParentFile().mkdirs();

            SourceableChartsConfig config = new SourceableChartsConfig();
            config.charts = new ArrayList<>(charts);
            MAPPER.writeValue(file, config);
        } catch (IOException e) {
            System.err.println("Failed to save sourceable charts config: " + e.getMessage());
        }
    }

    /**
     * Clear all charts and reset to defaults.
     */
    public void clearAll() {
        charts.clear();
        save();
        notifyListeners();
    }

    /**
     * Get the count of charts.
     */
    public int getChartCount() {
        return charts.size();
    }

    /**
     * Check if any charts exist.
     */
    public boolean hasCharts() {
        return !charts.isEmpty();
    }

    // ===== Config wrapper for Jackson =====

    private static class SourceableChartsConfig {
        public List<SourceableChart> charts;
    }
}
