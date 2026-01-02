package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

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
    private int maxOpenTrades = 1;     // Max parallel open trades
    private int minCandlesBetweenTrades = 0;  // Minimum candles between trade entries
    private boolean dcaEnabled = false;       // Dollar cost averaging mode
    private int dcaMaxEntries = 3;            // Max DCA entries when dcaEnabled
    private int dcaBarsBetween = 1;           // Min bars between DCA entries
    private String dcaMode = "require_signal"; // "require_signal" or "continue_always"
    private boolean enabled;
    private Instant created;
    private Instant updated;

    // Project configuration (backtest settings)
    private String symbol = "BTCUSDT";
    private String timeframe = "1h";
    private String duration = "1 year";
    private double initialCapital = 10000.0;
    private String positionSizingType = "fixed_percent";
    private double positionSizingValue = 10.0;
    private double feePercent = 0.10;
    private double slippagePercent = 0.05;

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
        return dcaMode != null ? dcaMode : "require_signal";
    }

    public void setDcaMode(String dcaMode) {
        this.dcaMode = dcaMode;
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

    // Project configuration getters and setters

    public String getSymbol() {
        return symbol != null ? symbol : "BTCUSDT";
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
        this.updated = Instant.now();
    }

    public String getTimeframe() {
        return timeframe != null ? timeframe : "1h";
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
        this.updated = Instant.now();
    }

    public String getDuration() {
        return duration != null ? duration : "1 year";
    }

    public void setDuration(String duration) {
        this.duration = duration;
        this.updated = Instant.now();
    }

    public double getInitialCapital() {
        return initialCapital > 0 ? initialCapital : 10000.0;
    }

    public void setInitialCapital(double initialCapital) {
        this.initialCapital = initialCapital > 0 ? initialCapital : 10000.0;
        this.updated = Instant.now();
    }

    public String getPositionSizingType() {
        return positionSizingType != null ? positionSizingType : "fixed_percent";
    }

    public void setPositionSizingType(String positionSizingType) {
        this.positionSizingType = positionSizingType;
        this.updated = Instant.now();
    }

    public double getPositionSizingValue() {
        return positionSizingValue > 0 ? positionSizingValue : 10.0;
    }

    public void setPositionSizingValue(double positionSizingValue) {
        this.positionSizingValue = positionSizingValue > 0 ? positionSizingValue : 10.0;
        this.updated = Instant.now();
    }

    public double getFeePercent() {
        return feePercent >= 0 ? feePercent : 0.10;
    }

    public void setFeePercent(double feePercent) {
        this.feePercent = feePercent >= 0 ? feePercent : 0.10;
        this.updated = Instant.now();
    }

    public double getSlippagePercent() {
        return slippagePercent >= 0 ? slippagePercent : 0.05;
    }

    public void setSlippagePercent(double slippagePercent) {
        this.slippagePercent = slippagePercent >= 0 ? slippagePercent : 0.05;
        this.updated = Instant.now();
    }

    /** Get total commission (fee + slippage) as decimal */
    public double getTotalCommission() {
        return (getFeePercent() + getSlippagePercent()) / 100.0;
    }

    @Override
    public String toString() {
        return name;
    }
}
