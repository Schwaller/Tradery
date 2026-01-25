package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Market type for backtest simulation.
 * Determines how holding costs are calculated.
 */
public enum MarketType {
    /**
     * Spot market - no holding costs.
     * Pure price action simulation.
     */
    SPOT("spot"),

    /**
     * Perpetual futures - uses real Binance funding rates.
     * Funding settled every 8 hours.
     * Longs pay when funding is positive, shorts receive.
     * Longs receive when funding is negative, shorts pay.
     */
    FUTURES("futures"),

    /**
     * Margin trading - uses configurable borrow interest.
     * Interest accrues hourly on notional value.
     * Both longs and shorts pay interest on borrowed funds.
     */
    MARGIN("margin");

    private final String value;

    MarketType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MarketType fromValue(String value) {
        if (value == null) return SPOT;
        for (MarketType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return SPOT;
    }

    /**
     * Check if this market type incurs holding costs.
     */
    public boolean hasHoldingCosts() {
        return this != SPOT;
    }

    /**
     * Check if this market type uses funding rates.
     */
    public boolean usesFunding() {
        return this == FUTURES;
    }

    /**
     * Check if this market type uses margin interest.
     */
    public boolean usesMarginInterest() {
        return this == MARGIN;
    }

    @Override
    public String toString() {
        return switch (this) {
            case SPOT -> "Spot";
            case FUTURES -> "Futures";
            case MARGIN -> "Margin";
        };
    }
}
