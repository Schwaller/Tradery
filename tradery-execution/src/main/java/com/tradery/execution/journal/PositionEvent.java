package com.tradery.execution.journal;

import com.tradery.exchange.model.OrderSide;
import com.tradery.execution.position.LivePosition;

/**
 * Journal event for position lifecycle (opened, modified, closed).
 */
public class PositionEvent extends ExecutionEvent {

    private String action; // opened, modified, closed
    private String strategyId;
    private String symbol;
    private OrderSide side;
    private double quantity;
    private double avgEntryPrice;
    private Double exitPrice;
    private Double realizedPnl;

    // For Jackson
    public PositionEvent() {}

    private PositionEvent(String action, LivePosition position) {
        this.action = action;
        this.strategyId = position.getStrategyId();
        this.symbol = position.getSymbol();
        this.side = position.getSide();
        this.quantity = position.getQuantity();
        this.avgEntryPrice = position.getAvgEntryPrice();
    }

    public static PositionEvent opened(LivePosition position) {
        return new PositionEvent("opened", position);
    }

    public static PositionEvent modified(LivePosition position) {
        return new PositionEvent("modified", position);
    }

    public static PositionEvent closed(LivePosition position, double exitPrice, double pnl) {
        PositionEvent event = new PositionEvent("closed", position);
        event.exitPrice = exitPrice;
        event.realizedPnl = pnl;
        return event;
    }

    @Override
    public String getEventType() { return "position"; }

    @Override
    public String getSummary() {
        if ("closed".equals(action)) {
            return String.format("[%s] %s %s %s PnL=%.2f",
                    action, strategyId, symbol, side, realizedPnl);
        }
        return String.format("[%s] %s %s %s %.4f @ %.2f",
                action, strategyId, symbol, side, quantity, avgEntryPrice);
    }

    // Getters for Jackson
    public String getAction() { return action; }
    public String getStrategyId() { return strategyId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public double getQuantity() { return quantity; }
    public double getAvgEntryPrice() { return avgEntryPrice; }
    public Double getExitPrice() { return exitPrice; }
    public Double getRealizedPnl() { return realizedPnl; }
}
