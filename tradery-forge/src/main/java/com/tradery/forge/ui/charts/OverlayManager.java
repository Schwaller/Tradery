package com.tradery.forge.ui.charts;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.overlay.ChartOverlay;
import com.tradery.charts.overlay.RayOverlay;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.chart.renderer.xy.XYDifferenceRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Manages overlay indicators on the price chart (SMA, EMA, Bollinger Bands, High/Low, Mayer Multiple).
 * Supports multiple overlays of the same type (e.g., multiple SMAs with different periods).
 */
public class OverlayManager {

    private final JFreeChart priceChart;

    // Multiple SMA/EMA overlays
    private final List<OverlayInstance> smaOverlays = new ArrayList<>();
    private final List<OverlayInstance> emaOverlays = new ArrayList<>();
    private int colorIndex = 0;  // cycles through OVERLAY_PALETTE

    // Other overlay dataset indices
    private int bbDatasetIndex = -1;
    private int hlDatasetIndex = -1;
    private int dailyPocDatasetIndex = -1;
    private int floatingPocDatasetIndex = -1;

    // Ichimoku Cloud overlay indices
    private int ichimokuLinesDatasetIndex = -1;  // Tenkan, Kijun, Chikou
    private int ichimokuCloudDatasetIndex = -1;   // Senkou Span A & B (for cloud fill)

    // VWAP overlay
    private int vwapDatasetIndex = -1;

    // Mayer Multiple state
    private boolean mayerMultipleEnabled = false;
    private int mayerPeriod = 200;

    // IndicatorEngine for POC calculations
    private IndicatorEngine indicatorEngine;

    // Ray overlay (from tradery-charts)
    private final RayOverlay rayOverlay = new RayOverlay();

    // Daily Volume Profile overlay (background computed)
    private DailyVolumeProfileOverlay dailyVolumeProfileOverlay;

    // Footprint Heatmap overlay
    private com.tradery.forge.ui.charts.footprint.FootprintHeatmapOverlay footprintHeatmapOverlay;

    // Current data context
    private String currentSymbol = "BTCUSDT";
    private String currentTimeframe = "1h";
    private List<Candle> currentCandles;

    // tradery-charts integration
    private ChartDataProvider chartDataProvider;
    private final List<ChartOverlay> appliedChartOverlays = new ArrayList<>();
    private int chartOverlayBaseIndex = 100;  // Base dataset index for chart overlays

    public OverlayManager(JFreeChart priceChart) {
        this.priceChart = priceChart;
        this.dailyVolumeProfileOverlay = new DailyVolumeProfileOverlay(priceChart);
        this.footprintHeatmapOverlay = new com.tradery.forge.ui.charts.footprint.FootprintHeatmapOverlay(priceChart);

        // RayOverlay starts disabled until explicitly enabled
        this.rayOverlay.setEnabled(false);

        // When daily volume profile is drawn, redraw rays to fix annotation list corruption
        this.dailyVolumeProfileOverlay.setOnDataReady(() -> {
            if (rayOverlay.isEnabled()) {
                rayOverlay.redraw();
            }
        });

        // When footprint heatmap is drawn, redraw rays
        this.footprintHeatmapOverlay.setOnDataReady(() -> {
            if (rayOverlay.isEnabled()) {
                rayOverlay.redraw();
            }
        });
    }

    /**
     * Set the data context for overlay computation.
     * Call this when symbol/timeframe changes.
     */
    public void setDataContext(String symbol, String timeframe) {
        this.currentSymbol = symbol;
        this.currentTimeframe = timeframe;
    }

    /**
     * Set candles for overlay computation.
     * Call this when candles are loaded/updated.
     */
    public void setCandles(List<Candle> candles) {
        this.currentCandles = candles;
    }

    // ===== Color Palette =====

    private Color getNextColor() {
        Color color = ChartStyles.OVERLAY_PALETTE[colorIndex % ChartStyles.OVERLAY_PALETTE.length];
        colorIndex++;
        return color;
    }

    /**
     * Reset the color index (call when clearing all overlays).
     */
    public void resetColorIndex() {
        colorIndex = 0;
    }

    // ===== SMA Overlays (Multiple) =====

