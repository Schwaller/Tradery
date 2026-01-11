package com.tradery.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tradery.data.AggTradesStore;
import com.tradery.data.CandleStore;
import com.tradery.data.FundingRateStore;
import com.tradery.data.OpenInterestStore;
import com.tradery.dsl.Parser;
import com.tradery.model.AggTrade;
import com.tradery.engine.BacktestEngine;
import com.tradery.engine.ConditionEvaluator;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.io.PhaseStore;
import com.tradery.io.ResultStore;
import com.tradery.io.StrategyStore;
import com.tradery.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight HTTP API server for Claude Code integration.
 * Exposes indicator calculations, candle data, DSL evaluation, and strategy management.
 *
 * Endpoints:
 *   GET  /status
 *   GET  /candles?symbol=BTCUSDT&timeframe=1h&bars=100
 *   GET  /indicator?name=RSI(14)&symbol=BTCUSDT&timeframe=1h&bars=100
 *   GET  /indicators?names=RSI(14),SMA(200)&symbol=BTCUSDT&timeframe=1h&bars=100
 *   GET  /eval?condition=RSI(14)<30&symbol=BTCUSDT&timeframe=1h&bars=500
 *   GET  /strategies                    - List all strategies
 *   GET  /strategy/{id}                 - Get strategy JSON
 *   POST /strategy/{id}                 - Update strategy (partial or full)
 *   POST /strategy/{id}/backtest        - Run backtest and return results (blocking)
 *   GET  /strategy/{id}/results         - Get latest backtest results
 */
public class ApiServer {

    private static final int DEFAULT_PORT = 7842;
    private static final int MAX_PORT_ATTEMPTS = 10;
    private static final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private HttpServer server;
    private final CandleStore candleStore;
    private final AggTradesStore aggTradesStore;
    private final FundingRateStore fundingRateStore;
    private final OpenInterestStore openInterestStore;
    private final StrategyStore strategyStore;
    private final PhaseStore phaseStore;
    private final String sessionToken;
    private int actualPort;

    public ApiServer(CandleStore candleStore, AggTradesStore aggTradesStore,
                     FundingRateStore fundingRateStore, OpenInterestStore openInterestStore,
                     StrategyStore strategyStore, PhaseStore phaseStore) {
        this.candleStore = candleStore;
        this.aggTradesStore = aggTradesStore;
        this.fundingRateStore = fundingRateStore;
        this.openInterestStore = openInterestStore;
        this.strategyStore = strategyStore;
        this.phaseStore = phaseStore;
        this.sessionToken = generateSessionToken();
    }

