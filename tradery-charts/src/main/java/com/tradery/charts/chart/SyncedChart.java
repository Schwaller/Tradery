package com.tradery.charts.chart;

import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartPanelFactory;
import com.tradery.charts.util.ChartStyles;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;

/**
 * Base class for synchronized charts.
 * Auto-registers with the ChartCoordinator on construction.
 *
 * <p>Subclasses implement {@link #createChart()} to build the JFreeChart,
 * and {@link #updateData(ChartDataProvider)} to refresh data.</p>
 */
public abstract class SyncedChart {

    protected final ChartCoordinator coordinator;
    protected JFreeChart chart;
    protected ChartPanel chartPanel;
    protected String title;

    /**
     * Create a synced chart that auto-registers with the coordinator.
     */
    protected SyncedChart(ChartCoordinator coordinator, String title) {
        this.coordinator = coordinator;
        this.title = title;
    }

    /**
     * Initialize the chart. Call this after construction.
     * Creates the chart and registers with the coordinator.
     */
    public void initialize() {
        this.chart = createChart();
        ChartStyles.stylizeChart(chart, title);

        this.chartPanel = ChartPanelFactory.create(chart);

        if (coordinator != null) {
            coordinator.register(chartPanel);
        }
    }

    /**
     * Create the JFreeChart. Subclasses implement this.
     */
    protected abstract JFreeChart createChart();

    /**
     * Update the chart with new data.
     */
    public abstract void updateData(ChartDataProvider provider);

    /**
     * Get the chart panel for adding to a container.
     */
    public ChartPanel getChartPanel() {
        return chartPanel;
    }

    /**
     * Get the JFreeChart.
     */
    public JFreeChart getChart() {
        return chart;
    }

    /**
     * Get the XYPlot.
     */
    public XYPlot getPlot() {
        return chart != null ? chart.getXYPlot() : null;
    }

    /**
     * Get the chart title.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set the full-screen toggle callback.
     */
    public void setFullScreenCallback(Runnable callback) {
        if (chartPanel != null) {
            ChartPanelFactory.setFullScreenCallback(chartPanel, callback);
        }
    }

    /**
     * Unregister from the coordinator and clean up.
     */
    public void dispose() {
        if (coordinator != null && chartPanel != null) {
            coordinator.unregister(chartPanel);
        }
    }
}
