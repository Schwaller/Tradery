package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.Date;
import java.util.List;

/**
 * Renderer for Funding Rate indicator.
 * Uses IndicatorEngine.getFunding() for data.
 * Displays funding rate as bars (green positive, red negative).
 */
public class FundingRenderer implements IndicatorChartRenderer {

    private static final Color POSITIVE_FUNDING = new Color(76, 175, 80);   // Green - longs pay shorts
    private static final Color NEGATIVE_FUNDING = new Color(244, 67, 54);   // Red - shorts pay longs

    public FundingRenderer() {
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        List<Candle> candles = provider.getCandles();

        // Get Funding from IndicatorEngine
        double[] funding = provider.getIndicatorEngine().getFunding();
        if (funding == null || funding.length == 0) return;

        // Create separate series for positive and negative funding
        TimeSeries positiveSeries = new TimeSeries("Funding+");
        TimeSeries negativeSeries = new TimeSeries("Funding-");

        for (int i = 0; i < candles.size() && i < funding.length; i++) {
            if (Double.isNaN(funding[i])) continue;
            Candle c = candles.get(i);
            Millisecond time = new Millisecond(new Date(c.timestamp()));

            // Funding is typically in percentage, multiply by 100 for display
            double value = funding[i] * 100;

            if (value >= 0) {
                positiveSeries.addOrUpdate(time, value);
                negativeSeries.addOrUpdate(time, 0.0);
            } else {
                positiveSeries.addOrUpdate(time, 0.0);
                negativeSeries.addOrUpdate(time, value);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(positiveSeries);
        dataset.addSeries(negativeSeries);

        // Create bar renderer with colors
        XYBarRenderer renderer = new XYBarRenderer();
        renderer.setSeriesPaint(0, POSITIVE_FUNDING);
        renderer.setSeriesPaint(1, NEGATIVE_FUNDING);
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);

        plot.setDataset(0, dataset);
        plot.setRenderer(0, renderer);
    }

    @Override
    public String getParameterString() {
        return "";
    }
}