    private String generateSessionToken() {
        // Generate a random 32-char hex token
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void start() throws IOException {
        // Find a free port starting from DEFAULT_PORT
        IOException lastException = null;
        for (int i = 0; i < MAX_PORT_ATTEMPTS; i++) {
            int tryPort = DEFAULT_PORT + i;
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", tryPort), 0);
                actualPort = tryPort;
                break;
            } catch (IOException e) {
                lastException = e;
                // Port in use, try next
            }
        }

        if (server == null) {
            throw new IOException("Could not find free port after " + MAX_PORT_ATTEMPTS + " attempts", lastException);
        }

        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/status", this::handleStatus);
        server.createContext("/candles", this::handleCandles);
        server.createContext("/indicator", this::handleIndicator);
        server.createContext("/indicators", this::handleIndicators);
        server.createContext("/eval", this::handleEval);
        server.createContext("/strategies", this::handleStrategies);
        server.createContext("/strategy/", this::handleStrategy);
        server.createContext("/phases", this::handlePhases);
        server.createContext("/phase/", this::handlePhase);

        server.start();
        System.out.println("API server started on http://localhost:" + actualPort + " (token: " + sessionToken.substring(0, 8) + "...)");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("API server stopped");
        }
    }

    public int getPort() {
        return actualPort;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Validate session token from request.
     * Token can be provided as:
     *   - Header: X-Session-Token
     *   - Query param: token=xxx
     */
    private boolean validateToken(HttpExchange exchange) {
        // Check header first
        String headerToken = exchange.getRequestHeaders().getFirst("X-Session-Token");
        if (sessionToken.equals(headerToken)) {
            return true;
        }

        // Check query param
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            Map<String, String> params = parseQuery(query);
            if (sessionToken.equals(params.get("token"))) {
                return true;
            }
        }

        return false;
    }

    private void requireAuth(HttpExchange exchange) throws IOException {
        if (!validateToken(exchange)) {
            sendError(exchange, 401, "Invalid or missing session token. Check ~/.tradery/api.json for current token.");
        }
    }

    // ========== Handlers ==========

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;
        if (!validateToken(exchange)) {
            sendError(exchange, 401, "Invalid or missing session token");
            return;
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("status", "running");
        response.put("version", "1.0");
        response.put("port", actualPort);

        sendJson(exchange, 200, response);
    }

    private void handleCandles(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;
        if (!validateToken(exchange)) {
            sendError(exchange, 401, "Invalid or missing session token");
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        String symbol = params.getOrDefault("symbol", "BTCUSDT");
        String timeframe = params.getOrDefault("timeframe", "1h");
        int bars = Integer.parseInt(params.getOrDefault("bars", "100"));

        try {
            List<Candle> candles = loadCandles(symbol, timeframe, bars);

            ObjectNode response = mapper.createObjectNode();
            response.put("symbol", symbol);
            response.put("timeframe", timeframe);
            response.put("count", candles.size());

            ArrayNode candlesArray = response.putArray("candles");
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                ObjectNode node = candlesArray.addObject();
                node.put("bar", i);
                node.put("time", c.timestamp());
                node.put("open", c.open());
                node.put("high", c.high());
                node.put("low", c.low());
                node.put("close", c.close());
                node.put("volume", c.volume());
            }

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to load candles: " + e.getMessage());
        }
    }

    private void handleIndicator(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;
        if (!validateToken(exchange)) {
            sendError(exchange, 401, "Invalid or missing session token");
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        String name = params.get("name");
        if (name == null || name.isEmpty()) {
            sendError(exchange, 400, "Missing required parameter: name");
            return;
        }

        String symbol = params.getOrDefault("symbol", "BTCUSDT");
        String timeframe = params.getOrDefault("timeframe", "1h");
        int bars = Integer.parseInt(params.getOrDefault("bars", "100"));

        try {
            // Check what data this indicator needs
            String[] indicatorNames = { name };
            boolean needsOrderflow = needsOrderflowData(indicatorNames);
            boolean needsFunding = needsFundingData(indicatorNames);
            boolean needsOI = needsOIData(indicatorNames);

            // Load candles (handles sub-minute transparently)
            DataResult data = loadData(symbol, timeframe, bars, needsOrderflow);
            List<Candle> candles = data.candles();

            IndicatorEngine engine = new IndicatorEngine();
            engine.setCandles(candles, timeframe);

            // Set aggTrades if available (for orderflow indicators)
            if (data.aggTrades() != null && !data.aggTrades().isEmpty()) {
                engine.setAggTrades(data.aggTrades());
            }

            // Load funding data if needed
            if (needsFunding && fundingRateStore != null && !candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                List<FundingRate> fundingRates = fundingRateStore.getFundingRates(symbol, startTime, endTime);
                if (fundingRates != null && !fundingRates.isEmpty()) {
                    engine.setFundingRates(fundingRates);
                }
            }

            // Load OI data if needed
            if (needsOI && openInterestStore != null && !candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                // OI data limited to 30 days
                long now = System.currentTimeMillis();
                long maxOiHistory = 30L * 24 * 60 * 60 * 1000;
                long oiStartTime = Math.max(startTime, now - maxOiHistory);
                if (oiStartTime < endTime) {
                    List<OpenInterest> oi = openInterestStore.getOpenInterest(symbol, oiStartTime, endTime, msg -> {});
                    if (oi != null && !oi.isEmpty()) {
                        engine.setOpenInterest(oi);
                    }
                }
            }

            double[] values = calculateIndicator(engine, name, candles.size());

            ObjectNode response = mapper.createObjectNode();
            response.put("indicator", name);
            response.put("symbol", symbol);
            response.put("timeframe", timeframe);
            response.put("count", candles.size());

            ArrayNode valuesArray = response.putArray("values");
            for (int i = 0; i < candles.size(); i++) {
                if (i < values.length && !Double.isNaN(values[i])) {
                    ObjectNode node = valuesArray.addObject();
                    node.put("bar", i);
                    node.put("time", candles.get(i).timestamp());
                    node.put("value", Math.round(values[i] * 10000.0) / 10000.0);
                }
            }

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to calculate indicator: " + e.getMessage());
        }
    }

    private void handleIndicators(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;
        if (!validateToken(exchange)) {
            sendError(exchange, 401, "Invalid or missing session token");
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        String names = params.get("names");
        if (names == null || names.isEmpty()) {
            sendError(exchange, 400, "Missing required parameter: names");
            return;
        }

        String symbol = params.getOrDefault("symbol", "BTCUSDT");
        String timeframe = params.getOrDefault("timeframe", "1h");
        int bars = Integer.parseInt(params.getOrDefault("bars", "100"));

        try {
            String[] indicatorNames = names.split(",");

            // Check what data the indicators need
            boolean needsOrderflow = needsOrderflowData(indicatorNames);
            boolean needsFunding = needsFundingData(indicatorNames);
            boolean needsOI = needsOIData(indicatorNames);

            // Load data (handles sub-minute transparently, loads aggTrades if needed)
            DataResult data = loadData(symbol, timeframe, bars, needsOrderflow);
            List<Candle> candles = data.candles();

            IndicatorEngine engine = new IndicatorEngine();
            engine.setCandles(candles, timeframe);

            // Set aggTrades if available (for orderflow indicators)
            if (data.aggTrades() != null && !data.aggTrades().isEmpty()) {
                engine.setAggTrades(data.aggTrades());
            }

            // Load funding data if needed
            if (needsFunding && fundingRateStore != null && !candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                List<FundingRate> fundingRates = fundingRateStore.getFundingRates(symbol, startTime, endTime);
                if (fundingRates != null && !fundingRates.isEmpty()) {
                    engine.setFundingRates(fundingRates);
                }
            }

            // Load OI data if needed
            if (needsOI && openInterestStore != null && !candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                // OI data limited to 30 days
                long now = System.currentTimeMillis();
                long maxOiHistory = 30L * 24 * 60 * 60 * 1000;
                long oiStartTime = Math.max(startTime, now - maxOiHistory);
                if (oiStartTime < endTime) {
                    List<OpenInterest> oi = openInterestStore.getOpenInterest(symbol, oiStartTime, endTime, msg -> {});
                    if (oi != null && !oi.isEmpty()) {
                        engine.setOpenInterest(oi);
                    }
                }
            }

            ObjectNode response = mapper.createObjectNode();
            response.put("symbol", symbol);
            response.put("timeframe", timeframe);
            response.put("count", candles.size());

            ObjectNode indicators = response.putObject("indicators");

            for (String name : indicatorNames) {
                name = name.trim();
                try {
                    double[] values = calculateIndicator(engine, name, candles.size());
                    ArrayNode valuesArray = indicators.putArray(name);
                    for (int i = 0; i < candles.size(); i++) {
                        if (i < values.length && !Double.isNaN(values[i])) {
                            valuesArray.add(Math.round(values[i] * 10000.0) / 10000.0);
                        } else {
                            valuesArray.addNull();
                        }
                    }
                } catch (Exception e) {
                    indicators.put(name, "error: " + e.getMessage());
                }
            }

            // Also include timestamps
            ArrayNode times = response.putArray("times");
            for (Candle c : candles) {
                times.add(c.timestamp());
            }

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to calculate indicators: " + e.getMessage());
        }
    }

    private void handleEval(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;
        if (!validateToken(exchange)) {
            sendError(exchange, 401, "Invalid or missing session token");
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        String condition = params.get("condition");
        if (condition == null || condition.isEmpty()) {
            sendError(exchange, 400, "Missing required parameter: condition");
            return;
        }

        String symbol = params.getOrDefault("symbol", "BTCUSDT");
        String timeframe = params.getOrDefault("timeframe", "1h");
        int bars = Integer.parseInt(params.getOrDefault("bars", "500"));
        int limit = Integer.parseInt(params.getOrDefault("limit", "50"));

        try {
            // Parse condition
            Parser parser = new Parser();
            Parser.ParseResult parseResult = parser.parse(condition);
            if (!parseResult.success()) {
                sendError(exchange, 400, "Parse error: " + parseResult.error());
                return;
            }

            List<Candle> candles = loadCandles(symbol, timeframe, bars);
            IndicatorEngine engine = new IndicatorEngine();
            engine.setCandles(candles, timeframe);

            ConditionEvaluator evaluator = new ConditionEvaluator(engine);

            ObjectNode response = mapper.createObjectNode();
            response.put("condition", condition);
            response.put("symbol", symbol);
            response.put("timeframe", timeframe);
            response.put("barsScanned", candles.size());

            ArrayNode matches = response.putArray("matches");
            int matchCount = 0;

            // Start from bar 50 to allow indicator warmup
            int startBar = Math.min(50, candles.size() / 2);

            for (int i = startBar; i < candles.size() && matchCount < limit; i++) {
                try {
                    if (evaluator.evaluate(parseResult.ast(), i)) {
                        Candle c = candles.get(i);
                        ObjectNode match = matches.addObject();
                        match.put("bar", i);
                        match.put("time", c.timestamp());
                        match.put("price", c.close());
                        matchCount++;
                    }
                } catch (Exception ignored) {
                    // Skip bars where evaluation fails (e.g., not enough data)
                }
            }

            response.put("matchCount", matchCount);

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to evaluate condition: " + e.getMessage());
        }
    }

    // ========== Strategy Management Handlers ==========

    private void handleStrategies(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;
        if (!validateToken(exchange)) {
            sendError(exchange, 401, "Invalid or missing session token");
            return;
        }

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

    private void handleStrategy(HttpExchange exchange) throws IOException {
        if (!validateToken(exchange)) {
            sendError(exchange, 401, "Invalid or missing session token");
            return;
        }

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
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
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
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to get results: " + e.getMessage());
        }
    }

    // ========== Phase Handlers ==========

    private void handlePhases(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;
        if (!validateToken(exchange)) {
            sendError(exchange, 401, "Invalid or missing session token");
            return;
        }

        try {
            List<Phase> phases = phaseStore.loadAll();

            ObjectNode response = mapper.createObjectNode();
            ArrayNode phasesArray = response.putArray("phases");

            for (Phase p : phases) {
                ObjectNode node = phasesArray.addObject();
                node.put("id", p.getId());
                node.put("name", p.getName());
                node.put("category", p.getCategory());
                node.put("description", p.getDescription());
                node.put("condition", p.getCondition());
                node.put("timeframe", p.getTimeframe());
                node.put("builtIn", p.isBuiltIn());
            }

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to list phases: " + e.getMessage());
        }
    }

    private void handlePhase(HttpExchange exchange) throws IOException {
        if (!validateToken(exchange)) {
            sendError(exchange, 401, "Invalid or missing session token");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        // Path format: /phase/{id} or /phase/{id}/bounds

        String[] parts = path.split("/");
        if (parts.length < 3) {
            sendError(exchange, 400, "Invalid path. Use /phase/{id} or /phase/{id}/bounds");
            return;
        }

        String phaseId = parts[2];
        String action = parts.length > 3 ? parts[3] : null;

        if (!checkMethod(exchange, "GET")) return;

        if (action == null) {
            // GET /phase/{id} - return phase details
            handleGetPhase(exchange, phaseId);
        } else if ("bounds".equals(action)) {
            // GET /phase/{id}/bounds?symbol=BTCUSDT&bars=500 - find where phase is active
            handlePhaseBounds(exchange, phaseId);
        } else {
            sendError(exchange, 404, "Unknown action: " + action);
        }
    }

    private void handleGetPhase(HttpExchange exchange, String phaseId) throws IOException {
        try {
            Phase phase = phaseStore.load(phaseId);
            if (phase == null) {
                sendError(exchange, 404, "Phase not found: " + phaseId);
                return;
            }

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(phase);
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to get phase: " + e.getMessage());
        }
    }

    private void handlePhaseBounds(HttpExchange exchange, String phaseId) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        try {
            Phase phase = phaseStore.load(phaseId);
            if (phase == null) {
                sendError(exchange, 404, "Phase not found: " + phaseId);
                return;
            }

            // Use phase's timeframe, or override from params
            String symbol = params.getOrDefault("symbol", phase.getSymbol() != null ? phase.getSymbol() : "BTCUSDT");
            String timeframe = params.getOrDefault("timeframe", phase.getTimeframe() != null ? phase.getTimeframe() : "1h");
            int bars = Integer.parseInt(params.getOrDefault("bars", "500"));

            // Load candles for the phase's timeframe
            List<Candle> candles = loadCandles(symbol, timeframe, bars);

            if (candles.isEmpty()) {
                sendError(exchange, 400, "No candle data available for " + symbol + " " + timeframe);
                return;
            }

            // Parse phase condition
            Parser parser = new Parser();
            Parser.ParseResult parseResult = parser.parse(phase.getCondition());
            if (!parseResult.success()) {
                sendError(exchange, 400, "Phase condition parse error: " + parseResult.error());
                return;
            }

            // Set up indicator engine and evaluator
            IndicatorEngine engine = new IndicatorEngine();
            engine.setCandles(candles, timeframe);
            ConditionEvaluator evaluator = new ConditionEvaluator(engine);

            // Find bounds where phase is active
            ObjectNode response = mapper.createObjectNode();
            response.put("phaseId", phaseId);
            response.put("phaseName", phase.getName());
            response.put("condition", phase.getCondition());
            response.put("symbol", symbol);
            response.put("timeframe", timeframe);
            response.put("barsScanned", candles.size());

            ArrayNode boundsArray = response.putArray("bounds");

            boolean inPhase = false;
            long startTime = 0;
            int startBar = 0;
            int activeBars = 0;

            // Start from bar 50 for indicator warmup
            int evalStart = Math.min(50, candles.size() / 4);

            for (int i = evalStart; i < candles.size(); i++) {
                boolean active;
                try {
                    active = evaluator.evaluate(parseResult.ast(), i);
                } catch (Exception e) {
                    active = false;
                }

                if (active && !inPhase) {
                    // Phase started
                    inPhase = true;
                    startTime = candles.get(i).timestamp();
                    startBar = i;
                } else if (!active && inPhase) {
                    // Phase ended
                    inPhase = false;
                    long endTime = candles.get(i - 1).timestamp();

                    ObjectNode bound = boundsArray.addObject();
                    bound.put("startBar", startBar);
                    bound.put("endBar", i - 1);
                    bound.put("startTime", startTime);
                    bound.put("endTime", endTime);
                    bound.put("bars", i - startBar);

                    activeBars += (i - startBar);
                }

                if (active) {
                    activeBars++;
                }
            }

            // If still in phase at the end
            if (inPhase) {
                long endTime = candles.get(candles.size() - 1).timestamp();
                ObjectNode bound = boundsArray.addObject();
                bound.put("startBar", startBar);
                bound.put("endBar", candles.size() - 1);
                bound.put("startTime", startTime);
                bound.put("endTime", endTime);
                bound.put("bars", candles.size() - startBar);
                bound.put("ongoing", true);
            }

            response.put("activeBars", activeBars);
            response.put("activePercent", Math.round((double) activeBars / (candles.size() - evalStart) * 10000) / 100.0);
            response.put("boundCount", boundsArray.size());

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Failed to evaluate phase bounds: " + e.getMessage());
        }
    }

    /**
     * Parse duration string to milliseconds.
     */
    private long parseDurationMillis(String duration) {
        if (duration == null) return 365L * 24 * 60 * 60 * 1000; // Default 1 year

        long hour = 60L * 60 * 1000;
        long day = 24 * hour;
        return switch (duration) {
            case "1 hour" -> hour;
            case "3 hours" -> 3 * hour;
            case "6 hours" -> 6 * hour;
            case "12 hours" -> 12 * hour;
            case "1 day" -> day;
            case "3 days" -> 3 * day;
            case "1 week" -> 7 * day;
            case "2 weeks" -> 14 * day;
            case "4 weeks" -> 28 * day;
            case "1 month" -> 30 * day;
            case "2 months" -> 60 * day;
            case "3 months" -> 90 * day;
            case "6 months" -> 180 * day;
            case "1 year" -> 365 * day;
            case "2 years" -> 730 * day;
            case "3 years" -> 1095 * day;
            case "5 years" -> 1825 * day;
            case "10 years" -> 3650 * day;
            default -> 365 * day;
        };
    }

    // ========== Helpers ==========

    /**
     * Holds loaded data - candles and optionally aggTrades for orderflow.
     */
    private record DataResult(List<Candle> candles, List<AggTrade> aggTrades) {}

    /**
     * Load candles for any timeframe. Handles sub-minute transparently by
     * loading aggTrades and generating candles from them.
     */
    private List<Candle> loadCandles(String symbol, String timeframe, int bars) throws IOException {
        return loadData(symbol, timeframe, bars, false).candles();
    }

    /**
     * Load data for any timeframe with optional aggTrades for orderflow indicators.
     * Sub-minute timeframes automatically include aggTrades (used for candle generation).
     * Standard timeframes can optionally load aggTrades for orderflow.
     */
    private DataResult loadData(String symbol, String timeframe, int bars, boolean needsOrderflow) throws IOException {
        int subMinuteSeconds = parseSubMinuteSeconds(timeframe);
        boolean isSubMinute = subMinuteSeconds > 0;

        Instant end = Instant.now();
        long endMs = end.toEpochMilli();
        long startMs;

        if (isSubMinute) {
            // Sub-minute: calculate time range based on seconds
            startMs = endMs - (long) bars * subMinuteSeconds * 1000L;
        } else {
            // Standard: calculate time range based on minutes
            startMs = end.minus((long) bars * getTimeframeMinutes(timeframe), ChronoUnit.MINUTES).toEpochMilli();
        }

        List<Candle> candles;
        List<AggTrade> aggTrades = null;

        if (isSubMinute) {
            // Sub-minute: load aggTrades and generate candles
            if (aggTradesStore == null) {
                throw new IOException("AggTrades store not available for sub-minute timeframes");
            }
            aggTrades = aggTradesStore.getAggTrades(symbol, startMs, endMs);
            if (aggTrades == null || aggTrades.isEmpty()) {
                throw new IOException("No aggTrades data available for " + symbol);
            }
            var generator = new com.tradery.data.SubMinuteCandleGenerator();
            candles = generator.generate(aggTrades, subMinuteSeconds, startMs, endMs);
        } else {
            // Standard timeframe: load from Binance
            candles = candleStore.getCandles(symbol, timeframe, startMs, endMs);

            // Load aggTrades for orderflow if requested
            if (needsOrderflow && aggTradesStore != null) {
                try {
                    aggTrades = aggTradesStore.getAggTrades(symbol, startMs, endMs);
                } catch (Exception e) {
                    // Orderflow data not available - continue without it
                }
            }
        }

        // Limit to requested bars (from end)
        if (candles.size() > bars) {
            candles = candles.subList(candles.size() - bars, candles.size());
        }

        return new DataResult(candles, aggTrades);
    }

    /**
     * Parse sub-minute timeframe string (e.g., "15s" -> 15, "30s" -> 30).
     * Returns 0 if not a sub-minute timeframe.
     */
    private int parseSubMinuteSeconds(String timeframe) {
        if (timeframe != null && timeframe.endsWith("s")) {
            try {
                return Integer.parseInt(timeframe.substring(0, timeframe.length() - 1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private int getTimeframeMinutes(String timeframe) {
        return switch (timeframe) {
            case "1m" -> 1;
            case "3m" -> 3;
            case "5m" -> 5;
            case "15m" -> 15;
            case "30m" -> 30;
            case "1h" -> 60;
            case "2h" -> 120;
            case "4h" -> 240;
            case "6h" -> 360;
            case "8h" -> 480;
            case "12h" -> 720;
            case "1d" -> 1440;
            case "3d" -> 4320;
            case "1w" -> 10080;
            default -> 60;
        };
    }

    /**
     * Check if any of the requested indicators require orderflow data (aggTrades).
     */
    private boolean needsOrderflowData(String[] indicatorNames) {
        for (String name : indicatorNames) {
            String lower = name.trim().toLowerCase();
            if (lower.equals("delta") || lower.equals("cvd") ||
                lower.equals("cumdelta") || lower.equals("cumulativedelta") ||
                lower.equals("buyvolume") || lower.equals("sellvolume") ||
                lower.equals("tradecount") ||
                lower.startsWith("whaledelta") || lower.startsWith("retaildelta") ||
                lower.startsWith("largetradecount")) {
                return true;
            }
        }
        return false;
    }

    private boolean needsFundingData(String[] indicatorNames) {
        for (String name : indicatorNames) {
            String lower = name.trim().toLowerCase();
            if (lower.equals("funding") || lower.equals("fundingrate") ||
                lower.equals("funding8h") || lower.equals("fundingrate8h")) {
                return true;
            }
        }
        return false;
    }

    private boolean needsOIData(String[] indicatorNames) {
        for (String name : indicatorNames) {
            String lower = name.trim().toLowerCase();
            if (lower.equals("oi") || lower.equals("openinterest") ||
                lower.equals("oichange") || lower.equals("openinterestchange")) {
                return true;
            }
        }
        return false;
    }

    private double[] calculateIndicator(IndicatorEngine engine, String name, int size) {
        // Parse indicator name and parameters
        // Supports: SMA(20), EMA(12), RSI(14), ATR(14), ADX(14), PLUS_DI(14), MINUS_DI(14)
        // MACD(12,26,9), MACD(12,26,9).line/signal/histogram
        // BBANDS(20,2), BBANDS(20,2).upper/middle/lower

        double[] result = new double[size];
        for (int i = 0; i < size; i++) result[i] = Double.NaN;

        // SMA
        Pattern smaPattern = Pattern.compile("SMA\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher smaMatcher = smaPattern.matcher(name);
        if (smaMatcher.matches()) {
            int period = Integer.parseInt(smaMatcher.group(1));
            double[] sma = engine.getSMA(period);
            System.arraycopy(sma, 0, result, 0, Math.min(sma.length, size));
            return result;
        }

        // EMA
        Pattern emaPattern = Pattern.compile("EMA\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher emaMatcher = emaPattern.matcher(name);
        if (emaMatcher.matches()) {
            int period = Integer.parseInt(emaMatcher.group(1));
            double[] ema = engine.getEMA(period);
            System.arraycopy(ema, 0, result, 0, Math.min(ema.length, size));
            return result;
        }

        // RSI
        Pattern rsiPattern = Pattern.compile("RSI\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher rsiMatcher = rsiPattern.matcher(name);
        if (rsiMatcher.matches()) {
            int period = Integer.parseInt(rsiMatcher.group(1));
            double[] rsi = engine.getRSI(period);
            System.arraycopy(rsi, 0, result, 0, Math.min(rsi.length, size));
            return result;
        }

        // ATR
        Pattern atrPattern = Pattern.compile("ATR\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher atrMatcher = atrPattern.matcher(name);
        if (atrMatcher.matches()) {
            int period = Integer.parseInt(atrMatcher.group(1));
            double[] atr = engine.getATR(period);
            System.arraycopy(atr, 0, result, 0, Math.min(atr.length, size));
            return result;
        }

        // ADX
        Pattern adxPattern = Pattern.compile("ADX\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher adxMatcher = adxPattern.matcher(name);
        if (adxMatcher.matches()) {
            int period = Integer.parseInt(adxMatcher.group(1));
            var adxResult = engine.getADX(period);
            double[] adx = adxResult.adx();
            System.arraycopy(adx, 0, result, 0, Math.min(adx.length, size));
            return result;
        }

        // PLUS_DI
        Pattern plusDiPattern = Pattern.compile("PLUS_DI\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher plusDiMatcher = plusDiPattern.matcher(name);
        if (plusDiMatcher.matches()) {
            int period = Integer.parseInt(plusDiMatcher.group(1));
            var adxResult = engine.getADX(period);
            double[] plusDi = adxResult.plusDI();
            System.arraycopy(plusDi, 0, result, 0, Math.min(plusDi.length, size));
            return result;
        }

        // MINUS_DI
        Pattern minusDiPattern = Pattern.compile("MINUS_DI\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher minusDiMatcher = minusDiPattern.matcher(name);
        if (minusDiMatcher.matches()) {
            int period = Integer.parseInt(minusDiMatcher.group(1));
            var adxResult = engine.getADX(period);
            double[] minusDi = adxResult.minusDI();
            System.arraycopy(minusDi, 0, result, 0, Math.min(minusDi.length, size));
            return result;
        }

        // MACD with component
        Pattern macdPattern = Pattern.compile("MACD\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\)(?:\\.(line|signal|histogram))?", Pattern.CASE_INSENSITIVE);
        Matcher macdMatcher = macdPattern.matcher(name);
        if (macdMatcher.matches()) {
            int fast = Integer.parseInt(macdMatcher.group(1));
            int slow = Integer.parseInt(macdMatcher.group(2));
            int signal = Integer.parseInt(macdMatcher.group(3));
            String component = macdMatcher.group(4);

            var macd = engine.getMACD(fast, slow, signal);
            double[] values = switch (component != null ? component.toLowerCase() : "line") {
                case "signal" -> macd.signal();
                case "histogram" -> macd.histogram();
                default -> macd.line();
            };
            System.arraycopy(values, 0, result, 0, Math.min(values.length, size));
            return result;
        }

        // BBANDS with component
        Pattern bbandsPattern = Pattern.compile("BBANDS\\((\\d+)\\s*,\\s*([\\d.]+)\\)(?:\\.(upper|middle|lower))?", Pattern.CASE_INSENSITIVE);
        Matcher bbandsMatcher = bbandsPattern.matcher(name);
        if (bbandsMatcher.matches()) {
            int period = Integer.parseInt(bbandsMatcher.group(1));
            double stdDev = Double.parseDouble(bbandsMatcher.group(2));
            String component = bbandsMatcher.group(3);

            var bbands = engine.getBollingerBands(period, stdDev);
            double[] values = switch (component != null ? component.toLowerCase() : "middle") {
                case "upper" -> bbands.upper();
                case "lower" -> bbands.lower();
                default -> bbands.middle();
            };
            System.arraycopy(values, 0, result, 0, Math.min(values.length, size));
            return result;
        }

        // Price references
        if (name.equalsIgnoreCase("close") || name.equalsIgnoreCase("price")) {
            for (int i = 0; i < size; i++) {
                Candle c = engine.getCandleAt(i);
                if (c != null) result[i] = c.close();
            }
            return result;
        }

        if (name.equalsIgnoreCase("volume")) {
            for (int i = 0; i < size; i++) {
                Candle c = engine.getCandleAt(i);
                if (c != null) result[i] = c.volume();
            }
            return result;
        }

        // ========== Orderflow Indicators (require aggTrades) ==========

        if (name.equalsIgnoreCase("delta")) {
            double[] delta = engine.getDelta();
            System.arraycopy(delta, 0, result, 0, Math.min(delta.length, size));
            return result;
        }

        if (name.equalsIgnoreCase("cvd") || name.equalsIgnoreCase("cumdelta") || name.equalsIgnoreCase("cumulativedelta")) {
            double[] cvd = engine.getCumulativeDelta();
            System.arraycopy(cvd, 0, result, 0, Math.min(cvd.length, size));
            return result;
        }

        if (name.equalsIgnoreCase("buyvolume")) {
            double[] buy = engine.getBuyVolume();
            System.arraycopy(buy, 0, result, 0, Math.min(buy.length, size));
            return result;
        }

        if (name.equalsIgnoreCase("sellvolume")) {
            double[] sell = engine.getSellVolume();
            System.arraycopy(sell, 0, result, 0, Math.min(sell.length, size));
            return result;
        }

        if (name.equalsIgnoreCase("tradecount")) {
            double[] count = engine.getTradeCount();
            System.arraycopy(count, 0, result, 0, Math.min(count.length, size));
            return result;
        }

        // Whale delta with threshold: WhaleDelta(50000)
        Pattern whaleDeltaPattern = Pattern.compile("WHALEDELTA\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher whaleDeltaMatcher = whaleDeltaPattern.matcher(name);
        if (whaleDeltaMatcher.matches()) {
            double threshold = Double.parseDouble(whaleDeltaMatcher.group(1));
            double[] whale = engine.getWhaleDelta(threshold);
            System.arraycopy(whale, 0, result, 0, Math.min(whale.length, size));
            return result;
        }

        // Retail delta with threshold: RetailDelta(50000)
        Pattern retailDeltaPattern = Pattern.compile("RETAILDELTA\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher retailDeltaMatcher = retailDeltaPattern.matcher(name);
        if (retailDeltaMatcher.matches()) {
            double threshold = Double.parseDouble(retailDeltaMatcher.group(1));
            double[] retail = engine.getRetailDelta(threshold);
            System.arraycopy(retail, 0, result, 0, Math.min(retail.length, size));
            return result;
        }

        // Large trade count with threshold: LargeTradeCount(50000)
        Pattern largeTradePattern = Pattern.compile("LARGETRADECOUNT\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher largeTradeMatcher = largeTradePattern.matcher(name);
        if (largeTradeMatcher.matches()) {
            double threshold = Double.parseDouble(largeTradeMatcher.group(1));
            double[] count = engine.getLargeTradeCount(threshold);
            System.arraycopy(count, 0, result, 0, Math.min(count.length, size));
            return result;
        }

        // ========== Funding Rate Indicators ==========

        if (name.equalsIgnoreCase("funding") || name.equalsIgnoreCase("fundingrate")) {
            double[] funding = engine.getFunding();
            System.arraycopy(funding, 0, result, 0, Math.min(funding.length, size));
            return result;
        }

        if (name.equalsIgnoreCase("funding8h") || name.equalsIgnoreCase("fundingrate8h")) {
            double[] funding8h = engine.getFunding8H();
            System.arraycopy(funding8h, 0, result, 0, Math.min(funding8h.length, size));
            return result;
        }

        // ========== Open Interest Indicators ==========

        if (name.equalsIgnoreCase("oi") || name.equalsIgnoreCase("openinterest")) {
            double[] oi = engine.getOI();
            System.arraycopy(oi, 0, result, 0, Math.min(oi.length, size));
            return result;
        }

        if (name.equalsIgnoreCase("oichange") || name.equalsIgnoreCase("openinterestchange")) {
            double[] oiChange = engine.getOIChange();
            System.arraycopy(oiChange, 0, result, 0, Math.min(oiChange.length, size));
            return result;
        }

        throw new IllegalArgumentException("Unknown indicator: " + name);
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;

        for (String param : query.split("&")) {
            String[] parts = param.split("=", 2);
            if (parts.length == 2) {
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private boolean checkMethod(HttpExchange exchange, String expected) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase(expected)) {
            sendError(exchange, 405, "Method not allowed");
            return false;
        }
        return true;
    }

    private void sendJson(HttpExchange exchange, int status, ObjectNode json) throws IOException {
        byte[] response = mapper.writeValueAsBytes(json);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        ObjectNode error = mapper.createObjectNode();
        error.put("error", message);
        sendJson(exchange, status, error);
    }
}
