package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.IchimokuCompute;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.List;

/**
 * Ichimoku Cloud overlay.
 * Subscribes to IchimokuCompute for async background computation.
 */
public class IchimokuOverlay implements ChartOverlay {

    private static final int DEFAULT_CONVERSION = 9;
    private static final int DEFAULT_BASE = 26;
    private static final int DEFAULT_SPAN_B = 52;
    private static final int DEFAULT_DISPLACEMENT = 26;

    private static final Color TENKAN_COLOR = new Color(0, 150, 255);
    private static final Color KIJUN_COLOR = new Color(255, 0, 100);
    private static final Color SENKOU_A_COLOR = new Color(0, 200, 100, 150);
    private static final Color SENKOU_B_COLOR = new Color(255, 100, 100, 150);
    private static final Color CHIKOU_COLOR = new Color(150, 150, 150);

    private final int conversionPeriod;
    private final int basePeriod;
    private final int spanBPeriod;
    private final int displacement;
    private IndicatorSubscription<Indicators.IchimokuResult> subscription;

    public IchimokuOverlay() {
        this(DEFAULT_CONVERSION, DEFAULT_BASE, DEFAULT_SPAN_B, DEFAULT_DISPLACEMENT);
    }

    public IchimokuOverlay(int conversionPeriod, int basePeriod, int spanBPeriod, int displacement) {
        this.conversionPeriod = conversionPeriod;
        this.basePeriod = basePeriod;
        this.spanBPeriod = spanBPeriod;
        this.displacement = displacement;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool != null) {
            if (subscription != null) subscription.close();
            subscription = pool.subscribe(new IchimokuCompute(conversionPeriod, basePeriod, spanBPeriod, displacement));
            subscription.onReady(ichi -> {
                if (ichi == null) return;
                List<Candle> candles = provider.getCandles();
                if (candles == null || candles.isEmpty()) return;
                renderIchimoku(plot, datasetIndex, candles, ichi);
                plot.getChart().fireChartChanged();
            });
        } else {
            List<Candle> candles = provider.getCandles();
            Indicators.IchimokuResult ichi = provider.getIndicatorEngine()
                    .getIchimoku(conversionPeriod, basePeriod, spanBPeriod, displacement);
            if (ichi == null) return;
            renderIchimoku(plot, datasetIndex, candles, ichi);
        }
    }

    private void renderIchimoku(XYPlot plot, int datasetIndex, List<Candle> candles,
                                 Indicators.IchimokuResult ichi) {
        int warmup = Math.max(Math.max(conversionPeriod, basePeriod), spanBPeriod) - 1;
        TimeSeriesCollection dataset = new TimeSeriesCollection();

        double[][] arrays = { ichi.tenkanSen(), ichi.kijunSen(), ichi.senkouSpanA(), ichi.senkouSpanB(), ichi.chikouSpan() };
        String[] names = { "Tenkan", "Kijun", "Senkou A", "Senkou B", "Chikou" };
        Color[] colors = { TENKAN_COLOR, KIJUN_COLOR, SENKOU_A_COLOR, SENKOU_B_COLOR, CHIKOU_COLOR };

        int seriesIdx = 0;
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);

        for (int s = 0; s < arrays.length; s++) {
            if (arrays[s] != null) {
                int startIdx = s == 4 ? 0 : warmup; // chikou starts at 0
                dataset.addSeries(TimeSeriesBuilder.createTimeSeries(names[s], candles, arrays[s], startIdx));
                renderer.setSeriesPaint(seriesIdx, colors[s]);
                renderer.setSeriesStroke(seriesIdx, s == 4 ? ChartStyles.DASHED_STROKE : ChartStyles.THIN_STROKE);
                seriesIdx++;
            }
        }

        plot.setDataset(datasetIndex, dataset);
        plot.setRenderer(datasetIndex, renderer);
    }

    @Override
    public String getDisplayName() {
        return "Ichimoku(" + conversionPeriod + "," + basePeriod + "," + spanBPeriod + ")";
    }

    @Override
    public int getDatasetCount() {
        return 1;
    }

    public int getConversionPeriod() { return conversionPeriod; }
    public int getBasePeriod() { return basePeriod; }
    public int getSpanBPeriod() { return spanBPeriod; }
    public int getDisplacement() { return displacement; }
}
