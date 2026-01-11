package com.tradery.ui.charts;

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

    // Overlays
    private boolean smaEnabled = false;
    private int smaPeriod = 20;
    private boolean emaEnabled = false;
    private int emaPeriod = 20;
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

    // Orderflow charts
    private boolean deltaEnabled = false;
    private boolean cvdEnabled = false;
    private boolean volumeRatioEnabled = false;
    private boolean whaleEnabled = false;
    private boolean retailEnabled = false;
    private double whaleThreshold = 50000;

    // Funding chart
    private boolean fundingEnabled = false;

    // POC overlays
    private boolean dailyPocEnabled = false;
    private boolean floatingPocEnabled = false;

    // Core charts
    private boolean volumeChartEnabled = true;
    private boolean equityChartEnabled = true;
    private boolean comparisonChartEnabled = true;
    private boolean capitalUsageChartEnabled = true;
    private boolean tradePLChartEnabled = true;

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

    public double getWhaleThreshold() { return whaleThreshold; }
    public void setWhaleThreshold(double threshold) { this.whaleThreshold = threshold; save(); }

    // ===== Funding Chart Getters/Setters =====

    public boolean isFundingEnabled() { return fundingEnabled; }
    public void setFundingEnabled(boolean enabled) { this.fundingEnabled = enabled; save(); }

    // ===== POC Overlay Getters/Setters =====

    public boolean isDailyPocEnabled() { return dailyPocEnabled; }
    public void setDailyPocEnabled(boolean enabled) { this.dailyPocEnabled = enabled; save(); }

    public boolean isFloatingPocEnabled() { return floatingPocEnabled; }
    public void setFloatingPocEnabled(boolean enabled) { this.floatingPocEnabled = enabled; save(); }

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

    // ===== Batch update (prevents multiple saves) =====

    /**
     * Update all settings at once without triggering multiple saves.
     */
    public void updateAll(ChartConfig other) {
        // Overlays
        this.smaEnabled = other.smaEnabled;
        this.smaPeriod = other.smaPeriod;
        this.emaEnabled = other.emaEnabled;
        this.emaPeriod = other.emaPeriod;
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

        // Orderflow
        this.deltaEnabled = other.deltaEnabled;
        this.cvdEnabled = other.cvdEnabled;
        this.volumeRatioEnabled = other.volumeRatioEnabled;
        this.whaleEnabled = other.whaleEnabled;
        this.retailEnabled = other.retailEnabled;
        this.whaleThreshold = other.whaleThreshold;

        // Funding
        this.fundingEnabled = other.fundingEnabled;

        // POC overlays
        this.dailyPocEnabled = other.dailyPocEnabled;
        this.floatingPocEnabled = other.floatingPocEnabled;

        // Core charts
        this.volumeChartEnabled = other.volumeChartEnabled;
        this.equityChartEnabled = other.equityChartEnabled;
        this.comparisonChartEnabled = other.comparisonChartEnabled;
        this.capitalUsageChartEnabled = other.capitalUsageChartEnabled;
        this.tradePLChartEnabled = other.tradePLChartEnabled;

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

        // Orderflow - all off by default
        deltaEnabled = false;
        cvdEnabled = false;
        volumeRatioEnabled = false;
        whaleEnabled = false;
        retailEnabled = false;
        whaleThreshold = 50000;

        // Funding - off by default
        fundingEnabled = false;

        // POC overlays - off by default
        dailyPocEnabled = false;
        floatingPocEnabled = false;

        // Core charts - all on by default
        volumeChartEnabled = true;
        equityChartEnabled = true;
        comparisonChartEnabled = true;
        capitalUsageChartEnabled = true;
        tradePLChartEnabled = true;

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
        // Overlays
        this.smaEnabled = other.smaEnabled;
        this.smaPeriod = other.smaPeriod;
        this.emaEnabled = other.emaEnabled;
        this.emaPeriod = other.emaPeriod;
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

        // Orderflow
        this.deltaEnabled = other.deltaEnabled;
        this.cvdEnabled = other.cvdEnabled;
        this.volumeRatioEnabled = other.volumeRatioEnabled;
        this.whaleEnabled = other.whaleEnabled;
        this.retailEnabled = other.retailEnabled;
        this.whaleThreshold = other.whaleThreshold;

        // Funding
        this.fundingEnabled = other.fundingEnabled;

        // POC overlays
        this.dailyPocEnabled = other.dailyPocEnabled;
        this.floatingPocEnabled = other.floatingPocEnabled;

        // Core charts
        this.volumeChartEnabled = other.volumeChartEnabled;
        this.equityChartEnabled = other.equityChartEnabled;
        this.comparisonChartEnabled = other.comparisonChartEnabled;
        this.capitalUsageChartEnabled = other.capitalUsageChartEnabled;
        this.tradePLChartEnabled = other.tradePLChartEnabled;
    }
}
