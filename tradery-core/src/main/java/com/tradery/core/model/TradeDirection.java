package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Trade direction: LONG (buy low, sell high) or SHORT (sell high, buy low).
 */
public enum TradeDirection {
    LONG("long", "Long (Buy)"),
    SHORT("short", "Short (Sell)");

    private final String value;
    private final String displayName;

    TradeDirection(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static TradeDirection fromValue(String value) {
        if (value == null) return LONG;
        for (TradeDirection direction : values()) {
            if (direction.value.equalsIgnoreCase(value)) {
                return direction;
            }
        }
        return LONG;
    }

    /**
     * Check if this is a long trade.
     */
    public boolean isLong() {
        return this == LONG;
    }

    /**
     * Check if this is a short trade.
     */
    public boolean isShort() {
        return this == SHORT;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
