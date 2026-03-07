package com.tradery.ui.controls.indicators;

/**
 * Coloring strategies for the Trade Size Spectrum heatmap.
 */
public enum SpectrumColorMode {
    /** Normalize all cells against the single max value in the visible window. */
    RELATIVE("Relative"),
    /** Normalize each bucket row independently (each row's max = red). */
    PER_BUCKET("Per Bucket"),
    /** Color by buy/sell imbalance: green = buy dominant, red = sell dominant. */
    DELTA("Delta"),
    /** Color by z-score: how many stddevs above the rolling mean per bucket. */
    Z_SCORE("Z-Score");

    private final String label;

    SpectrumColorMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
