package com.tradery.forge.ui.charts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists chart visibility configuration.
 * Stores overlay and chart panel enable states with parameters.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ChartConfig INSTANCE = new ChartConfig();

    // Overlays (legacy single values for backward compatibility)
    private boolean smaEnabled = false;
    private int smaPeriod = 20;
    private boolean emaEnabled = false;
    private int emaPeriod = 20;

    // Multiple overlay periods (new format)
    private List<Integer> smaPeriods = new ArrayList<>();
    private List<Integer> emaPeriods = new ArrayList<>();

    private boolean bollingerEnabled = false;
    private int bollingerPeriod = 20;
    private double bollingerStdDev = 2.0;
    private boolean highLowEnabled = false;
    private int highLowPeriod = 20;
    private boolean mayerEnabled = false;
    private int mayerPeriod = 200;

    // Indicator charts
    private boolean rsiEnabled = false;
    private int rsiPeriod = 14;
    private boolean macdEnabled = false;
    private int macdFast = 12;
    private int macdSlow = 26;
    private int macdSignal = 9;
    private boolean atrEnabled = false;
    private int atrPeriod = 14;
    private boolean stochasticEnabled = false;
    private int stochasticKPeriod = 14;
    private int stochasticDPeriod = 3;
    private boolean rangePositionEnabled = false;
    private int rangePositionPeriod = 200;
    private boolean adxEnabled = false;
    private int adxPeriod = 14;

    // Orderflow charts
    private boolean deltaEnabled = false;
    private boolean cvdEnabled = false;
    private boolean volumeRatioEnabled = false;
    private boolean whaleEnabled = false;
    private boolean retailEnabled = false;
    private boolean tradeCountEnabled = false;
    private double whaleThreshold = 50000;
    private double retailThreshold = 50000;

    // Funding chart
    private boolean fundingEnabled = false;

    // Open Interest chart
    private boolean oiEnabled = false;

    // Premium Index chart
    private boolean premiumEnabled = false;

    // Holding Cost charts
    private boolean holdingCostCumulativeEnabled = false;
    private boolean holdingCostEventsEnabled = false;

    // POC overlays
    private boolean dailyPocEnabled = false;
    private boolean floatingPocEnabled = false;
    private int floatingPocPeriod = 0;  // 0 = today's session, >0 = rolling N bars

    // VWAP overlay
    private boolean vwapEnabled = false;

    // Pivot Points overlay (tradery-charts)
    private boolean pivotPointsEnabled = false;
    private boolean pivotPointsShowR3S3 = false;  // Show extended R3/S3 levels

    // ATR Bands overlay (tradery-charts)
    private boolean atrBandsEnabled = false;
    private int atrBandsPeriod = 14;
    private double atrBandsMultiplier = 2.0;

    // Supertrend overlay (tradery-charts)
    private boolean supertrendEnabled = false;
    private int supertrendPeriod = 10;
    private double supertrendMultiplier = 3.0;

    // Keltner Channel overlay (tradery-charts)
    private boolean keltnerEnabled = false;
    private int keltnerEmaPeriod = 20;
    private int keltnerAtrPeriod = 10;
    private double keltnerMultiplier = 2.0;

    // Donchian Channel overlay (tradery-charts)
    private boolean donchianEnabled = false;
    private int donchianPeriod = 20;
    private boolean donchianShowMiddle = true;

    // Ray overlay
    private boolean rayOverlayEnabled = false;
    private int rayLookback = 0;  // 0 = no limit (use all data)
    private int raySkip = 5;
    private boolean rayHistoricEnabled = false;

    // Ichimoku Cloud overlay
    private boolean ichimokuEnabled = false;
    private int ichimokuConversionPeriod = 9;   // Tenkan-sen
    private int ichimokuBasePeriod = 26;        // Kijun-sen
    private int ichimokuSpanBPeriod = 52;       // Senkou Span B
    private int ichimokuDisplacement = 26;      // Cloud shift

    // Daily Volume Profile overlay
    private boolean dailyVolumeProfileEnabled = false;
    private int dailyVolumeProfileBins = 96;
    private int dailyVolumeProfileWidth = 60;
    private String dailyVolumeProfileColorMode = "VOLUME_INTENSITY";

    // Footprint Heatmap overlay
    private com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig footprintHeatmapConfig = new com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig();

    // Price chart mode
    private boolean candlestickMode = false;  // false = line, true = candlestick
    private int priceOpacity = 100;  // 0-100, applied to price line and candles (not cloud)

    // Axis position: "left", "right", or "both"
    private String priceAxisPosition = "left";

    // Core charts
    private boolean volumeChartEnabled = true;
    private boolean equityChartEnabled = true;
    private boolean comparisonChartEnabled = true;
    private boolean capitalUsageChartEnabled = true;
    private boolean tradePLChartEnabled = true;

    // Chart layout divider positions (proportional 0.0-1.0, keyed by chart identifier)
    private java.util.Map<String, Double> chartDividerPositions = new java.util.LinkedHashMap<>();

    // Listeners
    private transient List<Runnable> listeners = new ArrayList<>();
    private transient File configFile;
    private static transient boolean isLoading = false;

    private ChartConfig() {
        // Only load if this is the singleton instance being created, not Jackson deserialization
        if (!isLoading) {
            load();
        }
    }

    public static ChartConfig getInstance() {
        return INSTANCE;
    }

    // ===== Overlay Getters/Setters =====

    public boolean isSmaEnabled() { return smaEnabled; }
    public void setSmaEnabled(boolean enabled) { this.smaEnabled = enabled; save(); }
    public int getSmaPeriod() { return smaPeriod; }
    public void setSmaPeriod(int period) { this.smaPeriod = period; save(); }

    public boolean isEmaEnabled() { return emaEnabled; }
    public void setEmaEnabled(boolean enabled) { this.emaEnabled = enabled; save(); }
    public int getEmaPeriod() { return emaPeriod; }
    public void setEmaPeriod(int period) { this.emaPeriod = period; save(); }

    // ===== Multiple SMA Periods =====

    public List<Integer> getSmaPeriods() {
        return new ArrayList<>(smaPeriods);
    }

    public void setSmaPeriods(List<Integer> periods) {
        this.smaPeriods = new ArrayList<>(periods);
        // Update legacy fields for backward compat
        this.smaEnabled = !periods.isEmpty();
        if (!periods.isEmpty()) {
            this.smaPeriod = periods.get(0);
        }
        save();
    }

    public void addSmaPeriod(int period) {
        if (!smaPeriods.contains(period)) {
            smaPeriods.add(period);
            smaEnabled = true;
            smaPeriod = period;
            save();
        }
    }

    public void removeSmaPeriod(int period) {
        smaPeriods.remove(Integer.valueOf(period));
        smaEnabled = !smaPeriods.isEmpty();
        if (!smaPeriods.isEmpty()) {
            smaPeriod = smaPeriods.get(0);
        }
        save();
    }

    public void clearSmaPeriods() {
        smaPeriods.clear();
        smaEnabled = false;
        save();
    }

    // ===== Multiple EMA Periods =====

    public List<Integer> getEmaPeriods() {
        return new ArrayList<>(emaPeriods);
    }

    public void setEmaPeriods(List<Integer> periods) {
        this.emaPeriods = new ArrayList<>(periods);
        // Update legacy fields for backward compat
        this.emaEnabled = !periods.isEmpty();
        if (!periods.isEmpty()) {
            this.emaPeriod = periods.get(0);
        }
        save();
    }

    public void addEmaPeriod(int period) {
        if (!emaPeriods.contains(period)) {
            emaPeriods.add(period);
            emaEnabled = true;
            emaPeriod = period;
            save();
        }
    }

    public void removeEmaPeriod(int period) {
        emaPeriods.remove(Integer.valueOf(period));
        emaEnabled = !emaPeriods.isEmpty();
        if (!emaPeriods.isEmpty()) {
            emaPeriod = emaPeriods.get(0);
        }
        save();
    }

    public void clearEmaPeriods() {
        emaPeriods.clear();
        emaEnabled = false;
        save();
    }

    public boolean isBollingerEnabled() { return bollingerEnabled; }
    public void setBollingerEnabled(boolean enabled) { this.bollingerEnabled = enabled; save(); }
    public int getBollingerPeriod() { return bollingerPeriod; }
    public void setBollingerPeriod(int period) { this.bollingerPeriod = period; save(); }
    public double getBollingerStdDev() { return bollingerStdDev; }
    public void setBollingerStdDev(double stdDev) { this.bollingerStdDev = stdDev; save(); }

    public boolean isHighLowEnabled() { return highLowEnabled; }
    public void setHighLowEnabled(boolean enabled) { this.highLowEnabled = enabled; save(); }
    public int getHighLowPeriod() { return highLowPeriod; }
    public void setHighLowPeriod(int period) { this.highLowPeriod = period; save(); }

    public boolean isMayerEnabled() { return mayerEnabled; }
    public void setMayerEnabled(boolean enabled) { this.mayerEnabled = enabled; save(); }
    public int getMayerPeriod() { return mayerPeriod; }
    public void setMayerPeriod(int period) { this.mayerPeriod = period; save(); }

    // ===== Indicator Chart Getters/Setters =====

    public boolean isRsiEnabled() { return rsiEnabled; }
    public void setRsiEnabled(boolean enabled) { this.rsiEnabled = enabled; save(); }
    public int getRsiPeriod() { return rsiPeriod; }
    public void setRsiPeriod(int period) { this.rsiPeriod = period; save(); }

    public boolean isMacdEnabled() { return macdEnabled; }
    public void setMacdEnabled(boolean enabled) { this.macdEnabled = enabled; save(); }
    public int getMacdFast() { return macdFast; }
    public void setMacdFast(int fast) { this.macdFast = fast; save(); }
    public int getMacdSlow() { return macdSlow; }
    public void setMacdSlow(int slow) { this.macdSlow = slow; save(); }
    public int getMacdSignal() { return macdSignal; }
    public void setMacdSignal(int signal) { this.macdSignal = signal; save(); }

    public boolean isAtrEnabled() { return atrEnabled; }
    public void setAtrEnabled(boolean enabled) { this.atrEnabled = enabled; save(); }
    public int getAtrPeriod() { return atrPeriod; }
    public void setAtrPeriod(int period) { this.atrPeriod = period; save(); }

    public boolean isStochasticEnabled() { return stochasticEnabled; }
    public void setStochasticEnabled(boolean enabled) { this.stochasticEnabled = enabled; save(); }
    public int getStochasticKPeriod() { return stochasticKPeriod; }
    public void setStochasticKPeriod(int period) { this.stochasticKPeriod = period; save(); }
    public int getStochasticDPeriod() { return stochasticDPeriod; }
    public void setStochasticDPeriod(int period) { this.stochasticDPeriod = period; save(); }

    public boolean isRangePositionEnabled() { return rangePositionEnabled; }
    public void setRangePositionEnabled(boolean enabled) { this.rangePositionEnabled = enabled; save(); }
    public int getRangePositionPeriod() { return rangePositionPeriod; }
    public void setRangePositionPeriod(int period) { this.rangePositionPeriod = period; save(); }

    public boolean isAdxEnabled() { return adxEnabled; }
    public void setAdxEnabled(boolean enabled) { this.adxEnabled = enabled; save(); }
    public int getAdxPeriod() { return adxPeriod; }
    public void setAdxPeriod(int period) { this.adxPeriod = period; save(); }

    // ===== Orderflow Chart Getters/Setters =====

    public boolean isDeltaEnabled() { return deltaEnabled; }
    public void setDeltaEnabled(boolean enabled) { this.deltaEnabled = enabled; save(); }

    public boolean isCvdEnabled() { return cvdEnabled; }
    public void setCvdEnabled(boolean enabled) { this.cvdEnabled = enabled; save(); }

    public boolean isVolumeRatioEnabled() { return volumeRatioEnabled; }
    public void setVolumeRatioEnabled(boolean enabled) { this.volumeRatioEnabled = enabled; save(); }

    public boolean isWhaleEnabled() { return whaleEnabled; }
    public void setWhaleEnabled(boolean enabled) { this.whaleEnabled = enabled; save(); }

    public boolean isRetailEnabled() { return retailEnabled; }
    public void setRetailEnabled(boolean enabled) { this.retailEnabled = enabled; save(); }
    public boolean isTradeCountEnabled() { return tradeCountEnabled; }
    public void setTradeCountEnabled(boolean enabled) { this.tradeCountEnabled = enabled; save(); }

    public double getWhaleThreshold() { return whaleThreshold; }
    public void setWhaleThreshold(double threshold) { this.whaleThreshold = threshold; save(); }

    public double getRetailThreshold() { return retailThreshold; }
    public void setRetailThreshold(double threshold) { this.retailThreshold = threshold; save(); }

    // ===== Funding Chart Getters/Setters =====

    public boolean isFundingEnabled() { return fundingEnabled; }
    public void setFundingEnabled(boolean enabled) { this.fundingEnabled = enabled; save(); }

    // ===== Open Interest Chart Getters/Setters =====

    public boolean isOiEnabled() { return oiEnabled; }
    public void setOiEnabled(boolean enabled) { this.oiEnabled = enabled; save(); }

    // ===== Premium Index Chart Getters/Setters =====

    public boolean isPremiumEnabled() { return premiumEnabled; }
    public void setPremiumEnabled(boolean enabled) { this.premiumEnabled = enabled; save(); }

    public boolean isHoldingCostCumulativeEnabled() { return holdingCostCumulativeEnabled; }
    public void setHoldingCostCumulativeEnabled(boolean enabled) { this.holdingCostCumulativeEnabled = enabled; save(); }

    public boolean isHoldingCostEventsEnabled() { return holdingCostEventsEnabled; }
    public void setHoldingCostEventsEnabled(boolean enabled) { this.holdingCostEventsEnabled = enabled; save(); }

    // ===== POC Overlay Getters/Setters =====

    public boolean isDailyPocEnabled() { return dailyPocEnabled; }
    public void setDailyPocEnabled(boolean enabled) { this.dailyPocEnabled = enabled; save(); }

    public boolean isFloatingPocEnabled() { return floatingPocEnabled; }
    public void setFloatingPocEnabled(boolean enabled) { this.floatingPocEnabled = enabled; save(); }
    public int getFloatingPocPeriod() { return floatingPocPeriod; }
    public void setFloatingPocPeriod(int period) { this.floatingPocPeriod = period; save(); }

    public boolean isVwapEnabled() { return vwapEnabled; }
    public void setVwapEnabled(boolean enabled) { this.vwapEnabled = enabled; save(); }

    // ===== Pivot Points Overlay Getters/Setters =====

    public boolean isPivotPointsEnabled() { return pivotPointsEnabled; }
    public void setPivotPointsEnabled(boolean enabled) { this.pivotPointsEnabled = enabled; save(); }
    public boolean isPivotPointsShowR3S3() { return pivotPointsShowR3S3; }
    public void setPivotPointsShowR3S3(boolean show) { this.pivotPointsShowR3S3 = show; save(); }

    // ===== ATR Bands Overlay Getters/Setters =====

    public boolean isAtrBandsEnabled() { return atrBandsEnabled; }
    public void setAtrBandsEnabled(boolean enabled) { this.atrBandsEnabled = enabled; save(); }
    public int getAtrBandsPeriod() { return atrBandsPeriod; }
    public void setAtrBandsPeriod(int period) { this.atrBandsPeriod = period; save(); }
    public double getAtrBandsMultiplier() { return atrBandsMultiplier; }
    public void setAtrBandsMultiplier(double multiplier) { this.atrBandsMultiplier = multiplier; save(); }

    // ===== Supertrend Overlay Getters/Setters =====

    public boolean isSupertrendEnabled() { return supertrendEnabled; }
    public void setSupertrendEnabled(boolean enabled) { this.supertrendEnabled = enabled; save(); }
    public int getSupertrendPeriod() { return supertrendPeriod; }
    public void setSupertrendPeriod(int period) { this.supertrendPeriod = period; save(); }
    public double getSupertrendMultiplier() { return supertrendMultiplier; }
    public void setSupertrendMultiplier(double multiplier) { this.supertrendMultiplier = multiplier; save(); }

    // ===== Keltner Channel Overlay Getters/Setters =====

    public boolean isKeltnerEnabled() { return keltnerEnabled; }
    public void setKeltnerEnabled(boolean enabled) { this.keltnerEnabled = enabled; save(); }
    public int getKeltnerEmaPeriod() { return keltnerEmaPeriod; }
    public void setKeltnerEmaPeriod(int period) { this.keltnerEmaPeriod = period; save(); }
    public int getKeltnerAtrPeriod() { return keltnerAtrPeriod; }
    public void setKeltnerAtrPeriod(int period) { this.keltnerAtrPeriod = period; save(); }
    public double getKeltnerMultiplier() { return keltnerMultiplier; }
    public void setKeltnerMultiplier(double multiplier) { this.keltnerMultiplier = multiplier; save(); }

    // ===== Donchian Channel Overlay Getters/Setters =====

    public boolean isDonchianEnabled() { return donchianEnabled; }
    public void setDonchianEnabled(boolean enabled) { this.donchianEnabled = enabled; save(); }
    public int getDonchianPeriod() { return donchianPeriod; }
    public void setDonchianPeriod(int period) { this.donchianPeriod = period; save(); }
    public boolean isDonchianShowMiddle() { return donchianShowMiddle; }
    public void setDonchianShowMiddle(boolean show) { this.donchianShowMiddle = show; save(); }

    // ===== Ray Overlay Getters/Setters =====

    public boolean isRayOverlayEnabled() { return rayOverlayEnabled; }
    public void setRayOverlayEnabled(boolean enabled) { this.rayOverlayEnabled = enabled; save(); }
    public int getRayLookback() { return rayLookback; }
    public void setRayLookback(int lookback) { this.rayLookback = lookback; save(); }
    public int getRaySkip() { return raySkip; }
    public void setRaySkip(int skip) { this.raySkip = skip; save(); }
    public boolean isRayHistoricEnabled() { return rayHistoricEnabled; }
    public void setRayHistoricEnabled(boolean enabled) { this.rayHistoricEnabled = enabled; save(); }

    // ===== Ichimoku Cloud Overlay Getters/Setters =====

    public boolean isIchimokuEnabled() { return ichimokuEnabled; }
    public void setIchimokuEnabled(boolean enabled) { this.ichimokuEnabled = enabled; save(); }
    public int getIchimokuConversionPeriod() { return ichimokuConversionPeriod; }
    public void setIchimokuConversionPeriod(int period) { this.ichimokuConversionPeriod = period; save(); }
    public int getIchimokuBasePeriod() { return ichimokuBasePeriod; }
    public void setIchimokuBasePeriod(int period) { this.ichimokuBasePeriod = period; save(); }
    public int getIchimokuSpanBPeriod() { return ichimokuSpanBPeriod; }
    public void setIchimokuSpanBPeriod(int period) { this.ichimokuSpanBPeriod = period; save(); }
    public int getIchimokuDisplacement() { return ichimokuDisplacement; }
    public void setIchimokuDisplacement(int displacement) { this.ichimokuDisplacement = displacement; save(); }

    // ===== Daily Volume Profile Overlay Getters/Setters =====

    public boolean isDailyVolumeProfileEnabled() { return dailyVolumeProfileEnabled; }
    public void setDailyVolumeProfileEnabled(boolean enabled) { this.dailyVolumeProfileEnabled = enabled; save(); }
    public int getDailyVolumeProfileBins() { return dailyVolumeProfileBins; }
    public void setDailyVolumeProfileBins(int bins) { this.dailyVolumeProfileBins = bins; save(); }
    public int getDailyVolumeProfileWidth() { return dailyVolumeProfileWidth; }
    public void setDailyVolumeProfileWidth(int width) { this.dailyVolumeProfileWidth = width; save(); }
    public String getDailyVolumeProfileColorMode() { return dailyVolumeProfileColorMode; }
    public void setDailyVolumeProfileColorMode(String mode) { this.dailyVolumeProfileColorMode = mode; save(); }

    // ===== Footprint Heatmap Overlay Getters/Setters =====

    public com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig getFootprintHeatmapConfig() {
        if (footprintHeatmapConfig == null) {
            footprintHeatmapConfig = new com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig();
        }
        return footprintHeatmapConfig;
    }

    public void setFootprintHeatmapConfig(com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig config) {
        this.footprintHeatmapConfig = config;
        save();
        notifyListeners();
    }

    public boolean isFootprintHeatmapEnabled() {
        return getFootprintHeatmapConfig().isEnabled();
    }

    public void setFootprintHeatmapEnabled(boolean enabled) {
        getFootprintHeatmapConfig().setEnabled(enabled);
        save();
        notifyListeners();
    }

    // ===== Price Chart Mode =====
    public boolean isCandlestickMode() { return candlestickMode; }
    public void setCandlestickMode(boolean mode) { this.candlestickMode = mode; save(); notifyListeners(); }

    public int getPriceOpacity() { return priceOpacity; }
    public void setPriceOpacity(int opacity) { this.priceOpacity = Math.max(0, Math.min(100, opacity)); save(); notifyListeners(); }

    public String getPriceAxisPosition() { return priceAxisPosition != null ? priceAxisPosition : "left"; }
    public void setPriceAxisPosition(String position) { this.priceAxisPosition = position; save(); notifyListeners(); }

    // ===== Core Chart Getters/Setters =====

    public boolean isVolumeChartEnabled() { return volumeChartEnabled; }
    public void setVolumeChartEnabled(boolean enabled) { this.volumeChartEnabled = enabled; save(); }

    public boolean isEquityChartEnabled() { return equityChartEnabled; }
    public void setEquityChartEnabled(boolean enabled) { this.equityChartEnabled = enabled; save(); }

    public boolean isComparisonChartEnabled() { return comparisonChartEnabled; }
    public void setComparisonChartEnabled(boolean enabled) { this.comparisonChartEnabled = enabled; save(); }

    public boolean isCapitalUsageChartEnabled() { return capitalUsageChartEnabled; }
    public void setCapitalUsageChartEnabled(boolean enabled) { this.capitalUsageChartEnabled = enabled; save(); }

    public boolean isTradePLChartEnabled() { return tradePLChartEnabled; }
    public void setTradePLChartEnabled(boolean enabled) { this.tradePLChartEnabled = enabled; save(); }

    // ===== Chart Layout Divider Positions =====

    public java.util.Map<String, Double> getChartDividerPositions() {
        return new java.util.LinkedHashMap<>(chartDividerPositions);
    }

    public void setChartDividerPositions(java.util.Map<String, Double> positions) {
        this.chartDividerPositions = positions != null ? new java.util.LinkedHashMap<>(positions) : new java.util.LinkedHashMap<>();
        save();
    }

    public void setChartDividerPosition(String chartId, double position) {
        chartDividerPositions.put(chartId, position);
        save();
    }

    public Double getChartDividerPosition(String chartId) {
        return chartDividerPositions.get(chartId);
    }

    public void clearChartDividerPositions() {
        chartDividerPositions.clear();
        save();
        notifyListeners();
    }

    // ===== Batch update (prevents multiple saves) =====

    /**
     * Update all settings at once without triggering multiple saves.
     */
    public void updateAll(ChartConfig other) {
        // Overlays (legacy fields)
        this.smaEnabled = other.smaEnabled;
        this.smaPeriod = other.smaPeriod;
        this.emaEnabled = other.emaEnabled;
        this.emaPeriod = other.emaPeriod;

        // Multiple overlay periods
        this.smaPeriods = other.smaPeriods != null ? new ArrayList<>(other.smaPeriods) : new ArrayList<>();
        this.emaPeriods = other.emaPeriods != null ? new ArrayList<>(other.emaPeriods) : new ArrayList<>();

        this.bollingerEnabled = other.bollingerEnabled;
        this.bollingerPeriod = other.bollingerPeriod;
        this.bollingerStdDev = other.bollingerStdDev;
        this.highLowEnabled = other.highLowEnabled;
        this.highLowPeriod = other.highLowPeriod;
        this.mayerEnabled = other.mayerEnabled;
        this.mayerPeriod = other.mayerPeriod;

        // Indicator charts
        this.rsiEnabled = other.rsiEnabled;
        this.rsiPeriod = other.rsiPeriod;
        this.macdEnabled = other.macdEnabled;
        this.macdFast = other.macdFast;
        this.macdSlow = other.macdSlow;
        this.macdSignal = other.macdSignal;
        this.atrEnabled = other.atrEnabled;
        this.atrPeriod = other.atrPeriod;
        this.stochasticEnabled = other.stochasticEnabled;
        this.stochasticKPeriod = other.stochasticKPeriod;
        this.stochasticDPeriod = other.stochasticDPeriod;
        this.rangePositionEnabled = other.rangePositionEnabled;
        this.rangePositionPeriod = other.rangePositionPeriod;
        this.adxEnabled = other.adxEnabled;
        this.adxPeriod = other.adxPeriod;

        // Orderflow
        this.deltaEnabled = other.deltaEnabled;
        this.cvdEnabled = other.cvdEnabled;
        this.volumeRatioEnabled = other.volumeRatioEnabled;
        this.whaleEnabled = other.whaleEnabled;
        this.retailEnabled = other.retailEnabled;
        this.tradeCountEnabled = other.tradeCountEnabled;
        this.whaleThreshold = other.whaleThreshold;
        this.retailThreshold = other.retailThreshold;

        // Funding
        this.fundingEnabled = other.fundingEnabled;

        // Open Interest
        this.oiEnabled = other.oiEnabled;

        // Premium Index
        this.premiumEnabled = other.premiumEnabled;

        // POC overlays
        this.dailyPocEnabled = other.dailyPocEnabled;
        this.floatingPocEnabled = other.floatingPocEnabled;
        this.floatingPocPeriod = other.floatingPocPeriod;
        this.vwapEnabled = other.vwapEnabled;

        // Ray overlay
        this.rayOverlayEnabled = other.rayOverlayEnabled;
        this.rayLookback = other.rayLookback;
        this.raySkip = other.raySkip;
        this.rayHistoricEnabled = other.rayHistoricEnabled;

        // Ichimoku Cloud
        this.ichimokuEnabled = other.ichimokuEnabled;
        this.ichimokuConversionPeriod = other.ichimokuConversionPeriod;
        this.ichimokuBasePeriod = other.ichimokuBasePeriod;
        this.ichimokuSpanBPeriod = other.ichimokuSpanBPeriod;
        this.ichimokuDisplacement = other.ichimokuDisplacement;

        // Daily Volume Profile
        this.dailyVolumeProfileEnabled = other.dailyVolumeProfileEnabled;
        this.dailyVolumeProfileBins = other.dailyVolumeProfileBins;
        this.dailyVolumeProfileWidth = other.dailyVolumeProfileWidth;
        this.dailyVolumeProfileColorMode = other.dailyVolumeProfileColorMode;

        // Price chart mode
        this.candlestickMode = other.candlestickMode;
        this.priceOpacity = other.priceOpacity;
        this.priceAxisPosition = other.priceAxisPosition;

        // Core charts
        this.volumeChartEnabled = other.volumeChartEnabled;
        this.equityChartEnabled = other.equityChartEnabled;
        this.comparisonChartEnabled = other.comparisonChartEnabled;
        this.capitalUsageChartEnabled = other.capitalUsageChartEnabled;
        this.tradePLChartEnabled = other.tradePLChartEnabled;

        // Chart layout divider positions
        this.chartDividerPositions = other.chartDividerPositions != null
            ? new java.util.LinkedHashMap<>(other.chartDividerPositions)
            : new java.util.LinkedHashMap<>();

        save();
        notifyListeners();
    }

    // ===== Reset to Defaults =====

    /**
     * Reset all chart settings to defaults.
     */
    public void resetToDefaults() {
        // Overlays - all off by default
        smaEnabled = false;
        smaPeriod = 20;
        emaEnabled = false;
        emaPeriod = 20;
        smaPeriods = new ArrayList<>();
        emaPeriods = new ArrayList<>();
        bollingerEnabled = false;
        bollingerPeriod = 20;
        bollingerStdDev = 2.0;
        highLowEnabled = false;
        highLowPeriod = 20;
        mayerEnabled = false;
        mayerPeriod = 200;

        // Indicator charts - all off by default
        rsiEnabled = false;
        rsiPeriod = 14;
        macdEnabled = false;
        macdFast = 12;
        macdSlow = 26;
        macdSignal = 9;
        atrEnabled = false;
        atrPeriod = 14;
        stochasticEnabled = false;
        stochasticKPeriod = 14;
        stochasticDPeriod = 3;
        rangePositionEnabled = false;
        rangePositionPeriod = 200;
        adxEnabled = false;
        adxPeriod = 14;

        // Orderflow - all off by default
        deltaEnabled = false;
        cvdEnabled = false;
        volumeRatioEnabled = false;
        whaleEnabled = false;
        retailEnabled = false;
        tradeCountEnabled = false;
        whaleThreshold = 50000;
        retailThreshold = 50000;

        // Funding - off by default
        fundingEnabled = false;

        // Open Interest - off by default
        oiEnabled = false;

        // Premium Index - off by default
        premiumEnabled = false;

        // POC overlays - off by default
        dailyPocEnabled = false;
        floatingPocEnabled = false;
        floatingPocPeriod = 0;
        vwapEnabled = false;

        // Ray overlay - off by default
        rayOverlayEnabled = false;
        rayLookback = 0;  // 0 = no limit
        raySkip = 5;
        rayHistoricEnabled = false;

        // Ichimoku Cloud - off by default
        ichimokuEnabled = false;
        ichimokuConversionPeriod = 9;
        ichimokuBasePeriod = 26;
        ichimokuSpanBPeriod = 52;
        ichimokuDisplacement = 26;

        // Daily Volume Profile - off by default
        dailyVolumeProfileEnabled = false;
        dailyVolumeProfileBins = 96;
        dailyVolumeProfileWidth = 60;
        dailyVolumeProfileColorMode = "VOLUME_INTENSITY";

        // Price chart mode - line by default
        candlestickMode = false;
        priceOpacity = 100;
        priceAxisPosition = "left";

        // Core charts - all on by default
        volumeChartEnabled = true;
        equityChartEnabled = true;
        comparisonChartEnabled = true;
        capitalUsageChartEnabled = true;
        tradePLChartEnabled = true;

        // Chart layout divider positions - cleared on reset
        chartDividerPositions = new java.util.LinkedHashMap<>();

        save();
        notifyListeners();
    }

    // ===== Listeners =====

    public void addChangeListener(Runnable listener) {
        if (listeners == null) listeners = new ArrayList<>();
        listeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        if (listeners != null) listeners.remove(listener);
    }

    private void notifyListeners() {
        if (listeners != null) {
            for (Runnable listener : listeners) {
                listener.run();
            }
        }
    }

    // ===== Persistence =====

    private File getConfigFile() {
        if (configFile == null) {
            File userDir = new File(System.getProperty("user.home"), ".tradery");
            configFile = new File(userDir, "chart-config.json");
        }
        return configFile;
    }

    private void load() {
        try {
            File file = getConfigFile();
            if (file.exists()) {
                isLoading = true;
                try {
                    ChartConfig loaded = MAPPER.readValue(file, ChartConfig.class);
                    copyFrom(loaded);
                } finally {
                    isLoading = false;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load chart config: " + e.getMessage());
        }
    }

    private void save() {
        try {
            File file = getConfigFile();
            file.getParentFile().mkdirs();
            MAPPER.writeValue(file, this);
        } catch (IOException e) {
            System.err.println("Failed to save chart config: " + e.getMessage());
        }
    }

    private void copyFrom(ChartConfig other) {
        // Overlays - migrate legacy single values to lists if needed
        this.smaEnabled = other.smaEnabled;
        this.smaPeriod = other.smaPeriod;
        this.emaEnabled = other.emaEnabled;
        this.emaPeriod = other.emaPeriod;

        // Copy lists if present, otherwise migrate from legacy single values
        if (other.smaPeriods != null && !other.smaPeriods.isEmpty()) {
            this.smaPeriods = new ArrayList<>(other.smaPeriods);
        } else if (other.smaEnabled) {
            // Migrate from legacy single value
            this.smaPeriods = new ArrayList<>();
            this.smaPeriods.add(other.smaPeriod);
        } else {
            this.smaPeriods = new ArrayList<>();
        }

        if (other.emaPeriods != null && !other.emaPeriods.isEmpty()) {
            this.emaPeriods = new ArrayList<>(other.emaPeriods);
        } else if (other.emaEnabled) {
            // Migrate from legacy single value
            this.emaPeriods = new ArrayList<>();
            this.emaPeriods.add(other.emaPeriod);
        } else {
            this.emaPeriods = new ArrayList<>();
        }

        this.bollingerEnabled = other.bollingerEnabled;
        this.bollingerPeriod = other.bollingerPeriod;
        this.bollingerStdDev = other.bollingerStdDev;
        this.highLowEnabled = other.highLowEnabled;
        this.highLowPeriod = other.highLowPeriod;
        this.mayerEnabled = other.mayerEnabled;
        this.mayerPeriod = other.mayerPeriod;

        // Indicator charts
        this.rsiEnabled = other.rsiEnabled;
        this.rsiPeriod = other.rsiPeriod;
        this.macdEnabled = other.macdEnabled;
        this.macdFast = other.macdFast;
        this.macdSlow = other.macdSlow;
        this.macdSignal = other.macdSignal;
        this.atrEnabled = other.atrEnabled;
        this.atrPeriod = other.atrPeriod;
        this.stochasticEnabled = other.stochasticEnabled;
        this.stochasticKPeriod = other.stochasticKPeriod;
        this.stochasticDPeriod = other.stochasticDPeriod;
        this.rangePositionEnabled = other.rangePositionEnabled;
        this.rangePositionPeriod = other.rangePositionPeriod;
        this.adxEnabled = other.adxEnabled;
        this.adxPeriod = other.adxPeriod;

        // Orderflow
        this.deltaEnabled = other.deltaEnabled;
        this.cvdEnabled = other.cvdEnabled;
        this.volumeRatioEnabled = other.volumeRatioEnabled;
        this.whaleEnabled = other.whaleEnabled;
        this.retailEnabled = other.retailEnabled;
        this.tradeCountEnabled = other.tradeCountEnabled;
        this.whaleThreshold = other.whaleThreshold;
        this.retailThreshold = other.retailThreshold;

        // Funding
        this.fundingEnabled = other.fundingEnabled;

        // Open Interest
        this.oiEnabled = other.oiEnabled;

        // Premium Index
        this.premiumEnabled = other.premiumEnabled;

        // POC overlays
        this.dailyPocEnabled = other.dailyPocEnabled;
        this.floatingPocEnabled = other.floatingPocEnabled;
        this.floatingPocPeriod = other.floatingPocPeriod;
        this.vwapEnabled = other.vwapEnabled;

        // Ray overlay
        this.rayOverlayEnabled = other.rayOverlayEnabled;
        this.rayLookback = other.rayLookback;
        this.raySkip = other.raySkip;
        this.rayHistoricEnabled = other.rayHistoricEnabled;

        // Ichimoku Cloud
        this.ichimokuEnabled = other.ichimokuEnabled;
        this.ichimokuConversionPeriod = other.ichimokuConversionPeriod;
        this.ichimokuBasePeriod = other.ichimokuBasePeriod;
        this.ichimokuSpanBPeriod = other.ichimokuSpanBPeriod;
        this.ichimokuDisplacement = other.ichimokuDisplacement;

        // Daily Volume Profile
        this.dailyVolumeProfileEnabled = other.dailyVolumeProfileEnabled;
        this.dailyVolumeProfileBins = other.dailyVolumeProfileBins;
        this.dailyVolumeProfileWidth = other.dailyVolumeProfileWidth;
        this.dailyVolumeProfileColorMode = other.dailyVolumeProfileColorMode;

        // Footprint Heatmap
        if (other.footprintHeatmapConfig != null) {
            this.footprintHeatmapConfig = new com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig(other.footprintHeatmapConfig);
        }

        // Price chart mode
        this.candlestickMode = other.candlestickMode;
        this.priceOpacity = other.priceOpacity;
        this.priceAxisPosition = other.priceAxisPosition;

        // Core charts
        this.volumeChartEnabled = other.volumeChartEnabled;
        this.equityChartEnabled = other.equityChartEnabled;
        this.comparisonChartEnabled = other.comparisonChartEnabled;
        this.capitalUsageChartEnabled = other.capitalUsageChartEnabled;
        this.tradePLChartEnabled = other.tradePLChartEnabled;

        // Chart layout divider positions
        this.chartDividerPositions = other.chartDividerPositions != null
            ? new java.util.LinkedHashMap<>(other.chartDividerPositions)
            : new java.util.LinkedHashMap<>();
    }
}
