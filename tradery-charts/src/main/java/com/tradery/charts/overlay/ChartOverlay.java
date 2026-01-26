package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import org.jfree.chart.plot.XYPlot;

/**
 * Interface for chart overlays that render on top of the price chart.
 *
 * <p>Overlays use {@link com.tradery.core.indicators.IndicatorEngine} for
 * calculations instead of inline math, ensuring consistency with DSL
 * indicator values.</p>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Always get indicator data from {@code provider.getIndicatorEngine()}</li>
 *   <li>Never calculate indicators inline - use the engine</li>
 *   <li>Handle null/empty data gracefully</li>
 *   <li>Clear old annotations before applying new ones</li>
 * </ul>
 *
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class SmaOverlay implements ChartOverlay {
 *     private final int period;
 *
 *     public SmaOverlay(int period) {
 *         this.period = period;
 *     }
 *
 *     @Override
 *     public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
 *         double[] sma = provider.getIndicatorEngine().getSMA(period);
 *         // Create and add series to plot...
 *     }
 * }
 * }</pre>
 */
public interface ChartOverlay {

    /**
     * Apply this overlay to the plot.
     *
     * @param plot         The XYPlot to add the overlay to
     * @param provider     Data provider for candles and indicator engine
     * @param datasetIndex The dataset index to use (incremented for each overlay)
     */
    void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex);

    /**
     * Get a display name for this overlay.
     * Used in legends and tooltips.
     */
    String getDisplayName();

    /**
     * Get the number of datasets this overlay adds.
     * Most overlays add 1 dataset, but some (like Bollinger Bands) add multiple.
     */
    default int getDatasetCount() {
        return 1;
    }
}
