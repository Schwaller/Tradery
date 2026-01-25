package com.tradery.dsl;

import java.util.List;

/**
 * AST Node types for the DSL parser.
 * Uses sealed interfaces for type safety.
 */
public sealed interface AstNode {

    /**
     * Comparison: left > right, left < right, etc.
     */
    record Comparison(AstNode left, String operator, AstNode right) implements AstNode {}

    /**
     * Cross comparison: left crosses_above right, left crosses_below right
     */
    record CrossComparison(AstNode left, String operator, AstNode right) implements AstNode {}

    /**
     * Logical expression: left AND right, left OR right
     */
    record LogicalExpression(String operator, AstNode left, AstNode right) implements AstNode {}

    /**
     * Arithmetic expression: left * right, left / right, etc.
     */
    record ArithmeticExpression(String operator, AstNode left, AstNode right) implements AstNode {}

    /**
     * Indicator call: SMA(14), RSI(14), MACD(12, 26, 9), etc.
     */
    record IndicatorCall(String indicator, List<Double> params) implements AstNode {}

    /**
     * Range function call: HIGH_OF(20), LOW_OF(20), RANGE_POSITION(20, 0)
     * @param skip Optional second parameter for RANGE_POSITION (default 0)
     */
    record RangeFunctionCall(String func, int period, Integer skip) implements AstNode {
        // Convenience constructor for functions without skip parameter
        public RangeFunctionCall(String func, int period) {
            this(func, period, null);
        }
    }

    /**
     * Volume function call: AVG_VOLUME(20)
     */
    record VolumeFunctionCall(String func, Integer period) implements AstNode {}

    /**
     * Time function call: DAYOFWEEK, HOUR, DAY, MONTH (no parameters)
     */
    record TimeFunctionCall(String func) implements AstNode {}

    /**
     * Moon function call: MOON_PHASE (returns 0-1 where 0.5 = full moon)
     */
    record MoonFunctionCall(String func) implements AstNode {}

    /**
     * Holiday function call: IS_US_HOLIDAY (returns 1 on US bank holidays, 0 otherwise)
     */
    record HolidayFunctionCall(String func) implements AstNode {}

    /**
     * FOMC function call: IS_FOMC_MEETING (returns 1 on FOMC meeting days, 0 otherwise)
     */
    record FomcFunctionCall(String func) implements AstNode {}

    /**
     * Orderflow function call:
     * - VWAP, DELTA, CUM_DELTA: no parameters
     * - POC(period), VAH(period), VAL(period): optional period (default 20)
     * - WHALE_DELTA(threshold), WHALE_BUY_VOL(threshold), WHALE_SELL_VOL(threshold),
     *   LARGE_TRADE_COUNT(threshold): required threshold in USD
     */
    record OrderflowFunctionCall(String func, Integer period) implements AstNode {}

    /**
     * Funding rate function call: FUNDING, FUNDING_8H (no parameters)
     * Returns funding rate as percentage (e.g., 0.01 = 0.01%)
     */
    record FundingFunctionCall(String func) implements AstNode {}

    /**
     * Premium index function call:
     * - PREMIUM: Current premium % (futures vs spot)
     * - PREMIUM_AVG(period): Average premium over N bars
     * Returns premium as percentage (e.g., 0.01 = 0.01%)
     */
    record PremiumFunctionCall(String func, Integer period) implements AstNode {}

    /**
     * Session-based orderflow function call (no parameters):
     * - PREV_DAY_POC, PREV_DAY_VAH, PREV_DAY_VAL: Previous day's volume profile levels
     * - TODAY_POC, TODAY_VAH, TODAY_VAL: Current day's developing volume profile levels
     */
    record SessionOrderflowFunctionCall(String func) implements AstNode {}

    /**
     * OHLCV extended volume function call (no parameters):
     * - QUOTE_VOLUME: Volume in quote currency (USD for BTCUSDT)
     * - BUY_VOLUME: Taker buy volume (aggressive buyers)
     * - SELL_VOLUME: Taker sell volume (aggressive sellers)
     * - OHLCV_DELTA: Buy volume - sell volume (basic delta from OHLCV)
     * - OHLCV_CVD: Cumulative delta from OHLCV
     * - BUY_RATIO: Buy volume / total volume (0-1)
     * - TRADE_COUNT: Number of trades in the bar
     */
    record OhlcvVolumeFunctionCall(String func) implements AstNode {}

    /**
     * Open Interest function call:
     * - OI: Current open interest value
     * - OI_CHANGE: OI change from previous bar
     * - OI_DELTA(period): OI change over N bars
     */
    record OIFunctionCall(String func, Integer period) implements AstNode {}

