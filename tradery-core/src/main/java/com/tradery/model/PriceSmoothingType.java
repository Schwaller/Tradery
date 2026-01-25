package com.tradery.model;

/**
 * Defines how price is smoothed for hoop pattern matching.
 * Smoothing reduces noise from wicks and spikes.
 */
public enum PriceSmoothingType {
    /** Raw close price (default behavior) */
    NONE,

    /** Simple Moving Average over N bars */
    SMA,

    /** Exponential Moving Average over N bars */
    EMA,

    /** (High + Low + Close) / 3 - typical price, no period needed */
    HLC3
}
