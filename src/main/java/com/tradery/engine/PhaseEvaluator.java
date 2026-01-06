package com.tradery.engine;

import com.tradery.data.CandleStore;
import com.tradery.dsl.AstNode;
import com.tradery.dsl.Parser;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.model.Candle;
import com.tradery.model.Phase;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates phases on their own timeframes and maps results to strategy timeframe.
 * Phases are market regime conditions (ranging, trending, crash, etc.) that
 * act as entry filters for strategies.
 */
public class PhaseEvaluator {

    private final CandleStore candleStore;

    public PhaseEvaluator(CandleStore candleStore) {
        this.candleStore = candleStore;
    }

    /**
     * Pre-compute phase state for all required phases over the backtest period.
     *
     * @param requiredPhases   List of Phase objects to evaluate
     * @param strategyCandles  Candles from the strategy timeframe
     * @param strategyTimeframe Strategy's timeframe (e.g., "1h")
     * @return Map: phaseId -> boolean[] (true = active at strategy bar index)
     */
    public Map<String, boolean[]> evaluatePhases(
            List<Phase> requiredPhases,
            List<Candle> strategyCandles,
            String strategyTimeframe
    ) throws IOException {

        Map<String, boolean[]> result = new HashMap<>();

        if (requiredPhases.isEmpty() || strategyCandles.isEmpty()) {
            return result;
        }

        long startTime = strategyCandles.get(0).timestamp();
        long endTime = strategyCandles.get(strategyCandles.size() - 1).timestamp();

        for (Phase phase : requiredPhases) {
            // Calculate warmup needed for phase indicators
            long warmupMs = getWarmupMs(phase.getTimeframe(), phase.getCondition());

            // Load candles for phase's timeframe (with warmup buffer)
            List<Candle> phaseCandles = candleStore.getCandles(
                phase.getSymbol(),
                phase.getTimeframe(),
                startTime - warmupMs,
                endTime
            );

            if (phaseCandles.isEmpty()) {
                System.err.println("No candles for phase " + phase.getId() + " (" +
                    phase.getSymbol() + "/" + phase.getTimeframe() + ")");
                // Return all-false array for this phase
                result.put(phase.getId(), new boolean[strategyCandles.size()]);
                continue;
            }

            // Evaluate phase condition on phase timeframe
            boolean[] phaseState = evaluatePhaseOnTimeframe(phase, phaseCandles);

            // Map phase state to strategy candles
            boolean[] mappedState = mapToStrategyTimeframe(
                phaseCandles, phaseState,
                strategyCandles
            );

            result.put(phase.getId(), mappedState);
        }

        return result;
    }

    /**
     * Evaluate a phase's DSL condition on its own timeframe candles.
     */
    private boolean[] evaluatePhaseOnTimeframe(Phase phase, List<Candle> candles) {
        boolean[] state = new boolean[candles.size()];

        // Parse the DSL condition
        Parser parser = new Parser();
        Parser.ParseResult parsed = parser.parse(phase.getCondition());

        if (!parsed.success()) {
            System.err.println("Phase '" + phase.getName() + "' parse error: " + parsed.error());
            return state; // All false
        }

        // Set up indicator engine for phase candles
        IndicatorEngine engine = new IndicatorEngine();
        engine.setCandles(candles, phase.getTimeframe());
        ConditionEvaluator evaluator = new ConditionEvaluator(engine);

        // Calculate warmup bars needed for indicators
        int warmupBars = calculateWarmupBars(phase.getCondition());

        // Evaluate at each bar
        for (int i = warmupBars; i < candles.size(); i++) {
            try {
                state[i] = evaluator.evaluate(parsed.ast(), i);
            } catch (Exception e) {
                // Evaluation error - keep as false
                state[i] = false;
            }
        }

        return state;
    }

    /**
     * Map phase state (on phase timeframe) to strategy candles.
     * For each strategy candle, find the most recent phase candle and use its state.
     */
    private boolean[] mapToStrategyTimeframe(
            List<Candle> phaseCandles,
            boolean[] phaseState,
            List<Candle> strategyCandles
    ) {
        boolean[] mapped = new boolean[strategyCandles.size()];

        // Build timestamp -> index map for phase candles
        NavigableMap<Long, Integer> tsToIndex = new TreeMap<>();
        for (int i = 0; i < phaseCandles.size(); i++) {
            tsToIndex.put(phaseCandles.get(i).timestamp(), i);
        }

        // For each strategy candle, find applicable phase state
        for (int i = 0; i < strategyCandles.size(); i++) {
            long strategyTs = strategyCandles.get(i).timestamp();

            // Find the phase candle at or before this timestamp (floor entry)
            Map.Entry<Long, Integer> entry = tsToIndex.floorEntry(strategyTs);
            if (entry != null) {
                int phaseIndex = entry.getValue();
                mapped[i] = phaseState[phaseIndex];
            } else {
                mapped[i] = false; // No phase data yet
            }
        }

        return mapped;
    }

    /**
     * Get warmup time in milliseconds based on timeframe and condition.
     */
    private long getWarmupMs(String timeframe, String condition) {
        int warmupBars = calculateWarmupBars(condition);
        long tfMs = getTimeframeMs(timeframe);
        return tfMs * warmupBars;
    }

    /**
     * Calculate warmup bars needed based on indicator periods in the condition.
     */
    private int calculateWarmupBars(String condition) {
        int maxPeriod = 50; // Default minimum warmup

        // Extract periods from indicator calls
        Pattern periodPattern = Pattern.compile("(?:SMA|EMA|RSI|ATR|MACD|BBANDS|HIGH_OF|LOW_OF|AVG_VOLUME)\\s*\\(\\s*(\\d+)");
        Matcher matcher = periodPattern.matcher(condition);

        while (matcher.find()) {
            int period = Integer.parseInt(matcher.group(1));
            maxPeriod = Math.max(maxPeriod, period);
        }

        // Add buffer for indicator calculations
        return maxPeriod + 10;
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
     * Check if all required phases are active at a given strategy bar.
     *
     * @param phaseStates Map of phase ID to boolean state array
     * @param requiredPhaseIds List of required phase IDs (all must be active)
     * @param barIndex Current bar index in strategy candles
     * @return true if all required phases are active, or if no phases required
     */
    public static boolean allPhasesActive(
            Map<String, boolean[]> phaseStates,
            List<String> requiredPhaseIds,
            int barIndex
    ) {
        if (requiredPhaseIds == null || requiredPhaseIds.isEmpty()) {
            return true; // No phases required
        }

        for (String phaseId : requiredPhaseIds) {
            boolean[] state = phaseStates.get(phaseId);
            if (state == null) {
                return false; // Phase not found
            }
            if (barIndex < 0 || barIndex >= state.length) {
                return false; // Index out of bounds
            }
            if (!state[barIndex]) {
                return false; // Phase not active
            }
        }

        return true; // All phases active
    }
}
