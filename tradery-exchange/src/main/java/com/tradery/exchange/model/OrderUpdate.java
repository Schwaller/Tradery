package com.tradery.exchange.model;

import java.time.Instant;

public record OrderUpdate(
    String orderId,
    String symbol,
    OrderSide side,
    OrderType type,
    OrderStatus status,
    double requestedQuantity,
    double filledQuantity,
    Double avgFillPrice,
    Double triggerPrice,
    Instant timestamp
) {
}
