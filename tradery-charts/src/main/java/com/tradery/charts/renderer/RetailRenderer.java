package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.RetailDeltaCompute;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.util.Date;
import java.util.List;

/**
 * Renderer for Retail Delta indicator.
 * Shows delta from small trades only (below threshold).
 * Uses IndicatorPool with RetailDeltaCompute for async calculation.
 */
public class RetailRenderer implements IndicatorChartRenderer {

    private static final Color RETAIL_BUY_COLOR = new Color(129, 199, 132);   // Light green
    private static final Color RETAIL_SELL_COLOR = new Color(239, 154, 154);  // Light red

    private final double threshold;
    private IndicatorSubscription<double[]> subscription;

    public RetailRenderer(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new RetailDeltaCompute(threshold));
        subscription.onReady(retailDelta -> {
            if (retailDelta == null || retailDelta.length == 0) return;

            List<Candle> candles = provider.getCandles();

            // Build time series for positive and negative bars
            TimeSeries buySeries = new TimeSeries("Retail Buy");
            TimeSeries sellSeries = new TimeSeries("Retail Sell");

            for (int i = 0; i < candles.size() && i < retailDelta.length; i++) {
                Candle c = candles.get(i);
                double val = retailDelta[i];
                if (!Double.isNaN(val)) {
                    Millisecond time = new Millisecond(new Date(c.timestamp()));
                    if (val >= 0) {
                        buySeries.addOrUpdate(time, val);
                        sellSeries.addOrUpdate(time, 0.0);
                    } else {
                        buySeries.addOrUpdate(time, 0.0);
                        sellSeries.addOrUpdate(time, val);
                    }
                }
            }

            // Buy bars (positive)
            TimeSeriesCollection buyDataset = new TimeSeriesCollection();
            buyDataset.addSeries(buySeries);

            XYBarRenderer buyRenderer = new XYBarRenderer(0.1);
            buyRenderer.setSeriesPaint(0, RETAIL_BUY_COLOR);
            buyRenderer.setBarPainter(new StandardXYBarPainter());
            buyRenderer.setShadowVisible(false);
            buyRenderer.setDrawBarOutline(false);

            plot.setDataset(0, buyDataset);
            plot.setRenderer(0, buyRenderer);

            // Sell bars (negative)
            TimeSeriesCollection sellDataset = new TimeSeriesCollection();
            sellDataset.addSeries(sellSeries);

            XYBarRenderer sellRenderer = new XYBarRenderer(0.1);
            sellRenderer.setSeriesPaint(0, RETAIL_SELL_COLOR);
            sellRenderer.setBarPainter(new StandardXYBarPainter());
            sellRenderer.setShadowVisible(false);
            sellRenderer.setDrawBarOutline(false);

            plot.setDataset(1, sellDataset);
            plot.setRenderer(1, sellRenderer);

            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    @Override
    public String getParameterString() {
        return String.format("(%.0f)", threshold);
    }
}
