package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Trade direction: LONG (buy low, sell high) or SHORT (sell high, buy low).
 */
public enum TradeDirection {
    LONG("long"),
    SHORT("short");

    private final String value;

    TradeDirection(String value) {
        this.value = value;
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
        return value;
    }
}
