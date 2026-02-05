package com.tradery.forge.analysis;

import com.tradery.core.model.Candle;
import com.tradery.core.model.Phase;
import com.tradery.core.model.PhaseAnalysisResult;
import com.tradery.core.model.Trade;
import com.tradery.engine.PhaseEvaluator;
import com.tradery.forge.data.sqlite.SqliteDataStore;
import com.tradery.forge.io.PhaseStore;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * Analyzes phase correlation with trade performance.
 * For each phase, computes metrics when the phase is active vs inactive at trade entry.
 */
public class PhaseAnalyzer {

    private final SqliteDataStore dataStore;
    private final PhaseStore phaseStore;

    public PhaseAnalyzer(SqliteDataStore dataStore, PhaseStore phaseStore) {
        this.dataStore = dataStore;
        this.phaseStore = phaseStore;
    }

    public record Progress(int current, int total, String phaseName) {}

    /**
     * Analyze all available phases against the given trades.
     *
     * @param trades Completed trades from backtest (excludes rejected)
     * @param candles Candles used in the backtest
     * @param timeframe Strategy timeframe
     * @param progressCallback Optional callback for progress updates
     * @return Analysis results for each phase
     */
    public List<PhaseAnalysisResult> analyzePhases(
            List<Trade> trades,
            List<Candle> candles,
            String timeframe,
            Consumer<Progress> progressCallback
    ) throws IOException {

        // Load all available phases
        List<Phase> allPhases = phaseStore.loadAll();

        if (allPhases.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter to completed trades only (not rejected, not open)
        List<Trade> completedTrades = trades.stream()
                .filter(t -> t.exitTime() != null && !"rejected".equals(t.exitReason()))
                .toList();

        if (completedTrades.isEmpty()) {
            return Collections.emptyList();
        }

        // Determine time range from candles
        long startTime = candles.isEmpty() ? 0 : candles.get(0).timestamp();
        long endTime = candles.isEmpty() ? 0 : candles.get(candles.size() - 1).timestamp();

        // Pre-fetch phase candles for all unique symbol:timeframe combinations
        Map<String, List<Candle>> phaseCandles = fetchPhaseCandles(allPhases, startTime, endTime);

        // Create stateless evaluator
        PhaseEvaluator evaluator = new PhaseEvaluator();

        List<PhaseAnalysisResult> results = new ArrayList<>();
        int total = allPhases.size();
        int current = 0;

        for (Phase phase : allPhases) {
            current++;

            if (progressCallback != null) {
                progressCallback.accept(new Progress(current, total, phase.getName()));
            }

            try {
                PhaseAnalysisResult result = analyzePhase(phase, completedTrades, candles, timeframe, evaluator, phaseCandles);
                results.add(result);
            } catch (Exception e) {
                System.err.println("Failed to analyze phase " + phase.getId() + ": " + e.getMessage());
                // Add a result with N/A values
                results.add(createErrorResult(phase));
            }
        }

        return results;
    }

    /**
     * Fetch candles for all phases from the dataStore.
     */
    private Map<String, List<Candle>> fetchPhaseCandles(List<Phase> phases, long startTime, long endTime) {
        Map<String, List<Candle>> phaseCandles = new HashMap<>();

        // Collect unique symbol:timeframe combinations
        Set<String> keys = new HashSet<>();
        for (Phase phase : phases) {
            keys.add(phase.getSymbol() + ":" + phase.getTimeframe());
        }

        for (String key : keys) {
            String[] parts = key.split(":");
            String symbol = parts[0];
            String phaseTf = parts[1];

            // Add warmup period (200 bars)
            long warmupMs = getIntervalMs(phaseTf) * 200;
            long phaseStart = startTime - warmupMs;

            try {
                List<Candle> candles = dataStore.getCandles(symbol, phaseTf, phaseStart, endTime);
                phaseCandles.put(key, candles);
            } catch (Exception e) {
                System.err.println("Failed to fetch candles for " + key + ": " + e.getMessage());
                phaseCandles.put(key, Collections.emptyList());
            }
        }

        return phaseCandles;
    }

    /**
     * Get interval in milliseconds for a timeframe.
     */
    private long getIntervalMs(String interval) {
        return switch (interval) {
            case "1m" -> Duration.ofMinutes(1).toMillis();
            case "3m" -> Duration.ofMinutes(3).toMillis();
            case "5m" -> Duration.ofMinutes(5).toMillis();
            case "15m" -> Duration.ofMinutes(15).toMillis();
            case "30m" -> Duration.ofMinutes(30).toMillis();
            case "1h" -> Duration.ofHours(1).toMillis();
            case "2h" -> Duration.ofHours(2).toMillis();
            case "4h" -> Duration.ofHours(4).toMillis();
            case "6h" -> Duration.ofHours(6).toMillis();
            case "8h" -> Duration.ofHours(8).toMillis();
            case "12h" -> Duration.ofHours(12).toMillis();
            case "1d" -> Duration.ofDays(1).toMillis();
            case "3d" -> Duration.ofDays(3).toMillis();
            case "1w" -> Duration.ofDays(7).toMillis();
            case "1M" -> Duration.ofDays(30).toMillis();
            default -> Duration.ofHours(1).toMillis();
        };
    }

    /**
     * Analyze a single phase against the trades.
     */
    private PhaseAnalysisResult analyzePhase(
            Phase phase,
            List<Trade> trades,
            List<Candle> candles,
            String timeframe,
            PhaseEvaluator evaluator,
            Map<String, List<Candle>> phaseCandles
    ) throws IOException {

        // Evaluate this phase to get state per bar
        Map<String, boolean[]> phaseStates = evaluator.evaluatePhases(
                Collections.singletonList(phase),
                candles,
                timeframe,
                phaseCandles
        );

        boolean[] state = phaseStates.get(phase.getId());
        if (state == null) {
            return createErrorResult(phase);
        }

        // Partition trades by phase state at entry
        List<Trade> tradesInPhase = new ArrayList<>();
        List<Trade> tradesOutOfPhase = new ArrayList<>();

        for (Trade trade : trades) {
            int entryBar = trade.entryBar();
            if (entryBar >= 0 && entryBar < state.length && state[entryBar]) {
                tradesInPhase.add(trade);
            } else {
                tradesOutOfPhase.add(trade);
            }
        }

        // Calculate metrics for each group
        Metrics inMetrics = calculateMetrics(tradesInPhase);
        Metrics outMetrics = calculateMetrics(tradesOutOfPhase);

        // Calculate recommendation
        PhaseAnalysisResult.Recommendation recommendation = calculateRecommendation(
                inMetrics, outMetrics
        );

        // Calculate confidence score
        double confidence = calculateConfidence(
                tradesInPhase.size(), tradesOutOfPhase.size(),
                inMetrics.winRate - outMetrics.winRate
        );

        return new PhaseAnalysisResult(
                phase.getId(),
                phase.getName(),
                phase.getCategory() != null ? phase.getCategory() : "Custom",
                tradesInPhase.size(),
                inMetrics.wins,
                inMetrics.winRate,
                inMetrics.totalReturn,
                inMetrics.profitFactor,
                tradesOutOfPhase.size(),
                outMetrics.wins,
                outMetrics.winRate,
                outMetrics.totalReturn,
                outMetrics.profitFactor,
                recommendation,
                confidence
        );
    }

    private record Metrics(int wins, double winRate, double totalReturn, double profitFactor) {}

    private Metrics calculateMetrics(List<Trade> trades) {
        if (trades.isEmpty()) {
            return new Metrics(0, 0, 0, 0);
        }

        int wins = 0;
        double grossProfit = 0;
        double grossLoss = 0;
        double totalReturn = 0;

        for (Trade trade : trades) {
            Double pnlPct = trade.pnlPercent();
            if (pnlPct != null) {
                totalReturn += pnlPct;
                if (pnlPct > 0) {
                    wins++;
                    grossProfit += pnlPct;
                } else {
                    grossLoss += Math.abs(pnlPct);
                }
            }
        }

        double winRate = (trades.size() > 0) ? (wins * 100.0 / trades.size()) : 0;
        double profitFactor = (grossLoss > 0) ? (grossProfit / grossLoss) : (grossProfit > 0 ? Double.MAX_VALUE : 0);

        return new Metrics(wins, winRate, totalReturn, profitFactor);
    }

    private PhaseAnalysisResult.Recommendation calculateRecommendation(Metrics inMetrics, Metrics outMetrics) {
        // Need minimum sample size for reliable recommendation
        // (checked at result level, but we check win counts here)

        double winRateDiff = inMetrics.winRate - outMetrics.winRate;
        double pfIn = Math.min(inMetrics.profitFactor, 10);  // Cap for comparison
        double pfOut = Math.min(outMetrics.profitFactor, 10);
        double pfDiff = pfIn - pfOut;

        // REQUIRE if performance significantly better IN phase
        if (winRateDiff > 10.0 && pfDiff > 0.3) {
            return PhaseAnalysisResult.Recommendation.REQUIRE;
        }

        // EXCLUDE if performance significantly worse IN phase
        if (winRateDiff < -10.0 && pfDiff < -0.3) {
            return PhaseAnalysisResult.Recommendation.EXCLUDE;
        }

        return PhaseAnalysisResult.Recommendation.NEUTRAL;
    }

    private double calculateConfidence(int tradesIn, int tradesOut, double winRateDiff) {
        // Need minimum trades in both groups
        if (tradesIn < 5 || tradesOut < 5) {
            return 0.0;
        }

        // Higher confidence with more trades and larger differences
        double sampleScore = Math.min(1.0, (tradesIn + tradesOut) / 50.0);
        double diffScore = Math.min(1.0, Math.abs(winRateDiff) / 20.0);

        return sampleScore * diffScore;
    }

    private PhaseAnalysisResult createErrorResult(Phase phase) {
        return new PhaseAnalysisResult(
                phase.getId(),
                phase.getName(),
                phase.getCategory() != null ? phase.getCategory() : "Custom",
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                PhaseAnalysisResult.Recommendation.NEUTRAL,
                0
        );
    }
}
