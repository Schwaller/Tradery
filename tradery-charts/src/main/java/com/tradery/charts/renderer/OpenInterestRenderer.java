package com.tradery.charts.renderer;

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
 * Renderer for Open Interest indicator.
 * Uses IndicatorEngine.getOI() for data.
 * Displays open interest as a line chart.
 */
public class OpenInterestRenderer implements IndicatorChartRenderer {

    private static final Color OI_COLOR = new Color(156, 39, 176);  // Purple

    public OpenInterestRenderer() {
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        List<Candle> candles = provider.getCandles();

        // Get OI from IndicatorEngine
        double[] oi = provider.getIndicatorEngine().getOI();
        if (oi == null || oi.length == 0) return;

        // Build time series
        TimeSeriesCollection dataset = TimeSeriesBuilder.build("OI", candles, oi, 0);

        // Add to plot
        plot.setDataset(0, dataset);
        plot.setRenderer(0, RendererBuilder.lineRenderer(OI_COLOR, ChartStyles.MEDIUM_STROKE));
    }

    @Override
    public String getParameterString() {
        return "";
    }
}
