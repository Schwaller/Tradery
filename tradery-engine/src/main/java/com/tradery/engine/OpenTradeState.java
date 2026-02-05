package com.tradery.engine;

import com.tradery.core.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to track state for each open trade during backtesting.
 * Tracks price extremes, trailing stops, MFE/MAE analytics, and partial exit state.
 */
public class OpenTradeState {
    Trade trade;
    double highestPriceSinceEntry;
    double lowestPriceSinceEntry;  // For MAE tracking
    double trailingStopPrice;
    String exitReason;
    double exitPrice;
    String exitZone;
    ExitZone matchedZone;  // The zone that triggered the exit (for partial exit calculation)
    // DCA-out tracking
    double originalQuantity;              // Original quantity at entry
    double remainingQuantity;             // Current remaining quantity
    Map<String, Integer> zoneExitCount;   // zoneName -> number of exits done in this zone
    String lastZoneName;                  // Track zone transitions for RESET logic
    int lastExitBar;                      // Track last partial exit bar for minBarsBetweenExits
    // Trade analytics (MFE/MAE)
    double mfePercent;    // Maximum Favorable Excursion (best P&L %)
    double maePercent;    // Maximum Adverse Excursion (worst P&L %)
    int mfeBar;           // Bar when MFE was reached
    int maeBar;           // Bar when MAE was reached
    // Phase context
    List<String> activePhasesAtEntry;  // Phases active when trade was opened
    // Holding costs (funding fees for futures, interest for margin)
    double accumulatedHoldingCosts;    // Total holding costs accumulated
    long lastFundingTime;              // For futures: track last settlement processed
    long lastInterestTime;             // For margin: track last hour interest was calculated
    // Context analysis (better entry within CONTEXT_BARS before actual entry)
    Integer betterEntryBar;            // Bar with better entry price (null if actual entry was optimal)
    Double betterEntryPrice;           // Price at the better entry bar
    Double betterEntryImprovement;     // % improvement in potential PnL

    public OpenTradeState(Trade trade, double entryPrice) {
        this(trade, entryPrice, null);
    }

    public OpenTradeState(Trade trade, double entryPrice, List<String> activePhasesAtEntry) {
        this.trade = trade;
        this.highestPriceSinceEntry = entryPrice;
        this.lowestPriceSinceEntry = entryPrice;
        this.trailingStopPrice = 0;
        this.originalQuantity = trade.quantity();
        this.remainingQuantity = trade.quantity();
        this.zoneExitCount = new HashMap<>();
        this.lastZoneName = null;
        this.lastExitBar = -9999;
        // Initialize MFE/MAE at entry
        this.mfePercent = 0;
        this.maePercent = 0;
        this.mfeBar = trade.entryBar();
        this.maeBar = trade.entryBar();
        // Phase context
        this.activePhasesAtEntry = activePhasesAtEntry;
        // Holding costs
        this.accumulatedHoldingCosts = 0;
        this.lastFundingTime = 0;
        this.lastInterestTime = trade.entryTime();
    }

    /**
     * Update MFE/MAE tracking based on current bar's price action.
     * Call this at the start of each bar while trade is open.
     */
    public void updateExcursions(double high, double low, int barIndex, boolean isLong) {
        // Track price extremes
        if (high > highestPriceSinceEntry) {
            highestPriceSinceEntry = high;
        }
        if (low < lowestPriceSinceEntry) {
            lowestPriceSinceEntry = low;
        }

        double entryPrice = trade.entryPrice();

        // Calculate P&L % at high and low of bar
        double pnlAtHigh, pnlAtLow;
        if (isLong) {
            pnlAtHigh = ((high - entryPrice) / entryPrice) * 100;
            pnlAtLow = ((low - entryPrice) / entryPrice) * 100;
        } else {
            // Short: profit when price goes down
            pnlAtHigh = ((entryPrice - high) / entryPrice) * 100;
            pnlAtLow = ((entryPrice - low) / entryPrice) * 100;
        }

        // Update MFE (best P&L) - for longs use high, for shorts use low
        double bestPnl = isLong ? pnlAtHigh : pnlAtLow;
        if (bestPnl > mfePercent) {
            mfePercent = bestPnl;
            mfeBar = barIndex;
        }

        // Update MAE (worst P&L) - for longs use low, for shorts use high
        double worstPnl = isLong ? pnlAtLow : pnlAtHigh;
        if (worstPnl < maePercent) {
            maePercent = worstPnl;
            maeBar = barIndex;
        }
    }

