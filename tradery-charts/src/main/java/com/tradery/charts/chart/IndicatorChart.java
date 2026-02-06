package com.tradery.charts.chart;

import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.renderer.IndicatorChartRenderer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;

/**
 * Generic indicator chart that uses pluggable renderers.
 *
 * <p>Instead of one class per indicator, this chart delegates
 * rendering to {@link IndicatorChartRenderer} implementations
 * created via a {@link RendererFactory}.</p>
 *
 * <p>The renderer is created lazily on the first {@link #updateData} call,
 * which is when both the plot and provider are available. Once created,
 * the renderer's subscription handles all subsequent data updates via
 * the pool's {@code onReady} callback.</p>
 */
public class IndicatorChart extends SyncedChart {

    /**
     * Factory for creating renderers. Called once when both plot and provider are available.
     */
    @FunctionalInterface
    public interface RendererFactory {
        IndicatorChartRenderer create(XYPlot plot, ChartDataProvider provider);
    }

    private final IndicatorType indicatorType;
    private final RendererFactory rendererFactory;
    private IndicatorChartRenderer renderer;

    public IndicatorChart(ChartCoordinator coordinator, IndicatorType type, RendererFactory factory) {
        super(coordinator, type.getTitle());
        this.indicatorType = type;
        this.rendererFactory = factory;
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
        if (renderer != null) return;  // Already bound - pool handles updates

        renderer = rendererFactory.create(getPlot(), provider);
    }

    @Override
    public void dispose() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        super.dispose();
    }

    /**
     * Get the indicator type.
     */
    public IndicatorType getIndicatorType() {
        return indicatorType;
    }

    /**
     * Get the renderer (may be null before first updateData).
     */
    public IndicatorChartRenderer getRenderer() {
        return renderer;
    }
}
