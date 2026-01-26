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
 * Renderer for Cumulative Volume Delta (CVD) indicator.
 * Shows the running sum of delta (buy volume - sell volume) over time.
 * Uses IndicatorEngine.getCumDelta() for data.
 */
public class CvdRenderer implements IndicatorChartRenderer {

    private static final Color CVD_POSITIVE_COLOR = new Color(76, 175, 80);   // Green
    private static final Color CVD_NEGATIVE_COLOR = new Color(244, 67, 54);   // Red
    private static final Color CVD_LINE_COLOR = new Color(33, 150, 243);      // Blue

    private final boolean showAsLine;

    public CvdRenderer() {
        this(true);  // Default to line chart
    }

    public CvdRenderer(boolean showAsLine) {
        this.showAsLine = showAsLine;
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        List<Candle> candles = provider.getCandles();

        // Get CVD from IndicatorEngine
        double[] cvd = provider.getIndicatorEngine().getCumulativeDelta();
        if (cvd == null || cvd.length == 0) return;

        // Build time series
        TimeSeriesCollection dataset = TimeSeriesBuilder.build("CVD", candles, cvd, 0);

        // Add to plot
        plot.setDataset(0, dataset);

        if (showAsLine) {
            plot.setRenderer(0, RendererBuilder.lineRenderer(CVD_LINE_COLOR, ChartStyles.MEDIUM_STROKE));
        } else {
            // Could use area renderer for filled CVD
            plot.setRenderer(0, RendererBuilder.lineRenderer(CVD_LINE_COLOR, ChartStyles.MEDIUM_STROKE));
        }
    }

    @Override
    public String getParameterString() {
        return "";
    }
}
