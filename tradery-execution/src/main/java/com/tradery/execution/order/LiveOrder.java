package com.tradery.execution.order;

import com.tradery.exchange.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Local order state linked to an exchange orderId.
 */
public class LiveOrder {

    private final String exchangeOrderId;
    private final String strategyId;
    private final String symbol;
    private final OrderSide side;
    private final OrderType type;
    private final double requestedQuantity;
    private final Instant createdAt;

    private OrderStatus status;
    private double filledQuantity;
    private double avgFillPrice;
    private final List<Fill> fills = new ArrayList<>();
    private Instant updatedAt;

    public LiveOrder(String exchangeOrderId, String strategyId, OrderRequest request) {
        this.exchangeOrderId = exchangeOrderId;
        this.strategyId = strategyId;
        this.symbol = request.symbol();
        this.side = request.side();
        this.type = request.type();
        this.requestedQuantity = request.quantity();
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void applyFill(Fill fill) {
        fills.add(fill);
        double totalValue = avgFillPrice * filledQuantity + fill.price() * fill.quantity();
        filledQuantity += fill.quantity();
        avgFillPrice = filledQuantity > 0 ? totalValue / filledQuantity : 0;
        updatedAt = Instant.now();

        if (filledQuantity >= requestedQuantity) {
            status = OrderStatus.FILLED;
        } else {
            status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public String getExchangeOrderId() { return exchangeOrderId; }
    public String getStrategyId() { return strategyId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public OrderType getType() { return type; }
    public double getRequestedQuantity() { return requestedQuantity; }
    public OrderStatus getStatus() { return status; }
    public double getFilledQuantity() { return filledQuantity; }
    public double getAvgFillPrice() { return avgFillPrice; }
    public List<Fill> getFills() { return List.copyOf(fills); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isTerminal() {
        return status == OrderStatus.FILLED || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED;
    }

    public double getRemainingQuantity() {
        return requestedQuantity - filledQuantity;
    }
}
