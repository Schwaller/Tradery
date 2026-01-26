package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.Date;
import java.util.List;

/**
 * Point of Control (POC) overlay.
 * Shows the rolling POC price level where most volume traded over the lookback period.
 * Also optionally shows Value Area High (VAH) and Value Area Low (VAL).
 *
 * Uses IndicatorEngine.getPOCAt(), getVAHAt(), getVALAt() for data.
 */
public class PocOverlay implements ChartOverlay {

    private static final Color POC_COLOR = new Color(255, 193, 7);       // Amber
    private static final Color VAH_COLOR = new Color(76, 175, 80, 128);  // Green transparent
    private static final Color VAL_COLOR = new Color(244, 67, 54, 128);  // Red transparent

    private final int period;
    private final boolean showValueArea;

    public PocOverlay() {
        this(20, true);  // Default 20-bar POC with value area
    }

    public PocOverlay(int period) {
        this(period, true);
    }

    public PocOverlay(int period, boolean showValueArea) {
        this.period = period;
        this.showValueArea = showValueArea;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        List<Candle> candles = provider.getCandles();
        if (candles.isEmpty()) return;

        IndicatorEngine engine = provider.getIndicatorEngine();

        // Build POC time series
        TimeSeries pocSeries = new TimeSeries("POC(" + period + ")");
        for (int i = period - 1; i < candles.size(); i++) {
            double poc = engine.getPOCAt(period, i);
            if (!Double.isNaN(poc)) {
                Candle c = candles.get(i);
                pocSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), poc);
            }
        }

        TimeSeriesCollection pocDataset = new TimeSeriesCollection();
        pocDataset.addSeries(pocSeries);

        // Add POC line
        plot.setDataset(datasetIndex, pocDataset);
        plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(POC_COLOR, ChartStyles.MEDIUM_STROKE));

        // Add VAH/VAL if enabled
        if (showValueArea) {
            // VAH
            TimeSeries vahSeries = new TimeSeries("VAH");
            for (int i = period - 1; i < candles.size(); i++) {
                double vah = engine.getVAHAt(period, i);
                if (!Double.isNaN(vah)) {
                    Candle c = candles.get(i);
                    vahSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), vah);
                }
            }
            TimeSeriesCollection vahDataset = new TimeSeriesCollection();
            vahDataset.addSeries(vahSeries);
            plot.setDataset(datasetIndex + 1, vahDataset);
            plot.setRenderer(datasetIndex + 1, RendererBuilder.lineRenderer(VAH_COLOR, ChartStyles.DASHED_STROKE));

            // VAL
            TimeSeries valSeries = new TimeSeries("VAL");
            for (int i = period - 1; i < candles.size(); i++) {
                double val = engine.getVALAt(period, i);
                if (!Double.isNaN(val)) {
                    Candle c = candles.get(i);
                    valSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), val);
                }
            }
            TimeSeriesCollection valDataset = new TimeSeriesCollection();
            valDataset.addSeries(valSeries);
            plot.setDataset(datasetIndex + 2, valDataset);
            plot.setRenderer(datasetIndex + 2, RendererBuilder.lineRenderer(VAL_COLOR, ChartStyles.DASHED_STROKE));
        }
    }

    @Override
    public String getDisplayName() {
        return String.format("POC(%d)", period);
    }

    @Override
    public int getDatasetCount() {
        return showValueArea ? 3 : 1;
    }
}
