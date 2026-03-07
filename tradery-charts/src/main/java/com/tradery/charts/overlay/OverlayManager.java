package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.overlay.footprint.FootprintHeatmapOverlay;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import com.tradery.ui.controls.ChartConfig;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages overlay indicators on the price chart (SMA, EMA, Bollinger Bands, High/Low, Mayer Multiple, etc.).
 * Delegates computation to ChartOverlay implementations for async background processing.
 * <p>
 * Subclass this to add app-specific overlays (e.g., ForgeOverlayManager adds DailyVolumeProfile, FootprintHeatmap, Phases).
 */
public class OverlayManager {

    protected final JFreeChart priceChart;

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
    private ChartOverlay supertrendOverlay;
    private ChartOverlay keltnerOverlay;
    private ChartOverlay donchianOverlay;
    private ChartOverlay atrBandsOverlay;
    private ChartOverlay pivotPointsOverlay;

    // Mayer Multiple state
    private boolean mayerMultipleEnabled = false;
    private int mayerPeriod = 200;

    // Ray overlay (from tradery-charts)
    private final RayOverlay rayOverlay = new RayOverlay();

    // Current data context
    protected String currentSymbol = "BTCUSDT";
    protected String currentTimeframe = "1h";
    protected String currentMarketType = "perp";
    protected List<Candle> currentCandles;

    // tradery-charts integration
    private ChartDataProvider chartDataProvider;
    private final List<ChartOverlay> appliedChartOverlays = new ArrayList<>();
    private int chartOverlayBaseIndex = 100;  // Base dataset index for chart overlays

    public OverlayManager(JFreeChart priceChart) {
        this.priceChart = priceChart;

        // RayOverlay starts disabled until explicitly enabled
        this.rayOverlay.setEnabled(false);
    }

