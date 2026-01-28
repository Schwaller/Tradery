package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.PocCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
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
 * Subscribes to PocCompute for async background computation.
 */
public class PocOverlay implements ChartOverlay {

    private static final Color POC_COLOR = new Color(255, 193, 7);       // Amber
    private static final Color VAH_COLOR = new Color(76, 175, 80, 128);  // Green transparent
    private static final Color VAL_COLOR = new Color(244, 67, 54, 128);  // Red transparent

    private final int period;
    private final boolean showValueArea;
    private IndicatorSubscription<PocCompute.Result> subscription;

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
        if (candles == null || candles.isEmpty()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new PocCompute(period, showValueArea));
        subscription.onReady(result -> {
            if (result == null) return;
            List<Candle> c = provider.getCandles();
            if (c == null || c.isEmpty()) return;
            renderPoc(plot, datasetIndex, c, result);
            plot.getChart().fireChartChanged();
        });
    }

    private void renderPoc(XYPlot plot, int datasetIndex, List<Candle> candles,
                            PocCompute.Result result) {
        // Build POC time series
        TimeSeries pocSeries = new TimeSeries("POC(" + period + ")");
        for (int i = result.warmup() - 1; i < candles.size() && i < result.poc().length; i++) {
            double poc = result.poc()[i];
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
        if (showValueArea && result.vah() != null && result.val() != null) {
            // VAH
            TimeSeries vahSeries = new TimeSeries("VAH");
            for (int i = result.warmup() - 1; i < candles.size() && i < result.vah().length; i++) {
                double vah = result.vah()[i];
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
            for (int i = result.warmup() - 1; i < candles.size() && i < result.val().length; i++) {
                double val = result.val()[i];
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
