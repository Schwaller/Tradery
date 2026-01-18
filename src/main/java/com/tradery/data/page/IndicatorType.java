package com.tradery.data.page;

import com.tradery.data.DataType;

/**
 * Supported indicator types for the indicator page system.
 *
 * Indicators are a second layer that builds on top of data pages.
 * Each indicator depends on one or more data types (candles, funding, etc.).
 */
public enum IndicatorType {

    // Simple indicators (output: double[])
    RSI("RSI", DataDependency.CANDLES),
    SMA("SMA", DataDependency.CANDLES),
    EMA("EMA", DataDependency.CANDLES),
    ATR("ATR", DataDependency.CANDLES),
    ADX("ADX", DataDependency.CANDLES),
    PLUS_DI("PLUS_DI", DataDependency.CANDLES),
    MINUS_DI("MINUS_DI", DataDependency.CANDLES),

    // Composite indicators (output: custom result type)
    MACD("MACD", DataDependency.CANDLES),
    BBANDS("BBANDS", DataDependency.CANDLES),
    STOCHASTIC("STOCHASTIC", DataDependency.CANDLES),
    ICHIMOKU("ICHIMOKU", DataDependency.CANDLES),
    SUPERTREND("SUPERTREND", DataDependency.CANDLES),

    // Non-candle indicators
    FUNDING("FUNDING", DataDependency.FUNDING),
    FUNDING_8H("FUNDING_8H", DataDependency.FUNDING),
    OI("OI", DataDependency.OPEN_INTEREST),
    OI_CHANGE("OI_CHANGE", DataDependency.OPEN_INTEREST),
    PREMIUM("PREMIUM", DataDependency.PREMIUM),
    DELTA("DELTA", DataDependency.AGG_TRADES),
    CUM_DELTA("CUM_DELTA", DataDependency.AGG_TRADES),

    // Volume profile (candles-only, instant)
    VWAP("VWAP", DataDependency.CANDLES),
    POC("POC", DataDependency.CANDLES),
    VAH("VAH", DataDependency.CANDLES),
    VAL("VAL", DataDependency.CANDLES),

    // Rotating rays (expensive computation)
    RESISTANCE_RAYS("RESISTANCE_RAYS", DataDependency.CANDLES),
    SUPPORT_RAYS("SUPPORT_RAYS", DataDependency.CANDLES),
    HISTORIC_RAYS("HISTORIC_RAYS", DataDependency.CANDLES);

    private final String name;
    private final DataDependency dependency;

    IndicatorType(String name, DataDependency dependency) {
        this.name = name;
        this.dependency = dependency;
    }

    public String getName() {
        return name;
    }

    public DataDependency getDependency() {
        return dependency;
    }

    /**
     * Get the DataType required for this indicator.
     */
    public DataType getRequiredDataType() {
        return switch (dependency) {
            case CANDLES -> DataType.CANDLES;
            case FUNDING -> DataType.FUNDING;
            case OPEN_INTEREST -> DataType.OPEN_INTEREST;
            case PREMIUM -> DataType.PREMIUM_INDEX;
            case AGG_TRADES -> DataType.AGG_TRADES;
        };
    }

    /**
     * What kind of data this indicator depends on.
     */
    public enum DataDependency {
        CANDLES,
        FUNDING,
        OPEN_INTEREST,
        PREMIUM,
        AGG_TRADES
    }
}
