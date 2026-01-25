package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * When to evaluate exit zone conditions.
 */
public enum ZoneEvaluation {
    CANDLE_CLOSE("candle_close"),
    IMMEDIATE("immediate"),
    INTRA_CANDLE("intra_candle");

    private final String value;

    ZoneEvaluation(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ZoneEvaluation fromValue(String value) {
        if (value == null) return CANDLE_CLOSE;
        for (ZoneEvaluation type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return CANDLE_CLOSE;
    }
}
