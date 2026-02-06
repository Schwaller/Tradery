package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.WhaleDeltaCompute;
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
 * Renderer for Whale Delta indicator.
 * Subscribes to WhaleDeltaCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 */
public class WhaleRenderer implements IndicatorChartRenderer {

    private static final Color WHALE_BUY_COLOR = new Color(0, 150, 136);
    private static final Color WHALE_SELL_COLOR = new Color(233, 30, 99);

    private final double threshold;
    private final IndicatorSubscription<double[]> subscription;

    public WhaleRenderer(double threshold, XYPlot plot, ChartDataProvider provider) {
        this.threshold = threshold;

        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new WhaleDeltaCompute(threshold));
        subscription.onReady(whaleDelta -> {
            if (whaleDelta == null || whaleDelta.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "Whale Delta");

            List<Candle> candles = provider.getCandles();

            TimeSeries buySeries = new TimeSeries("Whale Buy");
            TimeSeries sellSeries = new TimeSeries("Whale Sell");

            for (int i = 0; i < candles.size() && i < whaleDelta.length; i++) {
                Candle c = candles.get(i);
                double val = whaleDelta[i];
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

            TimeSeriesCollection buyDataset = new TimeSeriesCollection();
            buyDataset.addSeries(buySeries);

            XYBarRenderer buyRenderer = new XYBarRenderer(0.1);
            buyRenderer.setSeriesPaint(0, WHALE_BUY_COLOR);
            buyRenderer.setBarPainter(new StandardXYBarPainter());
            buyRenderer.setShadowVisible(false);
            buyRenderer.setDrawBarOutline(false);

            plot.setDataset(0, buyDataset);
            plot.setRenderer(0, buyRenderer);

            TimeSeriesCollection sellDataset = new TimeSeriesCollection();
            sellDataset.addSeries(sellSeries);

            XYBarRenderer sellRenderer = new XYBarRenderer(0.1);
            sellRenderer.setSeriesPaint(0, WHALE_SELL_COLOR);
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
        subscription.close();
    }

    @Override
    public String getParameterString() {
        return String.format("(%.0f)", threshold);
    }

    private static void clearPlot(XYPlot plot) {
        for (int i = 0; i < plot.getDatasetCount(); i++) plot.setDataset(i, null);
        plot.clearAnnotations();
    }
}
