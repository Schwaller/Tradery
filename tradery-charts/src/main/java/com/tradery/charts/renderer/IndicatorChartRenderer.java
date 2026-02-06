package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import org.jfree.chart.plot.XYPlot;

/**
 * Interface for indicator chart renderers.
 *
 * <p>Renderers subscribe to {@link com.tradery.charts.indicator.IndicatorCompute}
 * instances via {@code provider.getIndicatorPool()} for async, off-EDT computation.</p>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Subscribe to compute classes via {@code provider.getIndicatorPool().subscribe(...)}</li>
 *   <li>Use {@link com.tradery.charts.util.TimeSeriesBuilder} for creating datasets</li>
 *   <li>Use {@link com.tradery.charts.util.RendererBuilder} for creating renderers</li>
 *   <li>Add reference lines using {@link com.tradery.charts.util.ChartAnnotationHelper}</li>
 * </ul>
 */
public interface IndicatorChartRenderer extends AutoCloseable {

    /**
     * Render the indicator on the plot.
     *
     * @param plot     The XYPlot to render on
     * @param provider Data provider for candles and indicator pool
     */
    void render(XYPlot plot, ChartDataProvider provider);

    /**
     * Get the indicator parameters as a display string.
     * Used for chart titles and legends.
     */
    String getParameterString();

    /**
     * Release resources held by this renderer (e.g. indicator subscriptions).
     * Default no-op for renderers that don't hold resources.
     */
    @Override
    default void close() {}
}
