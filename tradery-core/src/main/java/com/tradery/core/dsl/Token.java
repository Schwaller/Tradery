package com.tradery.core.dsl;

/**
 * Token produced by the lexer
 */
public record Token(TokenType type, String value, int position, int line) {

    public static Token eof(int position) {
        return new Token(TokenType.EOF, "", position, 1);
    }
}
