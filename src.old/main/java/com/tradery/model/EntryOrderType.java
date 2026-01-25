package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Entry order types that determine how signals are converted to entries.
 */
public enum EntryOrderType {
    MARKET("market"),      // Enter immediately at signal bar close
    LIMIT("limit"),        // Enter when price drops to offset below signal
    STOP("stop"),          // Enter when price rises to offset above signal
    TRAILING("trailing");  // Trail price down, enter on reversal

    private final String value;

    EntryOrderType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static EntryOrderType fromValue(String value) {
        if (value == null) return MARKET;
        for (EntryOrderType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return MARKET;
    }
}
