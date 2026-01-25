package com.tradery.dataservice.coingecko;

/**
 * Represents a coin from CoinGecko's /coins/list endpoint.
 * Used for symbol → coingecko_id mapping.
 */
public record CoinInfo(
    String id,       // CoinGecko ID (bitcoin, ethereum, etc.)
    String symbol,   // Ticker symbol (btc, eth, etc.)
    String name      // Full name (Bitcoin, Ethereum, etc.)
) {
}
