package com.tradery.engine;

import com.tradery.data.CandleStore;
import com.tradery.io.PhaseStore;
import com.tradery.model.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Analyzes phase correlation with trade performance.
 * For each phase, computes metrics when the phase is active vs inactive at trade entry.
 */
public class PhaseAnalyzer {

    private final CandleStore candleStore;
    private final PhaseStore phaseStore;

    public PhaseAnalyzer(CandleStore candleStore, PhaseStore phaseStore) {
        this.candleStore = candleStore;
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

        List<PhaseAnalysisResult> results = new ArrayList<>();
        PhaseEvaluator evaluator = new PhaseEvaluator(candleStore);

        int total = allPhases.size();
        int current = 0;

        for (Phase phase : allPhases) {
            current++;

            if (progressCallback != null) {
                progressCallback.accept(new Progress(current, total, phase.getName()));
            }

            try {
                PhaseAnalysisResult result = analyzePhase(phase, completedTrades, candles, timeframe, evaluator);
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
     * Analyze a single phase against the trades.
     */
    private PhaseAnalysisResult analyzePhase(
            Phase phase,
            List<Trade> trades,
            List<Candle> candles,
            String timeframe,
            PhaseEvaluator evaluator
    ) throws IOException {

        // Evaluate this phase to get state per bar
        Map<String, boolean[]> phaseStates = evaluator.evaluatePhases(
                Collections.singletonList(phase),
                candles,
                timeframe
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
