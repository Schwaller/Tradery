package com.tradery.ui.charts;

import com.tradery.model.Candle;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.Date;
import java.util.List;

/**
 * Builder for creating time series data from candles and indicator values.
 * Eliminates duplication of data series construction across IndicatorChartsManager.
 */
public final class TimeSeriesBuilder {

    private TimeSeriesBuilder() {} // Prevent instantiation

    /**
     * Build a TimeSeries from candle timestamps and indicator data.
     *
     * @param label     Series label
     * @param candles   Candle data for timestamps
     * @param data      Indicator values (same length as candles)
     * @param startIdx  Index to start from (for warmup periods)
     * @return TimeSeriesCollection with the series
     */
    public static TimeSeriesCollection build(String label, List<Candle> candles, double[] data, int startIdx) {
        TimeSeries series = new TimeSeries(label);

        for (int i = startIdx; i < candles.size() && i < data.length; i++) {
            if (!Double.isNaN(data[i])) {
                Candle c = candles.get(i);
                series.addOrUpdate(new Millisecond(new Date(c.timestamp())), data[i]);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(series);
        return dataset;
    }

    /**
     * Build a TimeSeries from candle timestamps and indicator data, starting from index 0.
     */
    public static TimeSeriesCollection build(String label, List<Candle> candles, double[] data) {
        return build(label, candles, data, 0);
    }

    /**
     * Build an XY series from candle timestamps and indicator data.
     * Useful for bar charts that need x-values as timestamps.
     *
     * @param label     Series label
     * @param candles   Candle data for timestamps
     * @param data      Indicator values (same length as candles)
     * @param startIdx  Index to start from (for warmup periods)
     * @return XYSeriesCollection with the series
     */
    public static XYSeriesCollection buildXY(String label, List<Candle> candles, double[] data, int startIdx) {
        XYSeries series = new XYSeries(label);

        for (int i = startIdx; i < candles.size() && i < data.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(data[i])) {
                series.add(c.timestamp(), data[i]);
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series);
        return dataset;
    }

    /**
     * Build an XY series from candle timestamps and indicator data, starting from index 0.
     */
    public static XYSeriesCollection buildXY(String label, List<Candle> candles, double[] data) {
        return buildXY(label, candles, data, 0);
    }

    /**
     * Create a TimeSeries object (without wrapping in collection).
     * Useful when you need to add multiple series to a single collection.
     */
    public static TimeSeries createTimeSeries(String label, List<Candle> candles, double[] data, int startIdx) {
        TimeSeries series = new TimeSeries(label);

        for (int i = startIdx; i < candles.size() && i < data.length; i++) {
            if (!Double.isNaN(data[i])) {
                Candle c = candles.get(i);
                series.addOrUpdate(new Millisecond(new Date(c.timestamp())), data[i]);
            }
        }

        return series;
    }

    /**
     * Create an XYSeries object (without wrapping in collection).
     * Useful when you need to add multiple series to a single collection.
     */
    public static XYSeries createXYSeries(String label, List<Candle> candles, double[] data, int startIdx) {
        XYSeries series = new XYSeries(label);

        for (int i = startIdx; i < candles.size() && i < data.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(data[i])) {
                series.add(c.timestamp(), data[i]);
            }
        }

        return series;
    }
}
