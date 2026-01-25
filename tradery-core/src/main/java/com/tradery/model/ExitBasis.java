package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Defines the basis for calculating partial exit quantities.
 */
public enum ExitBasis {
    ORIGINAL("original"),    // Percentage of original position size
    REMAINING("remaining");  // Percentage of remaining position size

    private final String value;

    ExitBasis(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ExitBasis fromValue(String value) {
        if (value == null) return REMAINING;
        for (ExitBasis basis : values()) {
            if (basis.value.equals(value)) {
                return basis;
            }
        }
        return REMAINING;
    }
}
