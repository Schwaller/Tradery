package com.tradery.model;

/**
 * Represents an exit zone configuration based on P&L percentage ranges.
 * Each zone can have its own exit conditions, SL/TP settings, and behavior.
 */
public record ExitZone(
    String name,
    Double minPnlPercent,
    Double maxPnlPercent,
    String exitCondition,
    StopLossType stopLossType,
    Double stopLossValue,
    TakeProfitType takeProfitType,
    Double takeProfitValue,
    boolean exitImmediately,
    int minBarsBeforeExit
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
            StopLossType.NONE,
            null,
            TakeProfitType.NONE,
            null,
            false,
            0
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
        private StopLossType stopLossType = StopLossType.NONE;
        private Double stopLossValue;
        private TakeProfitType takeProfitType = TakeProfitType.NONE;
        private Double takeProfitValue;
        private boolean exitImmediately = false;
        private int minBarsBeforeExit = 0;

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

        public Builder stopLoss(StopLossType type, Double value) {
            this.stopLossType = type;
            this.stopLossValue = value;
            return this;
        }

        public Builder takeProfit(TakeProfitType type, Double value) {
            this.takeProfitType = type;
            this.takeProfitValue = value;
            return this;
        }

        public Builder exitImmediately(boolean immediate) {
            this.exitImmediately = immediate;
            return this;
        }

        public Builder minBarsBeforeExit(int bars) {
            this.minBarsBeforeExit = bars;
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
                exitImmediately,
                minBarsBeforeExit
            );
        }
    }
}
