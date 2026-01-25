package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * DCA (Dollar Cost Averaging) behavior modes.
 */
public enum DcaMode {
    PAUSE("pause"),      // Pause DCA when conditions not met
    ABORT("abort"),      // Abort remaining DCA entries
    CONTINUE("continue"); // Continue regardless

    private final String value;

    DcaMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static DcaMode fromValue(String value) {
        if (value == null) return PAUSE;
        // Handle legacy values
        if ("require_signal".equals(value)) return PAUSE;
        if ("continue_always".equals(value)) return CONTINUE;
        for (DcaMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return PAUSE;
    }
}
