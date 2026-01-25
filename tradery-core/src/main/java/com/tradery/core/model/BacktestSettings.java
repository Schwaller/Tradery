package com.tradery.core.model;

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
    private Long anchorDate = null; // End date timestamp in millis, null = use current time
    private double initialCapital = 10000.0;
    private PositionSizingType positionSizingType = PositionSizingType.FIXED_PERCENT;
    private double positionSizingValue = 10.0;
    private double feePercent = 0.10;
    private double slippagePercent = 0.05;
    private MarketType marketType = MarketType.SPOT;  // Default: no holding costs
    private Double marginInterestHourly = null;  // Hourly interest rate in percent (e.g., 0.00042 = 0.00042%/hr)
    private Double marginInterestApr = null;    // Legacy: annual rate (kept for backward compatibility)

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

    /**
     * Get the anchor date (end date) for backtesting.
     * @return Timestamp in milliseconds, or null to use current time
     */
    public Long getAnchorDate() {
        return anchorDate;
    }

    /**
     * Set the anchor date (end date) for backtesting.
     * @param anchorDate Timestamp in milliseconds, or null to use current time
     */
    public void setAnchorDate(Long anchorDate) {
        this.anchorDate = anchorDate;
    }

    /**
     * Get the effective end date for backtesting.
     * Returns anchorDate if set, otherwise current time.
     */
    @JsonIgnore
    public long getEffectiveEndDate() {
        return anchorDate != null ? anchorDate : System.currentTimeMillis();
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

    public MarketType getMarketType() {
        return marketType != null ? marketType : MarketType.SPOT;
    }

    public void setMarketType(MarketType marketType) {
        this.marketType = marketType != null ? marketType : MarketType.SPOT;
    }

    /**
     * Get hourly interest rate in percent (e.g., 0.00042 means 0.00042%/hour).
     * If old APR was set, converts it to hourly.
     * Default: ~0.00042%/hr (roughly equivalent to 3.65% APR)
     */
    public double getMarginInterestHourly() {
        if (marginInterestHourly != null && marginInterestHourly >= 0) {
            return marginInterestHourly;
        }
        // Migrate from old APR if present
        if (marginInterestApr != null && marginInterestApr > 0) {
            return (marginInterestApr * 100) / 8760.0;  // Convert APR decimal to hourly percent
        }
        return 0.00042;  // Default ~3.65% APR
    }

    public void setMarginInterestHourly(double hourlyRate) {
        this.marginInterestHourly = hourlyRate >= 0 ? hourlyRate : 0.00042;
        this.marginInterestApr = null;  // Clear legacy field
    }

    // Legacy getter for backward compatibility with old YAML files
    public Double getMarginInterestApr() {
        return marginInterestApr;
    }

    public void setMarginInterestApr(Double apr) {
        this.marginInterestApr = apr;
    }

    /**
     * Check if holding costs should be calculated.
     * Returns true for FUTURES (funding fees) and MARGIN (interest).
     */
    @JsonIgnore
    public boolean hasHoldingCosts() {
        return getMarketType().hasHoldingCosts();
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
            getTotalCommission(),
            getMarketType(),
            getMarginInterestHourly()
        );
    }
}