    /**
     * Rotating Ray function call:
     * - RESISTANCE_RAY_BROKEN(ray, lookback, skip): Is price above resistance ray N?
     * - RESISTANCE_RAY_CROSSED(ray, lookback, skip): Did price cross above resistance ray N this bar?
     * - RESISTANCE_RAY_DISTANCE(ray, lookback, skip): % distance from price to resistance ray N
     * - RESISTANCE_RAYS_BROKEN(lookback, skip): Count of resistance rays price is above
     * - RESISTANCE_RAY_COUNT(lookback, skip): Total resistance rays that exist
     * - SUPPORT_RAY_BROKEN(ray, lookback, skip): Is price below support ray N?
     * - SUPPORT_RAY_CROSSED(ray, lookback, skip): Did price cross below support ray N this bar?
     * - SUPPORT_RAY_DISTANCE(ray, lookback, skip): % distance from price to support ray N
     * - SUPPORT_RAYS_BROKEN(lookback, skip): Count of support rays price is below
     * - SUPPORT_RAY_COUNT(lookback, skip): Total support rays that exist
     *
     * @param func Function name (RESISTANCE_RAY_BROKEN, etc.)
     * @param rayNum Ray number (1-indexed, null for count functions)
     * @param lookback Number of bars to look back for ATH/ATL
     * @param skip Number of recent bars to skip
     */
    record RayFunctionCall(String func, Integer rayNum, int lookback, int skip) implements AstNode {}

    /**
     * Property access: MACD(12,26,9).signal, BBANDS(20,2).upper
     */
    record PropertyAccess(IndicatorCall object, String property) implements AstNode {}

    /**
     * Price reference: close, open, high, low, volume
     */
    record PriceReference(String field) implements AstNode {}

    /**
     * Number literal: 14, 1.5, 200
     */
    record NumberLiteral(double value) implements AstNode {}

    /**
     * Boolean literal: true, false
     */
    record BooleanLiteral(boolean value) implements AstNode {}

    /**
     * Aggregate function call: LOWEST(expr, period), HIGHEST(expr, period), PERCENTILE(expr, period)
     * These evaluate an expression over a lookback period and return an aggregate value.
     */
    record AggregateFunctionCall(String func, AstNode expression, int period) implements AstNode {}

    /**
     * Lookback access: expr[n] - returns value of expression n bars ago
     * Example: ATR(14)[1] returns ATR(14) from previous bar
     */
    record LookbackAccess(AstNode expression, int barsAgo) implements AstNode {}

    /**
     * Math function call: abs(expr), min(expr1, expr2), max(expr1, expr2)
     * Utility functions for mathematical operations on expressions.
     * @param func Function name (abs, min, max)
     * @param args Arguments (1 for abs, 2 for min/max)
     */
    record MathFunctionCall(String func, List<AstNode> args) implements AstNode {}

    /**
     * Candlestick pattern function call: HAMMER(ratio), SHOOTING_STAR(ratio), DOJI(ratio)
     * Returns 1 if pattern detected, 0 otherwise.
     * @param func Function name (HAMMER, SHOOTING_STAR, DOJI)
     * @param ratio Pattern-specific ratio parameter (optional, has defaults)
     */
    record CandlePatternCall(String func, Double ratio) implements AstNode {}

    /**
     * Candlestick property function call: BODY_SIZE, BODY_RATIO, IS_BULLISH, IS_BEARISH
     * Returns candle properties. Supports lookback with [n] syntax.
     * @param func Function name
     */
    record CandlePropCall(String func) implements AstNode {}

    /**
     * Footprint function call for orderflow analysis:
     * - IMBALANCE_AT_POC, IMBALANCE_AT_VAH, IMBALANCE_AT_VAL: Buy/sell ratio at price levels
     * - STACKED_BUY_IMBALANCES(n), STACKED_SELL_IMBALANCES(n): Check for n consecutive imbalances
     * - ABSORPTION(volumeThreshold, maxMovement): Detect absorption patterns
     * - HIGH_VOLUME_NODE_COUNT(threshold): Count price levels with high volume
     * - VOLUME_ABOVE_POC_RATIO, VOLUME_BELOW_POC_RATIO: Volume distribution around POC
     * - FOOTPRINT_DELTA: Total delta from footprint aggregation
     * - FOOTPRINT_POC: POC price from footprint
     *
     * @param func Function name
     * @param params Parameters (interpretation depends on function)
     */
    record FootprintFunctionCall(String func, List<Double> params) implements AstNode {}

    /**
     * Cross-exchange function call for multi-exchange analysis:
     * - BINANCE_DELTA, BYBIT_DELTA, OKX_DELTA: Delta from specific exchange
     * - COMBINED_DELTA: Sum of delta across all enabled exchanges
     * - EXCHANGE_DELTA_SPREAD: Difference between max and min exchange delta
     * - EXCHANGE_DIVERGENCE: Returns 1 if exchanges disagree on direction
     * - COMBINED_IMBALANCE_AT_POC: Aggregated imbalance at combined POC
     * - EXCHANGES_WITH_BUY_IMBALANCE, EXCHANGES_WITH_SELL_IMBALANCE: Count of exchanges with imbalance
     * - WHALE_DELTA_COMBINED(threshold): Large trade delta across all exchanges
     * - DOMINANT_EXCHANGE: Returns enum value of highest volume exchange
     *
     * @param func Function name
     * @param params Parameters (e.g., threshold for whale detection)
     */
    record ExchangeFunctionCall(String func, List<Double> params) implements AstNode {}
}
