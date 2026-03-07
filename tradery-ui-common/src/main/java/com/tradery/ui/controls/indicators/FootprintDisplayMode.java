package com.tradery.ui.controls.indicators;

/**
 * Display modes for footprint chart visualization.
 */
public enum FootprintDisplayMode {
    /**
     * Show combined delta (single color per bucket based on delta direction)
     */
    COMBINED("Combined", "Single color based on delta direction"),

    /**
     * Split view - buy volume on left (green), sell volume on right (red)
     */
    SPLIT("Split", "Buy volume left (green), sell volume right (red)");

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
