package com.tradery.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.tradery.data.CandleStore;
import com.tradery.engine.BacktestEngine;
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
 *   GET  /strategies                    - List all strategies
 *   GET  /strategy/{id}                 - Get strategy JSON
 *   POST /strategy/{id}                 - Update strategy (partial or full)
 *   POST /strategy/{id}/validate        - Validate updates without saving
 *   POST /strategy/{id}/backtest        - Run backtest and return results
 *   GET  /strategy/{id}/results         - Get latest backtest results
 */
public class StrategyHandler extends ApiHandlerBase {

    private final StrategyStore strategyStore;
    private final PhaseStore phaseStore;
    private final CandleStore candleStore;

    public StrategyHandler(StrategyStore strategyStore, PhaseStore phaseStore, CandleStore candleStore) {
        this.strategyStore = strategyStore;
        this.phaseStore = phaseStore;
        this.candleStore = candleStore;
    }

    /**
     * Handle GET /strategies - list all strategies.
     */
    public void handleStrategies(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

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

            // Calculate date range from duration
            long endDate = System.currentTimeMillis();
            long startDate = endDate - parseDurationMillis(strategy.getDuration());

            // Build config from strategy settings
            BacktestConfig config = strategy.getBacktestSettings().toBacktestConfig(startDate, endDate);

            // Load candles
            List<Candle> candles = candleStore.getCandles(
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
            BacktestEngine engine = new BacktestEngine(candleStore);
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
}
