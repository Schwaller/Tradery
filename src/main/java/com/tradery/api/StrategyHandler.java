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
