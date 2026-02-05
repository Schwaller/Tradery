package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.impl.TradeCountCompute;
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
 * Renderer for Trade Count indicator.
 * Shows the number of individual trades per candle.
 * Higher trade counts indicate more market activity.
 * Uses IndicatorPool with TradeCountCompute for async calculation.
 */
public class TradeCountRenderer implements IndicatorChartRenderer {

    private static final Color TRADE_COUNT_COLOR = new Color(255, 152, 0);  // Orange

    public TradeCountRenderer() {
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        pool.subscribe(new TradeCountCompute()).onReady(tradeCount -> {
            if (tradeCount == null || tradeCount.length == 0) return;

            List<Candle> candles = provider.getCandles();

            // Build time series
            TimeSeries series = new TimeSeries("Trade Count");
            for (int i = 0; i < candles.size() && i < tradeCount.length; i++) {
                Candle c = candles.get(i);
                if (!Double.isNaN(tradeCount[i])) {
                    series.addOrUpdate(new Millisecond(new Date(c.timestamp())), tradeCount[i]);
                }
            }

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);

            // Use bar renderer for trade count
            XYBarRenderer renderer = new XYBarRenderer(0.1);
            renderer.setSeriesPaint(0, TRADE_COUNT_COLOR);
            renderer.setBarPainter(new StandardXYBarPainter());
            renderer.setShadowVisible(false);
            renderer.setDrawBarOutline(false);

            plot.setDataset(0, dataset);
            plot.setRenderer(0, renderer);

            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public String getParameterString() {
        return "";
    }
}
