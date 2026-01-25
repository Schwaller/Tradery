package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Orderflow mode configuration for a Strategy.
 * - DISABLED: No orderflow indicators
 * - ENABLED: All orderflow indicators available (Tier 1 instant, Tier 2 if synced)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderflowSettings {

    public enum Mode {
        DISABLED("disabled"),
        ENABLED("enabled");

        private final String value;

        Mode(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static Mode fromString(String value) {
            if (value == null) return DISABLED;
            // Handle legacy values
            if ("tier1".equalsIgnoreCase(value) || "full".equalsIgnoreCase(value)) {
                return ENABLED;
            }
            for (Mode mode : values()) {
                if (mode.value.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return DISABLED;
        }

        public String getDisplayName() {
            return switch (this) {
                case DISABLED -> "Disabled";
                case ENABLED -> "Enabled";
            };
        }
    }

    private Mode mode = Mode.DISABLED;

    // Volume profile settings
    private int volumeProfilePeriod = 20;
    private double valueAreaPercent = 70.0;

    public OrderflowSettings() {
        // For Jackson
    }

    // Mode

    public Mode getMode() {
        return mode != null ? mode : Mode.DISABLED;
    }

    public void setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.DISABLED;
    }

    // Volume profile period

    public int getVolumeProfilePeriod() {
        return volumeProfilePeriod;
    }

    public void setVolumeProfilePeriod(int volumeProfilePeriod) {
        this.volumeProfilePeriod = Math.max(1, volumeProfilePeriod);
    }

    // Value area percent

    public double getValueAreaPercent() {
        return valueAreaPercent;
    }

    public void setValueAreaPercent(double valueAreaPercent) {
        this.valueAreaPercent = Math.max(1.0, Math.min(99.0, valueAreaPercent));
    }

    // Convenience methods

    @JsonIgnore
    public boolean isEnabled() {
        return mode != null && mode != Mode.DISABLED;
    }

    public static OrderflowSettings defaults() {
        return new OrderflowSettings();
    }
}
