package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types of position sizing strategies.
 */
public enum PositionSizingType {
    FIXED_PERCENT("fixed_percent"),
    FIXED_DOLLAR("fixed_dollar"),
    RISK_PERCENT("risk_percent"),
    KELLY("kelly"),
    VOLATILITY("volatility");

    private final String value;

    PositionSizingType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static PositionSizingType fromValue(String value) {
        if (value == null) return FIXED_PERCENT;
        // Handle legacy "fixed_amount" alias
        if ("fixed_amount".equals(value)) return FIXED_DOLLAR;
        for (PositionSizingType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return FIXED_PERCENT;
    }
}
