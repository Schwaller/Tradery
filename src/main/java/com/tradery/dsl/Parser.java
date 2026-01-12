package com.tradery.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recursive descent parser for strategy DSL.
 *
 * Grammar (highest to lowest precedence):
 * expression     = logical_or
 * logical_or     = logical_and ( "OR" logical_and )*
 * logical_and    = comparison ( "AND" comparison )*
 * comparison     = arithmetic ( OPERATOR arithmetic | CROSS_OP arithmetic )?
 * arithmetic     = term ( (MULTIPLY | DIVIDE | PLUS | MINUS) term )*
 * term           = function_call | price_ref | number | boolean | "(" expression ")"
 */
public class Parser {

    private List<Token> tokens = new ArrayList<>();
    private int position = 0;

    /**
     * Parse source string into an AST
     */
    public ParseResult parse(String source) {
        try {
            this.tokens = Lexer.tokenize(source);
            this.position = 0;

            AstNode ast = expression();

            // Ensure we consumed all tokens
            if (current().type() != TokenType.EOF) {
                throw new ParserException("Unexpected token '" + current().value() +
                    "' at position " + current().position());
            }

            return new ParseResult(true, ast, null, null);
        } catch (Exception e) {
            return new ParseResult(false, null, e.getMessage(),
                position < tokens.size() ? tokens.get(position).position() : 0);
        }
    }

    // ========== Parser Methods ==========

    private AstNode expression() {
        return logicalOr();
    }

    private AstNode logicalOr() {
        AstNode left = logicalAnd();

        while (check(TokenType.LOGICAL) && "OR".equals(current().value())) {
            advance();
            AstNode right = logicalAnd();
            left = new AstNode.LogicalExpression("OR", left, right);
        }

        return left;
    }

    private AstNode logicalAnd() {
        AstNode left = comparison();

        while (check(TokenType.LOGICAL) && "AND".equals(current().value())) {
            advance();
            AstNode right = comparison();
            left = new AstNode.LogicalExpression("AND", left, right);
        }

        return left;
    }

    private AstNode comparison() {
        AstNode left = arithmetic();

        // Check for comparison operator
        if (check(TokenType.OPERATOR)) {
            String operator = current().value();
            advance();
            AstNode right = arithmetic();
            return new AstNode.Comparison(left, operator, right);
        }

        // Check for cross operator
        if (check(TokenType.CROSS_OP)) {
            String operator = current().value();
            advance();
            AstNode right = arithmetic();
            return new AstNode.CrossComparison(left, operator, right);
        }

        return left;
    }

    private AstNode arithmetic() {
        AstNode left = term();

        while (check(TokenType.MULTIPLY) || check(TokenType.DIVIDE) ||
               check(TokenType.PLUS) || check(TokenType.MINUS)) {
            String operator = current().value();
            advance();
            AstNode right = term();
            left = new AstNode.ArithmeticExpression(operator, left, right);
        }

        return left;
    }

    private AstNode term() {
        // Parenthesized expression
        if (check(TokenType.LPAREN)) {
            advance();
            AstNode expr = expression();
            expect(TokenType.RPAREN, "Expected ')' after expression");
            return expr;
        }

        // Boolean literal
        if (check(TokenType.BOOLEAN)) {
            boolean value = "true".equals(current().value());
            advance();
            return new AstNode.BooleanLiteral(value);
        }

        // Number literal
        if (check(TokenType.NUMBER)) {
            double value = Double.parseDouble(current().value());
            advance();
            return new AstNode.NumberLiteral(value);
        }

        // Price reference
        if (check(TokenType.PRICE)) {
            String field = current().value();
            advance();
            return new AstNode.PriceReference(field);
        }

        // Indicator call
        if (check(TokenType.INDICATOR)) {
            return indicatorCall();
        }

        // Range function
        if (check(TokenType.RANGE_FUNC)) {
            return rangeFunctionCall();
        }

        // Volume function
        if (check(TokenType.VOLUME_FUNC)) {
            return volumeFunctionCall();
        }

        // Time function (DAYOFWEEK, HOUR, DAY, MONTH - no parameters)
        if (check(TokenType.TIME_FUNC)) {
            String func = current().value();
            advance();
            return new AstNode.TimeFunctionCall(func);
        }

        // Moon function (MOON_PHASE - no parameters)
        if (check(TokenType.MOON_FUNC)) {
            String func = current().value();
            advance();
            return new AstNode.MoonFunctionCall(func);
        }

        // Holiday function (IS_US_HOLIDAY - no parameters)
        if (check(TokenType.HOLIDAY_FUNC)) {
            String func = current().value();
            advance();
            return new AstNode.HolidayFunctionCall(func);
        }

        // FOMC function (IS_FOMC_MEETING - no parameters)
        if (check(TokenType.FOMC_FUNC)) {
            String func = current().value();
            advance();
            return new AstNode.FomcFunctionCall(func);
        }

        // Orderflow function (VWAP, POC, VAH, VAL, DELTA, CUM_DELTA, WHALE_*, LARGE_TRADE_COUNT)
        if (check(TokenType.ORDERFLOW_FUNC)) {
            return orderflowFunctionCall();
        }

        // Funding function (FUNDING, FUNDING_8H)
        if (check(TokenType.FUNDING_FUNC)) {
            return fundingFunctionCall();
        }

        // Session orderflow function (PREV_DAY_POC, TODAY_POC, etc. - no parameters)
        if (check(TokenType.SESSION_ORDERFLOW_FUNC)) {
            String func = current().value();
            advance();
            return new AstNode.SessionOrderflowFunctionCall(func);
        }

        // Open Interest function (OI, OI_CHANGE, OI_DELTA)
        if (check(TokenType.OI_FUNC)) {
            return oiFunctionCall();
        }

        throw new ParserException("Unexpected token '" + current().value() +
            "' at position " + current().position());
    }

