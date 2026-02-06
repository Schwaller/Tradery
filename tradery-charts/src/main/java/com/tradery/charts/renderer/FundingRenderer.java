package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.FundingCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.util.Date;
import java.util.List;

/**
 * Renderer for Funding Rate indicator.
 * Subscribes to FundingCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 * Displays funding rate as bars (green positive, red negative).
 */
public class FundingRenderer implements IndicatorChartRenderer {

    private static final Color POSITIVE_FUNDING = new Color(76, 175, 80);
    private static final Color NEGATIVE_FUNDING = new Color(244, 67, 54);

    private final IndicatorSubscription<double[]> subscription;

    public FundingRenderer(XYPlot plot, ChartDataProvider provider) {
        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new FundingCompute());
        subscription.onReady(funding -> {
            if (funding == null || funding.length == 0) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "Funding");

            List<Candle> candles = provider.getCandles();

            TimeSeries positiveSeries = new TimeSeries("Funding+");
            TimeSeries negativeSeries = new TimeSeries("Funding-");

            for (int i = 0; i < candles.size() && i < funding.length; i++) {
                if (Double.isNaN(funding[i])) continue;
                Candle c = candles.get(i);
                Millisecond time = new Millisecond(new Date(c.timestamp()));

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

            XYBarRenderer renderer = new XYBarRenderer();
            renderer.setSeriesPaint(0, POSITIVE_FUNDING);
            renderer.setSeriesPaint(1, NEGATIVE_FUNDING);
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
