package com.tradery.engine;

import com.tradery.model.EntryOrderType;
import com.tradery.model.OffsetUnit;

/**
 * Tracks a pending entry order (LIMIT, STOP, or TRAILING).
 * Created when DSL signal fires with non-MARKET order type.
 */
public class PendingOrder {
    final int signalBar;              // Bar when DSL signal triggered
    final double signalPrice;         // Price at signal bar
    final double orderPrice;          // Target fill price (for LIMIT/STOP)
    double trailPrice;                // For TRAILING: lowest price seen (tracks down)
    final EntryOrderType orderType;
    final Integer expirationBar;      // Bar when order expires (null = never)
    final double trailingReversePercent;  // For TRAILING: reversal % to trigger

    public PendingOrder(int signalBar, double signalPrice, EntryOrderType orderType,
                        OffsetUnit offsetUnit, Double offsetValue, Double atr,
                        Double trailingReversePercent, Integer expirationBars) {
        this.signalBar = signalBar;
        this.signalPrice = signalPrice;
        this.orderType = orderType;
        this.trailingReversePercent = trailingReversePercent != null ? trailingReversePercent : 1.0;

        // Calculate order price for LIMIT/STOP based on unit type
        if (offsetValue != null && (orderType == EntryOrderType.LIMIT || orderType == EntryOrderType.STOP)) {
            if (offsetUnit == OffsetUnit.ATR && atr != null) {
                // ATR-based offset: value is multiplier of ATR(14)
                this.orderPrice = signalPrice + (offsetValue * atr);
            } else if (offsetUnit == OffsetUnit.PERCENT) {
                // Percentage offset
                this.orderPrice = signalPrice * (1 + offsetValue / 100.0);
            } else {
                this.orderPrice = signalPrice;
            }
        } else {
            this.orderPrice = signalPrice;
        }

        // Initialize trail price for TRAILING
        this.trailPrice = signalPrice;

        // Calculate expiration bar
        this.expirationBar = expirationBars != null ? signalBar + expirationBars : null;
    }

    /**
     * Check if this order has expired.
     */
    public boolean isExpired(int currentBar) {
        return expirationBar != null && currentBar > expirationBar;
    }

    /**
     * Check if LIMIT order should fill at current bar.
     * LIMIT fills when price drops TO or BELOW the order price.
     */
    public boolean shouldFillLimit(double low) {
        return orderType == EntryOrderType.LIMIT && low <= orderPrice;
    }

    /**
     * Check if STOP order should fill at current bar.
     * STOP fills when price rises TO or ABOVE the order price.
     */
    public boolean shouldFillStop(double high) {
        return orderType == EntryOrderType.STOP && high >= orderPrice;
    }

    /**
     * Update trailing price and check if TRAILING order should fill.
     * Returns the fill price if should fill, null otherwise.
     */
    public Double updateTrailingAndCheckFill(double high, double low, double close) {
        if (orderType != EntryOrderType.TRAILING) return null;

        // Trail down - update to lowest low seen
        if (low < trailPrice) {
            trailPrice = low;
        }

        // Check for reversal - price has bounced up from trail by X%
        double reversalTarget = trailPrice * (1 + trailingReversePercent / 100.0);
        if (close >= reversalTarget) {
            return close;  // Fill at close price on reversal
        }

        return null;  // No fill yet
    }

    /**
     * Get the fill price for LIMIT/STOP orders.
     */
    public double getFillPrice() {
        return orderPrice;
    }
}
