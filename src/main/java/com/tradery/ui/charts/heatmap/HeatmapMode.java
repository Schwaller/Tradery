package com.tradery.ui.charts.heatmap;

/**
 * Display modes for volume heatmap on price chart.
 */
public enum HeatmapMode {
    /**
     * Show buy volume only (green intensity)
     */
    BUY_VOLUME("Buy Volume", "Shows taker buy volume at each price level"),

    /**
     * Show sell volume only (red intensity)
     */
    SELL_VOLUME("Sell Volume", "Shows taker sell volume at each price level"),

    /**
     * Show total volume (blue/white intensity)
     */
    TOTAL_VOLUME("Total Volume", "Shows combined volume at each price level"),

    /**
     * Show delta (buy - sell) with color indicating direction
     */
    DELTA("Delta", "Shows buy/sell imbalance: green=buy pressure, red=sell pressure"),

    /**
     * Show buy and sell side-by-side (split bars)
     */
    SPLIT("Split", "Shows buy (left/green) and sell (right/red) side by side");

    private final String displayName;
    private final String description;

    HeatmapMode(String displayName, String description) {
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
