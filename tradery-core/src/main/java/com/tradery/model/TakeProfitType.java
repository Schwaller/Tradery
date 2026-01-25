package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types of take-profit configurations.
 */
public enum TakeProfitType {
    NONE("none"),
    FIXED_PERCENT("fixed_percent"),
    FIXED_ATR("fixed_atr");

    private final String value;

    TakeProfitType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public boolean isPercent() {
        return this == FIXED_PERCENT;
    }

    public boolean isAtr() {
        return this == FIXED_ATR;
    }

    public static TakeProfitType fromValue(String value) {
        if (value == null) return NONE;
        for (TakeProfitType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return NONE;
    }
}
