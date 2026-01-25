package com.tradery.desk.signal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents duplicate signals within a cooldown period.
 * Tracks last signal time per strategy/type combination.
 */
public class SignalDeduplicator {

    // Default cooldown: 1 candle (in milliseconds)
    // For 1h timeframe, this would be 3600000ms
    private final long defaultCooldownMs;

    // Map of "strategyId:signalType" -> last signal timestamp
    private final Map<String, Long> lastSignals = new ConcurrentHashMap<>();

    public SignalDeduplicator() {
        this(0); // No cooldown by default - each candle can trigger
    }

    public SignalDeduplicator(long defaultCooldownMs) {
        this.defaultCooldownMs = defaultCooldownMs;
    }

    /**
     * Check if a signal should be allowed (not a duplicate).
     * Returns true if the signal is allowed, false if it's a duplicate.
     */
    public boolean shouldAllow(SignalEvent signal) {
        return shouldAllow(signal, defaultCooldownMs);
    }

    /**
     * Check if a signal should be allowed with a specific cooldown.
     */
    public boolean shouldAllow(SignalEvent signal, long cooldownMs) {
        String key = makeKey(signal.strategyId(), signal.type());
        long now = System.currentTimeMillis();

        Long lastTime = lastSignals.get(key);
        if (lastTime != null && (now - lastTime) < cooldownMs) {
            return false; // Still in cooldown
        }

        // Record this signal
        lastSignals.put(key, now);
        return true;
    }

    /**
     * Check if a signal is a duplicate based on candle timestamp.
     * This ensures we only signal once per candle close.
     */
    public boolean isDuplicateCandle(SignalEvent signal) {
        String key = makeKey(signal.strategyId(), signal.type()) + ":candle";
        Long lastCandleTime = lastSignals.get(key);

        if (lastCandleTime != null && lastCandleTime == signal.candleTimestamp()) {
            return true; // Same candle
        }

        // Record this candle timestamp
        lastSignals.put(key, signal.candleTimestamp());
        return false;
    }

    /**
     * Record a signal without checking (for when signal is already validated).
     */
    public void recordSignal(SignalEvent signal) {
        String key = makeKey(signal.strategyId(), signal.type());
        lastSignals.put(key, System.currentTimeMillis());
        lastSignals.put(key + ":candle", signal.candleTimestamp());
    }

    /**
     * Clear all recorded signals.
     */
    public void clear() {
        lastSignals.clear();
    }

    /**
     * Clear signals for a specific strategy.
     */
    public void clearStrategy(String strategyId) {
        lastSignals.keySet().removeIf(k -> k.startsWith(strategyId + ":"));
    }

    private String makeKey(String strategyId, SignalEvent.SignalType type) {
        return strategyId + ":" + type.name();
    }
}
