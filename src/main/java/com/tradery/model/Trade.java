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
    String exitZone,    // Name of the exit zone that triggered the exit
    // Trade analytics for AI improvement
    Double mfe,         // Maximum Favorable Excursion (best P&L % during trade)
    Double mae,         // Maximum Adverse Excursion (worst drawdown % during trade)
    Integer mfeBar,     // Bar when MFE was reached
    Integer maeBar      // Bar when MAE was reached
) {
    /**
     * Create a new open trade
     */
    public static Trade open(String strategyId, String side, int bar, long timestamp,
                             double price, double quantity, double commission, String groupId) {
        String id = "trade-" + timestamp + "-" + (int)(Math.random() * 10000);
        return new Trade(
            id, strategyId, side, bar, timestamp, price, quantity,
            null, null, null, null, null, commission, null, groupId, null,
            null, null, null, null  // MFE/MAE analytics - populated on close
        );
    }

    /**
     * Create a rejected trade (signal fired but no capital available)
     */
    public static Trade rejected(String strategyId, String side, int bar, long timestamp, double price) {
        String id = "trade-" + timestamp + "-" + (int)(Math.random() * 10000);
        return new Trade(
            id, strategyId, side, bar, timestamp, price, 0,
            bar, timestamp, price, null, null, null, "rejected", null, null,
            null, null, null, null  // No analytics for rejected trades
        );
    }

    /**
     * Create an expired trade (pending order expired without filling)
     */
    public static Trade expired(String strategyId, String side, int signalBar, long signalTimestamp,
                                double signalPrice, int expirationBar) {
        String id = "trade-" + signalTimestamp + "-" + (int)(Math.random() * 10000);
        return new Trade(
            id, strategyId, side, signalBar, signalTimestamp, signalPrice, 0,
            expirationBar, signalTimestamp, signalPrice, null, null, null, "expired", null, null,
            null, null, null, null  // No analytics for expired orders
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
        return closeWithAnalytics(exitBar, exitTime, exitPrice, commissionRate, exitReason, exitZone, null, null, null, null);
    }

    /**
     * Close this trade with full analytics (MFE/MAE tracking)
     */
    public Trade closeWithAnalytics(int exitBar, long exitTime, double exitPrice, double commissionRate,
                                    String exitReason, String exitZone,
                                    Double mfe, Double mae, Integer mfeBar, Integer maeBar) {
        return partialCloseWithAnalytics(exitBar, exitTime, exitPrice, quantity, commissionRate, exitReason, exitZone,
                                         mfe, mae, mfeBar, maeBar);
    }

    /**
     * Partially close this trade - close a portion of the position.
     * Creates a new Trade record representing the closed portion.
     */
    public Trade partialClose(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                              double commissionRate, String exitReason, String exitZone) {
        return partialCloseWithAnalytics(exitBar, exitTime, exitPrice, exitQuantity, commissionRate, exitReason, exitZone,
                                         null, null, null, null);
    }

    /**
     * Partially close with analytics - the core close method.
     */
    public Trade partialCloseWithAnalytics(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                                           double commissionRate, String exitReason, String exitZone,
                                           Double mfe, Double mae, Integer mfeBar, Integer maeBar) {
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
            exitBar, exitTime, exitPrice, netPnl, pnlPct, totalCommission, exitReason, groupId, exitZone,
            mfe, mae, mfeBar, maeBar
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

    /**
     * Get duration in bars (null if trade is open)
     */
    public Integer duration() {
        return exitBar != null ? exitBar - entryBar : null;
    }

    /**
     * Get capture ratio: actual P&L / MFE (how much of potential profit was captured)
     * Returns null if MFE not available, 0 if MFE is 0
     * Value > 1 means exited better than peak (rare), < 1 means left money on table
     */
    public Double captureRatio() {
        if (pnlPercent == null || mfe == null) return null;
        if (mfe <= 0) return pnlPercent > 0 ? 1.0 : 0.0;  // No favorable excursion
        return pnlPercent / mfe;
    }

    /**
     * Get pain ratio: MAE / MFE (how much pain endured vs reward)
     * Lower is better. null if either metric unavailable
     */
    public Double painRatio() {
        if (mae == null || mfe == null || mfe <= 0) return null;
        return Math.abs(mae) / mfe;
    }

    /**
     * Get bars from entry to MFE (null if not available)
     */
    public Integer barsToMfe() {
        return mfeBar != null ? mfeBar - entryBar : null;
    }

    /**
     * Get bars from entry to MAE (null if not available)
     */
    public Integer barsToMae() {
        return maeBar != null ? maeBar - entryBar : null;
    }
}
