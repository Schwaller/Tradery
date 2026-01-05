package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Entry-side settings including condition, trade limits, and DCA.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntrySettings {

    private String condition = "";
    private int maxOpenTrades = 1;
    private int minCandlesBetween = 0;
    private DcaSettings dca = new DcaSettings();

    public EntrySettings() {}

    public EntrySettings(String condition, int maxOpenTrades, int minCandlesBetween, DcaSettings dca) {
        this.condition = condition != null ? condition : "";
        this.maxOpenTrades = maxOpenTrades;
        this.minCandlesBetween = minCandlesBetween;
        this.dca = dca != null ? dca : new DcaSettings();
    }

    public String getCondition() {
        return condition != null ? condition : "";
    }

    public void setCondition(String condition) {
        this.condition = condition != null ? condition : "";
    }

    public int getMaxOpenTrades() {
        return maxOpenTrades > 0 ? maxOpenTrades : 1;
    }

    public void setMaxOpenTrades(int maxOpenTrades) {
        this.maxOpenTrades = maxOpenTrades > 0 ? maxOpenTrades : 1;
    }

    public int getMinCandlesBetween() {
        return minCandlesBetween >= 0 ? minCandlesBetween : 0;
    }

    public void setMinCandlesBetween(int minCandlesBetween) {
        this.minCandlesBetween = minCandlesBetween >= 0 ? minCandlesBetween : 0;
    }

    public DcaSettings getDca() {
        if (dca == null) {
            dca = new DcaSettings();
        }
        return dca;
    }

    public void setDca(DcaSettings dca) {
        this.dca = dca != null ? dca : new DcaSettings();
    }

    public static EntrySettings defaults() {
        return new EntrySettings("", 1, 0, DcaSettings.defaults());
    }

    public static EntrySettings of(String condition) {
        return new EntrySettings(condition, 1, 0, DcaSettings.defaults());
    }
}
