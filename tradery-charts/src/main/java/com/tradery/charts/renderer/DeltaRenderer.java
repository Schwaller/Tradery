package com.tradery.charts.renderer;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.OhlcvDeltaCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.util.Date;
import java.util.List;

/**
 * Renderer for Delta (buy-sell volume difference) indicator.
 * Uses ChartDataProvider.getDelta() for orderflow data, with OHLCV delta fallback
 * via IndicatorPool and OhlcvDeltaCompute.
 * Displays delta as bars (green positive, red negative) with optional CVD line.
 */
public class DeltaRenderer implements IndicatorChartRenderer {

    private final boolean showCvd;
    private IndicatorSubscription<OhlcvDeltaCompute.Result> deltaSubscription;
    private IndicatorSubscription<OhlcvDeltaCompute.Result> cvdSubscription;

    /**
     * Create a Delta renderer without CVD line.
     */
    public DeltaRenderer() {
        this(false);
    }

    /**
     * Create a Delta renderer with optional CVD line.
     */
    public DeltaRenderer(boolean showCvd) {
        this.showCvd = showCvd;
    }

    @Override
    public void render(XYPlot plot, ChartDataProvider provider) {
        List<Candle> candles = provider.getCandles();

        // Get Delta from provider (may be null if no orderflow data)
        double[] delta = provider.getDelta();
        if (delta != null && delta.length > 0) {
            renderDelta(plot, provider, candles, delta);
        } else {
            // Fallback to OHLCV delta via pool
            IndicatorPool pool = provider.getIndicatorPool();
            if (pool == null) return;

            if (deltaSubscription != null) deltaSubscription.close();
            deltaSubscription = pool.subscribe(new OhlcvDeltaCompute());
            deltaSubscription.onReady(result -> {
                if (result == null || result.delta() == null || result.delta().length == 0) return;
                renderDelta(plot, provider, provider.getCandles(), result.delta());
                plot.getChart().fireChartChanged();
            });
        }
    }

    private void renderDelta(XYPlot plot, ChartDataProvider provider, List<Candle> candles, double[] delta) {
        // Create separate series for positive and negative delta
        TimeSeries positiveSeries = new TimeSeries("Delta+");
        TimeSeries negativeSeries = new TimeSeries("Delta-");

        for (int i = 0; i < candles.size() && i < delta.length; i++) {
            if (Double.isNaN(delta[i])) continue;
            Candle c = candles.get(i);
            Millisecond time = new Millisecond(new Date(c.timestamp()));

            if (delta[i] >= 0) {
                positiveSeries.addOrUpdate(time, delta[i]);
                negativeSeries.addOrUpdate(time, 0.0);
            } else {
                positiveSeries.addOrUpdate(time, 0.0);
                negativeSeries.addOrUpdate(time, delta[i]);
            }
        }

        TimeSeriesCollection deltaDataset = new TimeSeriesCollection();
        deltaDataset.addSeries(positiveSeries);
        deltaDataset.addSeries(negativeSeries);

        // Create bar renderer with colors
        XYBarRenderer barRenderer = new XYBarRenderer();
        barRenderer.setSeriesPaint(0, ChartStyles.DELTA_POSITIVE);
        barRenderer.setSeriesPaint(1, ChartStyles.DELTA_NEGATIVE);
        barRenderer.setShadowVisible(false);
        barRenderer.setDrawBarOutline(false);

        plot.setDataset(0, deltaDataset);
        plot.setRenderer(0, barRenderer);

        // Optionally add CVD line
        if (showCvd) {
            double[] cvd = provider.getCumulativeDelta();
            if (cvd != null && cvd.length > 0) {
                renderCvdLine(plot, candles, cvd);
            } else {
                // Fallback to OHLCV CVD via pool
                IndicatorPool pool = provider.getIndicatorPool();
                if (pool != null) {
                    if (cvdSubscription != null) cvdSubscription.close();
                    cvdSubscription = pool.subscribe(new OhlcvDeltaCompute());
                    cvdSubscription.onReady(result -> {
                        if (result != null && result.cvd() != null && result.cvd().length > 0) {
                            renderCvdLine(plot, provider.getCandles(), result.cvd());
                            plot.getChart().fireChartChanged();
                        }
                    });
                }
            }
        }
    }

    private void renderCvdLine(XYPlot plot, List<Candle> candles, double[] cvd) {
        TimeSeriesCollection cvdDataset = TimeSeriesBuilder.build(
            "CVD", candles, cvd, 0);

        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        lineRenderer.setSeriesPaint(0, ChartStyles.CVD_COLOR);
        lineRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);

        plot.setDataset(1, cvdDataset);
        plot.setRenderer(1, lineRenderer);
    }

    @Override
    public void close() {
        if (deltaSubscription != null) {
            deltaSubscription.close();
            deltaSubscription = null;
        }
        if (cvdSubscription != null) {
            cvdSubscription.close();
            cvdSubscription = null;
        }
    }

    @Override
    public String getParameterString() {
        return showCvd ? "+CVD" : "";
    }

    public boolean isShowCvd() {
        return showCvd;
    }
}
