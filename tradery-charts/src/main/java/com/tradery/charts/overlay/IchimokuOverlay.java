package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.TimeSeriesBuilder;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.util.List;

/**
 * Ichimoku Cloud overlay.
 * Uses IndicatorEngine.getIchimoku() for calculation.
 * Displays: Tenkan-sen, Kijun-sen, Senkou Span A, Senkou Span B, Chikou Span.
 */
public class IchimokuOverlay implements ChartOverlay {

    // Default Ichimoku parameters
    private static final int DEFAULT_CONVERSION = 9;
    private static final int DEFAULT_BASE = 26;
    private static final int DEFAULT_SPAN_B = 52;
    private static final int DEFAULT_DISPLACEMENT = 26;

    // Ichimoku colors
    private static final Color TENKAN_COLOR = new Color(0, 150, 255);      // Blue - Conversion Line
    private static final Color KIJUN_COLOR = new Color(255, 0, 100);       // Red - Base Line
    private static final Color SENKOU_A_COLOR = new Color(0, 200, 100, 150);  // Green - Leading Span A
    private static final Color SENKOU_B_COLOR = new Color(255, 100, 100, 150); // Red - Leading Span B
    private static final Color CHIKOU_COLOR = new Color(150, 150, 150);    // Gray - Lagging Span

    private final int conversionPeriod;
    private final int basePeriod;
    private final int spanBPeriod;
    private final int displacement;

    /**
     * Create an Ichimoku overlay with default parameters (9, 26, 52, 26).
     */
    public IchimokuOverlay() {
        this(DEFAULT_CONVERSION, DEFAULT_BASE, DEFAULT_SPAN_B, DEFAULT_DISPLACEMENT);
    }

    /**
     * Create an Ichimoku overlay with custom parameters.
     */
    public IchimokuOverlay(int conversionPeriod, int basePeriod, int spanBPeriod, int displacement) {
        this.conversionPeriod = conversionPeriod;
        this.basePeriod = basePeriod;
        this.spanBPeriod = spanBPeriod;
        this.displacement = displacement;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        if (!provider.hasCandles()) return;

        List<Candle> candles = provider.getCandles();
        IndicatorEngine engine = provider.getIndicatorEngine();

        // Get Ichimoku from IndicatorEngine
        Indicators.IchimokuResult ichi = engine.getIchimoku(conversionPeriod, basePeriod, spanBPeriod, displacement);
        if (ichi == null) return;

        double[] tenkan = ichi.tenkanSen();
        double[] kijun = ichi.kijunSen();
        double[] senkouA = ichi.senkouSpanA();
        double[] senkouB = ichi.senkouSpanB();
        double[] chikou = ichi.chikouSpan();

        // Warmup period is max of all periods
        int warmup = Math.max(Math.max(conversionPeriod, basePeriod), spanBPeriod) - 1;

        // Build time series for each line
        TimeSeriesCollection dataset = new TimeSeriesCollection();

        if (tenkan != null) {
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries("Tenkan", candles, tenkan, warmup));
        }
        if (kijun != null) {
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries("Kijun", candles, kijun, warmup));
        }
        if (senkouA != null) {
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries("Senkou A", candles, senkouA, warmup));
        }
        if (senkouB != null) {
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries("Senkou B", candles, senkouB, warmup));
        }
        if (chikou != null) {
            dataset.addSeries(TimeSeriesBuilder.createTimeSeries("Chikou", candles, chikou, 0));
        }

        // Create renderer with colors for each line
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        int seriesIdx = 0;

        if (tenkan != null) {
            renderer.setSeriesPaint(seriesIdx, TENKAN_COLOR);
            renderer.setSeriesStroke(seriesIdx++, ChartStyles.THIN_STROKE);
        }
        if (kijun != null) {
            renderer.setSeriesPaint(seriesIdx, KIJUN_COLOR);
            renderer.setSeriesStroke(seriesIdx++, ChartStyles.THIN_STROKE);
        }
        if (senkouA != null) {
            renderer.setSeriesPaint(seriesIdx, SENKOU_A_COLOR);
            renderer.setSeriesStroke(seriesIdx++, ChartStyles.THIN_STROKE);
        }
        if (senkouB != null) {
            renderer.setSeriesPaint(seriesIdx, SENKOU_B_COLOR);
            renderer.setSeriesStroke(seriesIdx++, ChartStyles.THIN_STROKE);
        }
        if (chikou != null) {
            renderer.setSeriesPaint(seriesIdx, CHIKOU_COLOR);
            renderer.setSeriesStroke(seriesIdx, ChartStyles.DASHED_STROKE);
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
        return 1;  // Single dataset with 5 series
    }

    public int getConversionPeriod() {
        return conversionPeriod;
    }

    public int getBasePeriod() {
        return basePeriod;
    }

    public int getSpanBPeriod() {
        return spanBPeriod;
    }

    public int getDisplacement() {
        return displacement;
    }
}
