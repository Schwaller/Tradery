package com.tradery.execution.risk;

import com.tradery.exchange.model.AccountState;
import com.tradery.execution.order.OrderIntent;
import com.tradery.execution.position.PositionTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Pre-trade risk validation checks.
 */
public class RiskManager {

    private static final Logger log = LoggerFactory.getLogger(RiskManager.class);

    private RiskLimits limits;
    private final PositionTracker positionTracker;
    private final LinkedList<Instant> recentOrders = new LinkedList<>();

    private double dailyStartingEquity;
    private double peakEquity;
    private volatile boolean killed;

    public RiskManager(RiskLimits limits, PositionTracker positionTracker) {
        this.limits = limits;
        this.positionTracker = positionTracker;
    }

    /**
     * Check whether an order intent passes all risk checks.
     * Returns a list of rejection reasons (empty = passed).
     */
    public List<String> check(OrderIntent intent, AccountState accountState, double positionSizeUsd) {
        List<String> rejections = new ArrayList<>();

        if (killed) {
            rejections.add("Kill switch activated — all trading halted");
            return rejections;
        }

        // Skip risk checks for reduce-only orders (closing positions)
        if (intent.reduceOnly()) {
            return rejections;
        }

        // Position size check
        if (positionSizeUsd > limits.maxPositionSizeUsd()) {
            rejections.add(String.format("Position size $%.0f exceeds max $%.0f",
                    positionSizeUsd, limits.maxPositionSizeUsd()));
        }

        // Max open positions check
        if (positionTracker.getOpenPositionCount() >= limits.maxOpenPositions()) {
            rejections.add(String.format("Max open positions reached (%d)", limits.maxOpenPositions()));
        }

        // Symbol whitelist check
        if (!limits.allowedSymbols().isEmpty() &&
                !limits.allowedSymbols().contains(intent.symbol())) {
            rejections.add("Symbol not in allowed list: " + intent.symbol());
        }

        // Daily loss check
        if (dailyStartingEquity > 0) {
            double dailyLoss = (dailyStartingEquity - accountState.equity()) / dailyStartingEquity * 100;
            if (dailyLoss >= limits.maxDailyLossPercent()) {
                rejections.add(String.format("Daily loss %.1f%% exceeds max %.1f%%",
                        dailyLoss, limits.maxDailyLossPercent()));
            }
        }

        // Drawdown check
        if (peakEquity > 0) {
            double drawdown = (peakEquity - accountState.equity()) / peakEquity * 100;
            if (drawdown >= limits.maxDrawdownPercent()) {
                rejections.add(String.format("Drawdown %.1f%% exceeds max %.1f%%",
                        drawdown, limits.maxDrawdownPercent()));
            }
        }

        // Rate limit check
        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minusSeconds(60);
        recentOrders.removeIf(t -> t.isBefore(oneMinuteAgo));
        if (recentOrders.size() >= limits.maxOrdersPerMinute()) {
            rejections.add(String.format("Order rate limit: %d orders in last minute (max %d)",
                    recentOrders.size(), limits.maxOrdersPerMinute()));
        }

        if (rejections.isEmpty()) {
            recentOrders.add(now);
        }

        return rejections;
    }

    /**
     * Update equity tracking for daily loss and drawdown calculations.
     */
    public void updateEquity(double equity) {
        if (dailyStartingEquity == 0) {
            dailyStartingEquity = equity;
        }
        if (equity > peakEquity) {
            peakEquity = equity;
        }
    }

    /**
     * Reset daily metrics (call at start of each trading day).
     */
    public void resetDaily(double equity) {
        dailyStartingEquity = equity;
        recentOrders.clear();
    }

    public void setKilled(boolean killed) {
        this.killed = killed;
    }

    public boolean isKilled() {
        return killed;
    }

    public void updateLimits(RiskLimits newLimits) {
        this.limits = newLimits;
    }

    public RiskLimits getLimits() {
        return limits;
    }
}
