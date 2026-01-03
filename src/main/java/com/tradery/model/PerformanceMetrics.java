package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Performance metrics calculated from backtest trades.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PerformanceMetrics(
    int totalTrades,
    int winningTrades,
    int losingTrades,
    double winRate,
    double profitFactor,
    double totalReturn,
    double totalReturnPercent,
    double maxDrawdown,
    double maxDrawdownPercent,
    double sharpeRatio,
    double averageWin,
    double averageLoss,
    double largestWin,
    double largestLoss,
    double averageHoldingPeriod,
    double finalEquity,
    double totalFees,
    double maxCapitalUsage,
    double maxCapitalDollars
) {
    /**
     * Create empty metrics (no trades)
     */
    public static PerformanceMetrics empty(double initialCapital) {
        return new PerformanceMetrics(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, initialCapital, 0, 0, 0
        );
    }

    /**
     * Calculate metrics from a list of trades
     */
    public static PerformanceMetrics calculate(java.util.List<Trade> trades, double initialCapital) {
        if (trades == null || trades.isEmpty()) {
            return empty(initialCapital);
        }

        int winners = 0;
        int losers = 0;
        double totalWins = 0;
        double totalLosses = 0;
        double largestWin = 0;
        double largestLoss = 0;
        double totalHoldingPeriod = 0;
        double totalFees = 0;

        double equity = initialCapital;
        double peak = initialCapital;
        double maxDD = 0;

        java.util.List<Double> returns = new java.util.ArrayList<>();

        for (Trade t : trades) {
            // Track fees for all trades (including rejected)
            if (t.commission() != null) {
                totalFees += t.commission();
            }

            if (t.pnl() == null) continue;

            double pnl = t.pnl();
            equity += pnl;

            if (pnl > 0) {
                winners++;
                totalWins += pnl;
                largestWin = Math.max(largestWin, pnl);
            } else if (pnl < 0) {
                losers++;
                totalLosses += Math.abs(pnl);
                largestLoss = Math.max(largestLoss, Math.abs(pnl));
            }

            // Track drawdown
            if (equity > peak) {
                peak = equity;
            }
            double dd = peak - equity;
            maxDD = Math.max(maxDD, dd);

            // Returns for Sharpe calculation
            if (t.pnlPercent() != null) {
                returns.add(t.pnlPercent());
            }

            // Holding period
            if (t.exitTime() != null) {
                totalHoldingPeriod += (t.exitTime() - t.entryTime());
            }
        }

        int total = winners + losers;
        double winRate = total > 0 ? (double) winners / total * 100 : 0;
        double profitFactor = totalLosses > 0 ? totalWins / totalLosses : totalWins > 0 ? Double.POSITIVE_INFINITY : 0;
        double avgWin = winners > 0 ? totalWins / winners : 0;
        double avgLoss = losers > 0 ? totalLosses / losers : 0;
        double totalReturn = equity - initialCapital;
        double totalReturnPct = (totalReturn / initialCapital) * 100;
        double maxDDPct = peak > 0 ? (maxDD / peak) * 100 : 0;
        double avgHolding = total > 0 ? totalHoldingPeriod / total / (60 * 60 * 1000) : 0; // in hours

        // Sharpe Ratio (annualized, assuming 252 trading days)
        double sharpe = 0;
        if (!returns.isEmpty()) {
            double mean = returns.stream().mapToDouble(d -> d).average().orElse(0);
            double variance = returns.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
            double stdDev = Math.sqrt(variance);
            if (stdDev > 0) {
                sharpe = (mean / stdDev) * Math.sqrt(252);
            }
        }

        // Calculate max capital usage
        double[] maxCap = calculateMaxCapitalUsage(trades, initialCapital);

        return new PerformanceMetrics(
            total, winners, losers, winRate, profitFactor,
            totalReturn, totalReturnPct, maxDD, maxDDPct, sharpe,
            avgWin, avgLoss, largestWin, largestLoss, avgHolding, equity, totalFees, maxCap[0], maxCap[1]
        );
    }

    /**
     * Calculate max capital usage (percentage and dollars) from trades
     */
    private static double[] calculateMaxCapitalUsage(java.util.List<Trade> trades, double initialCapital) {
        // Filter valid trades (non-rejected)
        java.util.List<Trade> validTrades = trades.stream()
            .filter(t -> t.quantity() > 0)
            .toList();

        if (validTrades.isEmpty()) return new double[] { 0, 0 };

        // Build timeline of events
        record Event(long time, boolean isEntry, double value, double pnl) {}
        java.util.List<Event> events = new java.util.ArrayList<>();

        for (Trade t : validTrades) {
            double value = t.entryPrice() * t.quantity();
            double pnl = t.pnl() != null ? t.pnl() : 0;
            events.add(new Event(t.entryTime(), true, value, 0));
            if (t.exitTime() != null) {
                events.add(new Event(t.exitTime(), false, value, pnl));
            }
        }

        // Sort: by time, exits before entries at same time
        events.sort((a, b) -> {
            int cmp = Long.compare(a.time(), b.time());
            if (cmp != 0) return cmp;
            return Boolean.compare(a.isEntry(), b.isEntry()); // false (exit) before true (entry)
        });

        double equity = initialCapital;
        double invested = 0;
        double maxUsage = 0;
        double dollarsAtMaxUsage = 0;

        for (Event e : events) {
            if (e.isEntry()) {
                invested += e.value();
            } else {
                invested -= e.value();
                equity += e.pnl();
            }
            invested = Math.max(0, invested);
            double usage = equity > 0 ? (invested / equity) * 100 : 0;
            if (usage > maxUsage) {
                maxUsage = usage;
                dollarsAtMaxUsage = invested;
            }
        }

        return new double[] { maxUsage, dollarsAtMaxUsage };
    }
}
