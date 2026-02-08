package com.tradery.execution.position;

import com.tradery.exchange.model.MarginMode;
import com.tradery.exchange.model.OrderSide;

import java.time.Instant;

/**
 * Current position state for a strategy:symbol pair.
 */
public class LivePosition {

    private final String strategyId;
    private final String symbol;
    private OrderSide side;
    private double quantity;
    private double avgEntryPrice;
    private double realizedPnl;
    private int leverage;
    private MarginMode marginMode;
    private final Instant openedAt;
    private Instant updatedAt;

    public LivePosition(String strategyId, String symbol, OrderSide side,
                        double quantity, double avgEntryPrice) {
        this.strategyId = strategyId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.avgEntryPrice = avgEntryPrice;
        this.leverage = 1;
        this.marginMode = MarginMode.CROSS;
        this.openedAt = Instant.now();
        this.updatedAt = this.openedAt;
    }

    /**
     * Apply a fill to this position. Returns realized PnL if the fill reduces the position.
     */
    public double applyFill(OrderSide fillSide, double fillQty, double fillPrice) {
        double pnl = 0;
        updatedAt = Instant.now();

        if (fillSide == side) {
            // Adding to position — recalculate average
            double totalCost = avgEntryPrice * quantity + fillPrice * fillQty;
            quantity += fillQty;
            avgEntryPrice = quantity > 0 ? totalCost / quantity : 0;
        } else {
            // Reducing or flipping
            double reduceQty = Math.min(fillQty, quantity);
            pnl = (fillPrice - avgEntryPrice) * reduceQty * (side == OrderSide.BUY ? 1 : -1);
            realizedPnl += pnl;

            quantity -= reduceQty;
            double remaining = fillQty - reduceQty;

            if (remaining > 0) {
                // Flipped side
                side = fillSide;
                quantity = remaining;
                avgEntryPrice = fillPrice;
            }
        }

        return pnl;
    }

    public boolean isClosed() {
        return quantity == 0;
    }

    public double unrealizedPnl(double markPrice) {
        return (markPrice - avgEntryPrice) * quantity * (side == OrderSide.BUY ? 1 : -1);
    }

    public double notionalValue(double markPrice) {
        return Math.abs(quantity) * markPrice;
    }

    // Getters
    public String getStrategyId() { return strategyId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public double getQuantity() { return quantity; }
    public double getAvgEntryPrice() { return avgEntryPrice; }
    public double getRealizedPnl() { return realizedPnl; }
    public int getLeverage() { return leverage; }
    public void setLeverage(int leverage) { this.leverage = leverage; }
    public MarginMode getMarginMode() { return marginMode; }
    public void setMarginMode(MarginMode marginMode) { this.marginMode = marginMode; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
