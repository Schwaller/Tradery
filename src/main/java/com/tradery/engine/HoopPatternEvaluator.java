package com.tradery.engine;

import com.tradery.data.CandleStore;
import com.tradery.model.*;

import java.io.IOException;
import java.util.*;

/**
 * Evaluates hoop patterns on their own timeframes and maps results to strategy timeframe.
 *
 * Unlike phases (which are boolean states per bar), hoop patterns are event-based:
 * they "complete" when all hoops in sequence are hit within their time windows.
 * The result is a boolean[] where true = pattern completed at that bar.
 */
public class HoopPatternEvaluator {

    private final CandleStore candleStore;

    public HoopPatternEvaluator(CandleStore candleStore) {
        this.candleStore = candleStore;
    }

    /**
     * Pre-compute pattern completions for all patterns over the backtest period.
     *
     * @param patterns         List of HoopPattern objects to evaluate
     * @param strategyCandles  Candles from the strategy timeframe
     * @param strategyTimeframe Strategy's timeframe (e.g., "1h")
     * @return Map: patternId -> boolean[] (true = pattern completed at strategy bar index)
     */
    public Map<String, boolean[]> evaluatePatterns(
            List<HoopPattern> patterns,
            List<Candle> strategyCandles,
            String strategyTimeframe
    ) throws IOException {

        Map<String, boolean[]> result = new HashMap<>();

        if (patterns.isEmpty() || strategyCandles.isEmpty()) {
            return result;
        }

        long startTime = strategyCandles.get(0).timestamp();
        long endTime = strategyCandles.get(strategyCandles.size() - 1).timestamp();

        for (HoopPattern pattern : patterns) {
            // Calculate warmup needed for pattern (max pattern length)
            long warmupMs = getWarmupMs(pattern);

            // Load candles for pattern's timeframe (with warmup buffer)
            List<Candle> patternCandles = candleStore.getCandles(
                pattern.getSymbol(),
                pattern.getTimeframe(),
                startTime - warmupMs,
                endTime
            );

            if (patternCandles.isEmpty()) {
                System.err.println("No candles for hoop pattern " + pattern.getId() +
                    " (" + pattern.getSymbol() + "/" + pattern.getTimeframe() + ")");
                result.put(pattern.getId(), new boolean[strategyCandles.size()]);
                continue;
            }

            // Find all pattern completions on pattern timeframe
            List<HoopMatchResult> matches = findPatternCompletions(pattern, patternCandles);

            // Map completion bars to strategy timeframe
            boolean[] mappedState = mapCompletionsToStrategyTimeframe(
                patternCandles, matches, strategyCandles
            );

            result.put(pattern.getId(), mappedState);
        }

        return result;
    }

    /**
     * Core pattern matching algorithm.
     * Returns list of all successful pattern matches with details.
     */
    public List<HoopMatchResult> findPatternCompletions(HoopPattern pattern, List<Candle> candles) {
        List<HoopMatchResult> matches = new ArrayList<>();
        List<Hoop> hoops = pattern.getHoops();

        if (hoops.isEmpty() || candles.isEmpty()) {
            return matches;
        }

        int lastCompletionBar = -pattern.getCooldownBars() - 1; // Allow first match

        // Scan through candles looking for pattern starts
        for (int startBar = 0; startBar < candles.size(); startBar++) {
            // Cooldown check - skip if too close to last completion
            if (!pattern.isAllowOverlap() &&
                (startBar - lastCompletionBar) <= pattern.getCooldownBars()) {
                continue;
            }

            // Try to match pattern starting at this bar
            HoopMatchResult match = tryMatchPattern(pattern, candles, startBar);

            if (match != null) {
                matches.add(match);
                lastCompletionBar = match.completionBar();

                // Skip ahead if not allowing overlap
                if (!pattern.isAllowOverlap()) {
                    startBar = match.completionBar();
                }
            }
        }

        return matches;
    }

    /**
     * Attempt to match a pattern starting at the given bar.
     * Returns HoopMatchResult if successful, null if pattern fails.
     */
    private HoopMatchResult tryMatchPattern(HoopPattern pattern, List<Candle> candles, int startBar) {
        List<Hoop> hoops = pattern.getHoops();

        double anchor = candles.get(startBar).close();
        int currentBar = startBar;
        double[] hitPrices = new double[hoops.size()];
        int[] hitBars = new int[hoops.size()];

        for (int h = 0; h < hoops.size(); h++) {
            Hoop hoop = hoops.get(h);

            // Define time window for this hoop
            int windowStart = hoop.getWindowStart(currentBar);
            int windowEnd = hoop.getWindowEnd(currentBar);

            // Clamp to valid range (must be after current bar)
            windowStart = Math.max(windowStart, currentBar + 1);
            windowEnd = Math.min(windowEnd, candles.size() - 1);

            // If window is invalid (start > end or past data), pattern fails
            if (windowStart > windowEnd) {
                return null;
            }

            // Scan window for hoop hit
            boolean hit = false;
            for (int bar = windowStart; bar <= windowEnd; bar++) {
                double close = candles.get(bar).close();

                if (hoop.priceInRange(close, anchor)) {
                    // Hoop hit!
                    hitPrices[h] = close;
                    hitBars[h] = bar;
                    currentBar = bar;

                    // Update anchor for next hoop
                    anchor = hoop.calculateNextAnchor(close, anchor);
                    hit = true;
                    break;
                }
            }

            if (!hit) {
                // Pattern failed - hoop not hit within window
                return null;
            }
        }

        // All hoops matched!
        return new HoopMatchResult(
            pattern.getId(),
            startBar,
            currentBar,
            candles.get(startBar).close(),
            hitPrices,
            hitBars
        );
    }

