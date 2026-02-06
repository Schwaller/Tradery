package com.tradery.charts.chart;

import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartPanelFactory;
import com.tradery.charts.util.ChartStyles;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
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
     * Set the range axis position: "left", "right", or "both".
     */
    public void setRangeAxisPosition(String position) {
        if (chart == null) return;
        XYPlot plot = chart.getXYPlot();
        if ("right".equals(position)) {
            plot.setRangeAxisLocation(AxisLocation.TOP_OR_RIGHT);
            // Remove secondary axis if it was in "both" mode
            if (plot.getRangeAxis(1) != null) {
                plot.setRangeAxis(1, null);
            }
        } else if ("both".equals(position)) {
            plot.setRangeAxisLocation(AxisLocation.TOP_OR_LEFT);
            NumberAxis leftAxis = (NumberAxis) plot.getRangeAxis();
            NumberAxis rightAxis = new NumberAxis();
            ChartStyles.styleNumberAxis(rightAxis, ChartStyles.getTheme());
            rightAxis.setAutoRange(false);
            rightAxis.setRange(leftAxis.getRange());
            plot.setRangeAxis(1, rightAxis);
            plot.setRangeAxisLocation(1, AxisLocation.TOP_OR_RIGHT);
            plot.addChangeListener(event -> {
                org.jfree.data.Range leftRange = leftAxis.getRange();
                org.jfree.data.Range rightRange = rightAxis.getRange();
                if (!leftRange.equals(rightRange)) {
                    rightAxis.setRange(leftRange);
                }
            });
        } else {
            plot.setRangeAxisLocation(AxisLocation.TOP_OR_LEFT);
            if (plot.getRangeAxis(1) != null) {
                plot.setRangeAxis(1, null);
            }
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