    /**
     * Calculate exit quantity for a zone based on its configuration.
     * Returns the quantity to exit (clipped to remaining), or 0 if max exits reached.
     */
    public double calculateExitQuantity(ExitZone zone) {
        double exitPercent = zone.getEffectiveExitPercent();
        int maxExits = zone.getEffectiveMaxExits();
        ExitBasis basis = zone.exitBasis();
        ExitReentry reentry = zone.exitReentry();
        String zoneName = zone.name();

        // Handle zone transition - reset count if configured
        if (lastZoneName != null && !lastZoneName.equals(zoneName) && reentry == ExitReentry.RESET) {
            zoneExitCount.clear();
        }
        lastZoneName = zoneName;

        // Check if max exits reached for this zone
        int exitsDone = zoneExitCount.getOrDefault(zoneName, 0);
        if (exitsDone >= maxExits) {
            return 0;  // No more exits allowed in this zone
        }

        // Calculate target quantity based on basis
        double targetQty;
        if (basis == ExitBasis.ORIGINAL) {
            targetQty = originalQuantity * (exitPercent / 100.0);
        } else {
            // REMAINING basis
            targetQty = remainingQuantity * (exitPercent / 100.0);
        }

        // Clip to remaining
        targetQty = Math.min(targetQty, remainingQuantity);
        targetQty = Math.max(targetQty, 0); // Can't be negative

        return targetQty;
    }

    /**
     * Check if enough bars have passed since last exit for this zone.
     */
    public boolean canExitInZone(ExitZone zone, int currentBar) {
        int minBarsBetween = zone.minBarsBetweenExits();
        return (currentBar - lastExitBar) >= minBarsBetween;
    }

    /**
     * Record a partial exit in a zone.
     */
    public void recordPartialExit(String zoneName, double quantity, int currentBar) {
        int current = zoneExitCount.getOrDefault(zoneName, 0);
        zoneExitCount.put(zoneName, current + 1);
        remainingQuantity -= quantity;
        lastExitBar = currentBar;
    }

    /**
     * Check if this position is fully closed.
     */
    public boolean isFullyClosed() {
        return remainingQuantity <= 0.0001; // Small epsilon for floating point
    }

    /**
     * Process a funding settlement (for FUTURES market type).
     * Only processes if this settlement hasn't been seen before.
     *
     * @param fee         The funding fee (positive = pay, negative = receive)
     * @param fundingTime The time of the funding settlement
     */
    public void processFundingSettlement(double fee, long fundingTime) {
        if (fundingTime <= lastFundingTime) {
            return;  // Already processed this settlement
        }
        accumulatedHoldingCosts += fee;
        lastFundingTime = fundingTime;
    }

    /**
     * Process margin interest for a time period (for MARGIN market type).
     * Updates the last interest time to prevent double-counting.
     *
     * @param interest    The interest amount (always positive)
     * @param currentTime The current time for tracking
     */
    public void processMarginInterest(double interest, long currentTime) {
        accumulatedHoldingCosts += interest;
        lastInterestTime = currentTime;
    }

    /**
     * Get the accumulated holding costs for this trade.
     * Positive values represent costs, negative values represent earnings.
     */
    public double getAccumulatedHoldingCosts() {
        return accumulatedHoldingCosts;
    }

    /**
     * Get the last funding time that was processed.
     */
    public long getLastFundingTime() {
        return lastFundingTime;
    }

    /**
     * Get the last time interest was calculated.
     */
    public long getLastInterestTime() {
        return lastInterestTime;
    }

    /**
     * Analyze context bars before entry to find better entry points.
     * For longs: find the lowest low within CONTEXT_BARS before entry
     * For shorts: find the highest high within CONTEXT_BARS before entry
     *
     * @param candles List of all candles in the backtest
     * @param isLong Whether this is a long trade
     */
    public void analyzeContextBars(List<Candle> candles, boolean isLong) {
        int entryBar = trade.entryBar();
        double entryPrice = trade.entryPrice();
        int contextBars = Trade.CONTEXT_BARS;

        int startBar = Math.max(0, entryBar - contextBars);
        if (startBar >= entryBar) {
            return;  // No context bars available
        }

        // Find best entry price in context window
        double bestPrice = entryPrice;
        int bestBar = entryBar;

        for (int i = startBar; i < entryBar; i++) {
            Candle c = candles.get(i);
            if (isLong) {
                // For longs, lower price is better
                if (c.low() < bestPrice) {
                    bestPrice = c.low();
                    bestBar = i;
                }
            } else {
                // For shorts, higher price is better
                if (c.high() > bestPrice) {
                    bestPrice = c.high();
                    bestBar = i;
                }
            }
        }

        // Only record if we found a better entry
        if (bestBar != entryBar) {
            this.betterEntryBar = bestBar;
            this.betterEntryPrice = bestPrice;

            // Calculate improvement: for longs, (entry - better) / entry * 100
            // For shorts, (better - entry) / entry * 100
            if (isLong) {
                this.betterEntryImprovement = ((entryPrice - bestPrice) / entryPrice) * 100;
            } else {
                this.betterEntryImprovement = ((bestPrice - entryPrice) / entryPrice) * 100;
            }
        }
    }

    /**
     * Get the bar with better entry price (or null if actual entry was optimal).
     */
    public Integer getBetterEntryBar() {
        return betterEntryBar;
    }

    /**
     * Get the better entry price (or null if actual entry was optimal).
     */
    public Double getBetterEntryPrice() {
        return betterEntryPrice;
    }

    /**
     * Get the % improvement if entered at better price (or null if actual entry was optimal).
     */
    public Double getBetterEntryImprovement() {
        return betterEntryImprovement;
    }
}
