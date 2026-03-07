package com.tradery.charts.indicator;

import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import com.tradery.core.model.SizeBucket;
import com.tradery.core.model.SpectrumWindow;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.data.page.PageState;
import com.tradery.ui.controls.indicators.SpectrumBucketMode;
import com.tradery.ui.controls.indicators.SpectrumColorMode;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.xy.DefaultXYZDataset;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Trade Size Spectrum heatmap chart. Uses constructor-injected SpectrumDataSource
 * for async data loading and SpectrumConfig for display settings.
 */
public class SpectrumChart extends IndicatorChartBase {

    /**
     * Interface for async spectrum data loading.
     * Forge implements this via SpectrumPageManager; Desk passes null (not supported).
     */
    public interface SpectrumDataSource {
        DataPageView<SpectrumWindow> request(String symbol, String bucketMode, long start, long end,
                                              DataPageListener<SpectrumWindow> listener, String consumer);
        void release(DataPageView<SpectrumWindow> page, DataPageListener<SpectrumWindow> listener);
    }

    /**
     * Interface for spectrum display configuration.
     * Forge implements this via ChartConfig; Desk provides defaults.
     */
    public interface SpectrumConfig {
        SpectrumColorMode getColorMode();
        SpectrumBucketMode getBucketMode();
    }

    private final SpectrumDataSource dataSource; // nullable
    private final SpectrumConfig config;

    private DataPageView<SpectrumWindow> spectrumPage;
    private DataPageListener<SpectrumWindow> spectrumListener;
    private List<SpectrumWindow> currentSpectrumData;
    private String currentBucketMode;

    private static final String[] BUCKET_LABELS = {
        "$1–10", "$10–100", "$100–1K", "$1K–10K", "$10K–100K", "$100K–1M", "$1M–10M", "$10M–100M", "$100M+"
    };

    /** Default config returning RELATIVE color mode and RAW bucket mode. */
    private static final SpectrumConfig DEFAULT_CONFIG = new SpectrumConfig() {
        @Override public SpectrumColorMode getColorMode() { return SpectrumColorMode.RELATIVE; }
        @Override public SpectrumBucketMode getBucketMode() { return SpectrumBucketMode.RAW; }
    };

    public SpectrumChart(SpectrumDataSource dataSource, SpectrumConfig config) {
        super(IndicatorType.SPECTRUM);
        this.dataSource = dataSource;
        this.config = config != null ? config : DEFAULT_CONFIG;
    }

    @Override public boolean requiresOrderflow() { return false; }
    @Override public boolean managesOwnAnnotations() { return true; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        if (dataSource != null) {
            requestSpectrumData(ctx.symbol(), ctx.startTime(), ctx.endTime());
        }
    }

    private void requestSpectrumData(String symbol, long startTime, long endTime) {
        if (spectrumPage != null && spectrumListener != null) {
            dataSource.release(spectrumPage, spectrumListener);
        }

        spectrumListener = new DataPageListener<>() {
            @Override
            public void onStateChanged(DataPageView<SpectrumWindow> page, PageState oldState, PageState newState) {
                if (newState == PageState.READY) {
                    currentSpectrumData = page.getData();
                    SwingUtilities.invokeLater(() -> renderSpectrum());
                }
            }

            @Override
            public void onDataChanged(DataPageView<SpectrumWindow> page) {
                if (page.isReady()) {
                    currentSpectrumData = page.getData();
                    SwingUtilities.invokeLater(() -> renderSpectrum());
                }
            }
        };

        String bucketMode = config.getBucketMode().storageKey();
        currentBucketMode = bucketMode;
        spectrumPage = dataSource.request(symbol, bucketMode, startTime, endTime, spectrumListener, "SpectrumChart");
    }

    /**
     * Re-render with current data (e.g., after color mode change).
     * If bucket mode changed, re-requests data.
     */
    public void refresh(String symbol, List<Candle> candles) {
        if (dataSource == null) return;

        String newBucketMode = config.getBucketMode().storageKey();
        if (!newBucketMode.equals(currentBucketMode) && symbol != null && candles != null && candles.size() >= 2) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            requestSpectrumData(symbol, startTime, endTime);
            return;
        }

