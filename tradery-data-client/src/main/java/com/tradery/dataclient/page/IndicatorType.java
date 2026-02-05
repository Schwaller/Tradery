package com.tradery.dataclient.page;

import com.tradery.data.page.DataType;

/**
 * Supported indicator types for the remote indicator page system.
 *
 * This is a streamlined version focusing on candle-based indicators,
 * which are the primary use case for the Desk application.
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

    // OHLCV-based volume indicators
    TRADE_COUNT("TRADE_COUNT", DataDependency.CANDLES),
    BUY_RATIO("BUY_RATIO", DataDependency.CANDLES),
    OHLCV_DELTA("OHLCV_DELTA", DataDependency.CANDLES),
    OHLCV_CVD("OHLCV_CVD", DataDependency.CANDLES),

    // Range analysis
    RANGE_POSITION("RANGE_POSITION", DataDependency.CANDLES),

    // Volume profile (candles-only, instant)
    VWAP("VWAP", DataDependency.CANDLES),
    POC("POC", DataDependency.CANDLES),
    VAH("VAH", DataDependency.CANDLES),
    VAL("VAL", DataDependency.CANDLES),

    // Rotating rays
    RESISTANCE_RAYS("RESISTANCE_RAYS", DataDependency.CANDLES),
    SUPPORT_RAYS("SUPPORT_RAYS", DataDependency.CANDLES);

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
        };
    }

    /**
     * What kind of data this indicator depends on.
     */
    public enum DataDependency {
        CANDLES
    }
}
