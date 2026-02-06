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
 * Subscribes to OhlcvDeltaCompute in the constructor for OHLCV delta fallback;
 * the onReady callback handles both initial rendering and recomputation.
 * Also checks provider.getDelta() for orderflow data on each callback.
 * Displays delta as bars (green positive, red negative) with optional CVD line.
 */
public class DeltaRenderer implements IndicatorChartRenderer {

    private final boolean showCvd;
    private final IndicatorSubscription<OhlcvDeltaCompute.Result> deltaSubscription;

    public DeltaRenderer(boolean showCvd, XYPlot plot, ChartDataProvider provider) {
        this.showCvd = showCvd;

        IndicatorPool pool = provider.getIndicatorPool();
        this.deltaSubscription = pool.subscribe(new OhlcvDeltaCompute());
        deltaSubscription.onReady(result -> {
            clearPlot(plot);
            ChartStyles.addChartTitleAnnotation(plot, "Delta");

            List<Candle> candles = provider.getCandles();

            // Prefer orderflow delta if available, otherwise use OHLCV delta
            double[] delta = provider.getDelta();
            if (delta == null || delta.length == 0) {
                if (result == null || result.delta() == null || result.delta().length == 0) return;
                delta = result.delta();
            }

            renderDelta(plot, candles, delta);

            // Optionally add CVD line
            if (showCvd) {
                double[] cvd = provider.getCumulativeDelta();
                if (cvd == null || cvd.length == 0) {
                    if (result != null && result.cvd() != null && result.cvd().length > 0) {
                        cvd = result.cvd();
                    }
                }
                if (cvd != null && cvd.length > 0) {
                    renderCvdLine(plot, candles, cvd);
                }
            }

            plot.getChart().fireChartChanged();
        });
    }

    private void renderDelta(XYPlot plot, List<Candle> candles, double[] delta) {
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

        XYBarRenderer barRenderer = new XYBarRenderer();
        barRenderer.setSeriesPaint(0, ChartStyles.DELTA_POSITIVE);
        barRenderer.setSeriesPaint(1, ChartStyles.DELTA_NEGATIVE);
        barRenderer.setShadowVisible(false);
        barRenderer.setDrawBarOutline(false);

        plot.setDataset(0, deltaDataset);
        plot.setRenderer(0, barRenderer);
    }

    private void renderCvdLine(XYPlot plot, List<Candle> candles, double[] cvd) {
        TimeSeriesCollection cvdDataset = TimeSeriesBuilder.build("CVD", candles, cvd, 0);

        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        lineRenderer.setSeriesPaint(0, ChartStyles.CVD_COLOR);
        lineRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);

        plot.setDataset(1, cvdDataset);
        plot.setRenderer(1, lineRenderer);
    }

    @Override
    public void close() {
        deltaSubscription.close();
    }

    @Override
    public String getParameterString() {
        return showCvd ? "+CVD" : "";
    }

    public boolean isShowCvd() {
        return showCvd;
    }

    private static void clearPlot(XYPlot plot) {
        for (int i = 0; i < plot.getDatasetCount(); i++) plot.setDataset(i, null);
        plot.clearAnnotations();
    }
}