    /**
     * Add an SMA overlay with the given period. Returns the created overlay instance.
     * Uses IndicatorEngine for calculation (no inline math).
     */
    public OverlayInstance addSmaOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            return null;
        }

        // Check if this period already exists
        for (OverlayInstance existing : smaOverlays) {
            if (existing.period() == period) {
                return existing;  // Already exists
            }
        }

        XYPlot plot = priceChart.getXYPlot();

        // Use IndicatorEngine for SMA calculation (eliminates inline math)
        double[] smaValues;
        if (indicatorEngine != null) {
            smaValues = indicatorEngine.getSMA(period);
        } else {
            // Fallback: calculate inline if no engine (shouldn't happen in normal use)
            smaValues = new double[candles.size()];
            for (int i = period - 1; i < candles.size(); i++) {
                double sum = 0;
                for (int j = 0; j < period; j++) {
                    sum += candles.get(i - j).close();
                }
                smaValues[i] = sum / period;
            }
        }

        TimeSeries smaSeries = new TimeSeries("SMA(" + period + ")");
        for (int i = period - 1; i < candles.size() && i < smaValues.length; i++) {
            if (!Double.isNaN(smaValues[i])) {
                smaSeries.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), smaValues[i]);
            }
        }

        // Add as secondary dataset
        TimeSeriesCollection smaDataset = new TimeSeriesCollection(smaSeries);
        int datasetIndex = findNextAvailableDatasetIndex(plot, 1);
        plot.setDataset(datasetIndex, smaDataset);

        // Style the SMA line with cycling color
        Color color = getNextColor();
        XYLineAndShapeRenderer smaRenderer = new XYLineAndShapeRenderer(true, false);
        smaRenderer.setSeriesPaint(0, color);
        smaRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(datasetIndex, smaRenderer);

        // Track this overlay
        OverlayInstance instance = new OverlayInstance("SMA", period, datasetIndex, color);
        smaOverlays.add(instance);
        return instance;
    }

    /**
     * Remove an SMA overlay by period.
     */
    public void removeSmaOverlay(int period) {
        OverlayInstance toRemove = null;
        for (OverlayInstance overlay : smaOverlays) {
            if (overlay.period() == period) {
                toRemove = overlay;
                break;
            }
        }
        if (toRemove != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(toRemove.datasetIndex(), null);
            smaOverlays.remove(toRemove);
        }
    }

    /**
     * Clear all SMA overlays.
     */
    public void clearAllSmaOverlays() {
        XYPlot plot = priceChart.getXYPlot();
        for (OverlayInstance overlay : smaOverlays) {
            plot.setDataset(overlay.datasetIndex(), null);
        }
        smaOverlays.clear();
    }

    /**
     * Get list of all active SMA overlays.
     */
    public List<OverlayInstance> getSmaOverlays() {
        return new ArrayList<>(smaOverlays);
    }

    /**
     * Legacy method for backward compatibility - clears existing and sets single SMA.
     */
    public void setSmaOverlay(int period, List<Candle> candles) {
        clearAllSmaOverlays();
        if (candles != null && candles.size() >= period) {
            addSmaOverlay(period, candles);
        }
    }

    /**
     * Legacy clear method - clears all SMA overlays.
     */
    public void clearSmaOverlay() {
        clearAllSmaOverlays();
    }

    public boolean isSmaEnabled() {
        return !smaOverlays.isEmpty();
    }

    // ===== EMA Overlays (Multiple) =====

    /**
     * Add an EMA overlay with the given period. Returns the created overlay instance.
     * Uses IndicatorEngine for calculation (no inline math).
     */
    public OverlayInstance addEmaOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            return null;
        }

        // Check if this period already exists
        for (OverlayInstance existing : emaOverlays) {
            if (existing.period() == period) {
                return existing;  // Already exists
            }
        }

        XYPlot plot = priceChart.getXYPlot();

        // Use IndicatorEngine for EMA calculation (eliminates inline math)
        double[] emaValues;
        if (indicatorEngine != null) {
            emaValues = indicatorEngine.getEMA(period);
        } else {
            // Fallback: calculate inline if no engine (shouldn't happen in normal use)
            emaValues = new double[candles.size()];
            double multiplier = 2.0 / (period + 1);
            double ema = 0;
            for (int i = 0; i < candles.size(); i++) {
                if (i < period - 1) {
                    emaValues[i] = Double.NaN;
                } else if (i == period - 1) {
                    double sum = 0;
                    for (int j = 0; j < period; j++) {
                        sum += candles.get(j).close();
                    }
                    ema = sum / period;
                    emaValues[i] = ema;
                } else {
                    ema = (candles.get(i).close() - ema) * multiplier + ema;
                    emaValues[i] = ema;
                }
            }
        }

        TimeSeries emaSeries = new TimeSeries("EMA(" + period + ")");
        for (int i = period - 1; i < candles.size() && i < emaValues.length; i++) {
            if (!Double.isNaN(emaValues[i])) {
                emaSeries.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), emaValues[i]);
            }
        }

        // Add as secondary dataset
        TimeSeriesCollection emaDataset = new TimeSeriesCollection(emaSeries);
        int datasetIndex = findNextAvailableDatasetIndex(plot, 1);
        plot.setDataset(datasetIndex, emaDataset);

        // Style the EMA line with cycling color
        Color color = getNextColor();
        XYLineAndShapeRenderer emaRenderer = new XYLineAndShapeRenderer(true, false);
        emaRenderer.setSeriesPaint(0, color);
        emaRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(datasetIndex, emaRenderer);

        // Track this overlay
        OverlayInstance instance = new OverlayInstance("EMA", period, datasetIndex, color);
        emaOverlays.add(instance);
        return instance;
    }

    /**
     * Remove an EMA overlay by period.
     */
    public void removeEmaOverlay(int period) {
        OverlayInstance toRemove = null;
        for (OverlayInstance overlay : emaOverlays) {
            if (overlay.period() == period) {
                toRemove = overlay;
                break;
            }
        }
        if (toRemove != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(toRemove.datasetIndex(), null);
            emaOverlays.remove(toRemove);
        }
    }

    /**
     * Clear all EMA overlays.
     */
    public void clearAllEmaOverlays() {
        XYPlot plot = priceChart.getXYPlot();
        for (OverlayInstance overlay : emaOverlays) {
            plot.setDataset(overlay.datasetIndex(), null);
        }
        emaOverlays.clear();
    }

    /**
     * Get list of all active EMA overlays.
     */
    public List<OverlayInstance> getEmaOverlays() {
        return new ArrayList<>(emaOverlays);
    }

    /**
     * Legacy method for backward compatibility - clears existing and sets single EMA.
     */
    public void setEmaOverlay(int period, List<Candle> candles) {
        clearAllEmaOverlays();
        if (candles != null && candles.size() >= period) {
            addEmaOverlay(period, candles);
        }
    }

    /**
     * Legacy clear method - clears all EMA overlays.
     */
    public void clearEmaOverlay() {
        clearAllEmaOverlays();
    }

    public boolean isEmaEnabled() {
        return !emaOverlays.isEmpty();
    }

    // ===== Bollinger Bands Overlay =====

    /**
     * Set Bollinger Bands overlay.
     * Uses IndicatorEngine for calculation (no inline math).
     */
    public void setBollingerOverlay(int period, double stdDevMultiplier, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearBollingerOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        TimeSeries upperSeries = new TimeSeries("BB Upper");
        TimeSeries middleSeries = new TimeSeries("BB Middle");
        TimeSeries lowerSeries = new TimeSeries("BB Lower");

        // Use IndicatorEngine for Bollinger calculation (eliminates inline math)
        if (indicatorEngine != null) {
            com.tradery.core.indicators.Indicators.BollingerResult bb =
                indicatorEngine.getBollingerBands(period, stdDevMultiplier);

            double[] upper = bb.upper();
            double[] middle = bb.middle();
            double[] lower = bb.lower();

            for (int i = period - 1; i < candles.size(); i++) {
                if (i < upper.length && !Double.isNaN(upper[i])) {
                    Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));
                    upperSeries.addOrUpdate(time, upper[i]);
                    middleSeries.addOrUpdate(time, middle[i]);
                    lowerSeries.addOrUpdate(time, lower[i]);
                }
            }
        } else {
            // Fallback: calculate inline if no engine (shouldn't happen in normal use)
            for (int i = period - 1; i < candles.size(); i++) {
                double sum = 0;
                for (int j = 0; j < period; j++) {
                    sum += candles.get(i - j).close();
                }
                double sma = sum / period;

                double sumSquaredDiff = 0;
                for (int j = 0; j < period; j++) {
                    double diff = candles.get(i - j).close() - sma;
                    sumSquaredDiff += diff * diff;
                }
                double stdDev = Math.sqrt(sumSquaredDiff / period);

                double upper = sma + (stdDevMultiplier * stdDev);
                double lower = sma - (stdDevMultiplier * stdDev);

                Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));
                upperSeries.addOrUpdate(time, upper);
                middleSeries.addOrUpdate(time, sma);
                lowerSeries.addOrUpdate(time, lower);
            }
        }

        TimeSeriesCollection bbDataset = new TimeSeriesCollection();
        bbDataset.addSeries(upperSeries);
        bbDataset.addSeries(middleSeries);
        bbDataset.addSeries(lowerSeries);

        if (bbDatasetIndex < 0) {
            bbDatasetIndex = findNextAvailableDatasetIndex(plot, 1);
        }

        plot.setDataset(bbDatasetIndex, bbDataset);

        // Style: purple bands with semi-transparent fill effect
        XYLineAndShapeRenderer bbRenderer = new XYLineAndShapeRenderer(true, false);
        bbRenderer.setSeriesPaint(0, ChartStyles.BB_COLOR);
        bbRenderer.setSeriesPaint(1, ChartStyles.BB_MIDDLE_COLOR);
        bbRenderer.setSeriesPaint(2, ChartStyles.BB_COLOR);
        bbRenderer.setSeriesStroke(0, ChartStyles.THIN_STROKE);
        bbRenderer.setSeriesStroke(1, new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[]{4.0f}, 0.0f)); // Dashed middle
        bbRenderer.setSeriesStroke(2, ChartStyles.THIN_STROKE);
        plot.setRenderer(bbDatasetIndex, bbRenderer);
    }

    public void clearBollingerOverlay() {
        if (bbDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(bbDatasetIndex, null);
            bbDatasetIndex = -1;
        }
    }

    public boolean isBollingerEnabled() {
        return bbDatasetIndex >= 0 && priceChart.getXYPlot().getDataset(bbDatasetIndex) != null;
    }

    // ===== High/Low Overlay =====

    /**
     * Set High/Low overlay showing the highest high and lowest low over a period.
     * Uses IndicatorEngine for calculation (no inline math).
     */
    public void setHighLowOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearHighLowOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        TimeSeries highSeries = new TimeSeries("High(" + period + ")");
        TimeSeries lowSeries = new TimeSeries("Low(" + period + ")");

        // Use IndicatorEngine for High/Low calculation (eliminates inline math)
        if (indicatorEngine != null) {
            double[] highValues = indicatorEngine.getHighOf(period);
            double[] lowValues = indicatorEngine.getLowOf(period);

            for (int i = period - 1; i < candles.size(); i++) {
                if (i < highValues.length && !Double.isNaN(highValues[i])) {
                    Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));
                    highSeries.addOrUpdate(time, highValues[i]);
                    lowSeries.addOrUpdate(time, lowValues[i]);
                }
            }
        } else {
            // Fallback: calculate inline if no engine (shouldn't happen in normal use)
            for (int i = period - 1; i < candles.size(); i++) {
                double high = Double.MIN_VALUE;
                double low = Double.MAX_VALUE;

                for (int j = 0; j < period; j++) {
                    Candle c = candles.get(i - j);
                    high = Math.max(high, c.high());
                    low = Math.min(low, c.low());
                }

                Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));
                highSeries.addOrUpdate(time, high);
                lowSeries.addOrUpdate(time, low);
            }
        }

        TimeSeriesCollection hlDataset = new TimeSeriesCollection();
        hlDataset.addSeries(highSeries);
        hlDataset.addSeries(lowSeries);

        if (hlDatasetIndex < 0) {
            hlDatasetIndex = findNextAvailableDatasetIndex(plot, 1);
        }

        plot.setDataset(hlDatasetIndex, hlDataset);

        // Style: blueish cloud fill between high and low lines
        XYDifferenceRenderer hlRenderer = new XYDifferenceRenderer(
            ChartStyles.HL_CLOUD_COLOR,  // Fill when high > low (always)
            ChartStyles.HL_CLOUD_COLOR,  // Fill when low > high (shouldn't happen)
            false                         // No shapes on data points
        );
        hlRenderer.setSeriesPaint(0, new Color(0, 0, 0, 0));  // Invisible lines
        hlRenderer.setSeriesPaint(1, new Color(0, 0, 0, 0));
        plot.setRenderer(hlDatasetIndex, hlRenderer);
    }

    public void clearHighLowOverlay() {
        if (hlDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(hlDatasetIndex, null);
            hlDatasetIndex = -1;
        }
    }

    public boolean isHighLowEnabled() {
        return hlDatasetIndex >= 0 && priceChart.getXYPlot().getDataset(hlDatasetIndex) != null;
    }

    // ===== Mayer Multiple =====

    public void setMayerMultipleEnabled(boolean enabled, int period) {
        this.mayerMultipleEnabled = enabled;
        this.mayerPeriod = period;
    }

    public boolean isMayerMultipleEnabled() {
        return mayerMultipleEnabled;
    }

    public int getMayerPeriod() {
        return mayerPeriod;
    }

    /**
     * Add Mayer Multiple threshold lines to the price chart.
     * Should be called during price chart update when Mayer is enabled.
     */
    public void addMayerMultipleAnnotations(XYPlot plot, List<Candle> candles) {
        if (!mayerMultipleEnabled || candles.size() < mayerPeriod) {
            return;
        }

        // Calculate SMA for Mayer Multiple
        double[] sma = new double[candles.size()];
        for (int i = mayerPeriod - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = 0; j < mayerPeriod; j++) {
                sum += candles.get(i - j).close();
            }
            sma[i] = sum / mayerPeriod;
        }

        // Draw threshold lines at Mayer levels
        double[] thresholds = {0.95, 0.98, 1.0, 1.02, 1.05};
        java.awt.Color[] colors = {
            ChartStyles.MAYER_DEEP_UNDERVALUED,
            ChartStyles.MAYER_UNDERVALUED,
            ChartStyles.MAYER_NEUTRAL,
            ChartStyles.MAYER_OVERVALUED,
            ChartStyles.MAYER_DEEP_OVERVALUED
        };

        for (int t = 0; t < thresholds.length; t++) {
            double mult = thresholds[t];
            java.awt.Color color = colors[t];

            for (int i = mayerPeriod; i < candles.size(); i++) {
                Candle prev = candles.get(i - 1);
                Candle curr = candles.get(i);

                double prevPrice = sma[i - 1] * mult;
                double currPrice = sma[i] * mult;

                XYLineAnnotation segment = new XYLineAnnotation(
                    prev.timestamp(), prevPrice,
                    curr.timestamp(), currPrice,
                    ChartStyles.DASHED_MAYER_STROKE, color
                );
                plot.addAnnotation(segment);
            }
        }
    }

    // ===== IndicatorEngine Setter =====

    public void setIndicatorEngine(IndicatorEngine engine) {
        this.indicatorEngine = engine;
    }

    public IndicatorEngine getIndicatorEngine() {
        return this.indicatorEngine;
    }

    // ===== Daily POC/VAH/VAL Overlay =====

    /**
     * Shows previous day's POC, VAH, and VAL as horizontal lines extending across the current day.
     */
    public void setDailyPocOverlay(List<Candle> candles) {
        if (candles == null || candles.isEmpty() || indicatorEngine == null) {
            clearDailyPocOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        TimeSeries pocSeries = new TimeSeries("Daily POC");
        TimeSeries vahSeries = new TimeSeries("Daily VAH");
        TimeSeries valSeries = new TimeSeries("Daily VAL");

        for (int i = 0; i < candles.size(); i++) {
            Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));

            double poc = indicatorEngine.getPrevDayPOCAt(i);
            if (!Double.isNaN(poc)) {
                pocSeries.addOrUpdate(time, poc);
            }

            double vah = indicatorEngine.getPrevDayVAHAt(i);
            if (!Double.isNaN(vah)) {
                vahSeries.addOrUpdate(time, vah);
            }

            double val = indicatorEngine.getPrevDayVALAt(i);
            if (!Double.isNaN(val)) {
                valSeries.addOrUpdate(time, val);
            }
        }

        if (pocSeries.isEmpty() && vahSeries.isEmpty() && valSeries.isEmpty()) {
            clearDailyPocOverlay();
            return;
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(pocSeries);
        dataset.addSeries(vahSeries);
        dataset.addSeries(valSeries);

        if (dailyPocDatasetIndex < 0) {
            dailyPocDatasetIndex = findNextAvailableDatasetIndex(plot, 10);
        }

        plot.setDataset(dailyPocDatasetIndex, dataset);

        // Style: cyan dashed lines - POC solid, VAH/VAL lighter
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.DAILY_POC_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.DASHED_STROKE);
        renderer.setSeriesPaint(1, ChartStyles.DAILY_VAH_COLOR);
        renderer.setSeriesStroke(1, ChartStyles.DASHED_STROKE);
        renderer.setSeriesPaint(2, ChartStyles.DAILY_VAL_COLOR);
        renderer.setSeriesStroke(2, ChartStyles.DASHED_STROKE);
        plot.setRenderer(dailyPocDatasetIndex, renderer);
    }

    public void clearDailyPocOverlay() {
        if (dailyPocDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(dailyPocDatasetIndex, null);
        }
    }

    public boolean isDailyPocEnabled() {
        return dailyPocDatasetIndex >= 0 && priceChart.getXYPlot().getDataset(dailyPocDatasetIndex) != null;
    }

    // ===== Floating POC/VAH/VAL Overlay =====

    /**
     * Shows the developing (floating) POC, VAH, and VAL.
     * @param period 0 = today's session (updates throughout the day),
     *               >0 = rolling N-bar lookback
     */
    public void setFloatingPocOverlay(List<Candle> candles, int period) {
        if (candles == null || candles.isEmpty() || indicatorEngine == null) {
            clearFloatingPocOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        String label = period > 0 ? "Rolling(" + period + ")" : "Floating";
        TimeSeries pocSeries = new TimeSeries(label + " POC");
        TimeSeries vahSeries = new TimeSeries(label + " VAH");
        TimeSeries valSeries = new TimeSeries(label + " VAL");

        for (int i = 0; i < candles.size(); i++) {
            Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));

            double poc, vah, val;
            if (period > 0) {
                // Rolling N-bar lookback
                poc = indicatorEngine.getPOCAt(period, i);
                vah = indicatorEngine.getVAHAt(period, i);
                val = indicatorEngine.getVALAt(period, i);
            } else {
                // Today's session (default)
                poc = indicatorEngine.getTodayPOCAt(i);
                vah = indicatorEngine.getTodayVAHAt(i);
                val = indicatorEngine.getTodayVALAt(i);
            }

            if (!Double.isNaN(poc)) {
                pocSeries.addOrUpdate(time, poc);
            }
            if (!Double.isNaN(vah)) {
                vahSeries.addOrUpdate(time, vah);
            }
            if (!Double.isNaN(val)) {
                valSeries.addOrUpdate(time, val);
            }
        }

        if (pocSeries.isEmpty() && vahSeries.isEmpty() && valSeries.isEmpty()) {
            clearFloatingPocOverlay();
            return;
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(pocSeries);
        dataset.addSeries(vahSeries);
        dataset.addSeries(valSeries);

        if (floatingPocDatasetIndex < 0) {
            floatingPocDatasetIndex = findNextAvailableDatasetIndex(plot, 11);
        }

        plot.setDataset(floatingPocDatasetIndex, dataset);

        // Style: magenta lines - POC solid, VAH/VAL lighter
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.FLOATING_POC_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        renderer.setSeriesPaint(1, ChartStyles.FLOATING_VAH_COLOR);
        renderer.setSeriesStroke(1, ChartStyles.MEDIUM_STROKE);
        renderer.setSeriesPaint(2, ChartStyles.FLOATING_VAL_COLOR);
        renderer.setSeriesStroke(2, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(floatingPocDatasetIndex, renderer);
    }

    public void clearFloatingPocOverlay() {
        if (floatingPocDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(floatingPocDatasetIndex, null);
        }
    }

    public boolean isFloatingPocEnabled() {
        return floatingPocDatasetIndex >= 0 && priceChart.getXYPlot().getDataset(floatingPocDatasetIndex) != null;
    }

    // ===== Ray Overlay =====

    /**
     * Set ray overlay enabled state and update with current candles.
     * Requires chartDataProvider to be set (for IndicatorPool access).
     */
    public void setRayOverlay(boolean enabled, int lookback, int skip, List<Candle> candles) {
        rayOverlay.setEnabled(enabled);
        rayOverlay.setLookback(lookback);
        rayOverlay.setSkip(skip);
        if (enabled && chartDataProvider != null && chartDataProvider.hasCandles()) {
            rayOverlay.apply(priceChart.getXYPlot(), chartDataProvider, 0);
        } else {
            rayOverlay.clear();
        }
    }

    /**
     * Update ray overlay with new candle data (call when candles change).
     */
    public void updateRayOverlay(List<Candle> candles) {
        if (rayOverlay.isEnabled() && chartDataProvider != null && chartDataProvider.hasCandles()) {
            rayOverlay.redraw();
        }
    }

    public void clearRayOverlay() {
        rayOverlay.setEnabled(false);
        rayOverlay.clear();
    }

    public boolean isRayOverlayEnabled() {
        return rayOverlay.isEnabled();
    }

    public int getRayLookback() {
        return rayOverlay.getLookback();
    }

    public int getRaySkip() {
        return rayOverlay.getSkip();
    }

    public void setRayShowResistance(boolean show) {
        rayOverlay.setShowResistance(show);
    }

    public boolean isRayShowResistance() {
        return rayOverlay.isShowResistance();
    }

    public void setRayShowSupport(boolean show) {
        rayOverlay.setShowSupport(show);
    }

    public boolean isRayShowSupport() {
        return rayOverlay.isShowSupport();
    }

    public void setRayShowHistoric(boolean show) {
        rayOverlay.setShowHistoricRays(show);
    }

    public boolean isRayShowHistoric() {
        return rayOverlay.isShowHistoricRays();
    }

    public void setRayHistoricInterval(int interval) {
        rayOverlay.setHistoricRayInterval(interval);
    }

    public int getRayHistoricInterval() {
        return rayOverlay.getHistoricRayInterval();
    }

    // ===== Ichimoku Cloud Overlay =====

    /**
     * Sets the Ichimoku Cloud overlay with all 5 components.
     * Default parameters: conversionPeriod=9, basePeriod=26, spanBPeriod=52, displacement=26
     */
    public void setIchimokuOverlay(List<Candle> candles) {
        setIchimokuOverlay(9, 26, 52, 26, candles);
    }

    /**
     * Sets the Ichimoku Cloud overlay with custom parameters.
     * Uses IndicatorEngine for calculation (no inline math).
     */
    public void setIchimokuOverlay(int conversionPeriod, int basePeriod, int spanBPeriod,
                                    int displacement, List<Candle> candles) {
        if (candles == null || candles.size() < Math.max(spanBPeriod, basePeriod) + displacement) {
            clearIchimokuOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        TimeSeries tenkanSeries = new TimeSeries("Tenkan-sen");
        TimeSeries kijunSeries = new TimeSeries("Kijun-sen");
        TimeSeries chikouSeries = new TimeSeries("Chikou Span");
        TimeSeries senkouASeries = new TimeSeries("Senkou Span A");
        TimeSeries senkouBSeries = new TimeSeries("Senkou Span B");

        // Use IndicatorEngine for Ichimoku calculation (eliminates inline math)
        if (indicatorEngine != null) {
            com.tradery.core.indicators.Indicators.IchimokuResult ichimoku =
                indicatorEngine.getIchimoku(conversionPeriod, basePeriod, spanBPeriod, displacement);

            double[] tenkan = ichimoku.tenkanSen();
            double[] kijun = ichimoku.kijunSen();
            double[] chikou = ichimoku.chikouSpan();
            double[] senkouA = ichimoku.senkouSpanA();
            double[] senkouB = ichimoku.senkouSpanB();

            for (int i = 0; i < candles.size(); i++) {
                Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));

                if (i < tenkan.length && !Double.isNaN(tenkan[i])) {
                    tenkanSeries.addOrUpdate(time, tenkan[i]);
                }
                if (i < kijun.length && !Double.isNaN(kijun[i])) {
                    kijunSeries.addOrUpdate(time, kijun[i]);
                }
                if (i < chikou.length && !Double.isNaN(chikou[i])) {
                    chikouSeries.addOrUpdate(time, chikou[i]);
                }
                if (i < senkouA.length && !Double.isNaN(senkouA[i])) {
                    senkouASeries.addOrUpdate(time, senkouA[i]);
                }
                if (i < senkouB.length && !Double.isNaN(senkouB[i])) {
                    senkouBSeries.addOrUpdate(time, senkouB[i]);
                }
            }
        } else {
            // Fallback: calculate inline if no engine (shouldn't happen in normal use)
            for (int i = 0; i < candles.size(); i++) {
                Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));

                // Tenkan-sen (Conversion Line)
                if (i >= conversionPeriod - 1) {
                    double high = Double.MIN_VALUE;
                    double low = Double.MAX_VALUE;
                    for (int j = i - conversionPeriod + 1; j <= i; j++) {
                        high = Math.max(high, candles.get(j).high());
                        low = Math.min(low, candles.get(j).low());
                    }
                    tenkanSeries.addOrUpdate(time, (high + low) / 2.0);
                }

                // Kijun-sen (Base Line)
                if (i >= basePeriod - 1) {
                    double high = Double.MIN_VALUE;
                    double low = Double.MAX_VALUE;
                    for (int j = i - basePeriod + 1; j <= i; j++) {
                        high = Math.max(high, candles.get(j).high());
                        low = Math.min(low, candles.get(j).low());
                    }
                    kijunSeries.addOrUpdate(time, (high + low) / 2.0);
                }

                // Chikou Span (Lagging Span)
                if (i + displacement < candles.size()) {
                    double chikouValue = candles.get(i + displacement).close();
                    chikouSeries.addOrUpdate(time, chikouValue);
                }
            }

            // Senkou Spans (shifted forward by displacement)
            for (int i = 0; i < candles.size(); i++) {
                Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));
                int sourceIndex = i - displacement;

                if (sourceIndex >= 0) {
                    if (sourceIndex >= Math.max(conversionPeriod, basePeriod) - 1) {
                        double tenkan = calculateMidpoint(candles, conversionPeriod, sourceIndex);
                        double kijun = calculateMidpoint(candles, basePeriod, sourceIndex);
                        senkouASeries.addOrUpdate(time, (tenkan + kijun) / 2.0);
                    }
                    if (sourceIndex >= spanBPeriod - 1) {
                        double spanB = calculateMidpoint(candles, spanBPeriod, sourceIndex);
                        senkouBSeries.addOrUpdate(time, spanB);
                    }
                }
            }
        }

        // Create datasets and renderers
        // Dataset 1: Lines (Tenkan, Kijun, Chikou)
        TimeSeriesCollection linesDataset = new TimeSeriesCollection();
        linesDataset.addSeries(tenkanSeries);
        linesDataset.addSeries(kijunSeries);
        linesDataset.addSeries(chikouSeries);

        if (ichimokuLinesDatasetIndex < 0) {
            ichimokuLinesDatasetIndex = findNextAvailableDatasetIndex(plot, 1);
        }
        plot.setDataset(ichimokuLinesDatasetIndex, linesDataset);

        XYLineAndShapeRenderer linesRenderer = new XYLineAndShapeRenderer(true, false);
        linesRenderer.setSeriesPaint(0, ChartStyles.ICHIMOKU_TENKAN_COLOR);
        linesRenderer.setSeriesPaint(1, ChartStyles.ICHIMOKU_KIJUN_COLOR);
        linesRenderer.setSeriesPaint(2, ChartStyles.ICHIMOKU_CHIKOU_COLOR);
        linesRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        linesRenderer.setSeriesStroke(1, ChartStyles.MEDIUM_STROKE);
        linesRenderer.setSeriesStroke(2, ChartStyles.THIN_STROKE);
        plot.setRenderer(ichimokuLinesDatasetIndex, linesRenderer);

        // Dataset 2: Cloud (Senkou Span A & B with fill)
        TimeSeriesCollection cloudDataset = new TimeSeriesCollection();
        cloudDataset.addSeries(senkouASeries);
        cloudDataset.addSeries(senkouBSeries);

        if (ichimokuCloudDatasetIndex < 0) {
            ichimokuCloudDatasetIndex = findNextAvailableDatasetIndex(plot, ichimokuLinesDatasetIndex + 1);
        }
        plot.setDataset(ichimokuCloudDatasetIndex, cloudDataset);

        // Use XYDifferenceRenderer for cloud fill between Span A and Span B
        XYDifferenceRenderer cloudRenderer = new XYDifferenceRenderer(
            ChartStyles.ICHIMOKU_CLOUD_BULLISH,  // Fill when series 0 > series 1 (Span A > Span B = bullish)
            ChartStyles.ICHIMOKU_CLOUD_BEARISH,  // Fill when series 1 > series 0 (Span B > Span A = bearish)
            false  // Don't show shapes
        );
        cloudRenderer.setSeriesPaint(0, ChartStyles.ICHIMOKU_SPAN_A_COLOR);
        cloudRenderer.setSeriesPaint(1, ChartStyles.ICHIMOKU_SPAN_B_COLOR);
        cloudRenderer.setSeriesStroke(0, ChartStyles.THIN_STROKE);
        cloudRenderer.setSeriesStroke(1, ChartStyles.THIN_STROKE);
        plot.setRenderer(ichimokuCloudDatasetIndex, cloudRenderer);
    }

    /**
     * Helper method to calculate midpoint (high + low) / 2 for a given period ending at barIndex.
     * Used as fallback when IndicatorEngine is not available.
     */
    private double calculateMidpoint(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) return Double.NaN;
        double high = Double.MIN_VALUE;
        double low = Double.MAX_VALUE;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            high = Math.max(high, candles.get(j).high());
            low = Math.min(low, candles.get(j).low());
        }
        return (high + low) / 2.0;
    }

    public void clearIchimokuOverlay() {
        XYPlot plot = priceChart.getXYPlot();
        if (ichimokuLinesDatasetIndex >= 0) {
            plot.setDataset(ichimokuLinesDatasetIndex, null);
            ichimokuLinesDatasetIndex = -1;
        }
        if (ichimokuCloudDatasetIndex >= 0) {
            plot.setDataset(ichimokuCloudDatasetIndex, null);
            ichimokuCloudDatasetIndex = -1;
        }
    }

    public boolean isIchimokuEnabled() {
        return ichimokuLinesDatasetIndex >= 0 && priceChart.getXYPlot().getDataset(ichimokuLinesDatasetIndex) != null;
    }

    // ===== VWAP Overlay =====

    /**
     * Sets the VWAP (Volume Weighted Average Price) overlay on the price chart.
     * VWAP is calculated using IndicatorEngine and resets at UTC day boundaries.
     */
    public void setVwapOverlay(List<Candle> candles) {
        if (candles == null || candles.isEmpty() || indicatorEngine == null) {
            clearVwapOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        TimeSeries vwapSeries = new TimeSeries("VWAP");

        for (int i = 0; i < candles.size(); i++) {
            double vwap = indicatorEngine.getVWAPAt(i);
            if (!Double.isNaN(vwap) && vwap > 0) {
                vwapSeries.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), vwap);
            }
        }

        if (vwapSeries.isEmpty()) {
            clearVwapOverlay();
            return;
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(vwapSeries);

        if (vwapDatasetIndex < 0) {
            vwapDatasetIndex = findNextAvailableDatasetIndex(plot, 12);
        }

        plot.setDataset(vwapDatasetIndex, dataset);

        // Style: yellow solid line for VWAP
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.VWAP_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(vwapDatasetIndex, renderer);
    }

    public void clearVwapOverlay() {
        if (vwapDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(vwapDatasetIndex, null);
            vwapDatasetIndex = -1;
        }
    }

    public boolean isVwapEnabled() {
        return vwapDatasetIndex >= 0 && priceChart.getXYPlot().getDataset(vwapDatasetIndex) != null;
    }

    // ===== Daily Volume Profile Overlay (Background Computed) =====

    /**
     * Sets the daily volume profile overlay showing volume distribution histograms
     * on the left side of each day's bounds. Computation happens in background thread.
     *
     * @param candles        List of candles (for time range)
     * @param numBins        Number of price bins per day (default 24)
     * @param valueAreaPct   Value area percentage (typically 70%)
     * @param histogramWidth Max width in pixels for histogram bars
     */
    public void setDailyVolumeProfileOverlay(List<Candle> candles, int numBins, double valueAreaPct, int histogramWidth) {
        if (candles == null || candles.isEmpty()) {
            clearDailyVolumeProfileOverlay();
            return;
        }

        // Configure and enable the overlay
        dailyVolumeProfileOverlay.setNumBins(numBins);
        dailyVolumeProfileOverlay.setValueAreaPct(valueAreaPct);
        dailyVolumeProfileOverlay.setHistogramWidth(histogramWidth);
        dailyVolumeProfileOverlay.setEnabled(true);

        // Request background computation
        dailyVolumeProfileOverlay.requestData(
            candles, currentSymbol, currentTimeframe,
            candles.get(0).timestamp(),
            candles.get(candles.size() - 1).timestamp());
    }

    /**
     * Sets the daily volume profile overlay with default parameters.
     * Default: 24 bins, 70% value area, 60px histogram width.
     */
    public void setDailyVolumeProfileOverlay(List<Candle> candles) {
        setDailyVolumeProfileOverlay(candles, 24, 70.0, 60);
    }

    /**
     * Clears the daily volume profile overlay from the chart.
     */
    public void clearDailyVolumeProfileOverlay() {
        dailyVolumeProfileOverlay.setEnabled(false);
    }

    /**
     * Get the daily volume profile overlay for direct access.
     */
    public DailyVolumeProfileOverlay getDailyVolumeProfileOverlay() {
        return dailyVolumeProfileOverlay;
    }

    /**
     * Checks if the daily volume profile overlay is currently enabled.
     */
    public boolean isDailyVolumeProfileEnabled() {
        return dailyVolumeProfileOverlay.isEnabled();
    }

    // ===== Footprint Heatmap Overlay =====

    /**
     * Request footprint heatmap data and render when ready.
     * Uses IndicatorPageManager for background computation.
     */
    public void updateFootprintHeatmapOverlay() {
        com.tradery.forge.ui.charts.ChartConfig config = com.tradery.forge.ui.charts.ChartConfig.getInstance();

        if (!config.isFootprintHeatmapEnabled()) {
            clearFootprintHeatmapOverlay();
            return;
        }

        if (currentCandles == null || currentCandles.isEmpty()) {
            return;
        }

        // Enable overlay and apply config - overlay manages its own IndicatorPage
        footprintHeatmapOverlay.setEnabled(true);
        footprintHeatmapOverlay.setConfig(config.getFootprintHeatmapConfig());
        footprintHeatmapOverlay.requestData(
            currentCandles,
            currentSymbol,
            currentTimeframe,
            currentCandles.get(0).timestamp(),
            currentCandles.get(currentCandles.size() - 1).timestamp()
        );
    }

    /**
     * Clear the footprint heatmap overlay.
     */
    public void clearFootprintHeatmapOverlay() {
        footprintHeatmapOverlay.setEnabled(false);
    }

    /**
     * Get the footprint heatmap overlay for direct access.
     */
    public com.tradery.forge.ui.charts.footprint.FootprintHeatmapOverlay getFootprintHeatmapOverlay() {
        return footprintHeatmapOverlay;
    }

    /**
     * Check if footprint heatmap overlay is enabled.
     */
    public boolean isFootprintHeatmapEnabled() {
        return footprintHeatmapOverlay.isEnabled();
    }

    // ===== tradery-charts Integration =====

    /**
     * Set the ChartDataProvider for tradery-charts overlays.
     * Call this when the data context changes.
     */
    public void setChartDataProvider(ChartDataProvider provider) {
        this.chartDataProvider = provider;
    }

    /**
     * Apply a tradery-charts ChartOverlay to the price chart.
     * The overlay is tracked and can be cleared with clearChartOverlays().
     *
     * @param overlay The ChartOverlay to apply
     * @return true if applied successfully
     */
    public boolean applyChartOverlay(ChartOverlay overlay) {
        if (chartDataProvider == null || !chartDataProvider.hasCandles()) {
            return false;
        }

        XYPlot plot = priceChart.getXYPlot();

        // Find next available dataset index
        int datasetIndex = chartOverlayBaseIndex;
        for (ChartOverlay existing : appliedChartOverlays) {
            datasetIndex += existing.getDatasetCount();
        }

        // Apply the overlay
        overlay.apply(plot, chartDataProvider, datasetIndex);
        appliedChartOverlays.add(overlay);

        return true;
    }

    /**
     * Remove a specific ChartOverlay from the price chart.
     *
     * @param overlay The overlay to remove
     * @return true if removed successfully
     */
    public boolean removeChartOverlay(ChartOverlay overlay) {
        if (!appliedChartOverlays.contains(overlay)) {
            return false;
        }

        // Remove all chart overlays and re-apply the remaining ones
        clearChartOverlays();

        appliedChartOverlays.remove(overlay);

        // Re-apply remaining overlays
        List<ChartOverlay> remaining = new ArrayList<>(appliedChartOverlays);
        appliedChartOverlays.clear();

        for (ChartOverlay remaining_overlay : remaining) {
            applyChartOverlay(remaining_overlay);
        }

        return true;
    }

    /**
     * Clear all applied tradery-charts overlays.
     */
    public void clearChartOverlays() {
        if (appliedChartOverlays.isEmpty()) {
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        // Clear all datasets used by chart overlays
        int datasetIndex = chartOverlayBaseIndex;
        for (ChartOverlay overlay : appliedChartOverlays) {
            int count = overlay.getDatasetCount();
            for (int i = 0; i < count; i++) {
                plot.setDataset(datasetIndex + i, null);
                plot.setRenderer(datasetIndex + i, null);
            }
            datasetIndex += count;
        }

        appliedChartOverlays.clear();
    }

    /**
     * Refresh all applied tradery-charts overlays.
     * Call this when the data changes.
     */
    public void refreshChartOverlays() {
        if (appliedChartOverlays.isEmpty() || chartDataProvider == null) {
            return;
        }

        // Store current overlays
        List<ChartOverlay> current = new ArrayList<>(appliedChartOverlays);

        // Clear and re-apply
        clearChartOverlays();
        for (ChartOverlay overlay : current) {
            applyChartOverlay(overlay);
        }
    }

    /**
     * Get the list of currently applied ChartOverlays.
     */
    public List<ChartOverlay> getAppliedChartOverlays() {
        return new ArrayList<>(appliedChartOverlays);
    }

    // ===== Clear All =====

    public void clearAll() {
        clearAllSmaOverlays();
        clearAllEmaOverlays();
        clearBollingerOverlay();
        clearHighLowOverlay();
        clearDailyPocOverlay();
        clearFloatingPocOverlay();
        clearRayOverlay();
        clearIchimokuOverlay();
        clearVwapOverlay();
        clearDailyVolumeProfileOverlay();
        clearFootprintHeatmapOverlay();
        clearChartOverlays();  // Clear tradery-charts overlays
        resetColorIndex();
    }

    // ===== Helper Methods =====

    private int findNextAvailableDatasetIndex(XYPlot plot, int startIndex) {
        int index = startIndex;
        while (plot.getDataset(index) != null) {
            index++;
        }
        return index;
    }
}
