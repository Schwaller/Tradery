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
     * Range function call: HIGH_OF(20), LOW_OF(20)
     */
    record RangeFunctionCall(String func, int period) implements AstNode {}

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
}
