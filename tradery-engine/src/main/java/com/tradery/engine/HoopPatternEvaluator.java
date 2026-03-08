package com.tradery.engine;

import com.tradery.core.indicators.EMA;
import com.tradery.core.indicators.SMA;
import com.tradery.core.model.*;

import java.util.*;

/**
 * Evaluates hoop patterns on their own timeframes and maps results to strategy timeframe.
 *
 * Unlike phases (which are boolean states per bar), hoop patterns are event-based:
 * they "complete" when all hoops in sequence are hit within their time windows.
 * The result is a boolean[] where true = pattern completed at that bar.
 *
 * This version requires all candles to be passed upfront - no data store access.
 */
public class HoopPatternEvaluator {

    /**
     * Pre-compute pattern completions for all patterns over the backtest period.
     * All candles must be provided in the patternCandles map.
     *
     * @param patterns         List of HoopPattern objects to evaluate
     * @param strategyCandles  Candles from the strategy timeframe
     * @param strategyTimeframe Strategy's timeframe (e.g., "1h")
     * @param patternCandles   Pre-fetched candles keyed by "symbol:timeframe"
     * @return Map: patternId -> boolean[] (true = pattern completed at strategy bar index)
     */
    public Map<String, boolean[]> evaluatePatterns(
            List<HoopPattern> patterns,
            List<Candle> strategyCandles,
            String strategyTimeframe,
            Map<String, List<Candle>> patternCandles
    ) {
        Map<String, boolean[]> result = new HashMap<>();

        if (patterns.isEmpty() || strategyCandles.isEmpty()) {
            return result;
        }

        if (patternCandles == null) {
            patternCandles = Collections.emptyMap();
        }

        for (HoopPattern pattern : patterns) {
            // Get candles from pre-fetched map
            String candleKey = pattern.getSymbol() + ":" + pattern.getTimeframe();
            List<Candle> candles = patternCandles.get(candleKey);

            if (candles == null || candles.isEmpty()) {
                System.err.println("No candles for hoop pattern " + pattern.getId() +
                    " (" + pattern.getSymbol() + "/" + pattern.getTimeframe() + ")");
                result.put(pattern.getId(), new boolean[strategyCandles.size()]);
                continue;
            }

            // Find all pattern completions on pattern timeframe
            List<HoopMatchResult> matches = findPatternCompletions(pattern, candles);

            // Map completion bars to strategy timeframe
            boolean[] mappedState = mapCompletionsToStrategyTimeframe(
                candles, matches, strategyCandles
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

        // Pre-compute smoothed prices for the entire candle series
        double[] smoothedPrices = calculateSmoothedPrices(pattern, candles);

        int lastCompletionBar = -pattern.getCooldownBars() - 1; // Allow first match

        // Scan through candles looking for pattern starts
        for (int startBar = 0; startBar < candles.size(); startBar++) {
            // Skip bars with NaN smoothed price (warmup period for SMA/EMA)
            if (Double.isNaN(smoothedPrices[startBar])) {
                continue;
            }

            // Cooldown check - skip if too close to last completion
            if (!pattern.isAllowOverlap() &&
                (startBar - lastCompletionBar) <= pattern.getCooldownBars()) {
                continue;
            }

            // Try to match pattern starting at this bar
            HoopMatchResult match = tryMatchPattern(pattern, candles, smoothedPrices, startBar);

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
     * Uses pre-computed smoothed prices for matching.
     * Uses backtracking: if a later hoop fails, revisits earlier hoops
     * to try alternative hits that might unlock the rest of the pattern.
     * Returns HoopMatchResult if successful, null if pattern fails.
     */
    private HoopMatchResult tryMatchPattern(HoopPattern pattern, List<Candle> candles,
                                            double[] smoothedPrices, int startBar) {
        List<Hoop> hoops = pattern.getHoops();
        double initialAnchor = smoothedPrices[startBar];

        int n = hoops.size();
        double[] hitPrices = new double[n];
        int[] hitBars = new int[n];
        double[] anchors = new double[n];    // anchor price at each hoop level
        int[] refBars = new int[n];          // reference bar for window calculation
        int[] nextSearch = new int[n];       // next bar to try at each level (-1 = start fresh)

        // Initialize first hoop level
        anchors[0] = initialAnchor;
        refBars[0] = startBar;
        nextSearch[0] = -1;

        int h = 0;
        while (h >= 0 && h < n) {
            Hoop hoop = hoops.get(h);
            double anchor = anchors[h];
            int refBar = refBars[h];

            // Define time window for this hoop
            int windowStart = hoop.getWindowStart(refBar);
            int windowEnd = hoop.getWindowEnd(refBar);
            windowStart = Math.max(windowStart, refBar + 1);
            windowEnd = Math.min(windowEnd, candles.size() - 1);

            // Determine where to start scanning (fresh or resuming after backtrack)
            int searchFrom = nextSearch[h] >= 0 ? nextSearch[h] : windowStart;

            // Window exhausted or invalid — backtrack
            if (windowStart > windowEnd || searchFrom > windowEnd) {
                h--;
                continue;
            }

            // Scan for a hit from searchFrom onward
            boolean hit = false;
            for (int bar = searchFrom; bar <= windowEnd; bar++) {
                double price = smoothedPrices[bar];
                if (Double.isNaN(price)) continue;

                if (hoop.priceInRange(price, anchor)) {
                    hitPrices[h] = price;
                    hitBars[h] = bar;
                    nextSearch[h] = bar + 1; // if we backtrack here, try next bar

                    // Set up next hoop level
                    if (h + 1 < n) {
                        anchors[h + 1] = hoop.calculateNextAnchor(price, anchor);
                        refBars[h + 1] = bar;
                        nextSearch[h + 1] = -1;
                    }

                    h++;
                    hit = true;
                    break;
                }
            }

            if (!hit) {
                h--; // backtrack to previous hoop
            }
        }

        if (h == n) {
            return new HoopMatchResult(
                pattern.getId(),
                startBar,
                hitBars[n - 1],
                initialAnchor,
                hitPrices,
                hitBars
            );
        }

        return null;
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

        // Build sorted array of completion timestamps
        long[] completionTs = new long[matches.size()];
        for (int i = 0; i < matches.size(); i++) {
            completionTs[i] = patternCandles.get(matches.get(i).completionBar()).timestamp();
        }
        Arrays.sort(completionTs);

        // For each strategy candle, binary-search for any completion in (prevTs, currentTs]
        for (int i = 0; i < strategyCandles.size(); i++) {
            long currentTs = strategyCandles.get(i).timestamp();
            long prevTs = i > 0 ? strategyCandles.get(i - 1).timestamp() : 0;

            // Find first index where completionTs[idx] > prevTs
            int idx = insertionPoint(completionTs, prevTs);
            if (idx < completionTs.length && completionTs[idx] <= currentTs) {
                mapped[i] = true;
            }
        }

        return mapped;
    }

    /**
     * Find the index of the first element strictly greater than the target.
     */
    private int insertionPoint(long[] sorted, long target) {
        int lo = 0, hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * Calculate smoothed prices based on pattern's smoothing settings.
     * Returns an array where each index corresponds to the smoothed price at that bar.
     * For SMA/EMA, warmup bars will be Double.NaN.
     */
    private double[] calculateSmoothedPrices(HoopPattern pattern, List<Candle> candles) {
        PriceSmoothingType type = pattern.getPriceSmoothingType();
        int period = pattern.getPriceSmoothingPeriod();

        return switch (type) {
            case NONE -> candles.stream().mapToDouble(Candle::close).toArray();
            case SMA -> SMA.calculate(candles, period);
            case EMA -> EMA.calculate(candles, period);
            case HLC3 -> candles.stream()
                .mapToDouble(c -> (c.high() + c.low() + c.close()) / 3.0)
                .toArray();
        };
    }

    /**
     * Get warmup time in milliseconds based on pattern length.
     */
    public static long getWarmupMs(HoopPattern pattern) {
        int smoothingPeriod = pattern.getPriceSmoothingType() == PriceSmoothingType.NONE ||
                              pattern.getPriceSmoothingType() == PriceSmoothingType.HLC3
                              ? 0 : pattern.getPriceSmoothingPeriod();
        int maxBars = pattern.getMaxPatternBars() + 20 + smoothingPeriod;
        long tfMs = getTimeframeMs(pattern.getTimeframe());
        return tfMs * maxBars;
    }

    /**
     * Get timeframe duration in milliseconds.
     */
    public static long getTimeframeMs(String timeframe) {
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
     * Check if all required patterns completed within the window and no excluded patterns completed.
     *
     * @param patternStates Map of pattern ID to boolean state array
     * @param requiredPatternIds List of required pattern IDs (all must be active/completed)
     * @param excludedPatternIds List of excluded pattern IDs (none must be active/completed)
     * @param barIndex Current bar index in strategy candles
     * @param completionWindowBars How many bars a completion signal stays active (1 = exact bar only)
     * @return true if conditions are met
     */
    public static boolean patternsMatch(
            Map<String, boolean[]> patternStates,
            List<String> requiredPatternIds,
            List<String> excludedPatternIds,
            int barIndex,
            int completionWindowBars
    ) {
        int window = Math.max(1, completionWindowBars);

        // Check required patterns (all must have completed within the window)
        if (requiredPatternIds != null && !requiredPatternIds.isEmpty()) {
            for (String patternId : requiredPatternIds) {
                boolean[] state = patternStates.get(patternId);
                if (state == null) {
                    return false;
                }
                boolean found = false;
                int windowStart = Math.max(0, barIndex - window + 1);
                for (int b = windowStart; b <= barIndex && b < state.length; b++) {
                    if (state[b]) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
        }

        // Check excluded patterns (none must have completed within the window)
        if (excludedPatternIds != null && !excludedPatternIds.isEmpty()) {
            for (String patternId : excludedPatternIds) {
                boolean[] state = patternStates.get(patternId);
                if (state == null) {
                    continue;
                }
                int windowStart = Math.max(0, barIndex - window + 1);
                for (int b = windowStart; b <= barIndex && b < state.length; b++) {
                    if (state[b]) {
                        return false;
                    }
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
