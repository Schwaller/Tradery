package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Trading strategy with DSL-based entry/exit conditions.
 * Stored as JSON in ~/.tradery/strategies/
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Strategy {

    private String id;
    private String name;
    private String description;
    private String entry;
    private String exit;
    private String stopLossType;       // "none", "fixed_percent", "trailing_percent", "fixed_atr", "trailing_atr"
    private Double stopLossValue;      // Percent or ATR multiplier
    private String takeProfitType;     // "none", "fixed_percent", "fixed_atr"
    private Double takeProfitValue;    // Percent or ATR multiplier

    // Exit zones - multiple exit configurations based on P&L percentage ranges
    private List<ExitZone> exitZones = new ArrayList<>();
    private String zoneEvaluation = "candle_close";  // "immediate" or "candle_close"

    private int maxOpenTrades = 1;     // Max parallel open trades
    private int minCandlesBetweenTrades = 0;  // Minimum candles between trade entries
    private boolean dcaEnabled = false;       // Dollar cost averaging mode
    private int dcaMaxEntries = 3;            // Max DCA entries when dcaEnabled
    private int dcaBarsBetween = 1;           // Min bars between DCA entries
    private String dcaMode = "pause"; // "pause", "abort", or "continue"
    private int minBarsBeforeExit = 0;        // Min bars after last entry before exit is evaluated
    private boolean enabled;
    private Instant created;
    private Instant updated;

    // Backtest settings (nested object)
    private BacktestSettings backtestSettings = new BacktestSettings();

    public Strategy() {
        // For Jackson
    }

    public Strategy(String id, String name, String description, String entry, String exit, boolean enabled) {
        this(id, name, description, entry, exit, "none", null, "none", null, enabled);
    }

    public Strategy(String id, String name, String description, String entry, String exit,
                    String stopLossType, Double stopLossValue, String takeProfitType, Double takeProfitValue, boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.entry = entry;
        this.exit = exit;
        this.stopLossType = stopLossType != null ? stopLossType : "none";
        this.stopLossValue = stopLossValue;
        this.takeProfitType = takeProfitType != null ? takeProfitType : "none";
        this.takeProfitValue = takeProfitValue;
        this.enabled = enabled;
        this.created = Instant.now();
        this.updated = Instant.now();
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updated = Instant.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updated = Instant.now();
    }

    public String getEntry() {
        return entry;
    }

    public void setEntry(String entry) {
        this.entry = entry;
        this.updated = Instant.now();
    }

    public String getExit() {
        return exit;
    }

    public void setExit(String exit) {
        this.exit = exit;
        this.updated = Instant.now();
    }

    public String getStopLossType() {
        return stopLossType != null ? stopLossType : "none";
    }

    public void setStopLossType(String stopLossType) {
        this.stopLossType = stopLossType;
        this.updated = Instant.now();
    }

    public Double getStopLossValue() {
        return stopLossValue;
    }

    public void setStopLossValue(Double stopLossValue) {
        this.stopLossValue = stopLossValue;
        this.updated = Instant.now();
    }

    public String getTakeProfitType() {
        return takeProfitType != null ? takeProfitType : "none";
    }

    public void setTakeProfitType(String takeProfitType) {
        this.takeProfitType = takeProfitType;
        this.updated = Instant.now();
    }

    public Double getTakeProfitValue() {
        return takeProfitValue;
    }

    public void setTakeProfitValue(Double takeProfitValue) {
        this.takeProfitValue = takeProfitValue;
        this.updated = Instant.now();
    }

    public List<ExitZone> getExitZones() {
        return exitZones != null ? exitZones : new ArrayList<>();
    }

    public void setExitZones(List<ExitZone> exitZones) {
        this.exitZones = exitZones != null ? exitZones : new ArrayList<>();
        this.updated = Instant.now();
    }

    public String getZoneEvaluation() {
        return zoneEvaluation != null ? zoneEvaluation : "candle_close";
    }

    public void setZoneEvaluation(String zoneEvaluation) {
        this.zoneEvaluation = zoneEvaluation;
        this.updated = Instant.now();
    }

    /**
     * Find the exit zone that matches the given P&L percentage.
     * Returns null if no zone matches.
     */
    public ExitZone findMatchingZone(double pnlPercent) {
        for (ExitZone zone : getExitZones()) {
            if (zone.matches(pnlPercent)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Check if exit zones are configured.
     */
    public boolean hasExitZones() {
        return exitZones != null && !exitZones.isEmpty();
    }

    public int getMaxOpenTrades() {
        return maxOpenTrades > 0 ? maxOpenTrades : 1;
    }

    public void setMaxOpenTrades(int maxOpenTrades) {
        this.maxOpenTrades = maxOpenTrades > 0 ? maxOpenTrades : 1;
        this.updated = Instant.now();
    }

    public int getMinCandlesBetweenTrades() {
        return minCandlesBetweenTrades >= 0 ? minCandlesBetweenTrades : 0;
    }

    public void setMinCandlesBetweenTrades(int minCandlesBetweenTrades) {
        this.minCandlesBetweenTrades = minCandlesBetweenTrades >= 0 ? minCandlesBetweenTrades : 0;
        this.updated = Instant.now();
    }

    public boolean isDcaEnabled() {
        return dcaEnabled;
    }

    public void setDcaEnabled(boolean dcaEnabled) {
        this.dcaEnabled = dcaEnabled;
        this.updated = Instant.now();
    }

    public int getDcaMaxEntries() {
        return dcaMaxEntries > 0 ? dcaMaxEntries : 3;
    }

    public void setDcaMaxEntries(int dcaMaxEntries) {
        this.dcaMaxEntries = dcaMaxEntries > 0 ? dcaMaxEntries : 1;
        this.updated = Instant.now();
    }

    public int getDcaBarsBetween() {
        return dcaBarsBetween >= 0 ? dcaBarsBetween : 1;
    }

    public void setDcaBarsBetween(int dcaBarsBetween) {
        this.dcaBarsBetween = dcaBarsBetween >= 0 ? dcaBarsBetween : 0;
        this.updated = Instant.now();
    }

    public String getDcaMode() {
        if (dcaMode == null) return "pause";
        // Handle legacy values
        if ("require_signal".equals(dcaMode)) return "pause";
        if ("continue_always".equals(dcaMode)) return "continue";
        return dcaMode;
    }

    public void setDcaMode(String dcaMode) {
        this.dcaMode = dcaMode;
        this.updated = Instant.now();
    }

    public int getMinBarsBeforeExit() {
        return minBarsBeforeExit >= 0 ? minBarsBeforeExit : 0;
    }

    public void setMinBarsBeforeExit(int minBarsBeforeExit) {
        this.minBarsBeforeExit = minBarsBeforeExit >= 0 ? minBarsBeforeExit : 0;
        this.updated = Instant.now();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updated = Instant.now();
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getUpdated() {
        return updated;
    }

    public void setUpdated(Instant updated) {
        this.updated = updated;
    }

    // Backtest settings accessor

    public BacktestSettings getBacktestSettings() {
        if (backtestSettings == null) {
            backtestSettings = new BacktestSettings();
        }
        return backtestSettings;
    }

    public void setBacktestSettings(BacktestSettings backtestSettings) {
        this.backtestSettings = backtestSettings != null ? backtestSettings : new BacktestSettings();
        this.updated = Instant.now();
    }

    // Delegate getters/setters for backward compatibility

    public String getSymbol() {
        return getBacktestSettings().getSymbol();
    }

    public void setSymbol(String symbol) {
        getBacktestSettings().setSymbol(symbol);
        this.updated = Instant.now();
    }

    public String getTimeframe() {
        return getBacktestSettings().getTimeframe();
    }

    public void setTimeframe(String timeframe) {
        getBacktestSettings().setTimeframe(timeframe);
        this.updated = Instant.now();
    }

    public String getDuration() {
        return getBacktestSettings().getDuration();
    }

    public void setDuration(String duration) {
        getBacktestSettings().setDuration(duration);
        this.updated = Instant.now();
    }

    public double getInitialCapital() {
        return getBacktestSettings().getInitialCapital();
    }

    public void setInitialCapital(double initialCapital) {
        getBacktestSettings().setInitialCapital(initialCapital);
        this.updated = Instant.now();
    }

    public String getPositionSizingType() {
        return getBacktestSettings().getPositionSizingType();
    }

    public void setPositionSizingType(String positionSizingType) {
        getBacktestSettings().setPositionSizingType(positionSizingType);
        this.updated = Instant.now();
    }

    public double getPositionSizingValue() {
        return getBacktestSettings().getPositionSizingValue();
    }

    public void setPositionSizingValue(double positionSizingValue) {
        getBacktestSettings().setPositionSizingValue(positionSizingValue);
        this.updated = Instant.now();
    }

    public double getFeePercent() {
        return getBacktestSettings().getFeePercent();
    }

    public void setFeePercent(double feePercent) {
        getBacktestSettings().setFeePercent(feePercent);
        this.updated = Instant.now();
    }

    public double getSlippagePercent() {
        return getBacktestSettings().getSlippagePercent();
    }

    public void setSlippagePercent(double slippagePercent) {
        getBacktestSettings().setSlippagePercent(slippagePercent);
        this.updated = Instant.now();
    }

    /** Get total commission (fee + slippage) as decimal */
    public double getTotalCommission() {
        return getBacktestSettings().getTotalCommission();
    }

    @Override
    public String toString() {
        return name;
    }
}
