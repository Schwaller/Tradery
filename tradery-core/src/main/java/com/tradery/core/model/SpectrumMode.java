package com.tradery.core.model;

/**
 * Display mode for spectrum visualization.
 * Controls which metric is shown in the spectrogram.
 */
public enum SpectrumMode {
    COUNT,      // Number of trades per bucket
    VOLUME,     // Total notional volume per bucket
    DELTA       // Buy volume - sell volume per bucket (diverging color)
}
