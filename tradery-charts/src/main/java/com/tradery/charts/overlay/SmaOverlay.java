package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.List;

/**
 * Simple Moving Average overlay.
 * Uses IndicatorEngine.getSMA() for calculation.
 */
public class SmaOverlay implements ChartOverlay {

    private final int period;
    private final Color color;

    /**
     * Create an SMA overlay with default color.
     */
    public SmaOverlay(int period) {
        this(period, ChartStyles.SMA_COLOR);
    }

    /**
     * Create an SMA overlay with custom color.
     */
    public SmaOverlay(int period, Color color) {
        this.period = period;
        this.color = color;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        List<Candle> candles = provider.getCandles();

        // Get SMA from IndicatorEngine - NOT inline calculation
        double[] sma = provider.getIndicatorEngine().getSMA(period);
        if (sma == null || sma.length == 0) return;

        // Build time series
        TimeSeriesCollection dataset = TimeSeriesBuilder.build(
            getDisplayName(), candles, sma, period - 1);

        // Add to plot
        plot.setDataset(datasetIndex, dataset);
        plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(color));
    }

    @Override
    public String getDisplayName() {
        return "SMA(" + period + ")";
    }

    public int getPeriod() {
        return period;
    }
}
