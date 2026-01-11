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
    ORDERFLOW_FUNC, // VWAP, POC, VAH, VAL, DELTA, CUM_DELTA

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