    /**
     * Map pattern completion bars to strategy candles.
     * A strategy bar is marked true if a pattern completed at a timestamp
     * that falls within the window of that strategy candle.
     */
    private boolean[] mapCompletionsToStrategyTimeframe(
            List<Candle> patternCandles,
            List<HoopMatchResult> matches,
            List<Candle> strategyCandles
    ) {
        boolean[] mapped = new boolean[strategyCandles.size()];

        if (matches.isEmpty()) {
            return mapped;
        }

        // Build list of completion timestamps
        List<Long> completionTimestamps = new ArrayList<>();
        for (HoopMatchResult match : matches) {
            long ts = patternCandles.get(match.completionBar()).timestamp();
            completionTimestamps.add(ts);
        }
        Collections.sort(completionTimestamps);

        // For each strategy candle, check if any pattern completed in the window
        // between the previous strategy candle and this one
        for (int i = 0; i < strategyCandles.size(); i++) {
            long currentTs = strategyCandles.get(i).timestamp();
            long prevTs = i > 0 ? strategyCandles.get(i - 1).timestamp() : 0;

            // Check if any completion timestamp falls in (prevTs, currentTs]
            for (Long completionTs : completionTimestamps) {
                if (completionTs > prevTs && completionTs <= currentTs) {
                    mapped[i] = true;
                    break;
                }
            }
        }

        return mapped;
    }

    /**
     * Get warmup time in milliseconds based on pattern length.
     */
    private long getWarmupMs(HoopPattern pattern) {
        int maxBars = pattern.getMaxPatternBars() + 20; // Buffer for pattern to start matching
        long tfMs = getTimeframeMs(pattern.getTimeframe());
        return tfMs * maxBars;
    }

    /**
     * Get timeframe duration in milliseconds.
     */
    private long getTimeframeMs(String timeframe) {
        return switch (timeframe) {
            case "1m" -> 60_000L;
            case "5m" -> 5 * 60_000L;
            case "15m" -> 15 * 60_000L;
            case "30m" -> 30 * 60_000L;
            case "1h" -> 60 * 60_000L;
            case "4h" -> 4 * 60 * 60_000L;
            case "1d" -> 24 * 60 * 60_000L;
            case "1w" -> 7 * 24 * 60 * 60_000L;
            default -> 60 * 60_000L; // Default to 1h
        };
    }

    /**
     * Check if all required patterns completed at bar and no excluded patterns completed.
     *
     * @param patternStates Map of pattern ID to boolean state array
     * @param requiredPatternIds List of required pattern IDs (all must be active/completed)
     * @param excludedPatternIds List of excluded pattern IDs (none must be active/completed)
     * @param barIndex Current bar index in strategy candles
     * @return true if conditions are met
     */
    public static boolean patternsMatch(
            Map<String, boolean[]> patternStates,
            List<String> requiredPatternIds,
            List<String> excludedPatternIds,
            int barIndex
    ) {
        // Check required patterns (all must have completed at this bar)
        if (requiredPatternIds != null && !requiredPatternIds.isEmpty()) {
            for (String patternId : requiredPatternIds) {
                boolean[] state = patternStates.get(patternId);
                if (state == null) {
                    return false; // Pattern not found
                }
                if (barIndex < 0 || barIndex >= state.length) {
                    return false; // Index out of bounds
                }
                if (!state[barIndex]) {
                    return false; // Required pattern didn't complete at this bar
                }
            }
        }

        // Check excluded patterns (none must have completed at this bar)
        if (excludedPatternIds != null && !excludedPatternIds.isEmpty()) {
            for (String patternId : excludedPatternIds) {
                boolean[] state = patternStates.get(patternId);
                if (state == null) {
                    continue; // Pattern not found - treat as not completed
                }
                if (barIndex >= 0 && barIndex < state.length && state[barIndex]) {
                    return false; // Excluded pattern completed at this bar
                }
            }
        }

        return true;
    }

    /**
     * Check if any of the required patterns completed at the given bar.
     * This is an OR check (at least one pattern must match).
     */
    public static boolean anyPatternMatches(
            Map<String, boolean[]> patternStates,
            List<String> patternIds,
            int barIndex
    ) {
        if (patternIds == null || patternIds.isEmpty()) {
            return false;
        }

        for (String patternId : patternIds) {
            boolean[] state = patternStates.get(patternId);
            if (state != null && barIndex >= 0 && barIndex < state.length && state[barIndex]) {
                return true;
            }
        }

        return false;
    }
}
