package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import org.jfree.chart.plot.XYPlot;

/**
 * Interface for indicator chart renderers.
 *
 * <p>Renderers are responsible for creating datasets and configuring
 * renderers for specific indicator types. They use
 * {@link com.tradery.core.indicators.IndicatorEngine} for calculations.</p>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Always get indicator data from {@code provider.getIndicatorEngine()}</li>
 *   <li>Use {@link com.tradery.charts.util.TimeSeriesBuilder} for creating datasets</li>
 *   <li>Use {@link com.tradery.charts.util.RendererBuilder} for creating renderers</li>
 *   <li>Add reference lines using {@link com.tradery.charts.util.ChartAnnotationHelper}</li>
 * </ul>
 */
public interface IndicatorChartRenderer {

    /**
     * Render the indicator on the plot.
     *
     * @param plot     The XYPlot to render on
     * @param provider Data provider for candles and indicator engine
     */
    void render(XYPlot plot, ChartDataProvider provider);

    /**
     * Get the indicator parameters as a display string.
     * Used for chart titles and legends.
     */
    String getParameterString();
}
