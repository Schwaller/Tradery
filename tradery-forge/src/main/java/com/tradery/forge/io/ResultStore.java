package com.tradery.forge.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.forge.TraderyApp;
import com.tradery.core.model.BacktestResult;
import com.tradery.core.model.Trade;
import com.tradery.core.model.PerformanceMetrics;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Stores backtest results as JSON files within the strategy folder.
 *
 * New structure (AI-friendly):
 * - ~/.tradery/strategies/{strategyId}/summary.json (metrics + analysis, no trades)
 * - ~/.tradery/strategies/{strategyId}/trades/0001_WIN_+2.5%_uptrend.json
 * - ~/.tradery/strategies/{strategyId}/history/ (full snapshots)
 *
 * This allows AI to:
 * - Glob filenames to understand trade distribution without parsing
 * - Read summary for quick overview
 * - Sample specific trades as needed
 */
public class ResultStore {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final File strategyDir;
    private final File historyDir;
    private final File tradesDir;
    private final ObjectMapper mapper;
    private final String strategyId;

    /**
     * Create a result store for a specific strategy
     * @param strategyId The strategy ID
     */
    public ResultStore(String strategyId) {
        this.strategyId = strategyId;

        // Store results in strategy folder: ~/.tradery/strategies/{strategyId}/
        this.strategyDir = new File(TraderyApp.USER_DIR, "strategies/" + strategyId);
        this.historyDir = new File(strategyDir, "history");
        this.tradesDir = new File(strategyDir, "trades");

        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Ensure directories exist
        strategyDir.mkdirs();
        historyDir.mkdirs();
        tradesDir.mkdirs();
    }

    /**
     * Get the strategy ID this store is for
     */
    public String getStrategyId() {
        return strategyId;
    }

    /**
     * Save a backtest result:
     * 1. Write individual trade files with descriptive names
     * 2. Write summary.json with metrics and pre-computed analysis
     * 3. Save full result to history
     */
    public void save(BacktestResult result) throws IOException {
        // Clear previous trade files
        clearTradesDir();

        // Write individual trade files
        writeTradeFiles(result.trades());

        // Write summary with analysis
        writeSummary(result);

        // Save to history (full result for point-in-time comparison)
        String timestamp = Instant.ofEpochMilli(result.endTime())
            .atZone(ZoneOffset.UTC)
            .format(DATE_FORMAT);
        String historyFilename = timestamp + ".json";
        File historyFile = new File(historyDir, historyFilename);
        mapper.writeValue(historyFile, result);

        // Write latest.json WITHOUT trades (use trades/ folder instead)
        // Keeps metrics, strategy, config but strips the large trades array
        File latestFile = new File(strategyDir, "latest.json");
        writeLatestWithoutTrades(latestFile, result);

        System.out.println("Saved " + result.trades().size() + " trade files to: " + tradesDir.getAbsolutePath());
    }

    /**
     * Write latest.json without the trades array (trades are in separate files).
     * Keeps: runId, configHash, strategy info, config, metrics, timing, errors/warnings
     * Removes: trades array (can be 1000s of entries)
     */
    private void writeLatestWithoutTrades(File file, BacktestResult result) throws IOException {
        Map<String, Object> stripped = new LinkedHashMap<>();
        stripped.put("runId", result.runId());
        stripped.put("configHash", result.configHash());
        stripped.put("strategyId", result.strategyId());
        stripped.put("strategyName", result.strategyName());
        stripped.put("strategy", result.strategy());
        stripped.put("config", result.config());
        // trades intentionally omitted - use trades/ folder
        stripped.put("tradesCount", result.trades() != null ? result.trades().size() : 0);
        stripped.put("tradesFolder", "trades/");
        stripped.put("metrics", result.metrics());
        stripped.put("startTime", result.startTime());
        stripped.put("endTime", result.endTime());
        stripped.put("barsProcessed", result.barsProcessed());
        stripped.put("duration", result.duration());
        stripped.put("errors", result.errors());
        stripped.put("warnings", result.warnings());
        mapper.writeValue(file, stripped);
    }

    /**
     * Write individual trade files with descriptive filenames
     * Format: {seq}_{WIN|LOSS|REJECTED}_{pnl%}_{phases}.json
     */
    private void writeTradeFiles(List<Trade> trades) throws IOException {
        if (trades == null) return;

        // Filter and sort trades by entry time
        List<Trade> validTrades = trades.stream()
            .filter(t -> t.exitTime() != null || "rejected".equals(t.exitReason()))
            .sorted(Comparator.comparingLong(Trade::entryTime))
            .toList();

        int seq = 1;
        for (Trade trade : validTrades) {
            String filename = buildTradeFilename(seq, trade);
            File tradeFile = new File(tradesDir, filename);
            mapper.writeValue(tradeFile, trade);
            seq++;
        }
    }

    /**
     * Build a descriptive filename for a trade
     * Format: {seq}_{WIN|LOSS|REJECTED}_{LONG|SHORT}_{pnl%}_{phases}.json
     */
    private String buildTradeFilename(int seq, Trade trade) {
        StringBuilder sb = new StringBuilder();

        // Sequence number (padded)
        sb.append(String.format("%04d", seq));
        sb.append("_");

        // Outcome
        if ("rejected".equals(trade.exitReason())) {
            sb.append("REJECTED");
        } else if (trade.pnl() != null && trade.pnl() >= 0) {
            sb.append("WIN");
        } else {
            sb.append("LOSS");
        }
        sb.append("_");

        // Side (LONG or SHORT)
        String side = trade.side();
        if (side != null && "short".equalsIgnoreCase(side)) {
            sb.append("SHORT");
        } else {
            sb.append("LONG");
        }
        sb.append("_");

        // P&L percent (sanitized for filename)
        if (trade.pnlPercent() != null) {
            String pnl = String.format("%+.1f", trade.pnlPercent()).replace(".", "p");
            sb.append(pnl).append("pct");
        } else {
            sb.append("0pct");
        }

        // Add top phases (max 2 for filename brevity)
        if (trade.activePhasesAtEntry() != null && !trade.activePhasesAtEntry().isEmpty()) {
            sb.append("_");
            List<String> phases = trade.activePhasesAtEntry().stream()
                .limit(2)
                .toList();
            sb.append(String.join("_", phases));
        }

        sb.append(".json");
        return sb.toString();
    }

    /**
     * Write summary.json with metrics and pre-computed analysis
     */
    private void writeSummary(BacktestResult result) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();

        // Basic info
        summary.put("runId", result.runId());
        summary.put("strategyId", result.strategyId());
        summary.put("strategyName", result.strategyName());
        summary.put("configHash", result.configHash());

        // Timing
        summary.put("startTime", result.startTime());
        summary.put("endTime", result.endTime());
        summary.put("barsProcessed", result.barsProcessed());
        summary.put("duration", result.duration());

