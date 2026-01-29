package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.BollingerCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.List;

/**
 * Bollinger Bands overlay.
 * Subscribes to BollingerCompute for async background computation.
 */
public class BollingerOverlay implements ChartOverlay {

    private final int period;
    private final double stdDev;
    private final Color bandColor;
    private final Color middleColor;
    private IndicatorSubscription<Indicators.BollingerResult> subscription;

    public BollingerOverlay(int period, double stdDev) {
        this(period, stdDev, ChartStyles.BB_COLOR, ChartStyles.BB_MIDDLE_COLOR);
    }

    public BollingerOverlay(int period, double stdDev, Color bandColor, Color middleColor) {
        this.period = period;
        this.stdDev = stdDev;
        this.bandColor = bandColor;
        this.middleColor = middleColor;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new BollingerCompute(period, stdDev));
        subscription.onReady(bb -> {
            if (bb == null) return;
            List<Candle> candles = provider.getCandles();
            if (candles == null || candles.isEmpty()) return;
            renderBands(plot, datasetIndex, candles, bb.upper(), bb.middle(), bb.lower());
            plot.getChart().fireChartChanged();
        });
    }

    private void renderBands(XYPlot plot, int datasetIndex, List<Candle> candles,
                              double[] upper, double[] middle, double[] lower) {
        int startIdx = period - 1;
        TimeSeries upperSeries = TimeSeriesBuilder.createTimeSeries("Upper", candles, upper, startIdx);
        TimeSeries middleSeries = TimeSeriesBuilder.createTimeSeries("Middle", candles, middle, startIdx);
        TimeSeries lowerSeries = TimeSeriesBuilder.createTimeSeries("Lower", candles, lower, startIdx);

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(upperSeries);
        dataset.addSeries(middleSeries);
        dataset.addSeries(lowerSeries);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, bandColor);
        renderer.setSeriesPaint(1, middleColor);
        renderer.setSeriesPaint(2, bandColor);
        renderer.setSeriesStroke(0, ChartStyles.THIN_STROKE);
        renderer.setSeriesStroke(1, ChartStyles.DASHED_STROKE);
        renderer.setSeriesStroke(2, ChartStyles.THIN_STROKE);

        plot.setDataset(datasetIndex, dataset);
        plot.setRenderer(datasetIndex, renderer);
    }

    @Override
    public String getDisplayName() {
        return "BB(" + period + "," + stdDev + ")";
    }

    @Override
    public int getDatasetCount() {
        return 1;
    }

    public int getPeriod() {
        return period;
    }

    public double getStdDev() {
        return stdDev;
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }
}
