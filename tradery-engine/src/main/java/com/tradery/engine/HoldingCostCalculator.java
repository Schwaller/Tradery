package com.tradery.engine;

import com.tradery.core.model.FundingRate;
import com.tradery.core.model.MarketType;

import java.util.List;

/**
 * Calculates holding costs for open positions based on market type.
 *
 * FUTURES: Uses real Binance funding rates (every 8 hours)
 * - Longs pay when funding rate is positive
 * - Shorts receive when funding rate is positive
 * - Longs receive when funding rate is negative
 * - Shorts pay when funding rate is negative
 *
 * MARGIN: Uses configurable borrow interest (accrues hourly)
 * - Both longs and shorts pay interest on notional value
 */
public class HoldingCostCalculator {

    private final List<FundingRate> fundingRates;
    private final MarketType marketType;
    private final double marginInterestHourly;  // Hourly rate in percent (e.g., 0.00042 = 0.00042%/hr)

    /**
     * Create a holding cost calculator.
     *
     * @param fundingRates         List of historical funding rates (for FUTURES mode)
     * @param marketType           The market type (SPOT, FUTURES, MARGIN)
     * @param marginInterestHourly Hourly interest rate in percent (e.g., 0.00042 = 0.00042%/hr)
     */
    public HoldingCostCalculator(List<FundingRate> fundingRates, MarketType marketType, double marginInterestHourly) {
        this.fundingRates = fundingRates != null ? fundingRates : List.of();
        this.marketType = marketType != null ? marketType : MarketType.SPOT;
        this.marginInterestHourly = marginInterestHourly;
    }

    /**
     * Check if this calculator will apply any holding costs.
     */
    public boolean isEnabled() {
        return marketType.hasHoldingCosts();
    }

    /**
     * Calculate funding fee for a FUTURES position at a funding settlement.
     *
     * Formula: fundingFee = quantity × markPrice × fundingRate
     *
     * @param rate       The funding rate record
     * @param quantity   Position size in base asset
     * @param isLong     True if long position, false if short
     * @return The funding fee (positive = pay, negative = receive)
     */
    public double calculateFundingFee(FundingRate rate, double quantity, boolean isLong) {
        if (rate == null || marketType != MarketType.FUTURES) {
            return 0;
        }

        // Use mark price from funding rate if available, otherwise we need current price
        double markPrice = rate.markPrice() > 0 ? rate.markPrice() : 0;
        if (markPrice == 0) {
            return 0;  // Can't calculate without price
        }

        double notional = quantity * markPrice;
        double fee = notional * rate.fundingRate();

        // Long pays positive funding, receives negative funding
        // Short receives positive funding, pays negative funding
        return isLong ? fee : -fee;
    }

    /**
     * Calculate funding fee using a specific price (when mark price not available).
     *
     * @param rate       The funding rate record
     * @param quantity   Position size in base asset
     * @param price      The current market price
     * @param isLong     True if long position, false if short
     * @return The funding fee (positive = pay, negative = receive)
     */
    public double calculateFundingFeeWithPrice(FundingRate rate, double quantity, double price, boolean isLong) {
        if (rate == null || marketType != MarketType.FUTURES) {
            return 0;
        }

        double markPrice = rate.markPrice() > 0 ? rate.markPrice() : price;
        double notional = quantity * markPrice;
        double fee = notional * rate.fundingRate();

        return isLong ? fee : -fee;
    }

    /**
     * Calculate margin interest for a time period.
     * Interest accrues hourly on notional value.
     *
     * Formula: interest = notionalValue × (hourlyRatePercent / 100) × hoursHeld
     *
     * @param notionalValue  Position value in quote currency
     * @param startTime      Start time in milliseconds
     * @param endTime        End time in milliseconds
     * @return The interest cost (always positive - both longs and shorts pay)
     */
    public double calculateMarginInterest(double notionalValue, long startTime, long endTime) {
        if (marketType != MarketType.MARGIN || marginInterestHourly <= 0) {
            return 0;
        }

        double hoursHeld = (endTime - startTime) / (1000.0 * 60 * 60);
        if (hoursHeld <= 0) {
            return 0;
        }

        // marginInterestHourly is in percent (e.g., 0.00042 means 0.00042%)
        double hourlyRateDecimal = marginInterestHourly / 100.0;
        return notionalValue * hourlyRateDecimal * hoursHeld;
    }

    /**
     * Find funding settlement that occurred within a time window.
     * Funding settlements happen every 8 hours at 00:00, 08:00, 16:00 UTC.
     *
     * @param windowStart Start of time window (exclusive)
     * @param windowEnd   End of time window (inclusive)
     * @return The funding rate if a settlement occurred in window, null otherwise
     */
    public FundingRate getSettlementInWindow(long windowStart, long windowEnd) {
        if (fundingRates.isEmpty() || marketType != MarketType.FUTURES) {
            return null;
        }

        // Find funding rate where fundingTime is in (windowStart, windowEnd]
        for (FundingRate fr : fundingRates) {
            if (fr.fundingTime() > windowStart && fr.fundingTime() <= windowEnd) {
                return fr;
            }
        }

        return null;
    }

    /**
     * Get the most recent funding rate before or at a given time.
     * Useful for estimating costs when exact settlement time is uncertain.
     *
     * @param timestamp Time in milliseconds
     * @return The most recent funding rate, or null if none found
     */
    public FundingRate getLatestFundingRateBefore(long timestamp) {
        if (fundingRates.isEmpty()) {
            return null;
        }

        FundingRate latest = null;
        for (FundingRate fr : fundingRates) {
            if (fr.fundingTime() <= timestamp) {
                if (latest == null || fr.fundingTime() > latest.fundingTime()) {
                    latest = fr;
                }
            }
        }

        return latest;
    }

    /**
     * Get the market type this calculator is configured for.
     */
    public MarketType getMarketType() {
        return marketType;
    }

    /**
     * Get the hourly margin interest rate in percent.
     */
    public double getMarginInterestHourly() {
        return marginInterestHourly;
    }
}
