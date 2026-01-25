package com.tradery.dataservice.symbols;

import java.time.Instant;

/**
 * Represents a tradeable asset on an exchange.
 * Maps exchange-specific symbol to canonical CoinGecko ID.
 */
public record ExchangeAsset(
    String exchange,        // Exchange name (binance, okx, bybit, etc.)
    String symbol,          // Exchange-specific symbol (BTC, XBT, etc.)
    String coingeckoId,     // CoinGecko canonical ID (bitcoin, ethereum, etc.)
    String coinName,        // Human-readable name (Bitcoin, Ethereum, etc.)
    boolean isActive,       // Whether asset is currently tradeable
    Instant firstSeen,      // When first discovered
    Instant lastSeen        // When last seen in sync
) {
    /**
     * Create a new asset with current timestamp.
     */
    public static ExchangeAsset create(String exchange, String symbol, String coingeckoId, String coinName) {
        Instant now = Instant.now();
        return new ExchangeAsset(exchange, symbol, coingeckoId, coinName, true, now, now);
    }

    /**
     * Create an updated version with refreshed lastSeen.
     */
    public ExchangeAsset withLastSeen(Instant lastSeen) {
        return new ExchangeAsset(exchange, symbol, coingeckoId, coinName, isActive, firstSeen, lastSeen);
    }

    /**
     * Create an updated version with active flag changed.
     */
    public ExchangeAsset withActive(boolean active) {
        return new ExchangeAsset(exchange, symbol, coingeckoId, coinName, active, firstSeen, lastSeen);
    }
}
