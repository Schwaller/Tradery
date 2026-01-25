package com.tradery.forge.ui.charts.sourceable;

/**
 * Types of charts that support configurable data sources.
 * Each type can have multiple instances with different exchange/market filters.
 */
public enum SourceableChartType {
    VOLUME("Volume", "Volume bars showing trading activity"),
    DELTA("Delta", "Buy volume minus sell volume (order flow direction)"),
    CVD("CVD", "Cumulative Volume Delta (running total)"),
    FOOTPRINT("Footprint", "Price-level heatmap showing volume distribution"),
    FUNDING("Funding", "Funding rate for perpetual futures"),
    PREMIUM("Premium", "Futures vs spot price spread (basis)"),
    OPEN_INTEREST("Open Interest", "Total open positions"),
    WHALE_DELTA("Whale Delta", "Large trade delta (whale activity)"),
    TRADE_COUNT("Trade Count", "Number of trades per bar");

    private final String displayName;
    private final String description;

    SourceableChartType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this chart type requires aggTrades data.
     */
    public boolean requiresAggTrades() {
        return switch (this) {
            case DELTA, CVD, FOOTPRINT, WHALE_DELTA, TRADE_COUNT -> true;
            case VOLUME, FUNDING, PREMIUM, OPEN_INTEREST -> false;
        };
    }

    /**
     * Check if this chart type supports multi-exchange data.
     */
    public boolean supportsMultiExchange() {
        return switch (this) {
            case VOLUME, DELTA, CVD, FOOTPRINT, WHALE_DELTA, TRADE_COUNT -> true;
            case FUNDING, PREMIUM, OPEN_INTEREST -> false; // These are per-exchange specific
        };
    }
}