        if (currentSpectrumData != null && !currentSpectrumData.isEmpty()) {
            renderSpectrum();
        }
    }

    private void renderSpectrum() {
        if (currentSpectrumData == null || currentSpectrumData.isEmpty()) return;

        XYPlot plot = component.getChart().getXYPlot();
        SpectrumColorMode colorMode = config.getColorMode();

        // Determine timeframe from data
        long timeframeMs = 3_600_000L;
        if (currentSpectrumData.size() >= 2) {
            timeframeMs = currentSpectrumData.get(1).windowStart() - currentSpectrumData.get(0).windowStart();
            if (timeframeMs <= 0) timeframeMs = 3_600_000L;
        }

        Map<Long, SizeBucket[]> mergedBuckets = new TreeMap<>();
        for (SpectrumWindow window : currentSpectrumData) {
            long candleTime = (window.windowStart() / timeframeMs) * timeframeMs;
            SizeBucket[] existing = mergedBuckets.get(candleTime);
            if (existing == null) {
                existing = new SizeBucket[SpectrumWindow.BUCKET_COUNT];
                for (int i = 0; i < existing.length; i++) existing[i] = SizeBucket.EMPTY;
                mergedBuckets.put(candleTime, existing);
            }
            for (int i = 0; i < SpectrumWindow.BUCKET_COUNT; i++) {
                existing[i] = existing[i].merge(window.buckets()[i]);
            }
        }

        DefaultXYZDataset dataset = new DefaultXYZDataset();
        int dataSize = mergedBuckets.size() * SpectrumWindow.BUCKET_COUNT;
        double[] xValues = new double[dataSize];
        double[] yValues = new double[dataSize];
        double[] zValues = new double[dataSize];

        boolean isDelta = colorMode == SpectrumColorMode.DELTA;

        switch (colorMode) {
            case RELATIVE -> computeRelativeIntensity(mergedBuckets, xValues, yValues, zValues);
            case PER_BUCKET -> computePerBucketIntensity(mergedBuckets, xValues, yValues, zValues);
            case DELTA -> computeDeltaIntensity(mergedBuckets, xValues, yValues, zValues);
            case Z_SCORE -> computeZScoreIntensity(mergedBuckets, xValues, yValues, zValues);
        }

        dataset.addSeries("Spectrum", new double[][]{xValues, yValues, zValues});
        plot.setDataset(0, dataset);

        XYBlockRenderer blockRenderer = new XYBlockRenderer();
        blockRenderer.setBlockWidth(timeframeMs);
        blockRenderer.setBlockHeight(1.0);
        blockRenderer.setBlockAnchor(RectangleAnchor.BOTTOM_LEFT);

        if (isDelta) {
            LookupPaintScale paintScale = new LookupPaintScale(-1.0, 1.0, new Color(0, 0, 0, 0));
            for (int i = 0; i <= 40; i++) {
                double fraction = -1.0 + 2.0 * i / 40;
                paintScale.add(fraction, deltaColor(fraction));
            }
            blockRenderer.setPaintScale(paintScale);
        } else {
            LookupPaintScale paintScale = new LookupPaintScale(0.0, 1.0, new Color(0, 0, 0, 0));
            for (int i = 0; i <= 20; i++) {
                double fraction = (double) i / 20;
                paintScale.add(fraction, spectrumColor(fraction));
            }
            blockRenderer.setPaintScale(paintScale);
        }

        plot.setRenderer(0, blockRenderer);

        SymbolAxis bucketAxis = new SymbolAxis("", BUCKET_LABELS);
        bucketAxis.setLabelPaint(ChartStyles.textColor());
        bucketAxis.setTickLabelPaint(ChartStyles.textColor());
        bucketAxis.setAxisLineVisible(false);
        bucketAxis.setTickMarksVisible(false);
        bucketAxis.setGridBandsVisible(false);
        bucketAxis.setFixedDimension(60);
        plot.setRangeAxis(0, bucketAxis);

        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Trade Size Spectrum (" + colorMode.getLabel() + ")");
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        // Rendering handled in subscribe() via async callbacks
        return false;
    }

    @Override
    public void dispose() {
        if (dataSource != null && spectrumPage != null && spectrumListener != null) {
            dataSource.release(spectrumPage, spectrumListener);
        }
        spectrumPage = null;
        spectrumListener = null;
        currentSpectrumData = null;
    }

    // ===== Intensity computations =====

    private void computeRelativeIntensity(Map<Long, SizeBucket[]> mergedBuckets, double[] x, double[] y, double[] z) {
        double maxVolume = 0;
        for (SizeBucket[] buckets : mergedBuckets.values()) {
            for (SizeBucket b : buckets) maxVolume = Math.max(maxVolume, b.totalVolume());
        }
        int idx = 0;
        double logMax = Math.log1p(maxVolume);
        for (var entry : mergedBuckets.entrySet()) {
            for (int b = 0; b < SpectrumWindow.BUCKET_COUNT; b++) {
                x[idx] = entry.getKey();
                y[idx] = b;
                double vol = entry.getValue()[b].totalVolume();
                z[idx] = logMax > 0 && vol > 0 ? Math.log1p(vol) / logMax : 0;
                idx++;
            }
        }
    }

    private void computePerBucketIntensity(Map<Long, SizeBucket[]> mergedBuckets, double[] x, double[] y, double[] z) {
        double[] bucketMax = new double[SpectrumWindow.BUCKET_COUNT];
        for (SizeBucket[] buckets : mergedBuckets.values()) {
            for (int b = 0; b < SpectrumWindow.BUCKET_COUNT; b++) {
                bucketMax[b] = Math.max(bucketMax[b], buckets[b].totalVolume());
            }
        }
        int idx = 0;
        for (var entry : mergedBuckets.entrySet()) {
            for (int b = 0; b < SpectrumWindow.BUCKET_COUNT; b++) {
                x[idx] = entry.getKey();
                y[idx] = b;
                double vol = entry.getValue()[b].totalVolume();
                double logMax = Math.log1p(bucketMax[b]);
                z[idx] = logMax > 0 && vol > 0 ? Math.log1p(vol) / logMax : 0;
                idx++;
            }
        }
    }

    private void computeDeltaIntensity(Map<Long, SizeBucket[]> mergedBuckets, double[] x, double[] y, double[] z) {
        double maxAbsDelta = 0;
        for (SizeBucket[] buckets : mergedBuckets.values()) {
            for (SizeBucket b : buckets) {
                maxAbsDelta = Math.max(maxAbsDelta, Math.abs(b.buyVolume() - b.sellVolume()));
            }
        }
        int idx = 0;
        for (var entry : mergedBuckets.entrySet()) {
            for (int b = 0; b < SpectrumWindow.BUCKET_COUNT; b++) {
                x[idx] = entry.getKey();
                y[idx] = b;
                double delta = entry.getValue()[b].buyVolume() - entry.getValue()[b].sellVolume();
                z[idx] = maxAbsDelta > 0 ? delta / maxAbsDelta : 0;
                idx++;
            }
        }
    }

    private void computeZScoreIntensity(Map<Long, SizeBucket[]> mergedBuckets, double[] x, double[] y, double[] z) {
        int n = mergedBuckets.size();
        double[] bucketSum = new double[SpectrumWindow.BUCKET_COUNT];
        double[] bucketSumSq = new double[SpectrumWindow.BUCKET_COUNT];
        for (SizeBucket[] buckets : mergedBuckets.values()) {
            for (int b = 0; b < SpectrumWindow.BUCKET_COUNT; b++) {
                double vol = buckets[b].totalVolume();
                bucketSum[b] += vol;
                bucketSumSq[b] += vol * vol;
            }
        }
        double[] bucketMean = new double[SpectrumWindow.BUCKET_COUNT];
        double[] bucketStd = new double[SpectrumWindow.BUCKET_COUNT];
        for (int b = 0; b < SpectrumWindow.BUCKET_COUNT; b++) {
            bucketMean[b] = n > 0 ? bucketSum[b] / n : 0;
            double variance = n > 1 ? (bucketSumSq[b] / n - bucketMean[b] * bucketMean[b]) : 0;
            bucketStd[b] = Math.sqrt(Math.max(0, variance));
        }
        int idx = 0;
        for (var entry : mergedBuckets.entrySet()) {
            for (int b = 0; b < SpectrumWindow.BUCKET_COUNT; b++) {
                x[idx] = entry.getKey();
                y[idx] = b;
                double vol = entry.getValue()[b].totalVolume();
                if (bucketStd[b] > 0) {
                    double zScore = (vol - bucketMean[b]) / bucketStd[b];
                    z[idx] = Math.max(0, Math.min(1.0, zScore / 3.0));
                } else {
                    z[idx] = 0;
                }
                idx++;
            }
        }
    }

    // ===== Color functions =====

    private static Color spectrumColor(double intensity) {
        if (intensity <= 0) return new Color(0, 0, 0, 0);
        if (intensity <= 0.25) {
            float t = (float) (intensity / 0.25);
            return new Color((int) (20 + t * 10), (int) (20 + t * 60), (int) (30 + t * 195));
        }
        if (intensity <= 0.5) {
            float t = (float) ((intensity - 0.25) / 0.25);
            return new Color((int) (30 + t * 0), (int) (80 + t * 175), (int) (225 - t * 25));
        }
        if (intensity <= 0.75) {
            float t = (float) ((intensity - 0.5) / 0.25);
            return new Color((int) (30 + t * 225), (int) (255 - t * 55), (int) (200 - t * 200));
        }
        float t = (float) Math.min(1.0, (intensity - 0.75) / 0.25);
        return new Color(255, (int) (200 - t * 140), 0);
    }

    private static Color deltaColor(double value) {
        if (Math.abs(value) < 0.01) return new Color(0, 0, 0, 0);
        if (value > 0) {
            float t = (float) Math.min(1.0, value);
            return new Color((int) (20 + t * 10), (int) (20 + t * 200), (int) (30 + t * 50));
        } else {
            float t = (float) Math.min(1.0, -value);
            return new Color((int) (20 + t * 210), (int) (20 + t * 20), (int) (30 + t * 10));
        }
    }
}
