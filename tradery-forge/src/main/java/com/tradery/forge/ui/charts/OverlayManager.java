package com.tradery.forge.ui.charts;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.overlay.*;
import com.tradery.core.model.Candle;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages overlay indicators on the price chart (SMA, EMA, Bollinger Bands, High/Low, Mayer Multiple, etc.).
 * Delegates computation to tradery-charts ChartOverlay implementations for async background processing.
 */
public class OverlayManager {

    private final JFreeChart priceChart;

    // Multiple SMA/EMA overlays (tracked by period for add/remove)
    private final List<OverlayEntry> smaEntries = new ArrayList<>();
    private final List<OverlayEntry> emaEntries = new ArrayList<>();
    private int colorIndex = 0;  // cycles through OVERLAY_PALETTE

    // Single-instance overlay tracking
    private ChartOverlay bollingerOverlay;
    private ChartOverlay highLowOverlay;
    private ChartOverlay dailyPocOverlay;
    private ChartOverlay floatingPocOverlay;
    private ChartOverlay ichimokuOverlay;
    private ChartOverlay vwapOverlay;
    private ChartOverlay mayerOverlay;

    // Mayer Multiple state
    private boolean mayerMultipleEnabled = false;
    private int mayerPeriod = 200;

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
     * Delegates to SmaOverlay for async background computation.
     */
    public OverlayInstance addSmaOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            return null;
        }

        // Check if this period already exists
        for (OverlayEntry entry : smaEntries) {
            if (entry.instance.period() == period) {
                return entry.instance;
            }
        }

        Color color = getNextColor();
        SmaOverlay overlay = new SmaOverlay(period, color);
        if (!applyChartOverlay(overlay)) {
            return null;
        }

        OverlayInstance instance = new OverlayInstance("SMA", period, -1, color);
        smaEntries.add(new OverlayEntry(instance, overlay));
        return instance;
    }

    /**
     * Remove an SMA overlay by period.
     */
    public void removeSmaOverlay(int period) {
        OverlayEntry toRemove = null;
        for (OverlayEntry entry : smaEntries) {
            if (entry.instance.period() == period) {
                toRemove = entry;
                break;
            }
        }
        if (toRemove != null) {
            removeChartOverlay(toRemove.overlay);
            smaEntries.remove(toRemove);
        }
    }

    /**
     * Clear all SMA overlays.
     */
    public void clearAllSmaOverlays() {
        for (OverlayEntry entry : smaEntries) {
            removeChartOverlay(entry.overlay);
        }
        smaEntries.clear();
    }

    /**
     * Get list of all active SMA overlays.
     */
    public List<OverlayInstance> getSmaOverlays() {
        List<OverlayInstance> result = new ArrayList<>();
        for (OverlayEntry entry : smaEntries) {
            result.add(entry.instance);
        }
        return result;
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
        return !smaEntries.isEmpty();
    }

    // ===== EMA Overlays (Multiple) =====

    /**
     * Add an EMA overlay with the given period. Returns the created overlay instance.
     * Delegates to EmaOverlay for async background computation.
     */
    public OverlayInstance addEmaOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            return null;
        }

        // Check if this period already exists
        for (OverlayEntry entry : emaEntries) {
            if (entry.instance.period() == period) {
                return entry.instance;
            }
        }

        Color color = getNextColor();
        EmaOverlay overlay = new EmaOverlay(period, color);
        if (!applyChartOverlay(overlay)) {
            return null;
        }

        OverlayInstance instance = new OverlayInstance("EMA", period, -1, color);
        emaEntries.add(new OverlayEntry(instance, overlay));
        return instance;
    }

    /**
     * Remove an EMA overlay by period.
     */
    public void removeEmaOverlay(int period) {
        OverlayEntry toRemove = null;
        for (OverlayEntry entry : emaEntries) {
            if (entry.instance.period() == period) {
                toRemove = entry;
                break;
            }
        }
        if (toRemove != null) {
            removeChartOverlay(toRemove.overlay);
            emaEntries.remove(toRemove);
        }
    }

    /**
     * Clear all EMA overlays.
     */
    public void clearAllEmaOverlays() {
        for (OverlayEntry entry : emaEntries) {
            removeChartOverlay(entry.overlay);
        }
        emaEntries.clear();
    }

    /**
     * Get list of all active EMA overlays.
     */
    public List<OverlayInstance> getEmaOverlays() {
        List<OverlayInstance> result = new ArrayList<>();
        for (OverlayEntry entry : emaEntries) {
            result.add(entry.instance);
        }
        return result;
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
        return !emaEntries.isEmpty();
    }

    // ===== Bollinger Bands Overlay =====

    /**
     * Set Bollinger Bands overlay.
     * Delegates to BollingerOverlay for async background computation.
     */
    public void setBollingerOverlay(int period, double stdDevMultiplier, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearBollingerOverlay();
            return;
        }

        clearBollingerOverlay();
        BollingerOverlay overlay = new BollingerOverlay(period, stdDevMultiplier);
        if (applyChartOverlay(overlay)) {
            bollingerOverlay = overlay;
        }
    }

    public void clearBollingerOverlay() {
        if (bollingerOverlay != null) {
            removeChartOverlay(bollingerOverlay);
            bollingerOverlay = null;
        }
    }

    public boolean isBollingerEnabled() {
        return bollingerOverlay != null;
    }

    // ===== High/Low Overlay =====

    /**
     * Set High/Low overlay showing the highest high and lowest low over a period.
     * Delegates to HighLowOverlay for async background computation.
     */
    public void setHighLowOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearHighLowOverlay();
            return;
        }

        clearHighLowOverlay();
        HighLowOverlay overlay = new HighLowOverlay(period);
        if (applyChartOverlay(overlay)) {
            highLowOverlay = overlay;
        }
    }

    public void clearHighLowOverlay() {
        if (highLowOverlay != null) {
            removeChartOverlay(highLowOverlay);
            highLowOverlay = null;
        }
    }

    public boolean isHighLowEnabled() {
        return highLowOverlay != null;
    }

    // ===== Mayer Multiple =====

    public void setMayerMultipleEnabled(boolean enabled, int period) {
        this.mayerMultipleEnabled = enabled;
        this.mayerPeriod = period;

        if (enabled) {
            clearMayerOverlay();
            MayerMultipleOverlay overlay = new MayerMultipleOverlay(period);
            if (applyChartOverlay(overlay)) {
                mayerOverlay = overlay;
            }
        } else {
            clearMayerOverlay();
        }
    }

    private void clearMayerOverlay() {
        if (mayerOverlay != null) {
            removeChartOverlay(mayerOverlay);
            mayerOverlay = null;
        }
    }

    public boolean isMayerMultipleEnabled() {
        return mayerMultipleEnabled;
    }

    public int getMayerPeriod() {
        return mayerPeriod;
    }


    // ===== Daily POC/VAH/VAL Overlay =====

    /**
     * Shows previous day's POC, VAH, and VAL as horizontal lines.
     * Delegates to DailyLevelsOverlay for async background computation.
     */
    public void setDailyPocOverlay(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            clearDailyPocOverlay();
            return;
        }

        clearDailyPocOverlay();
        DailyLevelsOverlay overlay = new DailyLevelsOverlay(true, false);
        if (applyChartOverlay(overlay)) {
            dailyPocOverlay = overlay;
        }
    }

    public void clearDailyPocOverlay() {
        if (dailyPocOverlay != null) {
            removeChartOverlay(dailyPocOverlay);
            dailyPocOverlay = null;
        }
    }

    public boolean isDailyPocEnabled() {
        return dailyPocOverlay != null;
    }

    // ===== Floating POC/VAH/VAL Overlay =====

    /**
     * Shows the developing (floating) POC, VAH, and VAL.
     * Delegates to PocOverlay for async background computation.
     * @param period 0 = today's session, >0 = rolling N-bar lookback
     */
    public void setFloatingPocOverlay(List<Candle> candles, int period) {
        if (candles == null || candles.isEmpty()) {
            clearFloatingPocOverlay();
            return;
        }

        clearFloatingPocOverlay();
        PocOverlay overlay = new PocOverlay(period > 0 ? period : 20, true);
        if (applyChartOverlay(overlay)) {
            floatingPocOverlay = overlay;
        }
    }

    public void clearFloatingPocOverlay() {
        if (floatingPocOverlay != null) {
            removeChartOverlay(floatingPocOverlay);
            floatingPocOverlay = null;
        }
    }

    public boolean isFloatingPocEnabled() {
        return floatingPocOverlay != null;
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
     * Sets the Ichimoku Cloud overlay with default parameters.
     */
    public void setIchimokuOverlay(List<Candle> candles) {
        setIchimokuOverlay(9, 26, 52, 26, candles);
    }

    /**
     * Sets the Ichimoku Cloud overlay with custom parameters.
     * Delegates to IchimokuOverlay for async background computation.
     */
    public void setIchimokuOverlay(int conversionPeriod, int basePeriod, int spanBPeriod,
                                    int displacement, List<Candle> candles) {
        if (candles == null || candles.size() < Math.max(spanBPeriod, basePeriod) + displacement) {
            clearIchimokuOverlay();
            return;
        }

        clearIchimokuOverlay();
        IchimokuOverlay overlay = new IchimokuOverlay(conversionPeriod, basePeriod, spanBPeriod, displacement);
        if (applyChartOverlay(overlay)) {
            ichimokuOverlay = overlay;
        }
    }

    public void clearIchimokuOverlay() {
        if (ichimokuOverlay != null) {
            removeChartOverlay(ichimokuOverlay);
            ichimokuOverlay = null;
        }
    }

    public boolean isIchimokuEnabled() {
        return ichimokuOverlay != null;
    }

    // ===== VWAP Overlay =====

    /**
     * Sets the VWAP overlay on the price chart.
     * Delegates to VwapOverlay for async background computation.
     */
    public void setVwapOverlay(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            clearVwapOverlay();
            return;
        }

        clearVwapOverlay();
        VwapOverlay overlay = new VwapOverlay();
        if (applyChartOverlay(overlay)) {
            vwapOverlay = overlay;
        }
    }

    public void clearVwapOverlay() {
        if (vwapOverlay != null) {
            removeChartOverlay(vwapOverlay);
            vwapOverlay = null;
        }
    }

    public boolean isVwapEnabled() {
        return vwapOverlay != null;
    }

    // ===== Daily Volume Profile Overlay (Background Computed) =====

    /**
     * Sets the daily volume profile overlay showing volume distribution histograms
     * on the left side of each day's bounds. Computation happens in background thread.
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
     */
    public void setDailyVolumeProfileOverlay(List<Candle> candles) {
        setDailyVolumeProfileOverlay(candles, 24, 70.0, 60);
    }

    public void clearDailyVolumeProfileOverlay() {
        dailyVolumeProfileOverlay.setEnabled(false);
    }

    public DailyVolumeProfileOverlay getDailyVolumeProfileOverlay() {
        return dailyVolumeProfileOverlay;
    }

    public boolean isDailyVolumeProfileEnabled() {
        return dailyVolumeProfileOverlay.isEnabled();
    }

    // ===== Footprint Heatmap Overlay =====

    /**
     * Request footprint heatmap data and render when ready.
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

    public void clearFootprintHeatmapOverlay() {
        footprintHeatmapOverlay.setEnabled(false);
    }

    public com.tradery.forge.ui.charts.footprint.FootprintHeatmapOverlay getFootprintHeatmapOverlay() {
        return footprintHeatmapOverlay;
    }

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
     */
    public boolean removeChartOverlay(ChartOverlay overlay) {
        if (!appliedChartOverlays.contains(overlay)) {
            return false;
        }

        // Close the removed overlay's subscription
        overlay.close();

        // Snapshot remaining overlays before clearing
        List<ChartOverlay> remaining = new ArrayList<>(appliedChartOverlays);
        remaining.remove(overlay);

        // Clear all from plot (without closing remaining overlays)
        clearChartOverlayDatasets();

        // Re-apply remaining overlays
        for (ChartOverlay remainingOverlay : remaining) {
            applyChartOverlay(remainingOverlay);
        }

        return true;
    }

    /**
     * Clear all applied tradery-charts overlays and close their subscriptions.
     */
    public void clearChartOverlays() {
        if (appliedChartOverlays.isEmpty()) {
            return;
        }

        // Close all subscriptions
        for (ChartOverlay overlay : appliedChartOverlays) {
            overlay.close();
        }

        clearChartOverlayDatasets();
    }

    /**
     * Clear datasets/renderers from the plot without closing subscriptions.
     * Used internally when re-applying remaining overlays after removal.
     */
    private void clearChartOverlayDatasets() {
        if (appliedChartOverlays.isEmpty()) {
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

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
        // Reset tracking fields (without individual removeChartOverlay calls)
        smaEntries.clear();
        emaEntries.clear();
        bollingerOverlay = null;
        highLowOverlay = null;
        mayerOverlay = null;
        mayerMultipleEnabled = false;
        dailyPocOverlay = null;
        floatingPocOverlay = null;
        ichimokuOverlay = null;
        vwapOverlay = null;

        // Clear all chart overlays from plot in one pass
        clearChartOverlays();

        // Clear non-ChartOverlay overlays
        clearRayOverlay();
        clearDailyVolumeProfileOverlay();
        clearFootprintHeatmapOverlay();

        resetColorIndex();
    }

    // ===== Internal =====

    /**
     * Pairs an OverlayInstance (for period tracking) with its ChartOverlay (for rendering).
     */
    private record OverlayEntry(OverlayInstance instance, ChartOverlay overlay) {}
}
