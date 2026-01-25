package com.tradery.forge.data;

import java.util.Map;

/**
 * Utility to estimate sync duration for aggTrades data.
 * Estimates are based on typical trading volumes per symbol.
 */
public final class SyncEstimator {

    private SyncEstimator() {} // Utility class

    // Approximate trades per day by symbol (based on typical volumes)
    private static final Map<String, Long> TRADES_PER_DAY = Map.ofEntries(
        Map.entry("BTCUSDT", 2_000_000L),
        Map.entry("ETHUSDT", 800_000L),
        Map.entry("BNBUSDT", 300_000L),
        Map.entry("SOLUSDT", 400_000L),
        Map.entry("XRPUSDT", 350_000L),
        Map.entry("DOGEUSDT", 300_000L),
        Map.entry("ADAUSDT", 250_000L),
        Map.entry("AVAXUSDT", 200_000L),
        Map.entry("DOTUSDT", 150_000L),
        Map.entry("MATICUSDT", 200_000L),
        Map.entry("LINKUSDT", 150_000L),
        Map.entry("LTCUSDT", 100_000L)
    );

    private static final long DEFAULT_TRADES_PER_DAY = 200_000L;

    /**
     * Get estimated trades per day for a symbol.
     */
    public static long getTradesPerDay(String symbol) {
        return TRADES_PER_DAY.getOrDefault(symbol.toUpperCase(), DEFAULT_TRADES_PER_DAY);
    }

    /**
     * Estimate sync time for a date range.
     *
     * @param symbol    Trading pair
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return Human-readable time estimate (e.g., "~2 min", "<1 min")
     */
    public static String estimateSyncTime(String symbol, long startTime, long endTime) {
        long days = Math.max(1, (endTime - startTime) / (24 * 60 * 60 * 1000));
        long tradesPerDay = getTradesPerDay(symbol);
        long totalTrades = days * tradesPerDay;

        // 1000 trades per request, ~50ms per request
        long requests = totalTrades / 1000;
        long seconds = requests * 50 / 1000;

        if (seconds < 30) return "<1 min";
        if (seconds < 90) return "~1 min";
        if (seconds < 150) return "~2 min";
        if (seconds < 210) return "~3 min";
        if (seconds < 300) return "~4 min";
        if (seconds < 420) return "~5-6 min";
        if (seconds < 600) return "~8-10 min";
        return "~" + (seconds / 60) + " min";
    }

    /**
     * Get display name for mode with sync estimate.
     *
     * @param symbol    Trading pair
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return Formatted string like "Full - includes Delta (~2 min sync)"
     */
    public static String getFullModeDisplayName(String symbol, long startTime, long endTime) {
        String estimate = estimateSyncTime(symbol, startTime, endTime);
        return "Full - includes Delta (" + estimate + " sync)";
    }
}
