package com.tradery.data;

/**
 * Identifies the consumer/source of a data requirement.
 * Used to group data loading progress by what's requesting the data.
 */
public enum DataConsumer {
    /**
     * Main backtest engine - requires data before simulation can run.
     */
    BACKTEST("Backtest"),

    /**
     * Chart views - loads data asynchronously for display.
     */
    CHART_VIEW("Charts"),

    /**
     * Phase preview chart - shows where phase conditions are active.
     */
    PHASE_PREVIEW("Phase Preview"),

    /**
     * Hoop pattern preview - shows pattern matches on chart.
     */
    HOOP_PREVIEW("Hoop Preview");

    private final String displayName;

    DataConsumer(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
