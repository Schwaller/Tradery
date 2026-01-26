package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import java.util.List;

/**
 * Renderer for ATR (Average True Range) indicator.
 * Uses IndicatorEngine.getATR() for calculation.
 */
public class AtrRenderer implements IndicatorChartRenderer {

    private final int period;

    public AtrRenderer(int period) {
        this.period = period;
    }

    /**
     * Create an ATR renderer with default period (14).
     */
    public AtrRenderer() {
        this(14);
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        List<Candle> candles = provider.getCandles();

        // Get ATR from IndicatorEngine - NOT inline calculation
        double[] atr = provider.getIndicatorEngine().getATR(period);
        if (atr == null || atr.length == 0) return;

        // Build time series
        TimeSeriesCollection dataset = TimeSeriesBuilder.build(
            "ATR(" + period + ")", candles, atr, period - 1);

        // Add to plot
        plot.setDataset(0, dataset);
        plot.setRenderer(0, RendererBuilder.lineRenderer(ChartStyles.ATR_COLOR));
    }

    @Override
    public String getParameterString() {
        return String.valueOf(period);
    }

    public int getPeriod() {
        return period;
    }
}