        // Config
        summary.put("config", result.config());

        // Metrics
        summary.put("metrics", result.metrics());

        // Trade counts (for quick reference)
        Map<String, Integer> tradeCounts = new LinkedHashMap<>();
        tradeCounts.put("total", result.metrics().totalTrades());
        tradeCounts.put("winners", result.metrics().winningTrades());
        tradeCounts.put("losers", result.metrics().losingTrades());
        int rejected = (int) result.trades().stream()
            .filter(t -> "rejected".equals(t.exitReason()))
            .count();
        tradeCounts.put("rejected", rejected);
        summary.put("tradeCounts", tradeCounts);

        // Pre-computed analysis for AI
        summary.put("analysis", computeAnalysis(result.trades(), result.metrics()));

        // History trends (compare with previous runs)
        summary.put("historyTrends", computeHistoryTrends(result));

        // Errors
        summary.put("errors", result.errors());

        // Write summary
        File summaryFile = new File(strategyDir, "summary.json");
        mapper.writeValue(summaryFile, summary);
    }

    /**
     * Compute analysis breakdown by phase, hour, day
     */
    private Map<String, Object> computeAnalysis(List<Trade> trades, PerformanceMetrics overall) {
        Map<String, Object> analysis = new LinkedHashMap<>();

        // Filter valid trades
        List<Trade> validTrades = trades.stream()
            .filter(t -> t.pnl() != null && !"rejected".equals(t.exitReason()))
            .toList();

        if (validTrades.isEmpty()) {
            return analysis;
        }

        // By phase at entry
        analysis.put("byPhase", analyzeByPhase(validTrades, overall.winRate()));

        // By hour of entry
        analysis.put("byHour", analyzeByHour(validTrades, overall.winRate()));

        // By day of week
        analysis.put("byDayOfWeek", analyzeByDayOfWeek(validTrades, overall.winRate()));

        // By exit reason
        analysis.put("byExitReason", analyzeByExitReason(validTrades));

        // Footprint/orderflow analysis (only if footprint data available)
        Map<String, Object> footprintAnalysis = analyzeByFootprint(validTrades, overall.winRate());
        if (!footprintAnalysis.isEmpty()) {
            analysis.put("byFootprint", footprintAnalysis);
        }

        // AI suggestions
        List<String> suggestions = generateSuggestions(validTrades, overall);

        // Add footprint-specific suggestions
        suggestions.addAll(generateFootprintSuggestions(validTrades));

        analysis.put("suggestions", suggestions);

        return analysis;
    }

    /**
     * Compute trends by comparing with historical runs.
     * Shows metric changes, config differences, and performance trajectory.
     */
    private Map<String, Object> computeHistoryTrends(BacktestResult current) {
        Map<String, Object> trends = new LinkedHashMap<>();

        // Load history (already sorted newest first)
        List<BacktestResult> history = loadHistory();

        // Filter to only include runs with same configHash (to exclude the current run we're about to save)
        // and runs from before this one
        List<BacktestResult> previousRuns = history.stream()
            .filter(r -> r.endTime() < current.startTime())
            .limit(10)  // Last 10 runs max
            .toList();

        if (previousRuns.isEmpty()) {
            trends.put("hasHistory", false);
            trends.put("message", "No previous runs to compare");
            return trends;
        }

        trends.put("hasHistory", true);
        trends.put("comparedRuns", previousRuns.size());

        // Get the most recent previous run for direct comparison
        BacktestResult lastRun = previousRuns.get(0);
        trends.put("lastRunTime", lastRun.endTime());

        // Metric deltas vs last run
        Map<String, Object> vsLastRun = new LinkedHashMap<>();
        PerformanceMetrics curr = current.metrics();
        PerformanceMetrics prev = lastRun.metrics();

        vsLastRun.put("winRate", formatDelta(curr.winRate(), prev.winRate(), "%"));
        vsLastRun.put("profitFactor", formatDelta(curr.profitFactor(), prev.profitFactor(), "x"));
        vsLastRun.put("totalReturnPercent", formatDelta(curr.totalReturnPercent(), prev.totalReturnPercent(), "%"));
        vsLastRun.put("maxDrawdownPercent", formatDelta(curr.maxDrawdownPercent(), prev.maxDrawdownPercent(), "%"));
        vsLastRun.put("sharpeRatio", formatDelta(curr.sharpeRatio(), prev.sharpeRatio(), ""));
        vsLastRun.put("totalTrades", curr.totalTrades() - prev.totalTrades());
        trends.put("vsLastRun", vsLastRun);

        // Config change detection
        boolean configChanged = !current.configHash().equals(lastRun.configHash());
        trends.put("configChanged", configChanged);

        if (configChanged) {
            // Try to identify what changed
            List<String> changes = detectConfigChanges(current, lastRun);
            trends.put("configChanges", changes);
        }

        // Performance trajectory over last N runs
        if (previousRuns.size() >= 3) {
            Map<String, Object> trajectory = new LinkedHashMap<>();

            // Win rate trend
            List<Double> winRates = new ArrayList<>();
            winRates.add(curr.winRate());
            previousRuns.stream().limit(5).forEach(r -> winRates.add(r.metrics().winRate()));
            trajectory.put("winRate", computeTrend(winRates));

            // Profit factor trend
            List<Double> profitFactors = new ArrayList<>();
            profitFactors.add(curr.profitFactor());
            previousRuns.stream().limit(5)
                .filter(r -> !Double.isInfinite(r.metrics().profitFactor()))
                .forEach(r -> profitFactors.add(r.metrics().profitFactor()));
            if (profitFactors.size() >= 3) {
                trajectory.put("profitFactor", computeTrend(profitFactors));
            }

            // Drawdown trend
            List<Double> drawdowns = new ArrayList<>();
            drawdowns.add(curr.maxDrawdownPercent());
            previousRuns.stream().limit(5).forEach(r -> drawdowns.add(r.metrics().maxDrawdownPercent()));
            trajectory.put("maxDrawdown", computeTrend(drawdowns));

            trends.put("trajectory", trajectory);
        }

        // Best/worst historical performance
        if (previousRuns.size() >= 2) {
            Map<String, Object> historical = new LinkedHashMap<>();

            double bestWinRate = previousRuns.stream()
                .mapToDouble(r -> r.metrics().winRate())
                .max().orElse(0);
            double worstWinRate = previousRuns.stream()
                .mapToDouble(r -> r.metrics().winRate())
                .min().orElse(0);

            historical.put("bestWinRate", Math.round(bestWinRate * 10) / 10.0);
            historical.put("worstWinRate", Math.round(worstWinRate * 10) / 10.0);
            historical.put("currentVsBest", formatDelta(curr.winRate(), bestWinRate, "%"));
            historical.put("isNewBest", curr.winRate() > bestWinRate);

            double bestReturn = previousRuns.stream()
                .mapToDouble(r -> r.metrics().totalReturnPercent())
                .max().orElse(0);
            historical.put("bestReturn", Math.round(bestReturn * 100) / 100.0);
            historical.put("isNewBestReturn", curr.totalReturnPercent() > bestReturn);

            trends.put("historical", historical);
        }

        return trends;
    }

    private Map<String, Object> formatDelta(double current, double previous, String suffix) {
        Map<String, Object> delta = new LinkedHashMap<>();

        // Handle infinity and NaN
        if (Double.isInfinite(current) || Double.isInfinite(previous) || Double.isNaN(current) || Double.isNaN(previous)) {
            delta.put("current", Double.isInfinite(current) ? "∞" : (Double.isNaN(current) ? "N/A" : current));
            delta.put("previous", Double.isInfinite(previous) ? "∞" : (Double.isNaN(previous) ? "N/A" : previous));
            delta.put("delta", "N/A");
            delta.put("improved", false);
            delta.put("display", "N/A");
            return delta;
        }

        double diff = current - previous;
        delta.put("current", Math.round(current * 100) / 100.0);
        delta.put("previous", Math.round(previous * 100) / 100.0);
        delta.put("delta", Math.round(diff * 100) / 100.0);
        delta.put("improved", diff > 0);

        String direction = diff > 0 ? "+" : "";
        delta.put("display", String.format("%s%.2f%s", direction, diff, suffix));

        return delta;
    }

    private List<String> detectConfigChanges(BacktestResult current, BacktestResult previous) {
        List<String> changes = new ArrayList<>();

        // Compare config objects
        var currConfig = current.config();
        var prevConfig = previous.config();

        if (currConfig == null || prevConfig == null) {
            changes.add("Config comparison unavailable");
            return changes;
        }

        if (!currConfig.symbol().equals(prevConfig.symbol())) {
            changes.add("Symbol: " + prevConfig.symbol() + " → " + currConfig.symbol());
        }
        if (!currConfig.resolution().equals(prevConfig.resolution())) {
            changes.add("Timeframe: " + prevConfig.resolution() + " → " + currConfig.resolution());
        }
        if (currConfig.commission() != prevConfig.commission()) {
            changes.add(String.format("Commission: %.4f → %.4f", prevConfig.commission(), currConfig.commission()));
        }

        // Compare strategy definition if available
        if (current.strategy() != null && previous.strategy() != null) {
            String currEntry = current.strategy().getEntry();
            String prevEntry = previous.strategy().getEntry();
            if (currEntry != null && !currEntry.equals(prevEntry)) {
                changes.add("Entry condition changed");
            }

            // Compare exit zones count
            int currZones = current.strategy().getExitZones() != null ? current.strategy().getExitZones().size() : 0;
            int prevZones = previous.strategy().getExitZones() != null ? previous.strategy().getExitZones().size() : 0;
            if (currZones != prevZones) {
                changes.add("Exit zones: " + prevZones + " → " + currZones);
            }

            // Compare phases
            var currPhases = current.strategy().getPhaseSettings();
            var prevPhases = previous.strategy().getPhaseSettings();
            if (currPhases != null && prevPhases != null) {
                int currRequired = currPhases.getRequiredPhaseIds() != null ? currPhases.getRequiredPhaseIds().size() : 0;
                int prevRequired = prevPhases.getRequiredPhaseIds() != null ? prevPhases.getRequiredPhaseIds().size() : 0;
                if (currRequired != prevRequired) {
                    changes.add("Required phases: " + prevRequired + " → " + currRequired);
                }
            }
        }

        if (changes.isEmpty()) {
            changes.add("Minor config adjustments");
        }

        return changes;
    }

    private Map<String, Object> computeTrend(List<Double> values) {
        Map<String, Object> trend = new LinkedHashMap<>();

        if (values.size() < 2) {
            trend.put("direction", "insufficient_data");
            return trend;
        }

        // Simple linear regression to determine trend
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = values.size();

        for (int i = 0; i < n; i++) {
            double x = i;  // 0 = current, 1 = previous, etc.
            double y = values.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

        // Negative slope means improving (since index 0 is current/newest)
        String direction;
        if (slope < -0.5) {
            direction = "improving";
        } else if (slope > 0.5) {
            direction = "declining";
        } else {
            direction = "stable";
        }

        trend.put("direction", direction);
        trend.put("slope", Math.round(slope * 100) / 100.0);
        trend.put("values", values.stream().map(v -> Math.round(v * 100) / 100.0).toList());

        return trend;
    }

    private Map<String, Object> analyzeByPhase(List<Trade> trades, double overallWinRate) {
        Map<String, List<Trade>> byPhase = new HashMap<>();

        for (Trade t : trades) {
            if (t.activePhasesAtEntry() != null) {
                for (String phase : t.activePhasesAtEntry()) {
                    byPhase.computeIfAbsent(phase, k -> new ArrayList<>()).add(t);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Trade>> entry : byPhase.entrySet()) {
            List<Trade> phaseTrades = entry.getValue();
            result.put(entry.getKey(), computeStats(phaseTrades, overallWinRate));
        }

        return result;
    }

    private Map<String, Object> analyzeByHour(List<Trade> trades, double overallWinRate) {
        Map<Integer, List<Trade>> byHour = new HashMap<>();

        for (Trade t : trades) {
            int hour = Instant.ofEpochMilli(t.entryTime())
                .atZone(ZoneOffset.UTC)
                .getHour();
            byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(t);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            List<Trade> hourTrades = byHour.get(h);
            if (hourTrades != null && !hourTrades.isEmpty()) {
                result.put(String.format("%02d", h), computeStats(hourTrades, overallWinRate));
            }
        }

        return result;
    }

    private Map<String, Object> analyzeByDayOfWeek(List<Trade> trades, double overallWinRate) {
        String[] dayNames = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        Map<Integer, List<Trade>> byDay = new HashMap<>();

        for (Trade t : trades) {
            int day = Instant.ofEpochMilli(t.entryTime())
                .atZone(ZoneOffset.UTC)
                .getDayOfWeek()
                .getValue();  // 1=Monday, 7=Sunday
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(t);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (int d = 1; d <= 7; d++) {
            List<Trade> dayTrades = byDay.get(d);
            if (dayTrades != null && !dayTrades.isEmpty()) {
                result.put(dayNames[d - 1], computeStats(dayTrades, overallWinRate));
            }
        }

        return result;
    }

    private Map<String, Object> analyzeByExitReason(List<Trade> trades) {
        Map<String, List<Trade>> byReason = new HashMap<>();

        for (Trade t : trades) {
            String reason = t.exitReason() != null ? t.exitReason() : "unknown";
            byReason.computeIfAbsent(reason, k -> new ArrayList<>()).add(t);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Trade>> entry : byReason.entrySet()) {
            List<Trade> reasonTrades = entry.getValue();
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("count", reasonTrades.size());
            stats.put("avgPnlPercent", reasonTrades.stream()
                .filter(t -> t.pnlPercent() != null)
                .mapToDouble(Trade::pnlPercent)
                .average()
                .orElse(0));
            result.put(entry.getKey(), stats);
        }

        return result;
    }

    private Map<String, Object> computeStats(List<Trade> trades, double overallWinRate) {
        int wins = (int) trades.stream().filter(t -> t.pnl() != null && t.pnl() > 0).count();
        double winRate = trades.isEmpty() ? 0 : (double) wins / trades.size() * 100;
        double avgPnl = trades.stream()
            .filter(t -> t.pnlPercent() != null)
            .mapToDouble(Trade::pnlPercent)
            .average()
            .orElse(0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", trades.size());
        stats.put("winRate", Math.round(winRate * 10) / 10.0);
        stats.put("avgPnlPercent", Math.round(avgPnl * 100) / 100.0);
        stats.put("vsOverall", Math.round((winRate - overallWinRate) * 10) / 10.0);  // Difference from overall

        return stats;
    }

    /**
     * Analyze trades by footprint metrics (imbalance, absorption, etc.)
     * Only includes analysis if footprint data is available for enough trades.
     */
    private Map<String, Object> analyzeByFootprint(List<Trade> trades, double overallWinRate) {
        Map<String, Object> analysis = new LinkedHashMap<>();

        // Filter trades with footprint data
        List<Trade> tradesWithFootprint = trades.stream()
            .filter(t -> t.entryFootprintMetrics() != null && !t.entryFootprintMetrics().isEmpty())
            .toList();

        if (tradesWithFootprint.size() < 5) {
            return analysis;  // Not enough footprint data
        }

        // Analyze by imbalance direction at POC
        analysis.put("byImbalance", analyzeByImbalance(tradesWithFootprint, overallWinRate));

        // Analyze by stacked imbalances
        analysis.put("byStackedImbalances", analyzeByStackedImbalances(tradesWithFootprint, overallWinRate));

        // Analyze by absorption
        analysis.put("byAbsorption", analyzeByAbsorption(tradesWithFootprint, overallWinRate));

        // Analyze by volume distribution
        analysis.put("byVolumeDistribution", analyzeByVolumeDistribution(tradesWithFootprint, overallWinRate));

        // Analyze by exchange divergence (if multi-exchange data available)
        Map<String, Object> exchangeAnalysis = analyzeByExchangeDivergence(tradesWithFootprint, overallWinRate);
        if (!exchangeAnalysis.isEmpty()) {
            analysis.put("byExchangeDivergence", exchangeAnalysis);
        }

        return analysis;
    }

    /**
     * Analyze trades by imbalance at POC (buy dominant vs sell dominant)
     */
    private Map<String, Object> analyzeByImbalance(List<Trade> trades, double overallWinRate) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Trade> buyImbalance = new ArrayList<>();
        List<Trade> sellImbalance = new ArrayList<>();
        List<Trade> neutral = new ArrayList<>();

        for (Trade t : trades) {
            Double imbalance = t.entryFootprintMetrics().get("imbalanceAtPoc");
            if (imbalance == null) continue;

            if (imbalance > 1.5) {
                buyImbalance.add(t);
            } else if (imbalance < 0.67) {
                sellImbalance.add(t);
            } else {
                neutral.add(t);
            }
        }

        if (buyImbalance.size() >= 3) {
            result.put("buyImbalance", computeStats(buyImbalance, overallWinRate));
        }
        if (sellImbalance.size() >= 3) {
            result.put("sellImbalance", computeStats(sellImbalance, overallWinRate));
        }
        if (neutral.size() >= 3) {
            result.put("neutral", computeStats(neutral, overallWinRate));
        }

        return result;
    }

    /**
     * Analyze trades by presence of stacked buy/sell imbalances
     */
    private Map<String, Object> analyzeByStackedImbalances(List<Trade> trades, double overallWinRate) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Trade> withStackedBuy = new ArrayList<>();
        List<Trade> withStackedSell = new ArrayList<>();
        List<Trade> noStacked = new ArrayList<>();

        for (Trade t : trades) {
            Double stackedBuy = t.entryFootprintMetrics().get("stackedBuyImbalances");
            Double stackedSell = t.entryFootprintMetrics().get("stackedSellImbalances");

            boolean hasBuy = stackedBuy != null && stackedBuy >= 3;
            boolean hasSell = stackedSell != null && stackedSell >= 3;

            if (hasBuy && !hasSell) {
                withStackedBuy.add(t);
            } else if (hasSell && !hasBuy) {
                withStackedSell.add(t);
            } else if (!hasBuy && !hasSell) {
                noStacked.add(t);
            }
        }

        if (withStackedBuy.size() >= 3) {
            result.put("stackedBuyImbalances", computeStats(withStackedBuy, overallWinRate));
        }
        if (withStackedSell.size() >= 3) {
            result.put("stackedSellImbalances", computeStats(withStackedSell, overallWinRate));
        }
        if (noStacked.size() >= 3) {
            result.put("noStackedImbalances", computeStats(noStacked, overallWinRate));
        }

        return result;
    }

    /**
     * Analyze trades by absorption presence
     */
    private Map<String, Object> analyzeByAbsorption(List<Trade> trades, double overallWinRate) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Trade> withAbsorption = new ArrayList<>();
        List<Trade> withoutAbsorption = new ArrayList<>();

        for (Trade t : trades) {
            Double absorption = t.entryFootprintMetrics().get("absorptionScore");
            if (absorption != null && absorption > 0) {
                withAbsorption.add(t);
            } else {
                withoutAbsorption.add(t);
            }
        }

        if (withAbsorption.size() >= 3) {
            result.put("withAbsorption", computeStats(withAbsorption, overallWinRate));
        }
        if (withoutAbsorption.size() >= 3) {
            result.put("withoutAbsorption", computeStats(withoutAbsorption, overallWinRate));
        }

        return result;
    }

    /**
     * Analyze trades by volume distribution (above vs below POC)
     */
    private Map<String, Object> analyzeByVolumeDistribution(List<Trade> trades, double overallWinRate) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Trade> volumeAbove = new ArrayList<>();  // More volume above POC (distribution)
        List<Trade> volumeBelow = new ArrayList<>();  // More volume below POC (accumulation)
        List<Trade> balanced = new ArrayList<>();

        for (Trade t : trades) {
            Double volAboveRatio = t.entryFootprintMetrics().get("volumeAbovePocRatio");
            if (volAboveRatio == null) continue;

            if (volAboveRatio > 0.6) {
                volumeAbove.add(t);
            } else if (volAboveRatio < 0.4) {
                volumeBelow.add(t);
            } else {
                balanced.add(t);
            }
        }

        if (volumeAbove.size() >= 3) {
            result.put("volumeAbovePoc", computeStats(volumeAbove, overallWinRate));
        }
        if (volumeBelow.size() >= 3) {
            result.put("volumeBelowPoc", computeStats(volumeBelow, overallWinRate));
        }
        if (balanced.size() >= 3) {
            result.put("balancedVolume", computeStats(balanced, overallWinRate));
        }

        return result;
    }

    /**
     * Analyze trades by exchange divergence (when different exchanges show different direction)
     */
    private Map<String, Object> analyzeByExchangeDivergence(List<Trade> trades, double overallWinRate) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Trade> withDivergence = new ArrayList<>();
        List<Trade> withoutDivergence = new ArrayList<>();

        for (Trade t : trades) {
            Double divergence = t.entryFootprintMetrics().get("exchangeDivergence");
            if (divergence == null) continue;

            if (divergence > 0) {
                withDivergence.add(t);
            } else {
                withoutDivergence.add(t);
            }
        }

        // Only include if we have multi-exchange data
        if (withDivergence.size() < 3 && withoutDivergence.size() < 3) {
            return result;  // Not enough multi-exchange data
        }

        if (withDivergence.size() >= 3) {
            result.put("exchangesDiverging", computeStats(withDivergence, overallWinRate));
        }
        if (withoutDivergence.size() >= 3) {
            result.put("exchangesAgreeing", computeStats(withoutDivergence, overallWinRate));
        }

        return result;
    }

    /**
     * Generate AI suggestions based on footprint patterns
     */
    private List<String> generateFootprintSuggestions(List<Trade> trades) {
        List<String> suggestions = new ArrayList<>();

        // Filter trades with footprint data
        List<Trade> tradesWithFootprint = trades.stream()
            .filter(t -> t.entryFootprintMetrics() != null && !t.entryFootprintMetrics().isEmpty())
            .toList();

        if (tradesWithFootprint.size() < 10) {
            return suggestions;  // Not enough data for meaningful suggestions
        }

        List<Trade> winners = tradesWithFootprint.stream()
            .filter(t -> t.pnl() != null && t.pnl() > 0)
            .toList();
        List<Trade> losers = tradesWithFootprint.stream()
            .filter(t -> t.pnl() != null && t.pnl() < 0)
            .toList();

        if (winners.size() < 5 || losers.size() < 5) {
            return suggestions;
        }

        // Compare imbalance at POC
        double winnerImbalance = winners.stream()
            .filter(t -> t.entryFootprintMetrics().containsKey("imbalanceAtPoc"))
            .mapToDouble(t -> t.entryFootprintMetrics().get("imbalanceAtPoc"))
            .average().orElse(Double.NaN);
        double loserImbalance = losers.stream()
            .filter(t -> t.entryFootprintMetrics().containsKey("imbalanceAtPoc"))
            .mapToDouble(t -> t.entryFootprintMetrics().get("imbalanceAtPoc"))
            .average().orElse(Double.NaN);

        if (!Double.isNaN(winnerImbalance) && !Double.isNaN(loserImbalance)) {
            double diff = winnerImbalance - loserImbalance;
            if (diff > 0.5) {
                suggestions.add(String.format(
                    "Winners show stronger buy imbalance at POC (%.1f vs %.1f) - consider IMBALANCE_AT_POC > %.1f filter",
                    winnerImbalance, loserImbalance, (winnerImbalance + loserImbalance) / 2));
            } else if (diff < -0.5) {
                suggestions.add(String.format(
                    "Winners show stronger sell imbalance at POC (%.1f vs %.1f) - consider IMBALANCE_AT_POC < %.1f filter",
                    winnerImbalance, loserImbalance, (winnerImbalance + loserImbalance) / 2));
            }
        }

        // Compare stacked imbalances
        double winnerStacked = winners.stream()
            .filter(t -> t.entryFootprintMetrics().containsKey("stackedBuyImbalances"))
            .mapToDouble(t -> t.entryFootprintMetrics().get("stackedBuyImbalances"))
            .average().orElse(0);
        double loserStacked = losers.stream()
            .filter(t -> t.entryFootprintMetrics().containsKey("stackedBuyImbalances"))
            .mapToDouble(t -> t.entryFootprintMetrics().get("stackedBuyImbalances"))
            .average().orElse(0);

        if (winnerStacked > loserStacked + 1) {
            suggestions.add(String.format(
                "Winners have %.1f avg stacked buy imbalances vs %.1f for losers - consider STACKED_BUY_IMBALANCES(%d) filter",
                winnerStacked, loserStacked, Math.max(3, (int) (winnerStacked + loserStacked) / 2)));
        }

        // Compare volume distribution
        double winnerVolAbove = winners.stream()
            .filter(t -> t.entryFootprintMetrics().containsKey("volumeAbovePocRatio"))
            .mapToDouble(t -> t.entryFootprintMetrics().get("volumeAbovePocRatio"))
            .average().orElse(Double.NaN);
        double loserVolAbove = losers.stream()
            .filter(t -> t.entryFootprintMetrics().containsKey("volumeAbovePocRatio"))
            .mapToDouble(t -> t.entryFootprintMetrics().get("volumeAbovePocRatio"))
            .average().orElse(Double.NaN);

        if (!Double.isNaN(winnerVolAbove) && !Double.isNaN(loserVolAbove)) {
            double diff = winnerVolAbove - loserVolAbove;
            if (diff > 0.1) {
                suggestions.add(String.format(
                    "Winners enter with more volume above POC (%.0f%% vs %.0f%%) - consider VOLUME_ABOVE_POC_RATIO > %.2f filter",
                    winnerVolAbove * 100, loserVolAbove * 100, (winnerVolAbove + loserVolAbove) / 2));
            } else if (diff < -0.1) {
                suggestions.add(String.format(
                    "Winners enter with more volume below POC (%.0f%% vs %.0f%% above) - consider VOLUME_BELOW_POC_RATIO > %.2f filter",
                    winnerVolAbove * 100, loserVolAbove * 100, 1 - (winnerVolAbove + loserVolAbove) / 2));
            }
        }

        // Check for absorption pattern correlation
        long winnersWithAbsorption = winners.stream()
            .filter(t -> t.entryFootprintMetrics().containsKey("absorptionScore"))
            .filter(t -> t.entryFootprintMetrics().get("absorptionScore") > 0)
            .count();
        long losersWithAbsorption = losers.stream()
            .filter(t -> t.entryFootprintMetrics().containsKey("absorptionScore"))
            .filter(t -> t.entryFootprintMetrics().get("absorptionScore") > 0)
            .count();

        if (winners.size() > 0 && losers.size() > 0) {
            double winnerAbsorptionRate = (double) winnersWithAbsorption / winners.size() * 100;
            double loserAbsorptionRate = (double) losersWithAbsorption / losers.size() * 100;

            if (winnerAbsorptionRate > loserAbsorptionRate + 15) {
                suggestions.add(String.format(
                    "%.0f%% of winners vs %.0f%% of losers show absorption - consider ABSORPTION filter",
                    winnerAbsorptionRate, loserAbsorptionRate));
            }
        }

        // Check for exchange divergence correlation
        long winnersWithDivergence = winners.stream()
            .filter(t -> t.entryFootprintMetrics().containsKey("exchangeDivergence"))
            .filter(t -> t.entryFootprintMetrics().get("exchangeDivergence") > 0)
            .count();
        long losersWithDivergence = losers.stream()
            .filter(t -> t.entryFootprintMetrics().containsKey("exchangeDivergence"))
            .filter(t -> t.entryFootprintMetrics().get("exchangeDivergence") > 0)
            .count();

        if (winnersWithDivergence + losersWithDivergence >= 5) {
            double winnerDivergenceRate = (double) winnersWithDivergence / winners.size() * 100;
            double loserDivergenceRate = (double) losersWithDivergence / losers.size() * 100;

            if (winnerDivergenceRate > loserDivergenceRate + 10) {
                suggestions.add(String.format(
                    "%.0f%% of winners entered during exchange divergence vs %.0f%% losers - consider EXCHANGE_DIVERGENCE filter",
                    winnerDivergenceRate, loserDivergenceRate));
            } else if (loserDivergenceRate > winnerDivergenceRate + 10) {
                suggestions.add(String.format(
                    "Exchange divergence correlates with losses (%.0f%% losers vs %.0f%% winners) - consider excluding EXCHANGE_DIVERGENCE",
                    loserDivergenceRate, winnerDivergenceRate));
            }
        }

        return suggestions;
    }

    private List<String> generateSuggestions(List<Trade> trades, PerformanceMetrics overall) {
        List<String> suggestions = new ArrayList<>();
        double overallWinRate = overall.winRate();

        // Phase suggestions
        generatePhaseSuggestions(trades, overallWinRate, suggestions);

        // Time-based suggestions (hour and day of week)
        generateTimeSuggestions(trades, overallWinRate, suggestions);

        // Exit zone/reason suggestions
        generateExitSuggestions(trades, suggestions);

        // MAE analysis suggestions
        generateMaeSuggestions(trades, overall, suggestions);

        // Holding period suggestions
        generateHoldingPeriodSuggestions(trades, suggestions);

        // Indicator correlation suggestions
        generateIndicatorSuggestions(trades, suggestions);

        // MFE capture ratio
        if (overall.averageMfe() > 0 && overall.totalTrades() > 0) {
            double avgActualPnl = trades.stream()
                .filter(t -> t.pnlPercent() != null)
                .mapToDouble(Trade::pnlPercent)
                .average()
                .orElse(0);

            double captureRatio = overall.averageMfe() > 0 ? avgActualPnl / overall.averageMfe() : 0;
            if (captureRatio < 0.5 && captureRatio > 0) {
                suggestions.add(String.format(
                    "Exits may be too early - capturing only %.0f%% of average favorable move (%.1f%% vs %.1f%% MFE)",
                    captureRatio * 100, avgActualPnl, overall.averageMfe()));
            }
        }

        // Consecutive losses
        if (overall.maxConsecutiveLosses() >= 5) {
            suggestions.add(String.format(
                "Max %d consecutive losses detected - consider position sizing adjustments",
                overall.maxConsecutiveLosses()));
        }

        return suggestions;
    }

    private void generatePhaseSuggestions(List<Trade> trades, double overallWinRate, List<String> suggestions) {
        Map<String, List<Trade>> byPhase = new HashMap<>();
        for (Trade t : trades) {
            if (t.activePhasesAtEntry() != null) {
                for (String phase : t.activePhasesAtEntry()) {
                    byPhase.computeIfAbsent(phase, k -> new ArrayList<>()).add(t);
                }
            }
        }

        for (Map.Entry<String, List<Trade>> entry : byPhase.entrySet()) {
            List<Trade> phaseTrades = entry.getValue();
            if (phaseTrades.size() >= 5) {
                int wins = (int) phaseTrades.stream().filter(t -> t.pnl() != null && t.pnl() > 0).count();
                double winRate = (double) wins / phaseTrades.size() * 100;
                double diff = winRate - overallWinRate;

                if (diff > 10) {
                    suggestions.add(String.format(
                        "Consider requiring '%s' phase (+%.0f%% win rate, %d trades)",
                        entry.getKey(), diff, phaseTrades.size()));
                } else if (diff < -10) {
                    suggestions.add(String.format(
                        "Consider excluding '%s' phase (%.0f%% worse win rate, %d trades)",
                        entry.getKey(), -diff, phaseTrades.size()));
                }
            }
        }
    }

    private void generateTimeSuggestions(List<Trade> trades, double overallWinRate, List<String> suggestions) {
        // Group by hour
        Map<Integer, List<Trade>> byHour = new HashMap<>();
        for (Trade t : trades) {
            int hour = Instant.ofEpochMilli(t.entryTime()).atZone(ZoneOffset.UTC).getHour();
            byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(t);
        }

        // Find best/worst hours
        int bestHour = -1, worstHour = -1;
        double bestDiff = 0, worstDiff = 0;
        int bestCount = 0, worstCount = 0;

        for (Map.Entry<Integer, List<Trade>> entry : byHour.entrySet()) {
            List<Trade> hourTrades = entry.getValue();
            if (hourTrades.size() >= 5) {
                int wins = (int) hourTrades.stream().filter(t -> t.pnl() != null && t.pnl() > 0).count();
                double winRate = (double) wins / hourTrades.size() * 100;
                double diff = winRate - overallWinRate;

                if (diff > bestDiff && diff > 12) {
                    bestDiff = diff;
                    bestHour = entry.getKey();
                    bestCount = hourTrades.size();
                }
                if (diff < worstDiff && diff < -12) {
                    worstDiff = diff;
                    worstHour = entry.getKey();
                    worstCount = hourTrades.size();
                }
            }
        }

        if (bestHour >= 0) {
            suggestions.add(String.format(
                "Hour %02d UTC shows +%.0f%% win rate (%d trades) - consider time filter",
                bestHour, bestDiff, bestCount));
        }
        if (worstHour >= 0) {
            suggestions.add(String.format(
                "Hour %02d UTC underperforms by %.0f%% (%d trades) - consider excluding",
                worstHour, -worstDiff, worstCount));
        }

        // Group by day of week
        String[] dayNames = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        Map<Integer, List<Trade>> byDay = new HashMap<>();
        for (Trade t : trades) {
            int day = Instant.ofEpochMilli(t.entryTime()).atZone(ZoneOffset.UTC).getDayOfWeek().getValue();
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(t);
        }

        int bestDay = -1, worstDay = -1;
        double bestDayDiff = 0, worstDayDiff = 0;
        int bestDayCount = 0, worstDayCount = 0;

        for (Map.Entry<Integer, List<Trade>> entry : byDay.entrySet()) {
            List<Trade> dayTrades = entry.getValue();
            if (dayTrades.size() >= 5) {
                int wins = (int) dayTrades.stream().filter(t -> t.pnl() != null && t.pnl() > 0).count();
                double winRate = (double) wins / dayTrades.size() * 100;
                double diff = winRate - overallWinRate;

                if (diff > bestDayDiff && diff > 10) {
                    bestDayDiff = diff;
                    bestDay = entry.getKey();
                    bestDayCount = dayTrades.size();
                }
                if (diff < worstDayDiff && diff < -10) {
                    worstDayDiff = diff;
                    worstDay = entry.getKey();
                    worstDayCount = dayTrades.size();
                }
            }
        }

        if (bestDay >= 1) {
            suggestions.add(String.format(
                "%s shows +%.0f%% win rate (%d trades) - consider day filter",
                dayNames[bestDay - 1], bestDayDiff, bestDayCount));
        }
        if (worstDay >= 1) {
            suggestions.add(String.format(
                "%s underperforms by %.0f%% (%d trades) - consider excluding",
                dayNames[worstDay - 1], -worstDayDiff, worstDayCount));
        }
    }

    private void generateExitSuggestions(List<Trade> trades, List<String> suggestions) {
        Map<String, List<Trade>> byReason = new HashMap<>();
        for (Trade t : trades) {
            String reason = t.exitReason() != null ? t.exitReason() : "unknown";
            byReason.computeIfAbsent(reason, k -> new ArrayList<>()).add(t);
        }

        // Compare stop_loss vs take_profit performance
        List<Trade> stopLossTrades = byReason.getOrDefault("stop_loss", Collections.emptyList());
        List<Trade> takeProfitTrades = byReason.getOrDefault("take_profit", Collections.emptyList());

        if (stopLossTrades.size() >= 5 && takeProfitTrades.size() >= 5) {
            double slAvg = stopLossTrades.stream()
                .filter(t -> t.pnlPercent() != null)
                .mapToDouble(Trade::pnlPercent)
                .average().orElse(0);
            double tpAvg = takeProfitTrades.stream()
                .filter(t -> t.pnlPercent() != null)
                .mapToDouble(Trade::pnlPercent)
                .average().orElse(0);

            // If stop losses are too severe relative to take profits
            if (slAvg < -3 && tpAvg > 0 && Math.abs(slAvg) > tpAvg * 1.5) {
                suggestions.add(String.format(
                    "Stop losses avg %.1f%% vs take profits +%.1f%% - consider tighter SL or wider TP",
                    slAvg, tpAvg));
            }
        }

        // Check signal exits (DSL condition based)
        List<Trade> signalTrades = byReason.getOrDefault("signal", Collections.emptyList());
        if (signalTrades.size() >= 5) {
            double signalAvg = signalTrades.stream()
                .filter(t -> t.pnlPercent() != null)
                .mapToDouble(Trade::pnlPercent)
                .average().orElse(0);

            // Compare to zone exits
            double zoneAvg = trades.stream()
                .filter(t -> t.exitReason() != null && !t.exitReason().equals("signal"))
                .filter(t -> t.pnlPercent() != null)
                .mapToDouble(Trade::pnlPercent)
                .average().orElse(0);

            if (signalAvg < zoneAvg - 1 && signalTrades.size() >= 10) {
                suggestions.add(String.format(
                    "DSL condition exits avg %.1f%% vs zone exits %.1f%% - review exit conditions",
                    signalAvg, zoneAvg));
            }
        }
    }

    private void generateMaeSuggestions(List<Trade> trades, PerformanceMetrics overall, List<String> suggestions) {
        List<Trade> winners = trades.stream()
            .filter(t -> t.pnl() != null && t.pnl() > 0)
            .filter(t -> t.mae() != null)
            .toList();
        List<Trade> losers = trades.stream()
            .filter(t -> t.pnl() != null && t.pnl() < 0)
            .filter(t -> t.mae() != null)
            .toList();

        if (winners.size() >= 5 && losers.size() >= 5) {
            double winnerMae = winners.stream().mapToDouble(Trade::mae).average().orElse(0);
            double loserMae = losers.stream().mapToDouble(Trade::mae).average().orElse(0);

            // If losers have much worse MAE early, a tighter stop could help
            if (loserMae < winnerMae - 2) {
                suggestions.add(String.format(
                    "Losers hit %.1f%% MAE vs winners %.1f%% - tighter stop-loss may cut losses earlier",
                    loserMae, winnerMae));
            }

            // Check if winners typically recover from drawdown (MAE bar < MFE bar)
            long winnersRecovering = winners.stream()
                .filter(t -> t.maeBar() != null && t.mfeBar() != null && t.maeBar() < t.mfeBar())
                .count();
            double recoveryRate = (double) winnersRecovering / winners.size() * 100;

            if (recoveryRate > 70) {
                suggestions.add(String.format(
                    "%.0f%% of winners hit drawdown before peak - holding through MAE works for this strategy",
                    recoveryRate));
            }
        }
    }

    private void generateHoldingPeriodSuggestions(List<Trade> trades, List<String> suggestions) {
        List<Trade> validTrades = trades.stream()
            .filter(t -> t.exitBar() != null)  // entryBar is primitive int, always set
            .filter(t -> t.pnl() != null)
            .toList();

        if (validTrades.size() < 20) return;

        // Calculate median holding period
        List<Integer> holdingPeriods = validTrades.stream()
            .map(t -> t.exitBar() - t.entryBar())
            .sorted()
            .toList();
        int medianHolding = holdingPeriods.get(holdingPeriods.size() / 2);

        // Split into short and long trades
        List<Trade> shortTrades = validTrades.stream()
            .filter(t -> (t.exitBar() - t.entryBar()) <= medianHolding)
            .toList();
        List<Trade> longTrades = validTrades.stream()
            .filter(t -> (t.exitBar() - t.entryBar()) > medianHolding)
            .toList();

        if (shortTrades.size() >= 5 && longTrades.size() >= 5) {
            int shortWins = (int) shortTrades.stream().filter(t -> t.pnl() > 0).count();
            int longWins = (int) longTrades.stream().filter(t -> t.pnl() > 0).count();
            double shortWinRate = (double) shortWins / shortTrades.size() * 100;
            double longWinRate = (double) longWins / longTrades.size() * 100;

            double shortAvgPnl = shortTrades.stream().mapToDouble(Trade::pnlPercent).average().orElse(0);
            double longAvgPnl = longTrades.stream().mapToDouble(Trade::pnlPercent).average().orElse(0);

            if (shortWinRate > longWinRate + 12) {
                suggestions.add(String.format(
                    "Quick trades (<=%d bars) have %.0f%% win rate vs %.0f%% for longer - consider time-based exit",
                    medianHolding, shortWinRate, longWinRate));
            } else if (longAvgPnl > shortAvgPnl + 1) {
                suggestions.add(String.format(
                    "Longer trades (>%d bars) avg +%.1f%% vs +%.1f%% - patience may improve returns",
                    medianHolding, longAvgPnl, shortAvgPnl));
            }
        }
    }

    private void generateIndicatorSuggestions(List<Trade> trades, List<String> suggestions) {
        List<Trade> winners = trades.stream()
            .filter(t -> t.pnl() != null && t.pnl() > 0)
            .filter(t -> t.entryIndicators() != null && !t.entryIndicators().isEmpty())
            .toList();
        List<Trade> losers = trades.stream()
            .filter(t -> t.pnl() != null && t.pnl() < 0)
            .filter(t -> t.entryIndicators() != null && !t.entryIndicators().isEmpty())
            .toList();

        if (winners.size() < 5 || losers.size() < 5) return;

        // Analyze RSI(14) at entry
        analyzeIndicatorDifference(winners, losers, "RSI(14)", suggestions,
            "Tighten RSI entry threshold - winners avg %.0f vs losers %.0f",
            true);  // lower is better for oversold entries

        // Analyze ADX(14) - trend strength
        analyzeIndicatorDifference(winners, losers, "ADX(14)", suggestions,
            "Add ADX filter - winners avg %.0f vs losers %.0f (stronger trends)",
            false);  // higher is better

        // Analyze ATR(14) - volatility
        analyzeIndicatorDifference(winners, losers, "ATR(14)", suggestions,
            "Consider volatility filter - winning entries have %.0f%% different ATR",
            true);  // interpret as percentage difference

        // Analyze price position relative to SMA(200)
        analyzePriceVsSma(winners, losers, "SMA(200)", suggestions);
    }

    private void analyzeIndicatorDifference(List<Trade> winners, List<Trade> losers,
                                            String indicator, List<String> suggestions,
                                            String format, boolean lowerIsBetter) {
        double winnerAvg = winners.stream()
            .filter(t -> t.entryIndicators().containsKey(indicator))
            .mapToDouble(t -> t.entryIndicators().get(indicator))
            .average().orElse(Double.NaN);

        double loserAvg = losers.stream()
            .filter(t -> t.entryIndicators().containsKey(indicator))
            .mapToDouble(t -> t.entryIndicators().get(indicator))
            .average().orElse(Double.NaN);

        if (Double.isNaN(winnerAvg) || Double.isNaN(loserAvg)) return;

        double diff = Math.abs(winnerAvg - loserAvg);
        double threshold = indicator.contains("RSI") ? 5 : (indicator.contains("ADX") ? 3 : winnerAvg * 0.15);

        if (diff > threshold) {
            if (indicator.contains("ATR")) {
                // ATR: show percentage difference
                double pctDiff = (winnerAvg - loserAvg) / loserAvg * 100;
                if (Math.abs(pctDiff) > 15) {
                    String direction = pctDiff < 0 ? "lower" : "higher";
                    suggestions.add(String.format(
                        "Winning entries have %.0f%% %s ATR - consider volatility filter",
                        Math.abs(pctDiff), direction));
                }
            } else {
                suggestions.add(String.format(format, winnerAvg, loserAvg));
            }
        }
    }

    private void analyzePriceVsSma(List<Trade> winners, List<Trade> losers, String smaKey, List<String> suggestions) {
        long winnersBelowSma = winners.stream()
            .filter(t -> t.entryIndicators().containsKey(smaKey) && t.entryIndicators().containsKey("price"))
            .filter(t -> t.entryIndicators().get("price") < t.entryIndicators().get(smaKey))
            .count();
        long losersBelowSma = losers.stream()
            .filter(t -> t.entryIndicators().containsKey(smaKey) && t.entryIndicators().containsKey("price"))
            .filter(t -> t.entryIndicators().get("price") < t.entryIndicators().get(smaKey))
            .count();

        if (winners.isEmpty() || losers.isEmpty()) return;

        double winnerPctBelow = (double) winnersBelowSma / winners.size() * 100;
        double loserPctBelow = (double) losersBelowSma / losers.size() * 100;

        // If winners are much more often above SMA than losers
        if (winnerPctBelow < loserPctBelow - 15) {
            suggestions.add(String.format(
                "Add 'price > %s' filter - %.0f%% of winners vs %.0f%% of losers entered above",
                smaKey, 100 - winnerPctBelow, 100 - loserPctBelow));
        } else if (winnerPctBelow > loserPctBelow + 15) {
            suggestions.add(String.format(
                "Add 'price < %s' filter - %.0f%% of winners vs %.0f%% of losers entered below",
                smaKey, winnerPctBelow, loserPctBelow));
        }
    }

    /**
     * Clear trade files directory
     */
    private void clearTradesDir() {
        File[] files = tradesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }

    /**
     * Load the latest result (backward compatible)
     */
    public BacktestResult loadLatest() {
        File latestFile = new File(strategyDir, "latest.json");
        if (!latestFile.exists()) {
            return null;
        }

        try {
            return mapper.readValue(latestFile, BacktestResult.class);
        } catch (IOException e) {
            System.err.println("Failed to load latest result: " + e.getMessage());
            return null;
        }
    }

    /**
     * List all historical results
     */
    public List<BacktestResult> loadHistory() {
        List<BacktestResult> results = new ArrayList<>();
        File[] files = historyDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (files != null) {
            for (File file : files) {
                try {
                    results.add(mapper.readValue(file, BacktestResult.class));
                } catch (IOException e) {
                    System.err.println("Failed to load " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        // Sort by end time, newest first
        results.sort((a, b) -> Long.compare(b.endTime(), a.endTime()));

        return results;
    }

    /**
     * Clear all results
     */
    public void clearAll() {
        File latestFile = new File(strategyDir, "latest.json");
        if (latestFile.exists()) {
            latestFile.delete();
        }

        File summaryFile = new File(strategyDir, "summary.json");
        if (summaryFile.exists()) {
            summaryFile.delete();
        }

        clearTradesDir();

        File[] historyFiles = historyDir.listFiles();
        if (historyFiles != null) {
            for (File f : historyFiles) {
                f.delete();
            }
        }
    }
}
