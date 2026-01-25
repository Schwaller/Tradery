package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Dollar Cost Averaging (DCA) settings for entry management.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DcaSettings {

    private boolean enabled = false;
    private int maxEntries = 3;
    private int barsBetween = 1;
    private DcaMode mode = DcaMode.PAUSE;

    public DcaSettings() {}

    public DcaSettings(boolean enabled, int maxEntries, int barsBetween, DcaMode mode) {
        this.enabled = enabled;
        this.maxEntries = maxEntries;
        this.barsBetween = barsBetween;
        this.mode = mode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxEntries() {
        return maxEntries > 0 ? maxEntries : 3;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries > 0 ? maxEntries : 1;
    }

    public int getBarsBetween() {
        return barsBetween >= 0 ? barsBetween : 1;
    }

    public void setBarsBetween(int barsBetween) {
        this.barsBetween = barsBetween >= 0 ? barsBetween : 0;
    }

    public DcaMode getMode() {
        return mode != null ? mode : DcaMode.PAUSE;
    }

    public void setMode(DcaMode mode) {
        this.mode = mode;
    }

    public static DcaSettings defaults() {
        return new DcaSettings(false, 3, 1, DcaMode.PAUSE);
    }
}
