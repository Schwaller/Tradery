package com.tradery.dsl;

/**
 * Token types for the DSL lexer
 */
public enum TokenType {
    // Indicators
    INDICATOR,      // SMA, EMA, RSI, MACD, BBANDS, ATR

    // Functions
    RANGE_FUNC,     // HIGH_OF, LOW_OF
    VOLUME_FUNC,    // AVG_VOLUME
    TIME_FUNC,      // DAYOFWEEK, HOUR, DAY, MONTH
    MOON_FUNC,      // MOON_PHASE
    HOLIDAY_FUNC,   // IS_US_HOLIDAY
    FOMC_FUNC,      // IS_FOMC_MEETING
    ORDERFLOW_FUNC, // VWAP, POC, VAH, VAL, DELTA, CUM_DELTA, WHALE_DELTA, WHALE_BUY_VOL, WHALE_SELL_VOL, LARGE_TRADE_COUNT
    SESSION_ORDERFLOW_FUNC, // PREV_DAY_POC, PREV_DAY_VAH, PREV_DAY_VAL, TODAY_POC, TODAY_VAH, TODAY_VAL
    OHLCV_VOLUME_FUNC, // QUOTE_VOLUME, BUY_VOLUME, SELL_VOLUME, OHLCV_DELTA, OHLCV_CVD, BUY_RATIO, TRADE_COUNT
    FUNDING_FUNC,   // FUNDING, FUNDING_8H
    PREMIUM_FUNC,   // PREMIUM, PREMIUM_AVG
    OI_FUNC,        // OI, OI_CHANGE, OI_DELTA
    RAY_FUNC,       // RESISTANCE_RAY_BROKEN, RESISTANCE_RAY_CROSSED, SUPPORT_RAY_BROKEN, etc.
    AGGREGATE_FUNC, // LOWEST, HIGHEST, PERCENTILE (operate on expressions over lookback)
    MATH_FUNC,      // abs, min, max (math utility functions)
    CANDLE_PATTERN_FUNC, // HAMMER, SHOOTING_STAR, DOJI (candlestick patterns with params)
    CANDLE_PROP_FUNC,    // BODY_SIZE, BODY_RATIO, IS_BULLISH, IS_BEARISH (candle properties)

    // Properties
    PROPERTY,       // .signal, .histogram, .upper, .lower, .middle

    // Literals
    NUMBER,         // 14, 20, 200, 1.5
    BOOLEAN,        // true, false

    // Operators
    OPERATOR,       // >, <, >=, <=, ==
    CROSS_OP,       // crosses_above, crosses_below
    LOGICAL,        // AND, OR

    // Punctuation
    LPAREN,         // (
    RPAREN,         // )
    LBRACKET,       // [
    RBRACKET,       // ]
    COMMA,          // ,
    DOT,            // .

    // Arithmetic
    MULTIPLY,       // *
    DIVIDE,         // /
    PLUS,           // +
    MINUS,          // -

    // Price references
    PRICE,          // price, open, high, low, close, volume

    // End of input
    EOF
}
