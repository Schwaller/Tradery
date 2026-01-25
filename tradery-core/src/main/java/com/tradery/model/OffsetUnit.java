package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Unit type for order offsets and stop/take-profit values.
 * Used in both entry orders and exit zones.
 */
public enum OffsetUnit {
    MARKET("market"),   // Immediate fill, no offset
    PERCENT("percent"), // Offset as percentage of price
    ATR("atr");         // Offset as ATR(14) multiplier

    private final String value;

    OffsetUnit(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public boolean isMarket() {
        return this == MARKET;
    }

    public boolean isPercent() {
        return this == PERCENT;
    }

    public boolean isAtr() {
        return this == ATR;
    }

    public static OffsetUnit fromValue(String value) {
        if (value == null) return MARKET;
        for (OffsetUnit unit : values()) {
            if (unit.value.equals(value)) {
                return unit;
            }
        }
        return MARKET;
    }
}
