package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Result of a backtest run.
 * Stored as JSON in ~/.tradery/results/
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BacktestResult(
    String runId,           // Unique ID for this run (for change detection)
    String configHash,      // Hash of strategy config (to verify results match input)
    String strategyId,
    String strategyName,
    Strategy strategy,      // Full strategy definition (for Claude Code)
    BacktestConfig config,
    List<Trade> trades,
    PerformanceMetrics metrics,
    long startTime,
    long endTime,
    int barsProcessed,
    long duration,
    List<String> errors,
    List<String> warnings   // Non-fatal issues (e.g., overlapping exit zones)
) {
    /**
     * Generate a new unique run ID
     */
    public static String newRunId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generate a hash of the strategy configuration for change detection
     */
    public static String hashConfig(String entry, String exitZonesJson, String symbol, String timeframe, String duration) {
        try {
            String content = entry + "|" + exitZonesJson + "|" + symbol + "|" + timeframe + "|" + duration;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 4; i++) {  // First 8 hex chars
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
    /**
     * Check if the backtest completed successfully (no errors)
     */
    public boolean isSuccessful() {
        return errors == null || errors.isEmpty();
    }

    /**
     * Check if the backtest has warnings
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * Get summary string
     */
    public String getSummary() {
        return String.format(
            "%s: %d trades, %.1f%% win rate, %.2f profit factor, %+.2f%% return",
            strategyName,
            metrics.totalTrades(),
            metrics.winRate(),
            metrics.profitFactor(),
            metrics.totalReturnPercent()
        );
    }
}
