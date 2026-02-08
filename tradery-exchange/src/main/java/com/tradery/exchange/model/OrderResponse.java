package com.tradery.exchange.model;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
    String orderId,
    String clientOrderId,
    String symbol,
    OrderSide side,
    OrderType type,
    OrderStatus status,
    double requestedQuantity,
    double filledQuantity,
    Double avgFillPrice,
    List<Fill> fills,
    Instant createdAt,
    Instant updatedAt
) {
    public boolean isFilled() {
        return status == OrderStatus.FILLED;
    }

    public boolean isOpen() {
        return status == OrderStatus.OPEN || status == OrderStatus.PARTIALLY_FILLED;
    }

    public double remainingQuantity() {
        return requestedQuantity - filledQuantity;
    }
}
