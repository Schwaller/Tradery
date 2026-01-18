package com.tradery.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.engine.BacktestEngine;
import com.tradery.engine.PhaseAnalyzer;
import com.tradery.io.PhaseStore;
import com.tradery.io.ResultStore;
import com.tradery.io.StrategyStore;
import com.tradery.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles strategy-related API endpoints:
 *   GET    /strategies                  - List all strategies
 *   POST   /strategies                  - Create new strategy
 *   GET    /strategy/{id}               - Get strategy JSON
 *   POST   /strategy/{id}               - Update strategy (partial or full)
 *   DELETE /strategy/{id}               - Delete strategy
 *   POST   /strategy/{id}/validate      - Validate updates without saving
 *   POST   /strategy/{id}/backtest      - Run backtest and return results
 *   GET    /strategy/{id}/results       - Get latest backtest results
 *   GET    /strategy/{id}/analyze-phases - Analyze trades vs all phases
 */
public class StrategyHandler extends ApiHandlerBase {

    private final StrategyStore strategyStore;
    private final PhaseStore phaseStore;
    private final SqliteDataStore dataStore;

    public StrategyHandler(StrategyStore strategyStore, PhaseStore phaseStore, SqliteDataStore dataStore) {
        this.strategyStore = strategyStore;
        this.phaseStore = phaseStore;
        this.dataStore = dataStore;
    }

