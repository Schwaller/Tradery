package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A single price checkpoint within a hoop pattern.
 * Defines a price range (as % from anchor) and time window (bars from previous hoop).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Hoop(
    String name,              // e.g., "pullback", "breakout"
    Double minPricePercent,   // Min price as % from anchor (e.g., -2.0 = 2% below)
    Double maxPricePercent,   // Max price as % from anchor (e.g., +3.0 = 3% above)
    int distance,             // Expected bars from previous hoop
    int tolerance,            // Can hit within +/- N bars of distance
    AnchorMode anchorMode     // How to set anchor for next hoop
) {
    /**
     * How to determine the 0% reference point for the next hoop.
     */
    public enum AnchorMode {
        ACTUAL_HIT("actual_hit"),   // Use close price where this hoop was hit
        AVG_RANGE("avg_range");     // Use midpoint of (minPrice + maxPrice) bounds

        private final String value;

        AnchorMode(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public static AnchorMode fromString(String value) {
            for (AnchorMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return ACTUAL_HIT;
        }
    }

    /**
     * Compact constructor with defaults.
     */
    public Hoop {
        if (anchorMode == null) anchorMode = AnchorMode.ACTUAL_HIT;
        if (distance < 1) distance = 1;
        if (tolerance < 0) tolerance = 0;
    }

    /**
     * Calculate the absolute minimum price bound from an anchor price.
     */
    public double getMinAbsolutePrice(double anchorPrice) {
        return minPricePercent != null
            ? anchorPrice * (1 + minPricePercent / 100.0)
            : 0;
    }

    /**
     * Calculate the absolute maximum price bound from an anchor price.
     */
    public double getMaxAbsolutePrice(double anchorPrice) {
        return maxPricePercent != null
            ? anchorPrice * (1 + maxPricePercent / 100.0)
            : Double.MAX_VALUE;
    }

    /**
     * Check if a price falls within this hoop's bounds.
     */
    public boolean priceInRange(double price, double anchorPrice) {
        double minAbs = getMinAbsolutePrice(anchorPrice);
        double maxAbs = getMaxAbsolutePrice(anchorPrice);
        return price >= minAbs && price <= maxAbs;
    }

    /**
     * Get the earliest bar in the time window relative to a reference bar.
     */
    public int getWindowStart(int referenceBar) {
        return referenceBar + distance - tolerance;
    }

    /**
     * Get the latest bar in the time window relative to a reference bar.
     */
    public int getWindowEnd(int referenceBar) {
        return referenceBar + distance + tolerance;
    }

    /**
     * Calculate the new anchor price based on anchor mode.
     */
    public double calculateNextAnchor(double hitPrice, double anchorPrice) {
        return switch (anchorMode) {
            case ACTUAL_HIT -> hitPrice;
            case AVG_RANGE -> {
                double minAbs = getMinAbsolutePrice(anchorPrice);
                double maxAbs = getMaxAbsolutePrice(anchorPrice);
                // Handle unbounded ranges
                if (maxAbs == Double.MAX_VALUE) yield hitPrice;
                yield (minAbs + maxAbs) / 2.0;
            }
        };
    }

    /**
     * Create a new hoop with default values.
     */
    public static Hoop createDefault(String name) {
        return new Hoop(name, -2.0, 2.0, 5, 2, AnchorMode.ACTUAL_HIT);
    }
}
