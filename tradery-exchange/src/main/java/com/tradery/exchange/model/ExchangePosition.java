package com.tradery.exchange.model;

import java.time.Instant;

public record ExchangePosition(
    String symbol,
    OrderSide side,
    double quantity,
    double entryPrice,
    double markPrice,
    double unrealizedPnl,
    double realizedPnl,
    int leverage,
    MarginMode marginMode,
    double liquidationPrice,
    double marginUsed,
    Instant updatedAt
) {
    public boolean isLong() {
        return side == OrderSide.BUY;
    }

    public boolean isShort() {
        return side == OrderSide.SELL;
    }

    public double notionalValue() {
        return Math.abs(quantity) * markPrice;
    }
}