    /**
     * Set the data context for overlay computation.
     * Call this when symbol/timeframe changes.
     */
    public void setDataContext(String symbol, String timeframe, String marketType) {
        this.currentSymbol = symbol;
        this.currentTimeframe = timeframe;
        this.currentMarketType = marketType != null ? marketType : "perp";
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

    // ===== Apply config =====

    /**
     * Apply all overlay settings from ChartConfig.
     * Clears existing overlays and re-creates them from config state.
     */
    public void applyConfig(ChartConfig config, List<Candle> candles) {
        clearAll();

        if (candles == null || candles.isEmpty()) return;

        // SMA overlays
        for (int period : config.getSmaPeriods()) {
            addSmaOverlay(period, candles);
        }

        // EMA overlays
        for (int period : config.getEmaPeriods()) {
            addEmaOverlay(period, candles);
        }

        // Single-instance overlays
        if (config.isBollingerEnabled()) {
            setBollingerOverlay(config.getBollingerPeriod(), config.getBollingerStdDev(), candles);
        }
        if (config.isHighLowEnabled()) {
            setHighLowOverlay(config.getHighLowPeriod(), candles);
        }
        if (config.isMayerEnabled()) {
            setMayerMultipleEnabled(true, config.getMayerPeriod());
        }
        if (config.isDailyPocEnabled()) {
            setDailyPocOverlay(candles);
        }
        if (config.isFloatingPocEnabled()) {
            setFloatingPocOverlay(candles, config.getFloatingPocPeriod());
        }
        if (config.isVwapEnabled()) {
            setVwapOverlay(candles);
        }
        if (config.isIchimokuEnabled()) {
            setIchimokuOverlay(config.getIchimokuConversionPeriod(), config.getIchimokuBasePeriod(),
                config.getIchimokuSpanBPeriod(), config.getIchimokuDisplacement(), candles);
        }
        if (config.isRayOverlayEnabled()) {
            setRayOverlay(true, config.getRayLookback(), config.getRaySkip(), candles);
            setRayShowHistoric(config.isRayHistoricEnabled());
        }
        if (config.isSupertrendEnabled()) {
            setSupertrendOverlay(config.getSupertrendPeriod(), config.getSupertrendMultiplier(), candles);
        }
        if (config.isKeltnerEnabled()) {
            setKeltnerOverlay(config.getKeltnerEmaPeriod(), config.getKeltnerAtrPeriod(), config.getKeltnerMultiplier(), candles);
        }
        if (config.isDonchianEnabled()) {
            setDonchianOverlay(config.getDonchianPeriod(), config.isDonchianShowMiddle(), candles);
        }
        if (config.isAtrBandsEnabled()) {
            setAtrBandsOverlay(config.getAtrBandsPeriod(), config.getAtrBandsMultiplier(), candles);
        }
        if (config.isPivotPointsEnabled()) {
            setPivotPointsOverlay(config.isPivotPointsShowR3S3(), candles);
        }

        // Daily Volume Profile (only if provider is set)
        if (dailyVolumeProfileOverlay != null && config.isDailyVolumeProfileEnabled()) {
            setDailyVolumeProfileOverlay(candles,
                config.getDailyVolumeProfileBins(), 70.0,
                config.getDailyVolumeProfileWidth());
        }

        // Footprint Heatmap (only if provider is set)
        if (footprintHeatmapOverlay != null && config.isFootprintHeatmapEnabled()) {
            updateFootprintHeatmapOverlay();
        }
    }

    // ===== SMA Overlays (Multiple) =====

    /**
     * Add an SMA overlay with the given period. Returns the created overlay instance.
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

    public void clearAllSmaOverlays() {
        for (OverlayEntry entry : smaEntries) {
            removeChartOverlay(entry.overlay);
        }
        smaEntries.clear();
    }

    public List<OverlayInstance> getSmaOverlays() {
        List<OverlayInstance> result = new ArrayList<>();
        for (OverlayEntry entry : smaEntries) {
            result.add(entry.instance);
        }
        return result;
    }

    public void setSmaOverlay(int period, List<Candle> candles) {
        clearAllSmaOverlays();
        if (candles != null && candles.size() >= period) {
            addSmaOverlay(period, candles);
        }
    }

    public void clearSmaOverlay() {
        clearAllSmaOverlays();
    }

    public boolean isSmaEnabled() {
        return !smaEntries.isEmpty();
    }

    // ===== EMA Overlays (Multiple) =====

    public OverlayInstance addEmaOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            return null;
        }

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

    public void clearAllEmaOverlays() {
        for (OverlayEntry entry : emaEntries) {
            removeChartOverlay(entry.overlay);
        }
        emaEntries.clear();
    }

    public List<OverlayInstance> getEmaOverlays() {
        List<OverlayInstance> result = new ArrayList<>();
        for (OverlayEntry entry : emaEntries) {
            result.add(entry.instance);
        }
        return result;
    }

    public void setEmaOverlay(int period, List<Candle> candles) {
        clearAllEmaOverlays();
        if (candles != null && candles.size() >= period) {
            addEmaOverlay(period, candles);
        }
    }

    public void clearEmaOverlay() {
        clearAllEmaOverlays();
    }

    public boolean isEmaEnabled() {
        return !emaEntries.isEmpty();
    }

    // ===== Bollinger Bands Overlay =====

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

    /**
     * Get the RayOverlay instance for direct access (e.g., redraw callbacks).
     */
    public RayOverlay getRayOverlay() {
        return rayOverlay;
    }

    // ===== Ichimoku Cloud Overlay =====

    public void setIchimokuOverlay(List<Candle> candles) {
        setIchimokuOverlay(9, 26, 52, 26, candles);
    }

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

    // ===== Supertrend Overlay =====

    public void setSupertrendOverlay(int period, double multiplier, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            clearSupertrendOverlay();
            return;
        }

        clearSupertrendOverlay();
        SupertrendOverlay overlay = new SupertrendOverlay(period, multiplier);
        if (applyChartOverlay(overlay)) {
            supertrendOverlay = overlay;
        }
    }

    public void clearSupertrendOverlay() {
        if (supertrendOverlay != null) {
            removeChartOverlay(supertrendOverlay);
            supertrendOverlay = null;
        }
    }

    public boolean isSupertrendEnabled() {
        return supertrendOverlay != null;
    }

    // ===== Keltner Channel Overlay =====

    public void setKeltnerOverlay(int emaPeriod, int atrPeriod, double multiplier, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            clearKeltnerOverlay();
            return;
        }

