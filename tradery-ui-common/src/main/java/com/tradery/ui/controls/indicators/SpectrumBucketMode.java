package com.tradery.ui.controls.indicators;

/**
 * How aggTrades are bucketed for the Trade Size Spectrum.
 */
public enum SpectrumBucketMode {
    /** Bucket each aggTrade individually (current/default behavior). */
    RAW("Raw AggTrades"),
    /** Reconstruct taker orders from consecutive aggTrades before bucketing. */
    TAKER_ORDER("Taker Orders");

    private final String label;

    SpectrumBucketMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Return the storage key used in SQLite mode column and coverage sub-keys. */
    public String storageKey() {
        return name().toLowerCase();
    }

    @Override
    public String toString() {
        return label;
    }
}
