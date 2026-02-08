package com.tradery.execution.journal;

import com.tradery.exchange.model.Fill;
import com.tradery.exchange.model.OrderSide;
import com.tradery.exchange.model.OrderStatus;
import com.tradery.execution.order.LiveOrder;

/**
 * Journal event for order lifecycle (placed, filled, cancelled, rejected).
 */
public class OrderEvent extends ExecutionEvent {

    private String action; // placed, filled, cancelled, rejected
    private String orderId;
    private String strategyId;
    private String symbol;
    private OrderSide side;
    private OrderStatus status;
    private double requestedQuantity;
    private double filledQuantity;
    private Double fillPrice;
    private Double fillFee;

    // For Jackson
    public OrderEvent() {}

    private OrderEvent(String action, LiveOrder order) {
        this.action = action;
        this.orderId = order.getExchangeOrderId();
        this.strategyId = order.getStrategyId();
        this.symbol = order.getSymbol();
        this.side = order.getSide();
        this.status = order.getStatus();
        this.requestedQuantity = order.getRequestedQuantity();
        this.filledQuantity = order.getFilledQuantity();
    }

    public static OrderEvent placed(LiveOrder order) {
        return new OrderEvent("placed", order);
    }

    public static OrderEvent filled(LiveOrder order, Fill fill) {
        OrderEvent event = new OrderEvent("filled", order);
        event.fillPrice = fill.price();
        event.fillFee = fill.fee();
        return event;
    }

    public static OrderEvent cancelled(LiveOrder order) {
        return new OrderEvent("cancelled", order);
    }

    public static OrderEvent rejected(LiveOrder order, String reason) {
        OrderEvent event = new OrderEvent("rejected", order);
        return event;
    }

    @Override
    public String getEventType() { return "order"; }

    @Override
    public String getSummary() {
        return String.format("[%s] %s %s %s %.4f %s @ %s",
                action, strategyId, side, symbol, requestedQuantity,
                status, fillPrice != null ? String.format("%.2f", fillPrice) : "pending");
    }

    // Getters for Jackson
    public String getAction() { return action; }
    public String getOrderId() { return orderId; }
    public String getStrategyId() { return strategyId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public OrderStatus getStatus() { return status; }
    public double getRequestedQuantity() { return requestedQuantity; }
    public double getFilledQuantity() { return filledQuantity; }
    public Double getFillPrice() { return fillPrice; }
    public Double getFillFee() { return fillFee; }
}
