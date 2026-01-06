package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a completed or open trade.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Trade(
    String id,
    String strategyId,
    String side,       // "long" or "short"
    int entryBar,
    long entryTime,
    double entryPrice,
    double quantity,
    Integer exitBar,
    Long exitTime,
    Double exitPrice,
    Double pnl,
    Double pnlPercent,
    Double commission,
    String exitReason,  // "signal", "stop_loss", "take_profit", "trailing_stop"
    String groupId,     // Groups DCA entries together
    String exitZone     // Name of the exit zone that triggered the exit
) {
    /**
     * Create a new open trade
     */
    public static Trade open(String strategyId, String side, int bar, long timestamp,
                             double price, double quantity, double commission, String groupId) {
        String id = "trade-" + timestamp + "-" + (int)(Math.random() * 10000);
        return new Trade(
            id, strategyId, side, bar, timestamp, price, quantity,
            null, null, null, null, null, commission, null, groupId, null
        );
    }

    /**
     * Create a rejected trade (signal fired but no capital available)
     */
    public static Trade rejected(String strategyId, String side, int bar, long timestamp, double price) {
        String id = "trade-" + timestamp + "-" + (int)(Math.random() * 10000);
        return new Trade(
            id, strategyId, side, bar, timestamp, price, 0,
            bar, timestamp, price, null, null, null, "rejected", null, null
        );
    }

    /**
     * Close this trade and calculate P&L
     */
    public Trade close(int exitBar, long exitTime, double exitPrice, double commission) {
        return close(exitBar, exitTime, exitPrice, commission, "signal", null);
    }

    /**
     * Close this trade with exit reason and calculate P&L
     */
    public Trade close(int exitBar, long exitTime, double exitPrice, double commissionRate, String exitReason) {
        return close(exitBar, exitTime, exitPrice, commissionRate, exitReason, null);
    }

    /**
     * Close this trade with exit reason, zone, and calculate P&L
     */
    public Trade close(int exitBar, long exitTime, double exitPrice, double commissionRate, String exitReason, String exitZone) {
        return partialClose(exitBar, exitTime, exitPrice, quantity, commissionRate, exitReason, exitZone);
    }

    /**
     * Partially close this trade - close a portion of the position.
     * Creates a new Trade record representing the closed portion.
     */
    public Trade partialClose(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                              double commissionRate, String exitReason, String exitZone) {
        double grossPnl = (exitPrice - entryPrice) * exitQuantity;
        if ("short".equals(side)) {
            grossPnl = -grossPnl;
        }

        // Commission is a percentage rate - calculate actual dollar amounts
        // For partial close, entry commission is proportional to exit quantity
        double entryCommissionRate = this.commission != null ? this.commission : 0;
        double entryCommission = entryPrice * exitQuantity * entryCommissionRate;
        double exitCommission = exitPrice * exitQuantity * commissionRate;
        double totalCommission = entryCommission + exitCommission;

        double netPnl = grossPnl - totalCommission;
        double pnlPct = (netPnl / (entryPrice * exitQuantity)) * 100;

        // Generate unique ID for partial exit
        String exitId = id + "-exit-" + exitBar;

        return new Trade(
            exitId, strategyId, side, entryBar, entryTime, entryPrice, exitQuantity,
            exitBar, exitTime, exitPrice, netPnl, pnlPct, totalCommission, exitReason, groupId, exitZone
        );
    }

    /**
     * Check if this trade is still open
     */
    public boolean isOpen() {
        return exitTime == null;
    }

    /**
     * Check if this was a winning trade
     */
    public boolean isWinner() {
        return pnl != null && pnl > 0;
    }

    /**
     * Get the trade value (entry price * quantity)
     */
    public double value() {
        return entryPrice * quantity;
    }
}
