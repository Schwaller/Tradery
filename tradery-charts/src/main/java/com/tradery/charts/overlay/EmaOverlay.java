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
 * Exponential Moving Average overlay.
 * Uses IndicatorEngine.getEMA() for calculation.
 */
public class EmaOverlay implements ChartOverlay {

    private final int period;
    private final Color color;

    /**
     * Create an EMA overlay with default color.
     */
    public EmaOverlay(int period) {
        this(period, ChartStyles.EMA_COLOR);
    }

    /**
     * Create an EMA overlay with custom color.
     */
    public EmaOverlay(int period, Color color) {
        this.period = period;
        this.color = color;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        List<Candle> candles = provider.getCandles();

        // Get EMA from IndicatorEngine - NOT inline calculation
        double[] ema = provider.getIndicatorEngine().getEMA(period);
        if (ema == null || ema.length == 0) return;

        // Build time series
        TimeSeriesCollection dataset = TimeSeriesBuilder.build(
            getDisplayName(), candles, ema, period - 1);

        // Add to plot
        plot.setDataset(datasetIndex, dataset);
        plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(color));
    }

    @Override
    public String getDisplayName() {
        return "EMA(" + period + ")";
    }

    public int getPeriod() {
        return period;
    }
}
