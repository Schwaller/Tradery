package com.tradery.ui.charts.footprint;

/**
 * Display modes for footprint chart visualization.
 */
public enum FootprintDisplayMode {
    /**
     * Show combined delta from all exchanges
     */
    COMBINED("Combined", "Aggregated delta from all exchanges"),

    /**
     * Show data from a single selected exchange
     */
    SINGLE_EXCHANGE("Single Exchange", "Filter to show only one exchange"),

    /**
     * Show per-exchange contribution stacked
     */
    STACKED("Stacked", "Show per-exchange contribution at each level"),

    /**
     * Highlight price levels where exchanges disagree
     */
    DIVERGENCE("Divergence", "Color-code where exchanges disagree on direction");

    private final String displayName;
    private final String description;

    FootprintDisplayMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
