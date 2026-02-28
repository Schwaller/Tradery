package com.tradery.dataservice.symbols;

import java.time.Instant;

/**
 * Represents a trading pair on an exchange.
 * First-class spot/perp support via marketType field.
 */
public record TradingPair(
    String exchange,         // Exchange name (binance, okx, bybit, etc.)
    MarketType marketType,   // SPOT or PERP (first-class dimension)
    String symbol,           // Full pair symbol (BTCUSDT, BTC-USDT-SWAP, etc.)
    String baseSymbol,       // Base asset (BTC)
    String quoteSymbol,      // Quote asset (USDT)
    String coingeckoBaseId,  // CoinGecko ID of base (bitcoin)
    String coingeckoQuoteId, // CoinGecko ID of quote (tether)
    boolean isActive,        // Whether pair is currently tradeable
    Instant firstSeen,       // When first discovered
    Instant lastSeen,        // When last seen in sync
    double tickSize          // Exchange-defined price tick size (e.g., 0.10 for BTCUSDT)
) {
    /**
     * Create a new trading pair with current timestamp.
     */
    public static TradingPair create(
            String exchange,
            MarketType marketType,
            String symbol,
            String baseSymbol,
            String quoteSymbol,
            String coingeckoBaseId,
            String coingeckoQuoteId) {
        Instant now = Instant.now();
        return new TradingPair(
            exchange, marketType, symbol, baseSymbol, quoteSymbol,
            coingeckoBaseId, coingeckoQuoteId, true, now, now, 0
        );
    }

    /**
     * Create a new trading pair with tick size.
     */
    public static TradingPair create(
            String exchange,
            MarketType marketType,
            String symbol,
            String baseSymbol,
            String quoteSymbol,
            String coingeckoBaseId,
            String coingeckoQuoteId,
            double tickSize) {
        Instant now = Instant.now();
        return new TradingPair(
            exchange, marketType, symbol, baseSymbol, quoteSymbol,
            coingeckoBaseId, coingeckoQuoteId, true, now, now, tickSize
        );
    }

    /**
     * Create an updated version with refreshed lastSeen.
     */
    public TradingPair withLastSeen(Instant lastSeen) {
        return new TradingPair(
            exchange, marketType, symbol, baseSymbol, quoteSymbol,
            coingeckoBaseId, coingeckoQuoteId, isActive, firstSeen, lastSeen, tickSize
        );
    }

    /**
     * Create an updated version with active flag changed.
     */
    public TradingPair withActive(boolean active) {
        return new TradingPair(
            exchange, marketType, symbol, baseSymbol, quoteSymbol,
            coingeckoBaseId, coingeckoQuoteId, active, firstSeen, lastSeen, tickSize
        );
    }
}