    private AstNode.OrderflowFunctionCall orderflowFunctionCall() {
        String func = current().value();
        advance();

        Integer period = null;

        // Check for optional/required period parameter
        if (check(TokenType.LPAREN)) {
            advance();
            List<Double> params = parseNumberList();
            expect(TokenType.RPAREN, "Expected ')' after " + func + " parameters");

            if (!params.isEmpty()) {
                period = params.get(0).intValue();
            }
        }

        // Handle different function types
        switch (func) {
            case "POC", "VAH", "VAL" -> {
                if (period == null) period = 20; // Default period
            }
            case "VWAP", "DELTA", "CUM_DELTA" -> {
                // No parameters needed
            }
            case "WHALE_DELTA", "WHALE_BUY_VOL", "WHALE_SELL_VOL", "LARGE_TRADE_COUNT" -> {
                // Threshold is required for whale/large trade functions
                if (period == null) {
                    throw new ParserException(func + " requires a threshold parameter, e.g., " + func + "(50000)");
                }
            }
        }

        return new AstNode.OrderflowFunctionCall(func, period);
    }

    private AstNode.FundingFunctionCall fundingFunctionCall() {
        String func = current().value();
        advance();
        // FUNDING and FUNDING_8H have no parameters
        return new AstNode.FundingFunctionCall(func);
    }

    private AstNode.OIFunctionCall oiFunctionCall() {
        String func = current().value();
        advance();

        Integer period = null;

        // OI_DELTA requires a period parameter, OI and OI_CHANGE do not
        if (check(TokenType.LPAREN)) {
            advance();
            List<Double> params = parseNumberList();
            expect(TokenType.RPAREN, "Expected ')' after " + func + " parameters");

            if (!params.isEmpty()) {
                period = params.get(0).intValue();
            }
        }

        // Validate based on function type
        switch (func) {
            case "OI", "OI_CHANGE" -> {
                // No parameters needed
            }
            case "OI_DELTA" -> {
                if (period == null) {
                    throw new ParserException("OI_DELTA requires a period parameter, e.g., OI_DELTA(12)");
                }
            }
        }

        return new AstNode.OIFunctionCall(func, period);
    }

    private AstNode indicatorCall() {
        String indicator = current().value();
        advance();

        expect(TokenType.LPAREN, "Expected '(' after " + indicator);
        List<Double> params = parseNumberList();
        expect(TokenType.RPAREN, "Expected ')' after " + indicator + " parameters");

        // Validate parameter count
        validateIndicatorParams(indicator, params);

        AstNode.IndicatorCall indicatorNode = new AstNode.IndicatorCall(indicator, params);

        // Check for property access
        if (check(TokenType.DOT)) {
            advance();
            if (!check(TokenType.PROPERTY)) {
                throw new ParserException("Expected property name after '.', got '" + current().value() + "'");
            }
            String property = current().value();
            advance();

            // Validate property for indicator
            validateProperty(indicator, property);

            return new AstNode.PropertyAccess(indicatorNode, property);
        }

        return indicatorNode;
    }

