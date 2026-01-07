package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents an exit zone configuration based on P&L percentage ranges.
 * Each zone can have its own exit conditions, SL/TP settings, and behavior.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExitZone(
    String name,
    Double minPnlPercent,
    Double maxPnlPercent,
    String exitCondition,
    StopLossType stopLossType,
    Double stopLossValue,
    TakeProfitType takeProfitType,
    Double takeProfitValue,
    Boolean exitImmediately,    // Use wrapper for null-safety during deserialization
    Integer minBarsBeforeExit,  // Use wrapper for null-safety during deserialization
    Double exitPercent,         // null = 100% (close all), 1-100 for partial per trigger
    Integer maxExits,           // null = unlimited, max number of partial exits in this zone
    ExitBasis exitBasis,        // ORIGINAL or REMAINING (default: REMAINING)
    ExitReentry exitReentry,    // CONTINUE or RESET (default: CONTINUE)
    Integer minBarsBetweenExits // Minimum bars between partial exits (null = 0)
) {
    /**
     * Compact constructor to set defaults for missing fields.
     */
    public ExitZone {
        if (exitImmediately == null) exitImmediately = false;
        if (minBarsBeforeExit == null) minBarsBeforeExit = 0;
        if (exitBasis == null) exitBasis = ExitBasis.REMAINING;
        if (exitReentry == null) exitReentry = ExitReentry.CONTINUE;
        if (minBarsBetweenExits == null) minBarsBetweenExits = 0;
    }

    /**
     * Check if a given P&L percentage falls within this zone's range.
     */
    public boolean matches(double pnlPercent) {
        boolean aboveMin = minPnlPercent == null || pnlPercent >= minPnlPercent;
        boolean belowMax = maxPnlPercent == null || pnlPercent < maxPnlPercent;
        return aboveMin && belowMax;
    }

    /**
     * Get effective exit percent per trigger (100 if null).
     */
    @JsonIgnore
    public double getEffectiveExitPercent() {
        return exitPercent != null ? exitPercent : 100.0;
    }

    /**
     * Get effective max exits for the zone (Integer.MAX_VALUE if null = unlimited).
     */
    @JsonIgnore
    public int getEffectiveMaxExits() {
        return maxExits != null ? maxExits : Integer.MAX_VALUE;
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
            0,
            null,
            null,
            ExitBasis.REMAINING,
            ExitReentry.CONTINUE,
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
        private Boolean exitImmediately = false;
        private Integer minBarsBeforeExit = 0;
        private Double exitPercent;
        private Integer maxExits;
        private ExitBasis exitBasis = ExitBasis.REMAINING;
        private ExitReentry exitReentry = ExitReentry.CONTINUE;
        private Integer minBarsBetweenExits = 0;

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

        public Builder exitImmediately(Boolean immediate) {
            this.exitImmediately = immediate;
            return this;
        }

        public Builder minBarsBeforeExit(Integer bars) {
            this.minBarsBeforeExit = bars;
            return this;
        }

        public Builder exitPercent(Double percent) {
            this.exitPercent = percent;
            return this;
        }

        public Builder maxExits(Integer count) {
            this.maxExits = count;
            return this;
        }

        public Builder exitBasis(ExitBasis basis) {
            this.exitBasis = basis;
            return this;
        }

        public Builder exitReentry(ExitReentry reentry) {
            this.exitReentry = reentry;
            return this;
        }

        public Builder minBarsBetweenExits(Integer bars) {
            this.minBarsBetweenExits = bars;
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
                minBarsBeforeExit,
                exitPercent,
                maxExits,
                exitBasis,
                exitReentry,
                minBarsBetweenExits
            );
        }
    }
}