    /**
     * Handle /strategies - GET to list, POST to create.
     */
    public void handleStrategies(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            handleListStrategies(exchange);
        } else if ("POST".equalsIgnoreCase(method)) {
            handleCreateStrategy(exchange);
        } else {
            sendError(exchange, 405, "Method not allowed. Use GET or POST.");
        }
    }

    private void handleListStrategies(HttpExchange exchange) throws IOException {
        try {
            List<Strategy> strategies = strategyStore.loadAll();

            ObjectNode response = mapper.createObjectNode();
            ArrayNode strategiesArray = response.putArray("strategies");

            for (Strategy s : strategies) {
                ObjectNode node = strategiesArray.addObject();
                node.put("id", s.getId());
                node.put("name", s.getName());
                node.put("enabled", s.isEnabled());
                node.put("symbol", s.getSymbol());
                node.put("timeframe", s.getTimeframe());
            }

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to list strategies: " + e.getMessage());
        }
    }

    /**
     * Handle POST /strategies - create new strategy.
     */
    private void handleCreateStrategy(HttpExchange exchange) throws IOException {
        try {
            // Read request body
            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Parse as Strategy
            Strategy strategy = mapper.readValue(body, Strategy.class);

            // Validate required fields
            if (strategy.getId() == null || strategy.getId().isBlank()) {
                sendError(exchange, 400, "Strategy must have an 'id' field");
                return;
            }
            if (strategy.getName() == null || strategy.getName().isBlank()) {
                sendError(exchange, 400, "Strategy must have a 'name' field");
                return;
            }

            // Check if already exists
            if (strategyStore.load(strategy.getId()) != null) {
                sendError(exchange, 409, "Strategy '" + strategy.getId() + "' already exists. Use POST /strategy/{id} to update.");
                return;
            }

            // Set defaults if not provided
            if (strategy.getEntrySettings() == null) {
                strategy.setEntrySettings(EntrySettings.defaults());
            }
            if (strategy.getExitSettings() == null) {
                strategy.setExitSettings(ExitSettings.defaults());
            }
            if (strategy.getBacktestSettings() == null) {
                strategy.setBacktestSettings(BacktestSettings.defaults());
            }
            if (strategy.getPhaseSettings() == null) {
                strategy.setPhaseSettings(PhaseSettings.defaults());
            }
            if (strategy.getOrderflowSettings() == null) {
                strategy.setOrderflowSettings(OrderflowSettings.defaults());
            }
            if (strategy.getHoopPatternSettings() == null) {
                strategy.setHoopPatternSettings(HoopPatternSettings.defaults());
            }
            strategy.setEnabled(true);

            // Save
            strategyStore.save(strategy);

            ObjectNode response = mapper.createObjectNode();
            response.put("success", true);
            response.put("strategyId", strategy.getId());
            response.put("message", "Strategy created. Use POST /strategy/" + strategy.getId() + "/backtest to test it.");

            sendJson(exchange, 201, response);
        } catch (Exception e) {
            sendError(exchange, 400, "Failed to create strategy: " + e.getMessage());
        }
    }

    /**
     * Handle /strategy/{id} endpoints - route to appropriate handler.
     */
    public void handleStrategy(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        // Path format: /strategy/{id} or /strategy/{id}/backtest or /strategy/{id}/results

        String[] parts = path.split("/");
        if (parts.length < 3) {
            sendError(exchange, 400, "Invalid path. Use /strategy/{id}");
            return;
        }

        String strategyId = parts[2];
        String action = parts.length > 3 ? parts[3] : null;
        String method = exchange.getRequestMethod();

        if (action == null) {
            // /strategy/{id}
            if ("GET".equalsIgnoreCase(method)) {
                handleGetStrategy(exchange, strategyId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handleUpdateStrategy(exchange, strategyId);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleDeleteStrategy(exchange, strategyId);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } else if ("backtest".equals(action)) {
            // /strategy/{id}/backtest
            if ("POST".equalsIgnoreCase(method)) {
                handleBacktest(exchange, strategyId);
            } else {
                sendError(exchange, 405, "Use POST for /backtest");
            }
        } else if ("results".equals(action)) {
            // /strategy/{id}/results
            if ("GET".equalsIgnoreCase(method)) {
                handleGetResults(exchange, strategyId);
            } else {
                sendError(exchange, 405, "Use GET for /results");
            }
        } else if ("validate".equals(action)) {
            // /strategy/{id}/validate
            if ("POST".equalsIgnoreCase(method)) {
                handleValidateStrategy(exchange, strategyId);
            } else {
                sendError(exchange, 405, "Use POST for /validate");
            }
        } else if ("analyze-phases".equals(action)) {
            // /strategy/{id}/analyze-phases
            if ("GET".equalsIgnoreCase(method)) {
                handleAnalyzePhases(exchange, strategyId);
            } else {
                sendError(exchange, 405, "Use GET for /analyze-phases");
            }
        } else {
            sendError(exchange, 404, "Unknown action: " + action);
        }
    }

    private void handleGetStrategy(HttpExchange exchange, String strategyId) throws IOException {
        try {
            Strategy strategy = strategyStore.load(strategyId);
            if (strategy == null) {
                sendError(exchange, 404, "Strategy not found: " + strategyId);
                return;
            }

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(strategy);
            sendJsonString(exchange, 200, json);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to get strategy: " + e.getMessage());
        }
    }

    private void handleUpdateStrategy(HttpExchange exchange, String strategyId) throws IOException {
        try {
            Strategy existing = strategyStore.load(strategyId);
            if (existing == null) {
                sendError(exchange, 404, "Strategy not found: " + strategyId);
                return;
            }

            // Read request body
            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Parse as JSON and merge with existing strategy
            JsonNode updates = mapper.readTree(body);

            // Apply updates to existing strategy
            if (updates.has("name")) {
                existing.setName(updates.get("name").asText());
            }
            if (updates.has("enabled")) {
                existing.setEnabled(updates.get("enabled").asBoolean());
            }
            if (updates.has("entrySettings")) {
                EntrySettings entry = mapper.treeToValue(updates.get("entrySettings"), EntrySettings.class);
                existing.setEntrySettings(entry);
            }
            if (updates.has("exitSettings")) {
                ExitSettings exit = mapper.treeToValue(updates.get("exitSettings"), ExitSettings.class);
                existing.setExitSettings(exit);
            }
            if (updates.has("backtestSettings")) {
                BacktestSettings backtest = mapper.treeToValue(updates.get("backtestSettings"), BacktestSettings.class);
                existing.setBacktestSettings(backtest);
            }
            if (updates.has("phaseSettings")) {
                PhaseSettings phases = mapper.treeToValue(updates.get("phaseSettings"), PhaseSettings.class);
                existing.setPhaseSettings(phases);
            }

            // Save
            strategyStore.save(existing);

            ObjectNode response = mapper.createObjectNode();
            response.put("success", true);
            response.put("strategyId", strategyId);
            response.put("message", "Strategy updated");

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to update strategy: " + e.getMessage());
        }
    }

    /**
     * Handle DELETE /strategy/{id} - delete strategy and all results.
     */
    private void handleDeleteStrategy(HttpExchange exchange, String strategyId) throws IOException {
        try {
            Strategy existing = strategyStore.load(strategyId);
            if (existing == null) {
                sendError(exchange, 404, "Strategy not found: " + strategyId);
                return;
            }

            // Delete strategy and results
            boolean deleted = strategyStore.delete(strategyId);

            if (deleted) {
                ObjectNode response = mapper.createObjectNode();
                response.put("success", true);
                response.put("strategyId", strategyId);
                response.put("message", "Strategy and all results deleted");
                sendJson(exchange, 200, response);
            } else {
                sendError(exchange, 500, "Failed to delete strategy");
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to delete strategy: " + e.getMessage());
        }
    }

    /**
     * Validate strategy updates without saving.
     * Returns validation errors or the merged config that would be saved.
     */
    private void handleValidateStrategy(HttpExchange exchange, String strategyId) throws IOException {
        try {
            Strategy existing = strategyStore.load(strategyId);
            if (existing == null) {
                sendError(exchange, 404, "Strategy not found: " + strategyId);
                return;
            }

            // Read request body
            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            List<String> errors = new ArrayList<>();
            ObjectNode response = mapper.createObjectNode();

            try {
                // Parse as JSON
                JsonNode updates = mapper.readTree(body);

                // Try to parse each section and collect errors
                if (updates.has("entrySettings")) {
                    try {
                        mapper.treeToValue(updates.get("entrySettings"), EntrySettings.class);
                    } catch (Exception e) {
                        errors.add("entrySettings: " + extractValidationError(e));
                    }
                }
                if (updates.has("exitSettings")) {
                    try {
                        mapper.treeToValue(updates.get("exitSettings"), ExitSettings.class);
                    } catch (Exception e) {
                        errors.add("exitSettings: " + extractValidationError(e));
                    }
                }
                if (updates.has("backtestSettings")) {
                    try {
                        mapper.treeToValue(updates.get("backtestSettings"), BacktestSettings.class);
                    } catch (Exception e) {
                        errors.add("backtestSettings: " + extractValidationError(e));
                    }
                }
                if (updates.has("phaseSettings")) {
                    try {
                        mapper.treeToValue(updates.get("phaseSettings"), PhaseSettings.class);
                    } catch (Exception e) {
                        errors.add("phaseSettings: " + extractValidationError(e));
                    }
                }
                if (updates.has("orderflowSettings")) {
                    try {
                        mapper.treeToValue(updates.get("orderflowSettings"), OrderflowSettings.class);
                    } catch (Exception e) {
                        errors.add("orderflowSettings: " + extractValidationError(e));
                    }
                }
                if (updates.has("hoopPatternSettings")) {
                    try {
                        mapper.treeToValue(updates.get("hoopPatternSettings"), HoopPatternSettings.class);
                    } catch (Exception e) {
                        errors.add("hoopPatternSettings: " + extractValidationError(e));
                    }
                }

            } catch (Exception e) {
                errors.add("JSON parse error: " + e.getMessage());
            }

            if (!errors.isEmpty()) {
                response.put("valid", false);
                ArrayNode errorsArray = response.putArray("errors");
                for (String error : errors) {
                    errorsArray.add(error);
                }
                sendJson(exchange, 400, response);
            } else {
                response.put("valid", true);
                response.put("strategyId", strategyId);
                response.put("message", "Validation passed. Use POST /strategy/" + strategyId + " to apply changes.");
                sendJson(exchange, 200, response);
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Validation failed: " + e.getMessage());
        }
    }

    /**
     * Extract a clean validation error message from an exception.
     */
    private String extractValidationError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "Unknown error";

        // Jackson deserialization errors often have useful info after "problem:"
        if (msg.contains("Cannot deserialize value of type")) {
            // Extract the key info: type, value, and expected values
            int fromIdx = msg.indexOf("from String \"");
            int notOneIdx = msg.indexOf("not one of the values accepted");
            if (fromIdx >= 0 && notOneIdx >= 0) {
                String value = msg.substring(fromIdx + 13, msg.indexOf("\"", fromIdx + 13));
                int bracketStart = msg.indexOf("[", notOneIdx);
                int bracketEnd = msg.indexOf("]", bracketStart);
                if (bracketStart >= 0 && bracketEnd >= 0) {
                    String expected = msg.substring(bracketStart, bracketEnd + 1);
                    return "Invalid value \"" + value + "\". Expected one of: " + expected;
                }
            }
        }

        // Truncate long messages
        if (msg.length() > 200) {
            return msg.substring(0, 200) + "...";
        }
        return msg;
    }

    private void handleBacktest(HttpExchange exchange, String strategyId) throws IOException {
        try {
            Strategy strategy = strategyStore.load(strategyId);
            if (strategy == null) {
                sendError(exchange, 404, "Strategy not found: " + strategyId);
                return;
            }

            // Calculate date range from duration and anchor date
            Long anchorDate = strategy.getBacktestSettings().getAnchorDate();
            long endDate = (anchorDate != null) ? anchorDate : System.currentTimeMillis();
            long startDate = endDate - parseDurationMillis(strategy.getDuration());

            // Build config from strategy settings
            BacktestConfig config = strategy.getBacktestSettings().toBacktestConfig(startDate, endDate);

            // Load candles
            List<Candle> candles = dataStore.getCandles(
                config.symbol(), config.resolution(), config.startDate(), config.endDate()
            );

            if (candles.isEmpty()) {
                sendError(exchange, 400, "No candle data available for " + config.symbol() + " " + config.resolution());
                return;
            }

            // Load required phases
            List<Phase> requiredPhases = new ArrayList<>();
            for (String phaseId : strategy.getRequiredPhaseIds()) {
                Phase phase = phaseStore.load(phaseId);
                if (phase != null) {
                    requiredPhases.add(phase);
                }
            }
            for (String phaseId : strategy.getExcludedPhaseIds()) {
                Phase phase = phaseStore.load(phaseId);
                if (phase != null) {
                    requiredPhases.add(phase);
                }
            }

            // Run backtest (blocking)
            BacktestEngine engine = new BacktestEngine(dataStore);
            BacktestResult result = engine.run(strategy, config, candles, requiredPhases, null);

            // Save results
            ResultStore resultStore = new ResultStore(strategyId);
            resultStore.save(result);

            // Return summary
            ObjectNode response = mapper.createObjectNode();
            response.put("success", true);
            response.put("strategyId", strategyId);

            ObjectNode metrics = response.putObject("metrics");
            PerformanceMetrics m = result.metrics();
            metrics.put("totalTrades", m.totalTrades());
            metrics.put("winningTrades", m.winningTrades());
            metrics.put("losingTrades", m.losingTrades());
            metrics.put("winRate", Math.round(m.winRate() * 100) / 100.0);
            metrics.put("profitFactor", Math.round(m.profitFactor() * 100) / 100.0);
            metrics.put("totalReturnPercent", Math.round(m.totalReturnPercent() * 100) / 100.0);
            metrics.put("maxDrawdownPercent", Math.round(m.maxDrawdownPercent() * 100) / 100.0);
            metrics.put("sharpeRatio", Math.round(m.sharpeRatio() * 100) / 100.0);

            response.put("tradesCount", result.trades().size());
            response.put("barsProcessed", result.barsProcessed());
            response.put("durationMs", result.duration());

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Backtest failed: " + e.getMessage());
        }
    }

    private void handleGetResults(HttpExchange exchange, String strategyId) throws IOException {
        try {
            ResultStore resultStore = new ResultStore(strategyId);
            BacktestResult result = resultStore.loadLatest();

            if (result == null) {
                sendError(exchange, 404, "No results found for strategy: " + strategyId);
                return;
            }

            // Return the full result as JSON
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            sendJsonString(exchange, 200, json);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to get results: " + e.getMessage());
        }
    }

    /**
     * Analyze strategy trades against all available phases.
     * Returns recommendations for which phases to REQUIRE or EXCLUDE.
     */
    private void handleAnalyzePhases(HttpExchange exchange, String strategyId) throws IOException {
        try {
            Strategy strategy = strategyStore.load(strategyId);
            if (strategy == null) {
                sendError(exchange, 404, "Strategy not found: " + strategyId);
                return;
            }

            // Load latest backtest results from history (which includes trades)
            ResultStore resultStore = new ResultStore(strategyId);
            List<BacktestResult> history = resultStore.loadHistory();
            if (history.isEmpty()) {
                sendError(exchange, 400, "No backtest results. Run POST /strategy/" + strategyId + "/backtest first.");
                return;
            }

            BacktestResult result = history.get(0); // Most recent
            List<Trade> trades = result.trades();
            if (trades == null || trades.isEmpty()) {
                sendError(exchange, 400, "No trades in backtest result. Cannot analyze phases.");
                return;
            }

            // Load candles for phase evaluation
            String timeframe = strategy.getTimeframe();
            long endDate = System.currentTimeMillis();
            long startDate = endDate - parseDurationMillis(strategy.getDuration());
            List<Candle> candles = dataStore.getCandles(
                strategy.getSymbol(), timeframe, startDate, endDate
            );

            if (candles.isEmpty()) {
                sendError(exchange, 500, "Failed to load candle data for phase analysis.");
                return;
            }

            // Run phase analysis
            PhaseAnalyzer analyzer = new PhaseAnalyzer(dataStore, phaseStore);
            List<PhaseAnalysisResult> results = analyzer.analyzePhases(trades, candles, timeframe, null);

            // Build response
            ObjectNode response = mapper.createObjectNode();
            response.put("strategyId", strategyId);
            response.put("tradesAnalyzed", trades.stream()
                .filter(t -> t.exitTime() != null && !"rejected".equals(t.exitReason()))
                .count());

            ArrayNode phasesArray = response.putArray("phases");
            for (PhaseAnalysisResult r : results) {
                ObjectNode phaseNode = phasesArray.addObject();
                phaseNode.put("phaseId", r.phaseId());
                phaseNode.put("phaseName", r.phaseName());
                phaseNode.put("category", r.phaseCategory());

                ObjectNode inPhase = phaseNode.putObject("inPhase");
                inPhase.put("trades", r.tradesInPhase());
                inPhase.put("wins", r.winsInPhase());
                inPhase.put("winRate", round(r.winRateInPhase()));
                inPhase.put("totalReturn", round(r.totalReturnInPhase()));
                inPhase.put("profitFactor", round(r.profitFactorInPhase()));

                ObjectNode outOfPhase = phaseNode.putObject("outOfPhase");
                outOfPhase.put("trades", r.tradesOutOfPhase());
                outOfPhase.put("wins", r.winsOutOfPhase());
                outOfPhase.put("winRate", round(r.winRateOutOfPhase()));
                outOfPhase.put("totalReturn", round(r.totalReturnOutOfPhase()));
                outOfPhase.put("profitFactor", round(r.profitFactorOutOfPhase()));

                phaseNode.put("winRateDiff", round(r.winRateDifference()));
                phaseNode.put("recommendation", r.recommendation().name());
                phaseNode.put("confidence", round(r.confidenceScore()));
            }

            // Add summary of recommendations
            ObjectNode summary = response.putObject("summary");
            ArrayNode requirePhases = summary.putArray("recommendRequire");
            ArrayNode excludePhases = summary.putArray("recommendExclude");

            for (PhaseAnalysisResult r : results) {
                if (r.recommendation() == PhaseAnalysisResult.Recommendation.REQUIRE && r.confidenceScore() > 0.3) {
                    ObjectNode rec = requirePhases.addObject();
                    rec.put("phaseId", r.phaseId());
                    rec.put("phaseName", r.phaseName());
                    rec.put("winRateDiff", round(r.winRateDifference()));
                    rec.put("confidence", round(r.confidenceScore()));
                } else if (r.recommendation() == PhaseAnalysisResult.Recommendation.EXCLUDE && r.confidenceScore() > 0.3) {
                    ObjectNode rec = excludePhases.addObject();
                    rec.put("phaseId", r.phaseId());
                    rec.put("phaseName", r.phaseName());
                    rec.put("winRateDiff", round(r.winRateDifference()));
                    rec.put("confidence", round(r.confidenceScore()));
                }
            }

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Phase analysis failed: " + e.getMessage());
        }
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
