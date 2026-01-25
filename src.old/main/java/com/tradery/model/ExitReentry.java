package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Defines behavior when re-entering an exit zone after leaving it.
 */
public enum ExitReentry {
    CONTINUE("continue"),  // Resume partial exit progress where left off
    RESET("reset");        // Reset progress, start fresh on re-entry

    private final String value;

    ExitReentry(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ExitReentry fromValue(String value) {
        if (value == null) return CONTINUE;
        for (ExitReentry reentry : values()) {
            if (reentry.value.equals(value)) {
                return reentry;
            }
        }
        return CONTINUE;
    }
}
