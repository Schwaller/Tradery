package com.tradery.engine;

import com.tradery.model.ExitBasis;
import com.tradery.model.ExitReentry;
import com.tradery.model.ExitZone;
import com.tradery.model.Trade;

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
}
