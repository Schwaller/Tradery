package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types of stop-loss configurations.
 */
public enum StopLossType {
    NONE("none"),
    FIXED_PERCENT("fixed_percent"),
    TRAILING_PERCENT("trailing_percent"),
    FIXED_ATR("fixed_atr"),
    TRAILING_ATR("trailing_atr");

    private final String value;

    StopLossType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public boolean isTrailing() {
        return this == TRAILING_PERCENT || this == TRAILING_ATR;
    }

    public boolean isPercent() {
        return this == FIXED_PERCENT || this == TRAILING_PERCENT;
    }

    public boolean isAtr() {
        return this == FIXED_ATR || this == TRAILING_ATR;
    }

    public static StopLossType fromValue(String value) {
        if (value == null) return NONE;
        for (StopLossType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return NONE;
    }
}
