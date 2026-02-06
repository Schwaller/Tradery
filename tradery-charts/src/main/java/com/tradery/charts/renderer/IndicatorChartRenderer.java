package com.tradery.charts.renderer;

/**
 * Interface for indicator chart renderers.
 *
 * <p>Renderers subscribe to indicator computes in their constructor and hold
 * a final subscription. The {@code onReady} callback handles both initial
 * rendering and recomputation when data changes.</p>
 */
public interface IndicatorChartRenderer extends AutoCloseable {

    /**
     * Get the indicator parameters as a display string.
     * Used for chart titles and legends.
     */
    String getParameterString();

    /**
     * Release resources held by this renderer (e.g. indicator subscriptions).
     */
    @Override
    void close();
}
