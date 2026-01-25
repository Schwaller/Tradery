package com.tradery.forge.ui.charts.footprint;

/**
 * Display modes for footprint chart visualization.
 */
public enum FootprintDisplayMode {
    /**
     * Show combined delta from all exchanges (single color per bucket)
     */
    COMBINED("Combined", "Single color based on delta direction"),

    /**
     * Split view - buy volume on left (green), sell volume on right (red)
     */
    SPLIT("Split", "Buy volume left (green), sell volume right (red)"),

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
