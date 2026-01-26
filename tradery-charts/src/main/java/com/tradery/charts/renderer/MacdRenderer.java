package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartAnnotationHelper;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.List;

/**
 * Renderer for MACD (Moving Average Convergence Divergence) indicator.
 * Uses IndicatorEngine.getMACD() for calculation.
 */
public class MacdRenderer implements IndicatorChartRenderer {

    private final int fastPeriod;
    private final int slowPeriod;
    private final int signalPeriod;

    public MacdRenderer(int fastPeriod, int slowPeriod, int signalPeriod) {
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
    }

    /**
     * Create a MACD renderer with default parameters (12, 26, 9).
     */
    public MacdRenderer() {
        this(12, 26, 9);
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        List<Candle> candles = provider.getCandles();
        IndicatorEngine engine = provider.getIndicatorEngine();

        // Get MACD from IndicatorEngine - NOT inline calculation
        Indicators.MACDResult macd = engine.getMACD(fastPeriod, slowPeriod, signalPeriod);
        if (macd == null) return;

        int startIdx = slowPeriod - 1;

        // Build line series (MACD line + signal)
        TimeSeries macdSeries = TimeSeriesBuilder.createTimeSeries("MACD", candles, macd.line(), startIdx);
        TimeSeries signalSeries = TimeSeriesBuilder.createTimeSeries("Signal", candles, macd.signal(), startIdx);

        TimeSeriesCollection lineDataset = new TimeSeriesCollection();
        lineDataset.addSeries(macdSeries);
        lineDataset.addSeries(signalSeries);

        plot.setDataset(0, lineDataset);
        plot.setRenderer(0, RendererBuilder.lineRenderer(
            ChartStyles.MACD_LINE_COLOR, ChartStyles.MEDIUM_STROKE,
            ChartStyles.MACD_SIGNAL_COLOR, ChartStyles.MEDIUM_STROKE));

        // Build histogram (bar chart)
        XYSeriesCollection histDataset = TimeSeriesBuilder.buildXY("Histogram", candles, macd.histogram(), startIdx);

        plot.setDataset(1, histDataset);
        plot.setRenderer(1, RendererBuilder.colorCodedBarRenderer(
            histDataset, ChartStyles.MACD_HIST_POS, ChartStyles.MACD_HIST_NEG));

        // Add zero reference line
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            ChartAnnotationHelper.addZeroLine(plot, startTime, endTime);
        }
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
}
