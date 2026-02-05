package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

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
    Integer maeBar,     // Bar when MAE was reached
    // Phase context for AI analysis
    List<String> activePhasesAtEntry,  // Phase IDs that were active when trade opened
    List<String> activePhasesAtExit,   // Phase IDs that were active when trade closed
    // Indicator values for AI analysis (what triggered the trade)
    Map<String, Double> entryIndicators,  // Indicator values at entry (e.g., "RSI(14)" -> 28.5)
    Map<String, Double> exitIndicators,   // Indicator values at exit
    Map<String, Double> mfeIndicators,    // Indicator values at MFE point (best price reached)
    Map<String, Double> maeIndicators,    // Indicator values at MAE point (worst drawdown)
    // Holding costs (funding fees for futures, interest for margin)
    Double holdingCosts,                  // Accumulated holding costs (positive = cost, negative = earnings)
    // Context analysis for AI (looking at bars before entry and after exit)
    Integer betterEntryBar,               // Bar within context window that had better entry price (null if entry was optimal)
    Double betterEntryPrice,              // Price at that bar
    Double betterEntryImprovement,        // % improvement in potential PnL if entered at better price
    Integer betterExitBar,                // Bar within context window after exit that had better exit price (null if exit was optimal)
    Double betterExitPrice,               // Price at that bar
    Double betterExitImprovement,         // % improvement in PnL if exited at better price
    // Footprint metrics for orderflow analysis (requires aggTrades data)
    Map<String, Double> entryFootprintMetrics,  // Footprint metrics at entry (imbalanceAtPoc, stackedBuyImbalances, etc.)
    Map<String, Double> exitFootprintMetrics,   // Footprint metrics at exit
    Map<String, Double> mfeFootprintMetrics,    // Footprint metrics at MFE point
    Map<String, Double> maeFootprintMetrics     // Footprint metrics at MAE point
) {
    /** Number of context bars to analyze before/after trades */
    public static final int CONTEXT_BARS = 15;
    /**
     * Create a new open trade
     */
    public static Trade open(String strategyId, String side, int bar, long timestamp,
                             double price, double quantity, double commission, String groupId) {
        return open(strategyId, side, bar, timestamp, price, quantity, commission, groupId, null, null);
    }

    /**
     * Create a new open trade with phase context
     */
    public static Trade open(String strategyId, String side, int bar, long timestamp,
                             double price, double quantity, double commission, String groupId,
                             List<String> activePhasesAtEntry) {
        return open(strategyId, side, bar, timestamp, price, quantity, commission, groupId, activePhasesAtEntry, null);
    }

    /**
     * Create a new open trade with phase context and indicator values
     */
    public static Trade open(String strategyId, String side, int bar, long timestamp,
                             double price, double quantity, double commission, String groupId,
                             List<String> activePhasesAtEntry, Map<String, Double> entryIndicators) {
        return open(strategyId, side, bar, timestamp, price, quantity, commission, groupId,
                    activePhasesAtEntry, entryIndicators, null);
    }

    /**
     * Create a new open trade with phase context, indicator values, and footprint metrics
     */
    public static Trade open(String strategyId, String side, int bar, long timestamp,
                             double price, double quantity, double commission, String groupId,
                             List<String> activePhasesAtEntry, Map<String, Double> entryIndicators,
                             Map<String, Double> entryFootprintMetrics) {
        String id = "trade-" + timestamp + "-" + (int)(Math.random() * 10000);
        return new Trade(
            id, strategyId, side, bar, timestamp, price, quantity,
            null, null, null, null, null, commission, null, groupId, null,
            null, null, null, null,  // MFE/MAE analytics - populated on close
            activePhasesAtEntry, null,  // Phases at entry, exit populated on close
            entryIndicators, null,  // Indicators at entry, exit populated on close
            null, null,  // MFE/MAE indicators - populated on close
            null,  // Holding costs - populated on close
            null, null, null,  // Better entry context - populated on close
            null, null, null,  // Better exit context - populated on close
            entryFootprintMetrics, null, null, null  // Footprint metrics - exit/MFE/MAE populated on close
        );
    }

    /**
     * Create a rejected trade (signal fired but no capital available)
     */
    public static Trade rejected(String strategyId, String side, int bar, long timestamp, double price) {
        return rejected(strategyId, side, bar, timestamp, price, null, null);
    }

    /**
     * Create a rejected trade with phase context
     */
    public static Trade rejected(String strategyId, String side, int bar, long timestamp, double price,
                                  List<String> activePhases) {
        return rejected(strategyId, side, bar, timestamp, price, activePhases, null);
    }

    /**
     * Create a rejected trade with phase context and indicators
     */
    public static Trade rejected(String strategyId, String side, int bar, long timestamp, double price,
                                  List<String> activePhases, Map<String, Double> indicators) {
        String id = "trade-" + timestamp + "-" + (int)(Math.random() * 10000);
        return new Trade(
            id, strategyId, side, bar, timestamp, price, 0,
            bar, timestamp, price, null, null, null, "rejected", null, null,
            null, null, null, null,  // No analytics for rejected trades
            activePhases, activePhases,  // Same phases for entry/exit (instant rejection)
            indicators, indicators,  // Same indicators for entry/exit
            null, null,  // No MFE/MAE indicators for rejected trades
            null,  // No holding costs for rejected trades
            null, null, null,  // No better entry context for rejected trades
            null, null, null,  // No better exit context for rejected trades
            null, null, null, null  // No footprint metrics for rejected trades
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
            null, null, null, null,  // No analytics for expired orders
            null, null,  // No phase context for expired orders
            null, null,  // No indicator context for expired orders
            null, null,  // No MFE/MAE indicators for expired orders
            null,  // No holding costs for expired orders
            null, null, null,  // No better entry context for expired orders
            null, null, null,  // No better exit context for expired orders
            null, null, null, null  // No footprint metrics for expired orders
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
                                         mfe, mae, mfeBar, maeBar, null, null);
    }

    /**
     * Partially close this trade - close a portion of the position.
     * Creates a new Trade record representing the closed portion.
     */
    public Trade partialClose(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                              double commissionRate, String exitReason, String exitZone) {
        return partialCloseWithAnalytics(exitBar, exitTime, exitPrice, exitQuantity, commissionRate, exitReason, exitZone,
                                         null, null, null, null, null, null);
    }

    /**
     * Partially close with analytics - delegates to full version.
     */
    public Trade partialCloseWithAnalytics(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                                           double commissionRate, String exitReason, String exitZone,
                                           Double mfe, Double mae, Integer mfeBar, Integer maeBar) {
        return partialCloseWithAnalytics(exitBar, exitTime, exitPrice, exitQuantity, commissionRate, exitReason, exitZone,
                                         mfe, mae, mfeBar, maeBar, null, null);
    }

    /**
     * Partially close with analytics and phase context.
     */
    public Trade partialCloseWithAnalytics(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                                           double commissionRate, String exitReason, String exitZone,
                                           Double mfe, Double mae, Integer mfeBar, Integer maeBar,
                                           List<String> activePhasesAtExit) {
        return partialCloseWithAnalytics(exitBar, exitTime, exitPrice, exitQuantity, commissionRate, exitReason, exitZone,
                                         mfe, mae, mfeBar, maeBar, activePhasesAtExit, null, null, null);
    }

    /**
     * Partially close with analytics, phase context, and exit indicator values.
     */
    public Trade partialCloseWithAnalytics(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                                           double commissionRate, String exitReason, String exitZone,
                                           Double mfe, Double mae, Integer mfeBar, Integer maeBar,
                                           List<String> activePhasesAtExit, Map<String, Double> exitIndicators) {
        return partialCloseWithAnalytics(exitBar, exitTime, exitPrice, exitQuantity, commissionRate, exitReason, exitZone,
                                         mfe, mae, mfeBar, maeBar, activePhasesAtExit, exitIndicators, null, null);
    }

    /**
     * Partially close with full analytics, phase context, exit and MFE/MAE indicator values.
     */
    public Trade partialCloseWithAnalytics(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                                           double commissionRate, String exitReason, String exitZone,
                                           Double mfe, Double mae, Integer mfeBar, Integer maeBar,
                                           List<String> activePhasesAtExit, Map<String, Double> exitIndicators,
                                           Map<String, Double> mfeIndicators, Map<String, Double> maeIndicators) {
        return partialCloseWithAnalytics(exitBar, exitTime, exitPrice, exitQuantity, commissionRate,
            exitReason, exitZone, mfe, mae, mfeBar, maeBar, activePhasesAtExit, exitIndicators,
            mfeIndicators, maeIndicators, null, null, null, null, null, null, null);
    }

    /**
     * Partially close with full analytics, phase context, exit/MFE/MAE indicator values, and holding costs.
     */
    public Trade partialCloseWithAnalytics(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                                           double commissionRate, String exitReason, String exitZone,
                                           Double mfe, Double mae, Integer mfeBar, Integer maeBar,
                                           List<String> activePhasesAtExit, Map<String, Double> exitIndicators,
                                           Map<String, Double> mfeIndicators, Map<String, Double> maeIndicators,
                                           Double holdingCosts) {
        return partialCloseWithAnalytics(exitBar, exitTime, exitPrice, exitQuantity, commissionRate,
            exitReason, exitZone, mfe, mae, mfeBar, maeBar, activePhasesAtExit, exitIndicators,
            mfeIndicators, maeIndicators, holdingCosts, null, null, null, null, null, null);
    }

    /**
     * Partially close with full analytics including better entry context analysis for AI insights.
     */
    public Trade partialCloseWithAnalytics(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                                           double commissionRate, String exitReason, String exitZone,
                                           Double mfe, Double mae, Integer mfeBar, Integer maeBar,
                                           List<String> activePhasesAtExit, Map<String, Double> exitIndicators,
                                           Map<String, Double> mfeIndicators, Map<String, Double> maeIndicators,
                                           Double holdingCosts,
                                           Integer betterEntryBar, Double betterEntryPrice, Double betterEntryImprovement) {
        return partialCloseWithAnalytics(exitBar, exitTime, exitPrice, exitQuantity, commissionRate,
            exitReason, exitZone, mfe, mae, mfeBar, maeBar, activePhasesAtExit, exitIndicators,
            mfeIndicators, maeIndicators, holdingCosts,
            betterEntryBar, betterEntryPrice, betterEntryImprovement, null, null, null);
    }

    /**
     * Partially close with full analytics including context analysis for AI insights.
     * Delegates to the full version with null footprint metrics.
     */
    public Trade partialCloseWithAnalytics(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                                           double commissionRate, String exitReason, String exitZone,
                                           Double mfe, Double mae, Integer mfeBar, Integer maeBar,
                                           List<String> activePhasesAtExit, Map<String, Double> exitIndicators,
                                           Map<String, Double> mfeIndicators, Map<String, Double> maeIndicators,
                                           Double holdingCosts,
                                           Integer betterEntryBar, Double betterEntryPrice, Double betterEntryImprovement,
                                           Integer betterExitBar, Double betterExitPrice, Double betterExitImprovement) {
        return partialCloseWithAnalytics(exitBar, exitTime, exitPrice, exitQuantity, commissionRate,
            exitReason, exitZone, mfe, mae, mfeBar, maeBar, activePhasesAtExit, exitIndicators,
            mfeIndicators, maeIndicators, holdingCosts, betterEntryBar, betterEntryPrice, betterEntryImprovement,
            betterExitBar, betterExitPrice, betterExitImprovement, null, null, null);
    }

    /**
     * Partially close with full analytics including context analysis and footprint metrics for AI insights.
     * This is the core close method that all others delegate to.
     *
     * @param betterEntryBar Bar within CONTEXT_BARS before entry that had better price (null if entry was optimal)
     * @param betterEntryPrice The price at the better entry bar
     * @param betterEntryImprovement % improvement in potential PnL if entered at better price
     * @param betterExitBar Bar within CONTEXT_BARS after exit that had better price (null if exit was optimal)
     * @param betterExitPrice The price at the better exit bar
     * @param betterExitImprovement % improvement in PnL if exited at better price
     * @param exitFootprintMetrics Footprint metrics at exit (null if no aggTrades data)
     * @param mfeFootprintMetrics Footprint metrics at MFE point (null if no aggTrades data)
     * @param maeFootprintMetrics Footprint metrics at MAE point (null if no aggTrades data)
     */
    public Trade partialCloseWithAnalytics(int exitBar, long exitTime, double exitPrice, double exitQuantity,
                                           double commissionRate, String exitReason, String exitZone,
                                           Double mfe, Double mae, Integer mfeBar, Integer maeBar,
                                           List<String> activePhasesAtExit, Map<String, Double> exitIndicators,
                                           Map<String, Double> mfeIndicators, Map<String, Double> maeIndicators,
                                           Double holdingCosts,
                                           Integer betterEntryBar, Double betterEntryPrice, Double betterEntryImprovement,
                                           Integer betterExitBar, Double betterExitPrice, Double betterExitImprovement,
                                           Map<String, Double> exitFootprintMetrics,
                                           Map<String, Double> mfeFootprintMetrics,
                                           Map<String, Double> maeFootprintMetrics) {
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

        // Subtract holding costs from net PnL
        double holdingCostsAmount = holdingCosts != null ? holdingCosts : 0;
        double netPnl = grossPnl - totalCommission - holdingCostsAmount;
        double pnlPct = (netPnl / (entryPrice * exitQuantity)) * 100;

        // Generate unique ID for partial exit
        String exitId = id + "-exit-" + exitBar;

        return new Trade(
            exitId, strategyId, side, entryBar, entryTime, entryPrice, exitQuantity,
            exitBar, exitTime, exitPrice, netPnl, pnlPct, totalCommission, exitReason, groupId, exitZone,
            mfe, mae, mfeBar, maeBar,
            this.activePhasesAtEntry, activePhasesAtExit,  // Preserve entry phases, add exit phases
            this.entryIndicators, exitIndicators,  // Preserve entry indicators, add exit indicators
            mfeIndicators, maeIndicators,  // Indicator values at MFE/MAE points
            holdingCosts,  // Holding costs (funding fees or margin interest)
            betterEntryBar, betterEntryPrice, betterEntryImprovement,  // Better entry context
            betterExitBar, betterExitPrice, betterExitImprovement,  // Better exit context
            this.entryFootprintMetrics, exitFootprintMetrics,  // Footprint metrics
            mfeFootprintMetrics, maeFootprintMetrics
        );
    }

    /**
     * Check if this trade is still open
     */
    @JsonIgnore
    public boolean isOpen() {
        return exitTime == null;
    }

    /**
     * Check if this was a winning trade
     */
    @JsonIgnore
    public boolean isWinner() {
        return pnl != null && pnl > 0;
    }

    /**
     * Get the trade value (entry price * quantity)
     */
    @JsonIgnore
    public double value() {
        return entryPrice * quantity;
    }

    /**
     * Get duration in bars (null if trade is open)
     */
    @JsonIgnore
    public Integer duration() {
        return exitBar != null ? exitBar - entryBar : null;
    }

    /**
     * Get capture ratio: actual P&L / MFE (how much of potential profit was captured)
     * Returns null if MFE not available, 0 if MFE is 0
     * Value > 1 means exited better than peak (rare), < 1 means left money on table
     */
    @JsonIgnore
    public Double captureRatio() {
        if (pnlPercent == null || mfe == null) return null;
        if (mfe <= 0) return pnlPercent > 0 ? 1.0 : 0.0;  // No favorable excursion
        return pnlPercent / mfe;
    }

    /**
     * Get pain ratio: MAE / MFE (how much pain endured vs reward)
     * Lower is better. null if either metric unavailable
     */
    @JsonIgnore
    public Double painRatio() {
        if (mae == null || mfe == null || mfe <= 0) return null;
        return Math.abs(mae) / mfe;
    }

    /**
     * Get bars from entry to MFE (null if not available)
     */
    @JsonIgnore
    public Integer barsToMfe() {
        return mfeBar != null ? mfeBar - entryBar : null;
    }

    /**
     * Get bars from entry to MAE (null if not available)
     */
    @JsonIgnore
    public Integer barsToMae() {
        return maeBar != null ? maeBar - entryBar : null;
    }
}
