package com.tradery.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.TraderyApp;
import com.tradery.model.BacktestResult;
import com.tradery.model.Trade;
import com.tradery.model.PerformanceMetrics;

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

        // Also write latest.json for backward compatibility
        File latestFile = new File(strategyDir, "latest.json");
        mapper.writeValue(latestFile, result);

        System.out.println("Saved " + result.trades().size() + " trade files to: " + tradesDir.getAbsolutePath());
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

        // AI suggestions
        analysis.put("suggestions", generateSuggestions(validTrades, overall));

        return analysis;
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

    private List<String> generateSuggestions(List<Trade> trades, PerformanceMetrics overall) {
        List<String> suggestions = new ArrayList<>();
        double overallWinRate = overall.winRate();

        // Analyze phases
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
            if (phaseTrades.size() >= 5) {  // Need enough data
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

        // Analyze MFE/MAE
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

        // Analyze consecutive losses
        if (overall.maxConsecutiveLosses() >= 5) {
            suggestions.add(String.format(
                "Max %d consecutive losses detected - consider position sizing adjustments",
                overall.maxConsecutiveLosses()));
        }

        return suggestions;
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
