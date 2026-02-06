package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.MacdCompute;
import com.tradery.core.indicators.Indicators;
import com.tradery.charts.util.ChartAnnotationHelper;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.List;

/**
 * Renderer for MACD (Moving Average Convergence Divergence) indicator.
 * Subscribes to MacdCompute in the constructor; the onReady callback
 * handles both initial rendering and recomputation.
 */
public class MacdRenderer implements IndicatorChartRenderer {

    private final int fastPeriod;
    private final int slowPeriod;
    private final int signalPeriod;
    private final IndicatorSubscription<Indicators.MACDResult> subscription;

    public MacdRenderer(int fastPeriod, int slowPeriod, int signalPeriod, XYPlot plot, ChartDataProvider provider) {
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;

        IndicatorPool pool = provider.getIndicatorPool();
        this.subscription = pool.subscribe(new MacdCompute(fastPeriod, slowPeriod, signalPeriod));
        subscription.onReady(macd -> {
            if (macd == null) return;

            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "MACD");

            List<Candle> candles = provider.getCandles();
            int startIdx = slowPeriod - 1;

            TimeSeries macdSeries = TimeSeriesBuilder.createTimeSeries("MACD", candles, macd.line(), startIdx);
            TimeSeries signalSeries = TimeSeriesBuilder.createTimeSeries("Signal", candles, macd.signal(), startIdx);

            TimeSeriesCollection lineDataset = new TimeSeriesCollection();
            lineDataset.addSeries(macdSeries);
            lineDataset.addSeries(signalSeries);

            plot.setDataset(0, lineDataset);
            plot.setRenderer(0, RendererBuilder.lineRenderer(
                ChartStyles.MACD_LINE_COLOR, ChartStyles.MEDIUM_STROKE,
                ChartStyles.MACD_SIGNAL_COLOR, ChartStyles.MEDIUM_STROKE));

            XYSeriesCollection histDataset = TimeSeriesBuilder.buildXY("Histogram", candles, macd.histogram(), startIdx);

            plot.setDataset(1, histDataset);
            plot.setRenderer(1, RendererBuilder.colorCodedBarRenderer(
                histDataset, ChartStyles.MACD_HIST_POS, ChartStyles.MACD_HIST_NEG));

            if (!candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                ChartAnnotationHelper.addZeroLine(plot, startTime, endTime);
            }

            plot.getChart().fireChartChanged();
        });
    }

    @Override
    public void close() {
        subscription.close();
    }

    @Override
    public String getParameterString() {
        return fastPeriod + "," + slowPeriod + "," + signalPeriod;
    }

    public int getFastPeriod() {
        return fastPeriod;
    }

    public int getSlowPeriod() {
        return slowPeriod;
    }

    public int getSignalPeriod() {
        return signalPeriod;
    }

    private static void clearPlot(XYPlot plot) {
        for (int i = 0; i < plot.getDatasetCount(); i++) plot.setDataset(i, null);
        plot.clearAnnotations();
    }
}
