package com.tradery.exchange.model;

import java.time.Instant;

public record Fill(
    String tradeId,
    String orderId,
    String symbol,
    OrderSide side,
    double price,
    double quantity,
    double fee,
    String feeCurrency,
    Instant timestamp
) {
    public double notionalValue() {
        return price * quantity;
    }
}
