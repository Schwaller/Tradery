package com.tradery.engine;

import com.tradery.core.model.EntryOrderType;
import com.tradery.core.model.OffsetUnit;

/**
 * Tracks a pending entry order (LIMIT, STOP, or TRAILING).
 * Created when DSL signal fires with non-MARKET order type.
 * Supports both long and short directions.
 */
public class PendingOrder {
    final int signalBar;              // Bar when DSL signal triggered
    final double signalPrice;         // Price at signal bar
    final double orderPrice;          // Target fill price (for LIMIT/STOP)
    double trailPrice;                // For TRAILING: tracks price extreme
    final EntryOrderType orderType;
    final Integer expirationBar;      // Bar when order expires (null = never)
    final double trailingReversePercent;  // For TRAILING: reversal % to trigger
    final boolean isLong;             // Trade direction

    public PendingOrder(int signalBar, double signalPrice, EntryOrderType orderType,
                        OffsetUnit offsetUnit, Double offsetValue, Double atr,
                        Double trailingReversePercent, Integer expirationBars,
                        boolean isLong) {
        this.signalBar = signalBar;
        this.signalPrice = signalPrice;
        this.orderType = orderType;
        this.trailingReversePercent = trailingReversePercent != null ? trailingReversePercent : 1.0;
        this.isLong = isLong;

        // Calculate order price for LIMIT/STOP based on unit type
        if (offsetValue != null && (orderType == EntryOrderType.LIMIT || orderType == EntryOrderType.STOP)) {
            double offset = 0;
            if (offsetUnit == OffsetUnit.ATR && atr != null) {
                offset = offsetValue * atr;
            } else if (offsetUnit == OffsetUnit.PERCENT) {
                offset = signalPrice * (offsetValue / 100.0);
            }
            // For LIMIT: long buys below signal, short sells above signal
            // For STOP: long buys above signal, short sells below signal
            // offsetValue sign convention: negative for LIMIT (buy lower), positive for STOP (buy higher)
            this.orderPrice = signalPrice + offset;
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
     * Long LIMIT: fills when price drops TO or BELOW the order price (buy the dip).
     * Short LIMIT: fills when price rises TO or ABOVE the order price (sell the rally).
     */
    public boolean shouldFillLimit(double high, double low) {
        if (orderType != EntryOrderType.LIMIT) return false;
        if (isLong) {
            return low <= orderPrice;  // Long: buy when price drops to limit
        } else {
            return high >= orderPrice; // Short: sell when price rises to limit
        }
    }

    /**
     * Check if STOP order should fill at current bar.
     * Long STOP: fills when price rises TO or ABOVE the order price (breakout entry).
     * Short STOP: fills when price drops TO or BELOW the order price (breakdown entry).
     */
    public boolean shouldFillStop(double high, double low) {
        if (orderType != EntryOrderType.STOP) return false;
        if (isLong) {
            return high >= orderPrice;  // Long: buy on breakout above
        } else {
            return low <= orderPrice;   // Short: sell on breakdown below
        }
    }

    /**
     * Update trailing price and check if TRAILING order should fill.
     * Long: trail down (track lowest low), enter on bounce up.
     * Short: trail up (track highest high), enter on drop down.
     * Returns the fill price if should fill, null otherwise.
     */
    public Double updateTrailingAndCheckFill(double high, double low, double close) {
        if (orderType != EntryOrderType.TRAILING) return null;

        if (isLong) {
            // Long: trail down - update to lowest low seen
            if (low < trailPrice) {
                trailPrice = low;
            }
            // Check for reversal - price has bounced up from trail by X%
            double reversalTarget = trailPrice * (1 + trailingReversePercent / 100.0);
            if (close >= reversalTarget) {
                return close;  // Fill at close price on reversal
            }
        } else {
            // Short: trail up - update to highest high seen
            if (high > trailPrice) {
                trailPrice = high;
            }
            // Check for reversal - price has dropped from trail by X%
            double reversalTarget = trailPrice * (1 - trailingReversePercent / 100.0);
            if (close <= reversalTarget) {
                return close;  // Fill at close price on reversal
            }
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
