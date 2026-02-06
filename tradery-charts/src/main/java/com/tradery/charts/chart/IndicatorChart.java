package com.tradery.charts.chart;

import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.renderer.IndicatorChartRenderer;
import com.tradery.charts.util.ChartStyles;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;

/**
 * Generic indicator chart that uses pluggable renderers.
 *
 * <p>Instead of one class per indicator, this chart delegates
 * rendering to {@link IndicatorChartRenderer} implementations.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * IndicatorChart rsiChart = new IndicatorChart(
 *     coordinator,
 *     IndicatorType.RSI,
 *     new RsiRenderer(14));
 * rsiChart.initialize();
 * rsiChart.updateData(provider);
 * }</pre>
 */
public class IndicatorChart extends SyncedChart {

    private final IndicatorType indicatorType;
    private final IndicatorChartRenderer renderer;

    public IndicatorChart(ChartCoordinator coordinator, IndicatorType type, IndicatorChartRenderer renderer) {
        super(coordinator, type.getTitle());
        this.indicatorType = type;
        this.renderer = renderer;
    }

    @Override
    protected JFreeChart createChart() {
        DateAxis domainAxis = new DateAxis("");
        NumberAxis rangeAxis = new NumberAxis("");

        // Apply fixed Y-axis range if defined
        double[] range = indicatorType.getYAxisRange();
        if (range != null && range.length == 2) {
            rangeAxis.setRange(range[0], range[1]);
        } else {
            rangeAxis.setAutoRangeIncludesZero(false);
        }

        XYPlot plot = new XYPlot(null, domainAxis, rangeAxis, null);

        return new JFreeChart(null, null, plot, false);
    }

    @Override
    public void updateData(ChartDataProvider provider) {
        if (!provider.hasCandles()) return;

        XYPlot plot = getPlot();

        // Clear old data
        for (int i = 0; i < plot.getDatasetCount(); i++) {
            plot.setDataset(i, null);
        }
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, title);

        // Delegate to renderer
        renderer.render(plot, provider);
    }

    @Override
    public void dispose() {
        renderer.close();
        super.dispose();
    }

    /**
     * Get the indicator type.
     */
    public IndicatorType getIndicatorType() {
        return indicatorType;
    }

    /**
     * Get the renderer.
     */
    public IndicatorChartRenderer getRenderer() {
        return renderer;
    }
}
