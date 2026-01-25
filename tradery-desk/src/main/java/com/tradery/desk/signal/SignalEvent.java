package com.tradery.desk.signal;

import com.tradery.desk.strategy.PublishedStrategy;
import com.tradery.model.Candle;

import java.time.Instant;

/**
 * Represents a signal event (entry or exit condition triggered).
 */
public record SignalEvent(
    SignalType type,
    String strategyId,
    String strategyName,
    int strategyVersion,
    String symbol,
    String timeframe,
    double price,
    long candleTimestamp,
    Instant timestamp,
    String condition
) {
    public enum SignalType {
        ENTRY("ENTRY", "🔵"),
        EXIT("EXIT", "🔴");

        private final String label;
        private final String emoji;

        SignalType(String label, String emoji) {
            this.label = label;
            this.emoji = emoji;
        }

        public String getLabel() {
            return label;
        }

        public String getEmoji() {
            return emoji;
        }
    }

    /**
     * Create an entry signal.
     */
    public static SignalEvent entry(PublishedStrategy strategy, Candle candle) {
        return new SignalEvent(
            SignalType.ENTRY,
            strategy.getId(),
            strategy.getName(),
            strategy.getVersion(),
            strategy.getSymbol(),
            strategy.getTimeframe(),
            candle.close(),
            candle.timestamp(),
            Instant.now(),
            strategy.getEntry()
        );
    }

    /**
     * Create an exit signal.
     */
    public static SignalEvent exit(PublishedStrategy strategy, Candle candle, String exitCondition) {
        return new SignalEvent(
            SignalType.EXIT,
            strategy.getId(),
            strategy.getName(),
            strategy.getVersion(),
            strategy.getSymbol(),
            strategy.getTimeframe(),
            candle.close(),
            candle.timestamp(),
            Instant.now(),
            exitCondition
        );
    }

    /**
     * Get a formatted summary string.
     */
    public String toSummary() {
        return String.format("%s %s @ %.2f (%s v%d)",
            type.getEmoji(),
            type.getLabel(),
            price,
            strategyName,
            strategyVersion
        );
    }

    /**
     * Get a detailed message for notifications.
     */
    public String toDetailedMessage() {
        return String.format("%s Signal: %s\nStrategy: %s v%d\nSymbol: %s %s\nPrice: %.4f\nCondition: %s",
            type.getLabel(),
            strategyName,
            strategyName,
            strategyVersion,
            symbol,
            timeframe,
            price,
            condition != null ? condition : "N/A"
        );
    }

    /**
     * Convert to JSON for webhooks.
     */
    public String toJson() {
        return String.format("""
            {
              "type": "%s",
              "strategyId": "%s",
              "strategyName": "%s",
              "strategyVersion": %d,
              "symbol": "%s",
              "timeframe": "%s",
              "price": %.8f,
              "candleTimestamp": %d,
              "timestamp": "%s",
              "condition": "%s"
            }""",
            type.name(),
            strategyId,
            strategyName,
            strategyVersion,
            symbol,
            timeframe,
            price,
            candleTimestamp,
            timestamp.toString(),
            condition != null ? condition.replace("\"", "\\\"") : ""
        );
    }
}
