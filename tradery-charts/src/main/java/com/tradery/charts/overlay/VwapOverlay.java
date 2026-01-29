package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.VwapCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.List;

/**
 * Volume Weighted Average Price (VWAP) overlay.
 * Subscribes to VwapCompute for async background computation.
 */
public class VwapOverlay implements ChartOverlay {

    private final Color color;
    private IndicatorSubscription<double[]> subscription;

    /**
     * Create a VWAP overlay with default color.
     */
    public VwapOverlay() {
        this(ChartStyles.VWAP_COLOR);
    }

    /**
     * Create a VWAP overlay with custom color.
     */
    public VwapOverlay(Color color) {
        this.color = color;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new VwapCompute());
        subscription.onReady(vwap -> {
            if (vwap == null || vwap.length == 0) return;
            List<Candle> candles = provider.getCandles();
            if (candles == null || candles.isEmpty()) return;

            // Build time series (VWAP is valid from bar 0)
            TimeSeriesCollection dataset = TimeSeriesBuilder.build(
                getDisplayName(), candles, vwap, 0);

            // Add to plot with dashed stroke for distinction
            plot.setDataset(datasetIndex, dataset);
            plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(color, ChartStyles.DASHED_STROKE));
            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public String getDisplayName() {
        return "VWAP";
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }
}
