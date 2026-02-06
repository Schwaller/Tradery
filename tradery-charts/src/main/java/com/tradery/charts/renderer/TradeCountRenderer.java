package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.TradeCountCompute;
import com.tradery.charts.util.ChartStyles;
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
 * Subscribes to TradeCountCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 */
public class TradeCountRenderer implements IndicatorChartRenderer {

    private static final Color TRADE_COUNT_COLOR = new Color(255, 152, 0);

    private final IndicatorSubscription<double[]> subscription;

    public TradeCountRenderer(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new TradeCountCompute());
        subscription.onReady(tradeCount -> {
            if (tradeCount == null || tradeCount.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "Trade Count");

            List<Candle> candles = provider.getCandles();

            TimeSeries series = new TimeSeries("Trade Count");
            for (int i = 0; i < candles.size() && i < tradeCount.length; i++) {
                Candle c = candles.get(i);
                if (!Double.isNaN(tradeCount[i])) {
                    series.addOrUpdate(new Millisecond(new Date(c.timestamp())), tradeCount[i]);
                }
            }

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);

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
    public void close() {
        subscription.close();
    }

    @Override
    public String getParameterString() {
        return "";
    }

    private static void clearPlot(XYPlot plot) {
        for (int i = 0; i < plot.getDatasetCount(); i++) plot.setDataset(i, null);
        plot.clearAnnotations();
    }
}
