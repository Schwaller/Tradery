package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import org.jfree.chart.plot.XYPlot;

/**
 * Interface for chart overlays that render on top of the price chart.
 *
 * <p>Overlays subscribe to {@link com.tradery.charts.indicator.IndicatorCompute}
 * instances via {@code provider.getIndicatorPool()} for async, off-EDT computation.</p>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Subscribe to compute classes via {@code provider.getIndicatorPool().subscribe(...)}</li>
 *   <li>Render data in the {@code onReady} callback</li>
 *   <li>Handle null/empty data gracefully</li>
 *   <li>Clear old annotations before applying new ones</li>
 * </ul>
 */
public interface ChartOverlay {

    /**
     * Apply this overlay to the plot.
     *
     * @param plot         The XYPlot to add the overlay to
     * @param provider     Data provider for candles and indicator pool
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
