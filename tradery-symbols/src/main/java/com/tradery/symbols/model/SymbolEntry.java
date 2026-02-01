package com.tradery.symbols.model;

/**
 * A trading pair entry from the symbols database.
 */
public record SymbolEntry(
    String symbol,
    String exchange,
    String marketType,
    String base,
    String quote,
    String coingeckoId
) {}
