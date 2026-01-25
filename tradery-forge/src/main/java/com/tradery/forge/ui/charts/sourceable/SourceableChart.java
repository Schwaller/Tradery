package com.tradery.forge.ui.charts.sourceable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.tradery.core.model.DataSourceSelection;
import org.jfree.chart.JFreeChart;

import java.util.UUID;

/**
 * Base class for charts with configurable data sources.
 * Allows multiple instances of the same chart type with different exchange/market filters.
 *
 * Example use cases:
 * - Volume chart showing Binance PERP data
 * - Delta chart showing combined Binance + Bybit data
 * - Footprint heatmap showing OKX only
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "chartType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SourceableChartInstance.class, name = "instance")
})
public abstract class SourceableChart {

    private String id;
    private String name;
    private DataSourceSelection sources;
    private boolean visible;
    private int height;  // Preferred height in pixels

    protected SourceableChart() {
        this.id = UUID.randomUUID().toString();
        this.visible = true;
        this.height = 100;
    }

    protected SourceableChart(String name, DataSourceSelection sources) {
        this();
        this.name = name;
        this.sources = sources;
    }

    // ===== Abstract Methods =====

    /**
     * Get the chart type for this instance.
     */
    public abstract SourceableChartType getChartType();

    /**
     * Update the chart data from the indicator engine.
     * Called when data changes or chart becomes visible.
     */
    public abstract void updateData(ChartDataContext context);

    /**
     * Get the JFreeChart instance for rendering.
     */
    public abstract JFreeChart getChart();

    /**
     * Get a display label for the chart header.
     * Includes name and source info.
     */
    public String getDisplayLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append(name != null ? name : getChartType().getDisplayName());

        if (sources != null && !sources.isAllSources()) {
            sb.append(" [").append(sources.getShortDescription()).append("]");
        }

        return sb.toString();
    }

    // ===== Getters and Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DataSourceSelection getSources() {
        return sources;
    }

    public void setSources(DataSourceSelection sources) {
        this.sources = sources;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = Math.max(60, height);  // Minimum height
    }
}
