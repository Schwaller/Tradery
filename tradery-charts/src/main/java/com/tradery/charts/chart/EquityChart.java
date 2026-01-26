package com.tradery.charts.chart;

import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.Date;

/**
 * Equity curve chart for portfolio/strategy performance visualization.
 * Shows equity line with optional drawdown highlighting.
 */
public class EquityChart extends SyncedChart {

    private static final Color EQUITY_COLOR = new Color(33, 150, 243);        // Blue
    private static final Color DRAWDOWN_COLOR = new Color(244, 67, 54, 80);   // Transparent red
    private static final Color HIGH_WATER_COLOR = new Color(76, 175, 80, 100); // Transparent green

    private boolean showDrawdown = true;
    private boolean showHighWaterMark = false;

    // Equity data
    private long[] timestamps;
    private double[] equityValues;

    public EquityChart(ChartCoordinator coordinator) {
        this(coordinator, "Equity");
    }

    public EquityChart(ChartCoordinator coordinator, String title) {
        super(coordinator, title);
    }

    @Override
    protected JFreeChart createChart() {
        DateAxis domainAxis = new DateAxis("");
        NumberAxis rangeAxis = new NumberAxis("");
        rangeAxis.setAutoRangeIncludesZero(false);

        XYPlot plot = new XYPlot(null, domainAxis, rangeAxis, null);

        return new JFreeChart(null, null, plot, false);
    }

    @Override
    public void updateData(ChartDataProvider provider) {
        // EquityChart uses setEquityData() instead of ChartDataProvider
        // This method is a no-op for EquityChart
    }

    /**
     * Set the equity data for the chart.
     *
     * @param timestamps Timestamps in epoch milliseconds
     * @param equityValues Equity values at each timestamp
     */
    public void setEquityData(long[] timestamps, double[] equityValues) {
        this.timestamps = timestamps;
        this.equityValues = equityValues;
        refreshChart();
    }

    /**
     * Refresh the chart with current data.
     */
    private void refreshChart() {
        if (timestamps == null || equityValues == null || timestamps.length == 0) {
            return;
        }

        XYPlot plot = getPlot();
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, title);

        int datasetIndex = 0;

        // Calculate high water mark and drawdown if needed
        double[] highWaterMark = new double[timestamps.length];
        double[] drawdown = new double[timestamps.length];
        double peak = equityValues[0];

        for (int i = 0; i < timestamps.length; i++) {
            if (equityValues[i] > peak) {
                peak = equityValues[i];
            }
            highWaterMark[i] = peak;
            drawdown[i] = equityValues[i] - peak;  // Negative when in drawdown
        }

        // Drawdown area (behind equity line)
        if (showDrawdown) {
            TimeSeries drawdownSeries = new TimeSeries("Drawdown");
            for (int i = 0; i < timestamps.length; i++) {
                if (!Double.isNaN(drawdown[i]) && drawdown[i] < 0) {
                    drawdownSeries.addOrUpdate(new Millisecond(new Date(timestamps[i])), equityValues[i]);
                }
            }

            TimeSeriesCollection drawdownDataset = new TimeSeriesCollection();
            drawdownDataset.addSeries(drawdownSeries);

            XYAreaRenderer areaRenderer = new XYAreaRenderer();
            areaRenderer.setSeriesPaint(0, DRAWDOWN_COLOR);
            areaRenderer.setOutline(false);

            plot.setDataset(datasetIndex, drawdownDataset);
            plot.setRenderer(datasetIndex, areaRenderer);
            datasetIndex++;
        }

        // High water mark line
        if (showHighWaterMark) {
            TimeSeries hwmSeries = new TimeSeries("High Water Mark");
            for (int i = 0; i < timestamps.length; i++) {
                if (!Double.isNaN(highWaterMark[i])) {
                    hwmSeries.addOrUpdate(new Millisecond(new Date(timestamps[i])), highWaterMark[i]);
                }
            }

            TimeSeriesCollection hwmDataset = new TimeSeriesCollection();
            hwmDataset.addSeries(hwmSeries);

            XYLineAndShapeRenderer hwmRenderer = new XYLineAndShapeRenderer(true, false);
            hwmRenderer.setSeriesPaint(0, HIGH_WATER_COLOR);
            hwmRenderer.setSeriesStroke(0, ChartStyles.DASHED_STROKE);

            plot.setDataset(datasetIndex, hwmDataset);
            plot.setRenderer(datasetIndex, hwmRenderer);
            datasetIndex++;
        }

        // Equity line (on top)
        TimeSeries equitySeries = new TimeSeries("Equity");
        for (int i = 0; i < timestamps.length; i++) {
            if (!Double.isNaN(equityValues[i])) {
                equitySeries.addOrUpdate(new Millisecond(new Date(timestamps[i])), equityValues[i]);
            }
        }

        TimeSeriesCollection equityDataset = new TimeSeriesCollection();
        equityDataset.addSeries(equitySeries);

        XYLineAndShapeRenderer equityRenderer = new XYLineAndShapeRenderer(true, false);
        equityRenderer.setSeriesPaint(0, EQUITY_COLOR);
        equityRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);

        plot.setDataset(datasetIndex, equityDataset);
        plot.setRenderer(datasetIndex, equityRenderer);
    }

    // ===== Configuration =====

    /**
     * Set whether to show drawdown highlighting.
     */
    public void setShowDrawdown(boolean showDrawdown) {
        this.showDrawdown = showDrawdown;
        refreshChart();
    }

    /**
     * Set whether to show high water mark line.
     */
    public void setShowHighWaterMark(boolean showHighWaterMark) {
        this.showHighWaterMark = showHighWaterMark;
        refreshChart();
    }

    public boolean isShowDrawdown() {
        return showDrawdown;
    }

    public boolean isShowHighWaterMark() {
        return showHighWaterMark;
    }
}
