package com.tradery.indicators.registry;

/**
 * Types of data an indicator can depend on.
 * Used to check if required data is available before computation.
 */
public enum DataDependency {
    /**
     * OHLCV candle data - always required, always available.
     */
    CANDLES,

    /**
     * Aggregated trades for orderflow indicators (Delta, CVD, Whale, etc.).
     * Optional - some indicators fall back to OHLCV approximation.
     */
    AGG_TRADES,

    /**
     * Funding rate data for perpetual futures.
     */
    FUNDING,

    /**
     * Open interest data.
     */
    OPEN_INTEREST,

    /**
     * Premium index (futures vs spot spread).
     */
    PREMIUM
}
