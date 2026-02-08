package com.tradery.exchange.model;

public record AssetInfo(
    String symbol,
    String baseAsset,
    String quoteAsset,
    int szDecimals,
    int pxDecimals,
    double minOrderSize,
    double maxLeverage,
    boolean marginTradingEnabled,
    int universeIndex
) {
}