    private void validateIndicatorParams(String indicator, List<Double> params) {
        switch (indicator) {
            case "MACD" -> {
                if (params.size() != 3) {
                    throw new ParserException("MACD requires 3 parameters (fast, slow, signal), got " + params.size());
                }
            }
            case "BBANDS" -> {
                if (params.size() != 2) {
                    throw new ParserException("BBANDS requires 2 parameters (period, stdDev), got " + params.size());
                }
            }
            case "STOCHASTIC" -> {
                if (params.size() < 1 || params.size() > 2) {
                    throw new ParserException("STOCHASTIC requires 1-2 parameters (kPeriod, dPeriod), got " + params.size());
                }
            }
            case "SMA", "EMA", "RSI", "ATR" -> {
                if (params.size() != 1) {
                    throw new ParserException(indicator + " requires 1 parameter (period), got " + params.size());
                }
            }
        }
    }

    private void validateProperty(String indicator, String property) {
        Set<String> macdProps = Set.of("signal", "histogram", "line");
        Set<String> bbandsProps = Set.of("upper", "lower", "middle");
        Set<String> stochProps = Set.of("k", "d");

        switch (indicator) {
            case "MACD" -> {
                if (!macdProps.contains(property)) {
                    throw new ParserException("MACD only has properties: signal, histogram, line");
                }
            }
            case "BBANDS" -> {
                if (!bbandsProps.contains(property)) {
                    throw new ParserException("BBANDS only has properties: upper, lower, middle");
                }
            }
            case "STOCHASTIC" -> {
                if (!stochProps.contains(property)) {
                    throw new ParserException("STOCHASTIC only has properties: k, d");
                }
            }
            case "SMA", "EMA", "RSI", "ATR" -> {
                throw new ParserException(indicator + " does not have properties");
            }
        }
    }

    private AstNode.RangeFunctionCall rangeFunctionCall() {
        String func = current().value();
        advance();

        expect(TokenType.LPAREN, "Expected '(' after " + func);
        List<Double> params = parseNumberList();
        expect(TokenType.RPAREN, "Expected ')' after " + func + " parameters");

        // RANGE_POSITION takes 2 params: period, skip
        if ("RANGE_POSITION".equals(func)) {
            if (params.size() < 1 || params.size() > 2) {
                throw new ParserException("RANGE_POSITION requires 1-2 parameters (period, skip), got " + params.size());
            }
            int period = params.get(0).intValue();
            int skip = params.size() > 1 ? params.get(1).intValue() : 0;
            return new AstNode.RangeFunctionCall(func, period, skip);
        }

        // HIGH_OF and LOW_OF take 1 param
        if (params.size() != 1) {
            throw new ParserException(func + " requires 1 parameter (period), got " + params.size());
        }

        return new AstNode.RangeFunctionCall(func, params.get(0).intValue());
    }

    private AstNode.VolumeFunctionCall volumeFunctionCall() {
        String func = current().value();
        advance();

        expect(TokenType.LPAREN, "Expected '(' after " + func);
        List<Double> params = parseNumberList();
        expect(TokenType.RPAREN, "Expected ')' after " + func + " parameters");

        if (params.size() != 1) {
            throw new ParserException(func + " requires 1 parameter (period), got " + params.size());
        }

        return new AstNode.VolumeFunctionCall(func, params.get(0).intValue());
    }

    private List<Double> parseNumberList() {
        List<Double> numbers = new ArrayList<>();

        if (check(TokenType.NUMBER)) {
            numbers.add(Double.parseDouble(current().value()));
            advance();

            while (check(TokenType.COMMA)) {
                advance();
                if (!check(TokenType.NUMBER)) {
                    throw new ParserException("Expected number after ',', got '" + current().value() + "'");
                }
                numbers.add(Double.parseDouble(current().value()));
                advance();
            }
        }

        return numbers;
    }

    // ========== Helper Methods ==========

    private Token current() {
        if (position >= tokens.size()) {
            return Token.eof(position);
        }
        return tokens.get(position);
    }

    private boolean check(TokenType type) {
        return current().type() == type;
    }

    private Token advance() {
        Token token = current();
        if (token.type() != TokenType.EOF) {
            position++;
        }
        return token;
    }

    private void expect(TokenType type, String message) {
        if (!check(type)) {
            throw new ParserException(message + ", got '" + current().value() +
                "' at position " + current().position());
        }
        advance();
    }

    // ========== Result Types ==========

    /**
     * Result of parsing
     */
    public record ParseResult(boolean success, AstNode ast, String error, Integer errorPosition) {}

    /**
     * Exception thrown during parsing
     */
    public static class ParserException extends RuntimeException {
        public ParserException(String message) {
            super(message);
        }
    }
}