        clearKeltnerOverlay();
        KeltnerChannelOverlay overlay = new KeltnerChannelOverlay(emaPeriod, atrPeriod, multiplier);
        if (applyChartOverlay(overlay)) {
            keltnerOverlay = overlay;
        }
    }

    public void clearKeltnerOverlay() {
        if (keltnerOverlay != null) {
            removeChartOverlay(keltnerOverlay);
            keltnerOverlay = null;
        }
    }

    public boolean isKeltnerEnabled() {
        return keltnerOverlay != null;
    }

    // ===== Donchian Channel Overlay =====

    public void setDonchianOverlay(int period, boolean showMiddle, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            clearDonchianOverlay();
            return;
        }

        clearDonchianOverlay();
        DonchianChannelOverlay overlay = new DonchianChannelOverlay(period, showMiddle);
        if (applyChartOverlay(overlay)) {
            donchianOverlay = overlay;
        }
    }

    public void clearDonchianOverlay() {
        if (donchianOverlay != null) {
            removeChartOverlay(donchianOverlay);
            donchianOverlay = null;
        }
    }

    public boolean isDonchianEnabled() {
        return donchianOverlay != null;
    }

    // ===== ATR Bands Overlay =====

    public void setAtrBandsOverlay(int period, double multiplier, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            clearAtrBandsOverlay();
            return;
        }

        clearAtrBandsOverlay();
        AtrBandsOverlay overlay = new AtrBandsOverlay(period, multiplier);
        if (applyChartOverlay(overlay)) {
            atrBandsOverlay = overlay;
        }
    }

    public void clearAtrBandsOverlay() {
        if (atrBandsOverlay != null) {
            removeChartOverlay(atrBandsOverlay);
            atrBandsOverlay = null;
        }
    }

    public boolean isAtrBandsEnabled() {
        return atrBandsOverlay != null;
    }

    // ===== Pivot Points Overlay =====

    public void setPivotPointsOverlay(boolean showR3S3, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            clearPivotPointsOverlay();
            return;
        }

        clearPivotPointsOverlay();
        PivotPointsOverlay overlay = new PivotPointsOverlay(showR3S3);
        if (applyChartOverlay(overlay)) {
            pivotPointsOverlay = overlay;
        }
    }

    public void clearPivotPointsOverlay() {
        if (pivotPointsOverlay != null) {
            removeChartOverlay(pivotPointsOverlay);
            pivotPointsOverlay = null;
        }
    }

    public boolean isPivotPointsEnabled() {
        return pivotPointsOverlay != null;
    }

    // ===== tradery-charts Integration =====

    /**
     * Set the ChartDataProvider for tradery-charts overlays.
     */
    public void setChartDataProvider(ChartDataProvider provider) {
        this.chartDataProvider = provider;
    }

    /**
     * Apply a ChartOverlay to the price chart.
     */
    public boolean applyChartOverlay(ChartOverlay overlay) {
        if (chartDataProvider == null || !chartDataProvider.hasCandles()) {
            return false;
        }

        XYPlot plot = priceChart.getXYPlot();

        int datasetIndex = chartOverlayBaseIndex;
        for (ChartOverlay existing : appliedChartOverlays) {
            datasetIndex += existing.getDatasetCount();
        }

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

        overlay.close();

        List<ChartOverlay> remaining = new ArrayList<>(appliedChartOverlays);
        remaining.remove(overlay);

        clearChartOverlayDatasets();

        for (ChartOverlay remainingOverlay : remaining) {
            applyChartOverlay(remainingOverlay);
        }

        return true;
    }

    /**
     * Clear all applied chart overlays and close their subscriptions.
     */
    public void clearChartOverlays() {
        if (appliedChartOverlays.isEmpty()) {
            return;
        }

        for (ChartOverlay overlay : appliedChartOverlays) {
            overlay.close();
        }

        clearChartOverlayDatasets();
    }

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
     * Refresh all applied chart overlays.
     */
    public void refreshChartOverlays() {
        if (appliedChartOverlays.isEmpty() || chartDataProvider == null) {
            return;
        }

        List<ChartOverlay> current = new ArrayList<>(appliedChartOverlays);
        clearChartOverlays();
        for (ChartOverlay overlay : current) {
            applyChartOverlay(overlay);
        }
    }

    public List<ChartOverlay> getAppliedChartOverlays() {
        return new ArrayList<>(appliedChartOverlays);
    }

    // ===== Daily Volume Profile Overlay =====

    private DailyVolumeProfileOverlay dailyVolumeProfileOverlay;

    /**
     * Set the daily profile provider. Creates the DVP overlay instance.
     * Call during app setup before data is loaded.
     */
    public void setDailyProfileProvider(DailyProfileProvider provider) {
        dailyVolumeProfileOverlay = new DailyVolumeProfileOverlay(priceChart, provider);
        dailyVolumeProfileOverlay.setOnDataReady(() -> {
            if (isRayOverlayEnabled()) getRayOverlay().redraw();
        });
    }

    public void setDailyVolumeProfileOverlay(List<Candle> candles, int numBins, double valueAreaPct, int histogramWidth) {
        if (dailyVolumeProfileOverlay == null) return;
        if (candles == null || candles.isEmpty()) {
            clearDailyVolumeProfileOverlay();
            return;
        }

        dailyVolumeProfileOverlay.setNumBins(numBins);
        dailyVolumeProfileOverlay.setValueAreaPct(valueAreaPct);
        dailyVolumeProfileOverlay.setHistogramWidth(histogramWidth);
        dailyVolumeProfileOverlay.setEnabled(true);

        dailyVolumeProfileOverlay.requestData(
            candles, currentSymbol, currentTimeframe,
            candles.get(0).timestamp(),
            candles.get(candles.size() - 1).timestamp());
    }

    public void setDailyVolumeProfileOverlay(List<Candle> candles) {
        setDailyVolumeProfileOverlay(candles, 24, 70.0, 60);
    }

    public void clearDailyVolumeProfileOverlay() {
        if (dailyVolumeProfileOverlay != null) {
            dailyVolumeProfileOverlay.setEnabled(false);
        }
    }

    public DailyVolumeProfileOverlay getDailyVolumeProfileOverlay() {
        return dailyVolumeProfileOverlay;
    }

    public boolean isDailyVolumeProfileEnabled() {
        return dailyVolumeProfileOverlay != null && dailyVolumeProfileOverlay.isEnabled();
    }

    // ===== Footprint Heatmap Overlay =====

    private FootprintHeatmapOverlay footprintHeatmapOverlay;

    /**
     * Set the footprint profile provider. Creates the footprint overlay instance.
     * Call during app setup before data is loaded.
     */
    public void setFootprintProfileProvider(FootprintProfileProvider provider) {
        footprintHeatmapOverlay = new FootprintHeatmapOverlay(priceChart, provider);
        footprintHeatmapOverlay.setOnDataReady(() -> {
            if (isRayOverlayEnabled()) getRayOverlay().redraw();
        });
    }

    public void updateFootprintHeatmapOverlay() {
        if (footprintHeatmapOverlay == null) return;

        ChartConfig config = ChartConfig.getInstance();

        if (!config.isFootprintHeatmapEnabled()) {
            clearFootprintHeatmapOverlay();
            return;
        }

        if (currentCandles == null || currentCandles.isEmpty()) {
            return;
        }

        footprintHeatmapOverlay.setEnabled(true);
        footprintHeatmapOverlay.setConfig(config.getFootprintHeatmapConfig());
        footprintHeatmapOverlay.setMarketType(currentMarketType);
        footprintHeatmapOverlay.requestData(
            currentCandles,
            currentSymbol,
            currentTimeframe,
            currentCandles.get(0).timestamp(),
            currentCandles.get(currentCandles.size() - 1).timestamp()
        );
    }

    public void clearFootprintHeatmapOverlay() {
        if (footprintHeatmapOverlay != null) {
            footprintHeatmapOverlay.setEnabled(false);
        }
    }

    public FootprintHeatmapOverlay getFootprintHeatmapOverlay() {
        return footprintHeatmapOverlay;
    }

    public boolean isFootprintHeatmapEnabled() {
        return footprintHeatmapOverlay != null && footprintHeatmapOverlay.isEnabled();
    }

    // ===== Clear All =====

    public void clearAll() {
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
        supertrendOverlay = null;
        keltnerOverlay = null;
        donchianOverlay = null;
        atrBandsOverlay = null;
        pivotPointsOverlay = null;

        clearChartOverlays();
        clearRayOverlay();
        clearDailyVolumeProfileOverlay();
        clearFootprintHeatmapOverlay();
        resetColorIndex();
    }

    // ===== Lifecycle =====

    /**
     * Release all resources held by this overlay manager.
     */
    public void dispose() {
        clearAll();
    }

    // ===== Internal =====

    private record OverlayEntry(OverlayInstance instance, ChartOverlay overlay) {}
}
