package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * User-configurable backtest settings.
 * Stored as part of Strategy JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BacktestSettings {

    private String symbol = "BTCUSDT";
    private String timeframe = "1h";
    private String duration = "1 year";
    private double initialCapital = 10000.0;
    private PositionSizingType positionSizingType = PositionSizingType.FIXED_PERCENT;
    private double positionSizingValue = 10.0;
    private double feePercent = 0.10;
    private double slippagePercent = 0.05;

    public BacktestSettings() {
        // For Jackson
    }

    public BacktestSettings(String symbol, String timeframe, String duration,
                            double initialCapital, PositionSizingType positionSizingType,
                            double positionSizingValue, double feePercent, double slippagePercent) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.duration = duration;
        this.initialCapital = initialCapital;
        this.positionSizingType = positionSizingType;
        this.positionSizingValue = positionSizingValue;
        this.feePercent = feePercent;
        this.slippagePercent = slippagePercent;
    }

    /**
     * Create default settings
     */
    public static BacktestSettings defaults() {
        return new BacktestSettings();
    }

    // Getters and setters

    public String getSymbol() {
        return symbol != null ? symbol : "BTCUSDT";
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTimeframe() {
        return timeframe != null ? timeframe : "1h";
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public String getDuration() {
        return duration != null ? duration : "1 year";
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public double getInitialCapital() {
        return initialCapital > 0 ? initialCapital : 10000.0;
    }

    public void setInitialCapital(double initialCapital) {
        this.initialCapital = initialCapital > 0 ? initialCapital : 10000.0;
    }

    public PositionSizingType getPositionSizingType() {
        return positionSizingType != null ? positionSizingType : PositionSizingType.FIXED_PERCENT;
    }

    public void setPositionSizingType(PositionSizingType positionSizingType) {
        this.positionSizingType = positionSizingType;
    }

    public double getPositionSizingValue() {
        return positionSizingValue > 0 ? positionSizingValue : 10.0;
    }

    public void setPositionSizingValue(double positionSizingValue) {
        this.positionSizingValue = positionSizingValue > 0 ? positionSizingValue : 10.0;
    }

    public double getFeePercent() {
        return feePercent >= 0 ? feePercent : 0.10;
    }

    public void setFeePercent(double feePercent) {
        this.feePercent = feePercent >= 0 ? feePercent : 0.10;
    }

    public double getSlippagePercent() {
        return slippagePercent >= 0 ? slippagePercent : 0.05;
    }

    public void setSlippagePercent(double slippagePercent) {
        this.slippagePercent = slippagePercent >= 0 ? slippagePercent : 0.05;
    }

    /**
     * Get total commission (fee + slippage) as decimal
     */
    @JsonIgnore
    public double getTotalCommission() {
        return (getFeePercent() + getSlippagePercent()) / 100.0;
    }

    /**
     * Convert to BacktestConfig for running a backtest
     */
    public BacktestConfig toBacktestConfig(long startDate, long endDate) {
        return new BacktestConfig(
            getSymbol(),
            getTimeframe(),
            startDate,
            endDate,
            getInitialCapital(),
            getPositionSizingType(),
            getPositionSizingValue(),
            getTotalCommission()
        );
    }
}
