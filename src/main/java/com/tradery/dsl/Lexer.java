package com.tradery.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DSL Lexer - tokenizes strategy expressions
 */
public class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
        // Indicators
        Map.entry("SMA", TokenType.INDICATOR),
        Map.entry("EMA", TokenType.INDICATOR),
        Map.entry("RSI", TokenType.INDICATOR),
        Map.entry("MACD", TokenType.INDICATOR),
        Map.entry("BBANDS", TokenType.INDICATOR),
        Map.entry("ATR", TokenType.INDICATOR),
        Map.entry("ADX", TokenType.INDICATOR),
        Map.entry("PLUS_DI", TokenType.INDICATOR),
        Map.entry("MINUS_DI", TokenType.INDICATOR),

        // Range functions
        Map.entry("HIGH_OF", TokenType.RANGE_FUNC),
        Map.entry("LOW_OF", TokenType.RANGE_FUNC),

        // Volume functions
        Map.entry("AVG_VOLUME", TokenType.VOLUME_FUNC),

        // Time functions
        Map.entry("DAYOFWEEK", TokenType.TIME_FUNC),
        Map.entry("HOUR", TokenType.TIME_FUNC),
        Map.entry("DAY", TokenType.TIME_FUNC),
        Map.entry("MONTH", TokenType.TIME_FUNC),

        // Moon functions
        Map.entry("MOON_PHASE", TokenType.MOON_FUNC),

        // Holiday functions
        Map.entry("IS_US_HOLIDAY", TokenType.HOLIDAY_FUNC),

        // FOMC functions
        Map.entry("IS_FOMC_MEETING", TokenType.FOMC_FUNC),

        // Orderflow functions
        Map.entry("VWAP", TokenType.ORDERFLOW_FUNC),
        Map.entry("POC", TokenType.ORDERFLOW_FUNC),
        Map.entry("VAH", TokenType.ORDERFLOW_FUNC),
        Map.entry("VAL", TokenType.ORDERFLOW_FUNC),
        Map.entry("DELTA", TokenType.ORDERFLOW_FUNC),
        Map.entry("CUM_DELTA", TokenType.ORDERFLOW_FUNC),
        Map.entry("WHALE_DELTA", TokenType.ORDERFLOW_FUNC),
        Map.entry("WHALE_BUY_VOL", TokenType.ORDERFLOW_FUNC),
        Map.entry("WHALE_SELL_VOL", TokenType.ORDERFLOW_FUNC),
        Map.entry("LARGE_TRADE_COUNT", TokenType.ORDERFLOW_FUNC),

        // Funding rate functions
        Map.entry("FUNDING", TokenType.FUNDING_FUNC),
        Map.entry("FUNDING_8H", TokenType.FUNDING_FUNC),

        // Price references
        Map.entry("price", TokenType.PRICE),
        Map.entry("open", TokenType.PRICE),
        Map.entry("high", TokenType.PRICE),
        Map.entry("low", TokenType.PRICE),
        Map.entry("close", TokenType.PRICE),
        Map.entry("volume", TokenType.PRICE),

        // Logical operators
        Map.entry("AND", TokenType.LOGICAL),
        Map.entry("OR", TokenType.LOGICAL),

        // Cross operators
        Map.entry("crosses_above", TokenType.CROSS_OP),
        Map.entry("crosses_below", TokenType.CROSS_OP),

        // Boolean literals
        Map.entry("true", TokenType.BOOLEAN),
        Map.entry("false", TokenType.BOOLEAN),

        // Properties
        Map.entry("signal", TokenType.PROPERTY),
        Map.entry("histogram", TokenType.PROPERTY),
        Map.entry("upper", TokenType.PROPERTY),
        Map.entry("lower", TokenType.PROPERTY),
        Map.entry("middle", TokenType.PROPERTY),
        Map.entry("line", TokenType.PROPERTY)
    );

    private final String source;
    private int position = 0;
    private int line = 1;
    private final List<Token> tokens = new ArrayList<>();

    public Lexer(String source) {
        this.source = source;
    }

    /**
     * Tokenize the source string
     */
    public List<Token> tokenize() {
        tokens.clear();
        position = 0;
        line = 1;

        while (position < source.length()) {
            skipWhitespace();

            if (position >= source.length()) {
                break;
            }

            char c = source.charAt(position);

            // Single character tokens
            switch (c) {
                case '(' -> { addToken(TokenType.LPAREN, "("); position++; continue; }
                case ')' -> { addToken(TokenType.RPAREN, ")"); position++; continue; }
                case ',' -> { addToken(TokenType.COMMA, ","); position++; continue; }
                case '.' -> { addToken(TokenType.DOT, "."); position++; continue; }
                case '*' -> { addToken(TokenType.MULTIPLY, "*"); position++; continue; }
                case '/' -> { addToken(TokenType.DIVIDE, "/"); position++; continue; }
                case '+' -> { addToken(TokenType.PLUS, "+"); position++; continue; }
            }

            // Minus or negative number
            if (c == '-') {
                if (position + 1 < source.length() && Character.isDigit(source.charAt(position + 1))) {
                    readNumber();
                } else {
                    addToken(TokenType.MINUS, "-");
                    position++;
                }
                continue;
            }

            // Comparison operators
            if (c == '>') {
                if (peek(1) == '=') {
                    addToken(TokenType.OPERATOR, ">=");
                    position += 2;
                } else {
                    addToken(TokenType.OPERATOR, ">");
                    position++;
                }
                continue;
            }

            if (c == '<') {
                if (peek(1) == '=') {
                    addToken(TokenType.OPERATOR, "<=");
                    position += 2;
                } else {
                    addToken(TokenType.OPERATOR, "<");
                    position++;
                }
                continue;
            }

            if (c == '=' && peek(1) == '=') {
                addToken(TokenType.OPERATOR, "==");
                position += 2;
                continue;
            }

            // Numbers
            if (Character.isDigit(c)) {
                readNumber();
                continue;
            }

            // Identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                readIdentifier();
                continue;
            }

            throw new LexerException("Unexpected character '" + c + "' at position " + position);
        }

        tokens.add(Token.eof(position));
        return tokens;
    }

    private void skipWhitespace() {
        while (position < source.length()) {
            char c = source.charAt(position);
            if (c == ' ' || c == '\t' || c == '\r') {
                position++;
            } else if (c == '\n') {
                line++;
                position++;
            } else {
                break;
            }
        }
    }

    private char peek(int offset) {
        int pos = position + offset;
        if (pos >= source.length()) {
            return '\0';
        }
        return source.charAt(pos);
    }

    private void readNumber() {
        int start = position;
        boolean hasDecimal = false;

        // Handle negative numbers
        if (source.charAt(position) == '-') {
            position++;
        }

        while (position < source.length()) {
            char c = source.charAt(position);
            if (Character.isDigit(c)) {
                position++;
            } else if (c == '.' && !hasDecimal) {
                hasDecimal = true;
                position++;
            } else {
                break;
            }
        }

        String value = source.substring(start, position);
        tokens.add(new Token(TokenType.NUMBER, value, start, line));
    }

    private void readIdentifier() {
        int start = position;

        while (position < source.length()) {
            char c = source.charAt(position);
            if (Character.isLetterOrDigit(c) || c == '_') {
                position++;
            } else {
                break;
            }
        }

        String value = source.substring(start, position);
        TokenType type = KEYWORDS.get(value);

        if (type != null) {
            tokens.add(new Token(type, value, start, line));
        } else {
            throw new LexerException("Unknown identifier '" + value + "' at position " + start);
        }
    }

    private void addToken(TokenType type, String value) {
        tokens.add(new Token(type, value, position, line));
    }

    /**
     * Convenience function to tokenize a string
     */
    public static List<Token> tokenize(String source) {
        return new Lexer(source).tokenize();
    }

    /**
     * Exception thrown when lexer encounters an error
     */
    public static class LexerException extends RuntimeException {
        public LexerException(String message) {
            super(message);
        }
    }
}
