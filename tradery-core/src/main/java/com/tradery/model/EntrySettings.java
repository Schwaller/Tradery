package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Entry-side settings including condition, trade limits, and DCA.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntrySettings {

    private String condition = "";
    private int maxOpenTrades = 1;
    private int minCandlesBetween = 0;
    private DcaSettings dca = new DcaSettings();
    private TradeDirection direction = TradeDirection.LONG;

    // Pending order settings
    private EntryOrderType orderType = EntryOrderType.MARKET;
    private OffsetUnit orderOffsetUnit = OffsetUnit.MARKET;  // MARKET, PERCENT, or ATR
    private Double orderOffsetValue;        // For LIMIT (negative) / STOP (positive)
    @Deprecated
    private Double orderOffsetPercent;      // Legacy field - use orderOffsetValue instead
    private Double trailingReversePercent;  // For TRAILING: reversal % to trigger entry
    private Integer expirationBars;         // Optional: cancel pending order after X bars

    public EntrySettings() {}

    public EntrySettings(String condition, int maxOpenTrades, int minCandlesBetween, DcaSettings dca) {
        this.condition = condition != null ? condition : "";
        this.maxOpenTrades = maxOpenTrades;
        this.minCandlesBetween = minCandlesBetween;
        this.dca = dca != null ? dca : new DcaSettings();
    }

    public String getCondition() {
        return condition != null ? condition : "";
    }

    public void setCondition(String condition) {
        this.condition = condition != null ? condition : "";
    }

    public int getMaxOpenTrades() {
        return maxOpenTrades > 0 ? maxOpenTrades : 1;
    }

    public void setMaxOpenTrades(int maxOpenTrades) {
        this.maxOpenTrades = maxOpenTrades > 0 ? maxOpenTrades : 1;
    }

    public int getMinCandlesBetween() {
        return minCandlesBetween >= 0 ? minCandlesBetween : 0;
    }

    public void setMinCandlesBetween(int minCandlesBetween) {
        this.minCandlesBetween = minCandlesBetween >= 0 ? minCandlesBetween : 0;
    }

    public DcaSettings getDca() {
        if (dca == null) {
            dca = new DcaSettings();
        }
        return dca;
    }

    public void setDca(DcaSettings dca) {
        this.dca = dca != null ? dca : new DcaSettings();
    }

    public TradeDirection getDirection() {
        return direction != null ? direction : TradeDirection.LONG;
    }

    public void setDirection(TradeDirection direction) {
        this.direction = direction != null ? direction : TradeDirection.LONG;
    }

    public EntryOrderType getOrderType() {
        return orderType != null ? orderType : EntryOrderType.MARKET;
    }

    public void setOrderType(EntryOrderType orderType) {
        this.orderType = orderType != null ? orderType : EntryOrderType.MARKET;
    }

    public OffsetUnit getOrderOffsetUnit() {
        return orderOffsetUnit != null ? orderOffsetUnit : OffsetUnit.MARKET;
    }

    public void setOrderOffsetUnit(OffsetUnit orderOffsetUnit) {
        this.orderOffsetUnit = orderOffsetUnit != null ? orderOffsetUnit : OffsetUnit.MARKET;
    }

    public Double getOrderOffsetValue() {
        // Backward compat: use legacy field if new field not set
        if (orderOffsetValue != null) {
            return orderOffsetValue;
        }
        return orderOffsetPercent;
    }

    public void setOrderOffsetValue(Double orderOffsetValue) {
        this.orderOffsetValue = orderOffsetValue;
        this.orderOffsetPercent = null; // Clear legacy field
    }

    @Deprecated
    public Double getOrderOffsetPercent() {
        return getOrderOffsetValue();
    }

    @Deprecated
    public void setOrderOffsetPercent(Double orderOffsetPercent) {
        // Migrate to new field
        this.orderOffsetValue = orderOffsetPercent;
        this.orderOffsetPercent = null;
        if (orderOffsetPercent != null && orderOffsetUnit == OffsetUnit.MARKET) {
            this.orderOffsetUnit = OffsetUnit.PERCENT;
        }
    }

    public Double getTrailingReversePercent() {
        return trailingReversePercent;
    }

    public void setTrailingReversePercent(Double trailingReversePercent) {
        this.trailingReversePercent = trailingReversePercent;
    }

    public Integer getExpirationBars() {
        return expirationBars;
    }

    public void setExpirationBars(Integer expirationBars) {
        this.expirationBars = expirationBars;
    }

    public static EntrySettings defaults() {
        return new EntrySettings("", 1, 0, DcaSettings.defaults());
    }

    public static EntrySettings of(String condition) {
        return new EntrySettings(condition, 1, 0, DcaSettings.defaults());
    }
}
