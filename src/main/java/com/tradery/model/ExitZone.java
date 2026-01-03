package com.tradery.model;

/**
 * Represents an exit zone configuration based on P&L percentage ranges.
 * Each zone can have its own exit conditions, SL/TP settings, and behavior.
 */
public record ExitZone(
    String name,              // e.g., "Profit", "Small Loss", "Big Loss"
    Double minPnlPercent,     // Lower bound (null = no lower bound, i.e., -infinity)
    Double maxPnlPercent,     // Upper bound (null = no upper bound, i.e., +infinity)
    String exitCondition,     // DSL expression for exit signal (optional)
    String stopLossType,      // "none", "fixed_percent", "trailing_percent", "fixed_atr", "trailing_atr"
    Double stopLossValue,     // Percent or ATR multiplier
    String takeProfitType,    // "none", "fixed_percent", "fixed_atr"
    Double takeProfitValue,   // Percent or ATR multiplier
    boolean exitImmediately   // If true, exit as soon as P&L enters this zone
) {
    /**
     * Check if a given P&L percentage falls within this zone's range.
     */
    public boolean matches(double pnlPercent) {
        boolean aboveMin = minPnlPercent == null || pnlPercent >= minPnlPercent;
        boolean belowMax = maxPnlPercent == null || pnlPercent < maxPnlPercent;
        return aboveMin && belowMax;
    }

    /**
     * Create a default zone with no conditions (catch-all).
     */
    public static ExitZone defaultZone() {
        return new ExitZone(
            "Default",
            null,
            null,
            "",
            "none",
            null,
            "none",
            null,
            false
        );
    }

    /**
     * Create a zone builder for fluent construction.
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private Double minPnlPercent;
        private Double maxPnlPercent;
        private String exitCondition = "";
        private String stopLossType = "none";
        private Double stopLossValue;
        private String takeProfitType = "none";
        private Double takeProfitValue;
        private boolean exitImmediately = false;

        public Builder(String name) {
            this.name = name;
        }

        public Builder minPnl(Double min) {
            this.minPnlPercent = min;
            return this;
        }

        public Builder maxPnl(Double max) {
            this.maxPnlPercent = max;
            return this;
        }

        public Builder exitCondition(String condition) {
            this.exitCondition = condition;
            return this;
        }

        public Builder stopLoss(String type, Double value) {
            this.stopLossType = type;
            this.stopLossValue = value;
            return this;
        }

        public Builder takeProfit(String type, Double value) {
            this.takeProfitType = type;
            this.takeProfitValue = value;
            return this;
        }

        public Builder exitImmediately(boolean immediate) {
            this.exitImmediately = immediate;
            return this;
        }

        public ExitZone build() {
            return new ExitZone(
                name,
                minPnlPercent,
                maxPnlPercent,
                exitCondition,
                stopLossType,
                stopLossValue,
                takeProfitType,
                takeProfitValue,
                exitImmediately
            );
        }
    }
}
