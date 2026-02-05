package com.tradery.forge.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tradery.core.dsl.Parser;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.AggTrade;
import com.tradery.core.model.Candle;
import com.tradery.core.model.FundingRate;
import com.tradery.core.model.OpenInterest;
import com.tradery.engine.ConditionEvaluator;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.AggTradesStore;
import com.tradery.data.page.DataType;
import com.tradery.forge.data.FundingRateStore;
import com.tradery.forge.data.OpenInterestStore;
import com.tradery.forge.data.log.DownloadEvent;
import com.tradery.forge.data.log.DownloadLogStore;
import com.tradery.forge.data.log.DownloadStatistics;
import com.tradery.forge.data.page.DataPageManager;
import com.tradery.forge.data.sqlite.SqliteDataStore;
import com.tradery.forge.io.PhaseStore;
import com.tradery.forge.io.StrategyStore;
import com.tradery.forge.ui.LauncherFrame;
import com.tradery.forge.ui.ProjectWindow;
import com.tradery.forge.ui.charts.ChartConfig;

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
 *   GET  /data-status                   - Data coverage and gaps info
 *   GET  /pages                         - Active data pages and listeners (debugging)
 *   GET  /ui                            - Open windows and chart/indicator config (debugging)
 *   POST /ui/open?window={type}         - Open a window (phases, hoops, settings, data, dsl-help, launcher, project)
 *   GET  /thread-dump                   - Thread dump with EDT analysis (debugging)
 */
public class ApiServer {

    private static final int DEFAULT_PORT = 7842;
    private static final int MAX_PORT_ATTEMPTS = 10;
    private static final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private HttpServer server;
    private final SqliteDataStore dataStore;
    private final AggTradesStore aggTradesStore;
    private final FundingRateStore fundingRateStore;
    private final OpenInterestStore openInterestStore;
    private int actualPort;

    // Extracted handlers
    private final StrategyHandler strategyHandler;
    private final PhaseHandler phaseHandler;

    public ApiServer(SqliteDataStore dataStore, AggTradesStore aggTradesStore,
                     FundingRateStore fundingRateStore, OpenInterestStore openInterestStore,
                     StrategyStore strategyStore, PhaseStore phaseStore) {
        this.dataStore = dataStore;
        this.aggTradesStore = aggTradesStore;
        this.fundingRateStore = fundingRateStore;
        this.openInterestStore = openInterestStore;

        // Initialize extracted handlers
        this.strategyHandler = new StrategyHandler(strategyStore, phaseStore, dataStore);
        this.phaseHandler = new PhaseHandler(phaseStore, dataStore);
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
            }
        }

        if (server == null) {
            throw new IOException("Could not find free port in range " + DEFAULT_PORT + "-" + (DEFAULT_PORT + MAX_PORT_ATTEMPTS - 1), lastException);
        }
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/status", this::handleStatus);
        server.createContext("/candles", this::handleCandles);
        server.createContext("/indicator", this::handleIndicator);
        server.createContext("/indicators", this::handleIndicators);
        server.createContext("/eval", this::handleEval);
        server.createContext("/strategies", strategyHandler::handleStrategies);
        server.createContext("/strategy/", strategyHandler::handleStrategy);
        server.createContext("/phases", phaseHandler::handlePhases);
        server.createContext("/phase/", phaseHandler::handlePhase);
        server.createContext("/data-status", this::handleDataStatus);
        server.createContext("/pages", this::handlePages);
        server.createContext("/ui/chart-config", this::handleChartConfig);
        server.createContext("/ui/open", this::handleUIOpen);
        server.createContext("/ui", this::handleUI);
        server.createContext("/thread-dump", this::handleThreadDump);
        server.createContext("/download-log", this::handleDownloadLog);
        server.createContext("/download-stats", this::handleDownloadStats);

        server.start();
        System.out.println("API server started on http://localhost:" + actualPort);
    }

    public int getPort() {
        return actualPort;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("API server stopped");
        }
    }

    // ========== Handlers ==========

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

        ObjectNode response = mapper.createObjectNode();
        response.put("status", "running");
        response.put("version", "1.0");

        sendJson(exchange, 200, response);
    }

    private void handleCandles(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

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

    /**
     * Handle data status endpoint - shows loading states, gaps, and coverage.
     *
     * GET /data-status?symbol=BTCUSDT&timeframe=1h&duration=1month
     */
    private void handleDataStatus(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        String symbol = params.getOrDefault("symbol", "BTCUSDT");
        String timeframe = params.getOrDefault("timeframe", "1h");
        String duration = params.getOrDefault("duration", "1 month");

        long durationMs = parseDurationMillis(duration);
        long endTime = System.currentTimeMillis();
        long startTime = endTime - durationMs;

        ObjectNode response = mapper.createObjectNode();
        response.put("symbol", symbol);
        response.put("timeframe", timeframe);
        response.put("startTime", startTime);
        response.put("endTime", endTime);

        try {
            // Page managers info
            ObjectNode loadingState = response.putObject("loadingState");
            var candlePageMgr = ApplicationContext.getInstance().getCandlePageManager();
            var aggTradesPageMgr = ApplicationContext.getInstance().getAggTradesPageManager();
            loadingState.put("candlePageCount", candlePageMgr != null ? candlePageMgr.getActivePageCount() : 0);
            loadingState.put("aggTradesPageCount", aggTradesPageMgr != null ? aggTradesPageMgr.getActivePageCount() : 0);

            // Data coverage and gaps
            ObjectNode coverage = response.putObject("coverage");

            // Candles
            try {
                List<long[]> candleGaps = dataStore.findGaps(symbol, "candles", timeframe, startTime, endTime);
                ObjectNode candlesInfo = coverage.putObject("candles");
                candlesInfo.put("hasGaps", !candleGaps.isEmpty());
                candlesInfo.put("gapCount", candleGaps.size());
                if (!candleGaps.isEmpty()) {
                    ArrayNode gaps = candlesInfo.putArray("gaps");
                    long totalGapMs = 0;
                    for (long[] gap : candleGaps) {
                        ObjectNode gapNode = gaps.addObject();
                        gapNode.put("start", gap[0]);
                        gapNode.put("end", gap[1]);
                        gapNode.put("hours", (gap[1] - gap[0]) / 3600000.0);
                        totalGapMs += gap[1] - gap[0];
                    }
                    candlesInfo.put("totalGapHours", totalGapMs / 3600000.0);
                }

                // Also get actual count
                List<Candle> candles = dataStore.getCandles(symbol, timeframe, startTime, endTime);
                candlesInfo.put("recordCount", candles.size());
                if (!candles.isEmpty()) {
                    candlesInfo.put("firstRecord", candles.get(0).timestamp());
                    candlesInfo.put("lastRecord", candles.get(candles.size() - 1).timestamp());
                }
            } catch (Exception e) {
                coverage.putObject("candles").put("error", e.getMessage());
            }

            // Funding rates
            if (fundingRateStore != null) {
                try {
                    List<FundingRate> funding = fundingRateStore.getFundingRatesCacheOnly(symbol, startTime, endTime);
                    ObjectNode fundingInfo = coverage.putObject("funding");
                    fundingInfo.put("recordCount", funding.size());
                    if (!funding.isEmpty()) {
                        fundingInfo.put("firstRecord", funding.get(0).fundingTime());
                        fundingInfo.put("lastRecord", funding.get(funding.size() - 1).fundingTime());
                    }
                } catch (Exception e) {
                    coverage.putObject("funding").put("error", e.getMessage());
                }
            }

            // Open Interest
            if (openInterestStore != null) {
                try {
                    // OI limited to 30 days
                    long now = System.currentTimeMillis();
                    long maxOiHistory = 30L * 24 * 60 * 60 * 1000;
                    long oiStartTime = Math.max(startTime, now - maxOiHistory);

                    List<OpenInterest> oi = openInterestStore.getOpenInterestCacheOnly(symbol, oiStartTime, endTime);
                    ObjectNode oiInfo = coverage.putObject("openInterest");
                    oiInfo.put("recordCount", oi.size());
                    oiInfo.put("limitedTo30Days", startTime < oiStartTime);
                    if (!oi.isEmpty()) {
                        oiInfo.put("firstRecord", oi.get(0).timestamp());
                        oiInfo.put("lastRecord", oi.get(oi.size() - 1).timestamp());
                    }
                } catch (Exception e) {
                    coverage.putObject("openInterest").put("error", e.getMessage());
                }
            }

            // AggTrades
            if (aggTradesStore != null) {
                try {
                    var syncStatus = aggTradesStore.getSyncStatus(symbol, startTime, endTime);
                    ObjectNode aggInfo = coverage.putObject("aggTrades");
                    aggInfo.put("hasData", syncStatus.hasData());
                    aggInfo.put("status", syncStatus.getStatusMessage());
                    aggInfo.put("hoursComplete", syncStatus.hoursComplete());
                    aggInfo.put("hoursTotal", syncStatus.hoursTotal());
                    if (syncStatus.hoursTotal() > 0) {
                        aggInfo.put("syncPercent", (syncStatus.hoursComplete() * 100.0) / syncStatus.hoursTotal());
                    }
                } catch (Exception e) {
                    coverage.putObject("aggTrades").put("error", e.getMessage());
                }
            }

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to get data status: " + e.getMessage());
        }
    }

    /**
     * Handle pages endpoint - shows all active data pages for debugging.
     *
     * GET /pages
     *
     * Returns information about all active pages across all PageManagers,
     * including their state, listener count (consumers), and record count.
     */
    private void handlePages(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

        ObjectNode response = mapper.createObjectNode();
        ApplicationContext ctx = ApplicationContext.getInstance();

        int totalPages = 0;
        int totalListeners = 0;

        // Candle pages
        var candlePageMgr = ctx.getCandlePageManager();
        if (candlePageMgr != null) {
            ArrayNode candlePages = response.putArray("candles");
            for (DataPageManager.PageInfo info : candlePageMgr.getActivePages()) {
                ObjectNode pageNode = candlePages.addObject();
                pageNode.put("key", info.key());
                pageNode.put("symbol", info.symbol());
                pageNode.put("timeframe", info.timeframe());
                pageNode.put("state", info.state().name());
                pageNode.put("loadProgress", info.loadProgress());
                pageNode.put("listeners", info.listenerCount());
                pageNode.put("records", info.recordCount());
                ArrayNode consumers = pageNode.putArray("consumers");
                if (info.consumers() != null) {
                    info.consumers().forEach(consumers::add);
                }
                totalPages++;
                totalListeners += info.listenerCount();
            }
        }

        // Funding pages
        var fundingPageMgr = ctx.getFundingPageManager();
        if (fundingPageMgr != null) {
            ArrayNode fundingPages = response.putArray("funding");
            for (DataPageManager.PageInfo info : fundingPageMgr.getActivePages()) {
                ObjectNode pageNode = fundingPages.addObject();
                pageNode.put("key", info.key());
                pageNode.put("symbol", info.symbol());
                pageNode.put("state", info.state().name());
                pageNode.put("loadProgress", info.loadProgress());
                pageNode.put("listeners", info.listenerCount());
                pageNode.put("records", info.recordCount());
                ArrayNode consumers = pageNode.putArray("consumers");
                if (info.consumers() != null) {
                    info.consumers().forEach(consumers::add);
                }
                totalPages++;
                totalListeners += info.listenerCount();
            }
        }

        // OI pages
        var oiPageMgr = ctx.getOIPageManager();
        if (oiPageMgr != null) {
            ArrayNode oiPages = response.putArray("openInterest");
            for (DataPageManager.PageInfo info : oiPageMgr.getActivePages()) {
                ObjectNode pageNode = oiPages.addObject();
                pageNode.put("key", info.key());
                pageNode.put("symbol", info.symbol());
                pageNode.put("state", info.state().name());
                pageNode.put("loadProgress", info.loadProgress());
                pageNode.put("listeners", info.listenerCount());
                pageNode.put("records", info.recordCount());
                ArrayNode consumers = pageNode.putArray("consumers");
                if (info.consumers() != null) {
                    info.consumers().forEach(consumers::add);
                }
                totalPages++;
                totalListeners += info.listenerCount();
            }
        }

        // AggTrades pages
        var aggTradesPageMgr = ctx.getAggTradesPageManager();
        if (aggTradesPageMgr != null) {
            ArrayNode aggPages = response.putArray("aggTrades");
            for (DataPageManager.PageInfo info : aggTradesPageMgr.getActivePages()) {
                ObjectNode pageNode = aggPages.addObject();
                pageNode.put("key", info.key());
                pageNode.put("symbol", info.symbol());
                pageNode.put("state", info.state().name());
                pageNode.put("loadProgress", info.loadProgress());
                pageNode.put("listeners", info.listenerCount());
                pageNode.put("records", info.recordCount());
                ArrayNode consumers = pageNode.putArray("consumers");
                if (info.consumers() != null) {
                    info.consumers().forEach(consumers::add);
                }
                totalPages++;
                totalListeners += info.listenerCount();
            }
            // Include memory usage for aggTrades
            response.put("aggTradesRecordCount", aggTradesPageMgr.getCurrentRecordCount());
        }

        // Premium pages
        var premiumPageMgr = ctx.getPremiumPageManager();
        if (premiumPageMgr != null) {
            ArrayNode premiumPages = response.putArray("premium");
            for (DataPageManager.PageInfo info : premiumPageMgr.getActivePages()) {
                ObjectNode pageNode = premiumPages.addObject();
                pageNode.put("key", info.key());
                pageNode.put("symbol", info.symbol());
                pageNode.put("timeframe", info.timeframe());
                pageNode.put("state", info.state().name());
                pageNode.put("loadProgress", info.loadProgress());
                pageNode.put("listeners", info.listenerCount());
                pageNode.put("records", info.recordCount());
                ArrayNode consumers = pageNode.putArray("consumers");
                if (info.consumers() != null) {
                    info.consumers().forEach(consumers::add);
                }
                totalPages++;
                totalListeners += info.listenerCount();
            }
        }

        // Indicator pages (computed layer on top of data pages)
        var indicatorPageMgr = ctx.getIndicatorPageManager();
        if (indicatorPageMgr != null) {
            ArrayNode indicatorPages = response.putArray("indicators");
            for (var info : indicatorPageMgr.getActivePages()) {
                ObjectNode pageNode = indicatorPages.addObject();
                pageNode.put("key", info.key());
                pageNode.put("type", info.type());
                pageNode.put("params", info.params());
                pageNode.put("symbol", info.symbol());
                if (info.timeframe() != null) {
                    pageNode.put("timeframe", info.timeframe());
                }
                pageNode.put("state", info.state().name());
                pageNode.put("listeners", info.listenerCount());
                pageNode.put("hasData", info.hasData());
                if (info.consumers() != null && !info.consumers().isEmpty()) {
                    ArrayNode consumers = pageNode.putArray("consumers");
                    info.consumers().forEach(consumers::add);
                }
                totalPages++;
                totalListeners += info.listenerCount();
            }
        }

        // Summary
        ObjectNode summary = response.putObject("summary");
        summary.put("totalPages", totalPages);
        summary.put("totalListeners", totalListeners);

        sendJson(exchange, 200, response);
    }

    /**
     * Handle UI endpoint - shows open windows and their chart/indicator config.
     *
     * GET /ui
     *
     * Returns information about all open project windows including symbol,
     * timeframe, enabled overlays, and enabled indicator charts.
     */
    private void handleUI(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

        ObjectNode response = mapper.createObjectNode();

        LauncherFrame launcher = LauncherFrame.getInstance();
        if (launcher == null) {
            response.put("launcherOpen", false);
            response.putArray("windows");
            sendJson(exchange, 200, response);
            return;
        }

        response.put("launcherOpen", true);

        // Get all open project windows
        ArrayNode windows = response.putArray("windows");
        for (ProjectWindow.WindowInfo info : launcher.getOpenWindowsInfo()) {
            ObjectNode windowNode = windows.addObject();
            windowNode.put("strategyId", info.strategyId());
            windowNode.put("strategyName", info.strategyName());
            windowNode.put("symbol", info.symbol());
            windowNode.put("timeframe", info.timeframe());
            windowNode.put("duration", info.duration());
            windowNode.put("isVisible", info.isVisible());

            ArrayNode overlays = windowNode.putArray("enabledOverlays");
            for (String overlay : info.enabledOverlays()) {
                overlays.add(overlay);
            }

            ArrayNode indicators = windowNode.putArray("enabledIndicators");
            for (String indicator : info.enabledIndicators()) {
                indicators.add(indicator);
            }
        }

        response.put("windowCount", launcher.getOpenWindowsInfo().size());

        String lastFocused = launcher.getLastFocusedStrategyId();
        if (lastFocused != null) {
            response.put("lastFocusedStrategyId", lastFocused);
        }

        sendJson(exchange, 200, response);
    }

    /**
     * GET /ui/chart-config - returns current chart config (enabled overlays/indicators with params).
     * POST /ui/chart-config - update chart config. Accepts partial JSON:
     * {
     *   "overlays": { "SMA": { "enabled": true, "periods": [50, 200] }, "BBANDS": { "enabled": false } },
     *   "indicators": { "RSI": { "enabled": true, "period": 14 }, "MACD": { "enabled": true, "fast": 12, "slow": 26, "signal": 9 } }
     * }
     */
    private void handleChartConfig(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        ChartConfig config = ChartConfig.getInstance();

        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, buildChartConfigJson(config));
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonNode root = mapper.readTree(body);

        // Apply overlay updates
        JsonNode overlays = root.get("overlays");
        if (overlays != null) {
            applyOverlay(overlays, "SMA", enabled -> config.setSmaEnabled(enabled),
                node -> { if (node.has("periods")) {
                    List<Integer> periods = new ArrayList<>();
                    node.get("periods").forEach(p -> periods.add(p.asInt()));
                    config.setSmaPeriods(periods);
                } else if (node.has("period")) config.setSmaEnabled(true);
            });
            applyOverlay(overlays, "EMA", enabled -> config.setEmaEnabled(enabled),
                node -> { if (node.has("periods")) {
                    List<Integer> periods = new ArrayList<>();
                    node.get("periods").forEach(p -> periods.add(p.asInt()));
                    config.setEmaPeriods(periods);
                }
            });
            applyOverlay(overlays, "BBANDS", enabled -> config.setBollingerEnabled(enabled),
                node -> {
                    if (node.has("period")) config.setBollingerPeriod(node.get("period").asInt());
                    if (node.has("stdDev")) config.setBollingerStdDev(node.get("stdDev").asDouble());
                });
            applyOverlay(overlays, "HighLow", enabled -> config.setHighLowEnabled(enabled),
                node -> { if (node.has("period")) config.setHighLowPeriod(node.get("period").asInt()); });
            applyOverlay(overlays, "Mayer", enabled -> config.setMayerEnabled(enabled),
                node -> { if (node.has("period")) config.setMayerPeriod(node.get("period").asInt()); });
            applySimpleOverlay(overlays, "VWAP", config::setVwapEnabled);
            applySimpleOverlay(overlays, "DailyPOC", config::setDailyPocEnabled);
            applySimpleOverlay(overlays, "FloatingPOC", config::setFloatingPocEnabled);
            applySimpleOverlay(overlays, "Rays", config::setRayOverlayEnabled);
            applySimpleOverlay(overlays, "Ichimoku", config::setIchimokuEnabled);
        }

        // Apply phase overlays
        JsonNode phaseOverlays = root.get("phaseOverlays");
        if (phaseOverlays != null && phaseOverlays.isArray()) {
            List<String> ids = new ArrayList<>();
            phaseOverlays.forEach(n -> ids.add(n.asText()));
            config.setPhaseOverlayIds(ids);
        }

        // Apply indicator updates
        JsonNode indicators = root.get("indicators");
        if (indicators != null) {
            applyOverlay(indicators, "RSI", config::setRsiEnabled,
                node -> { if (node.has("period")) config.setRsiPeriod(node.get("period").asInt()); });
            applyOverlay(indicators, "MACD", config::setMacdEnabled,
                node -> {
                    if (node.has("fast")) config.setMacdFast(node.get("fast").asInt());
                    if (node.has("slow")) config.setMacdSlow(node.get("slow").asInt());
                    if (node.has("signal")) config.setMacdSignal(node.get("signal").asInt());
                });
            applyOverlay(indicators, "ATR", config::setAtrEnabled,
                node -> { if (node.has("period")) config.setAtrPeriod(node.get("period").asInt()); });
            applyOverlay(indicators, "STOCHASTIC", config::setStochasticEnabled,
                node -> {
                    if (node.has("kPeriod")) config.setStochasticKPeriod(node.get("kPeriod").asInt());
                    if (node.has("dPeriod")) config.setStochasticDPeriod(node.get("dPeriod").asInt());
                });
            applyOverlay(indicators, "RANGE_POSITION", config::setRangePositionEnabled,
                node -> { if (node.has("period")) config.setRangePositionPeriod(node.get("period").asInt()); });
            applyOverlay(indicators, "ADX", config::setAdxEnabled,
                node -> { if (node.has("period")) config.setAdxPeriod(node.get("period").asInt()); });
            applySimpleOverlay(indicators, "DELTA", config::setDeltaEnabled);
            applySimpleOverlay(indicators, "CVD", config::setCvdEnabled);
            applySimpleOverlay(indicators, "FUNDING", config::setFundingEnabled);
            applySimpleOverlay(indicators, "OI", config::setOiEnabled);
            applySimpleOverlay(indicators, "PREMIUM", config::setPremiumEnabled);
        }

        config.notifyChanged();

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        response.set("chartConfig", buildChartConfigJson(config));
        sendJson(exchange, 200, response);
    }

    private void applyOverlay(JsonNode parent, String key,
                              java.util.function.Consumer<Boolean> enableSetter,
                              java.util.function.Consumer<JsonNode> paramApplier) {
        JsonNode node = parent.get(key);
        if (node == null) return;
        if (node.has("enabled")) enableSetter.accept(node.get("enabled").asBoolean());
        paramApplier.accept(node);
    }

    private void applySimpleOverlay(JsonNode parent, String key,
                                    java.util.function.Consumer<Boolean> enableSetter) {
        JsonNode node = parent.get(key);
        if (node == null) return;
        if (node.has("enabled")) enableSetter.accept(node.get("enabled").asBoolean());
    }

    private ObjectNode buildChartConfigJson(ChartConfig config) {
        ObjectNode root = mapper.createObjectNode();

        ObjectNode overlays = root.putObject("overlays");
        // SMA
        ObjectNode sma = overlays.putObject("SMA");
        sma.put("enabled", config.isSmaEnabled());
        ArrayNode smaPeriods = sma.putArray("periods");
        config.getSmaPeriods().forEach(smaPeriods::add);
        // EMA
        ObjectNode ema = overlays.putObject("EMA");
        ema.put("enabled", config.isEmaEnabled());
        ArrayNode emaPeriods = ema.putArray("periods");
        config.getEmaPeriods().forEach(emaPeriods::add);
        // BBANDS
        ObjectNode bb = overlays.putObject("BBANDS");
        bb.put("enabled", config.isBollingerEnabled());
        bb.put("period", config.getBollingerPeriod());
        bb.put("stdDev", config.getBollingerStdDev());
        // HighLow
        ObjectNode hl = overlays.putObject("HighLow");
        hl.put("enabled", config.isHighLowEnabled());
        hl.put("period", config.getHighLowPeriod());
        // Mayer
        ObjectNode mayer = overlays.putObject("Mayer");
        mayer.put("enabled", config.isMayerEnabled());
        mayer.put("period", config.getMayerPeriod());
        // Simple overlays
        overlays.putObject("VWAP").put("enabled", config.isVwapEnabled());
        overlays.putObject("DailyPOC").put("enabled", config.isDailyPocEnabled());
        overlays.putObject("FloatingPOC").put("enabled", config.isFloatingPocEnabled());
        overlays.putObject("Rays").put("enabled", config.isRayOverlayEnabled());
        overlays.putObject("Ichimoku").put("enabled", config.isIchimokuEnabled());

        ObjectNode indicators = root.putObject("indicators");
        // RSI
        ObjectNode rsi = indicators.putObject("RSI");
        rsi.put("enabled", config.isRsiEnabled());
        rsi.put("period", config.getRsiPeriod());
        // MACD
        ObjectNode macd = indicators.putObject("MACD");
        macd.put("enabled", config.isMacdEnabled());
        macd.put("fast", config.getMacdFast());
        macd.put("slow", config.getMacdSlow());
        macd.put("signal", config.getMacdSignal());
        // ATR
        ObjectNode atr = indicators.putObject("ATR");
        atr.put("enabled", config.isAtrEnabled());
        atr.put("period", config.getAtrPeriod());
        // STOCHASTIC
        ObjectNode stoch = indicators.putObject("STOCHASTIC");
        stoch.put("enabled", config.isStochasticEnabled());
        stoch.put("kPeriod", config.getStochasticKPeriod());
        stoch.put("dPeriod", config.getStochasticDPeriod());
        // RANGE_POSITION
        ObjectNode rp = indicators.putObject("RANGE_POSITION");
        rp.put("enabled", config.isRangePositionEnabled());
        rp.put("period", config.getRangePositionPeriod());
        // ADX
        ObjectNode adx = indicators.putObject("ADX");
        adx.put("enabled", config.isAdxEnabled());
        adx.put("period", config.getAdxPeriod());
        // Simple indicators
        indicators.putObject("DELTA").put("enabled", config.isDeltaEnabled());
        indicators.putObject("CVD").put("enabled", config.isCvdEnabled());
        indicators.putObject("FUNDING").put("enabled", config.isFundingEnabled());
        indicators.putObject("OI").put("enabled", config.isOiEnabled());
        indicators.putObject("PREMIUM").put("enabled", config.isPremiumEnabled());

        // Phase overlays
        ArrayNode phaseOverlays = root.putArray("phaseOverlays");
        config.getPhaseOverlayIds().forEach(phaseOverlays::add);

        return root;
    }

    /**
     * Handle UI open endpoint - opens windows programmatically for AI agents.
     *
     * POST /ui/open?window=phases     - Open Phases editor
     * POST /ui/open?window=hoops      - Open Hoops editor
     * POST /ui/open?window=settings   - Open Settings dialog
     * POST /ui/open?window=data       - Open Data Management dialog
     * POST /ui/open?window=dsl-help   - Open DSL Help dialog
     * POST /ui/open?window=downloads  - Open Download Dashboard
     * POST /ui/open?window=launcher   - Bring launcher to front
     * POST /ui/open?window=project&id={strategyId} - Open a strategy project
     *
     * Returns JSON with success status and opened window info.
     */
    private void handleUIOpen(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "POST")) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        String window = params.get("window");
        if (window == null || window.isEmpty()) {
            sendError(exchange, 400, "Missing required parameter: window. " +
                "Valid options: phases, hoops, settings, data, dsl-help, downloads, launcher, project");
            return;
        }

        LauncherFrame launcher = LauncherFrame.getInstance();
        if (launcher == null) {
            sendError(exchange, 503, "Application not fully initialized - launcher not available");
            return;
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("window", window);

        switch (window.toLowerCase()) {
            case "phases" -> {
                launcher.openPhases();
                response.put("success", true);
                response.put("message", "Phases window opened");
            }
            case "hoops" -> {
                launcher.openHoops();
                response.put("success", true);
                response.put("message", "Hoops window opened");
            }
            case "settings" -> {
                launcher.openSettings();
                response.put("success", true);
                response.put("message", "Settings dialog opened");
            }
            case "data", "data-management" -> {
                launcher.openDataManagement();
                response.put("success", true);
                response.put("message", "Data Management dialog opened");
            }
            case "dsl-help", "dsl", "help" -> {
                launcher.openDslHelp();
                response.put("success", true);
                response.put("message", "DSL Help dialog opened");
            }
            case "downloads", "download-dashboard" -> {
                launcher.openDownloadDashboard();
                response.put("success", true);
                response.put("message", "Download Dashboard opened");
            }
            case "launcher" -> {
                launcher.bringToFront();
                response.put("success", true);
                response.put("message", "Launcher brought to front");
            }
            case "project", "strategy" -> {
                String strategyId = params.get("id");
                if (strategyId == null || strategyId.isEmpty()) {
                    sendError(exchange, 400, "Missing required parameter: id (strategy ID)");
                    return;
                }
                boolean success = launcher.openProjectById(strategyId);
                response.put("success", success);
                response.put("strategyId", strategyId);
                if (success) {
                    response.put("message", "Project window opened for strategy: " + strategyId);
                } else {
                    response.put("message", "Strategy not found: " + strategyId);
                }
            }
            default -> {
                sendError(exchange, 400, "Unknown window type: " + window + ". " +
                    "Valid options: phases, hoops, settings, data, dsl-help, downloads, launcher, project");
                return;
            }
        }

        sendJson(exchange, 200, response);
    }

    /**
     * Handle thread dump endpoint - captures all thread states for debugging.
     *
     * GET /thread-dump
     *
     * Returns stack traces for all threads, with EDT (Event Dispatch Thread)
     * highlighted at the top. Use this when UI is frozen to identify blocking calls.
     */
    private void handleThreadDump(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

        ObjectNode response = mapper.createObjectNode();
        response.put("timestamp", System.currentTimeMillis());
        response.put("timestampHuman", java.time.Instant.now().toString());

        // Get all thread stack traces
        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();

        // Find EDT first
        Thread edtThread = null;
        StackTraceElement[] edtStack = null;
        for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
            Thread t = entry.getKey();
            if (t.getName().contains("AWT-EventQueue") || t.getName().contains("EDT")) {
                edtThread = t;
                edtStack = entry.getValue();
                break;
            }
        }

        // EDT info (most important for UI blocking)
        ObjectNode edtInfo = response.putObject("edt");
        if (edtThread != null) {
            edtInfo.put("name", edtThread.getName());
            edtInfo.put("state", edtThread.getState().name());
            edtInfo.put("isBlocked", edtThread.getState() == Thread.State.BLOCKED ||
                                      edtThread.getState() == Thread.State.WAITING ||
                                      edtThread.getState() == Thread.State.TIMED_WAITING);

            ArrayNode edtStackArray = edtInfo.putArray("stackTrace");
            if (edtStack != null) {
                for (StackTraceElement elem : edtStack) {
                    edtStackArray.add(elem.toString());
                }
            }

            // Quick analysis - what's EDT doing?
            if (edtStack != null && edtStack.length > 0) {
                String topFrame = edtStack[0].toString();
                String analysis = analyzeEdtState(edtStack);
                edtInfo.put("topFrame", topFrame);
                edtInfo.put("analysis", analysis);
            }
        } else {
            edtInfo.put("error", "EDT not found");
        }

        // All threads summary
        ArrayNode threadsArray = response.putArray("threads");
        for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
            Thread t = entry.getKey();
            StackTraceElement[] stack = entry.getValue();

            ObjectNode threadNode = threadsArray.addObject();
            threadNode.put("name", t.getName());
            threadNode.put("state", t.getState().name());
            threadNode.put("daemon", t.isDaemon());
            threadNode.put("priority", t.getPriority());

            // Only include stack for interesting threads (not idle daemon threads)
            boolean isInteresting = t.getState() == Thread.State.BLOCKED ||
                                    t.getState() == Thread.State.RUNNABLE ||
                                    t.getName().contains("AWT") ||
                                    t.getName().contains("Tradery") ||
                                    t.getName().contains("DataPage") ||
                                    t.getName().contains("Indicator") ||
                                    t.getName().contains("pool");

            if (isInteresting && stack.length > 0) {
                ArrayNode stackArray = threadNode.putArray("stackTrace");
                // Limit to top 20 frames
                int limit = Math.min(stack.length, 20);
                for (int i = 0; i < limit; i++) {
                    stackArray.add(stack[i].toString());
                }
                if (stack.length > limit) {
                    stackArray.add("... " + (stack.length - limit) + " more frames");
                }
            }
        }

        response.put("threadCount", allThreads.size());

        sendJson(exchange, 200, response);
    }

    /**
     * Analyze EDT stack trace to provide quick diagnosis.
     */
    private String analyzeEdtState(StackTraceElement[] stack) {
        if (stack == null || stack.length == 0) {
            return "No stack trace";
        }

        // First check for normal idle state (waiting for next event)
        for (StackTraceElement elem : stack) {
            if (elem.toString().contains("EventQueue.getNextEvent")) {
                return "IDLE - Waiting for next event (normal)";
            }
        }

        // Look for common blocking patterns
        for (StackTraceElement elem : stack) {
            String frame = elem.toString();

            // Database/SQLite blocking
            if (frame.contains("sqlite") || frame.contains("SqliteDataStore") ||
                frame.contains("jdbc") || frame.contains("getConnection")) {
                return "BLOCKED ON DATABASE - SQLite operation on EDT!";
            }

            // Network/HTTP blocking
            if (frame.contains("HttpURLConnection") || frame.contains("OkHttp") ||
                frame.contains("BinanceClient") || frame.contains("Socket") ||
                frame.contains("SocketInputStream") || frame.contains("HttpClient")) {
                return "BLOCKED ON NETWORK - HTTP/API call on EDT!";
            }

            // File I/O blocking
            if (frame.contains("FileInputStream") || frame.contains("FileOutputStream") ||
                frame.contains("BufferedReader") || frame.contains("CsvReader") ||
                frame.contains("Files.read") || frame.contains("Files.write")) {
                return "BLOCKED ON FILE I/O - File operation on EDT!";
            }

            // Sleep (intentional or bug)
            if (frame.contains("Thread.sleep")) {
                return "BLOCKED ON SLEEP - Thread.sleep on EDT!";
            }

            // Swing repaint (normal)
            if (frame.contains("RepaintManager") || frame.contains("paintComponent") ||
                frame.contains("JComponent.paint")) {
                return "PAINTING - Rendering UI components (normal)";
            }

            // Layout (can be slow but normal)
            if (frame.contains("LayoutManager") || frame.contains("doLayout") ||
                frame.contains("validateTree")) {
                return "LAYOUT - Computing component layout";
            }
        }

        // Check for lock contention last (less specific)
        for (StackTraceElement elem : stack) {
            String frame = elem.toString();
            if (frame.contains("ReentrantLock.lock") || frame.contains("synchronized")) {
                return "BLOCKED ON LOCK - Waiting for lock/monitor";
            }
        }

        return "BUSY - Check stack trace for details";
    }

    /**
     * Handle download log endpoint - query download events with filters.
     *
     * GET /download-log?since=&dataType=&eventType=&pageKey=&limit=
     *
     * Parameters:
     *   - since: Timestamp in ms (default: last 5 minutes)
     *   - dataType: Filter by data type (CANDLES, FUNDING, OPEN_INTEREST, AGG_TRADES, PREMIUM_INDEX)
     *   - eventType: Filter by event type (PAGE_CREATED, LOAD_STARTED, LOAD_COMPLETED, ERROR, etc.)
     *   - pageKey: Filter by page key (substring match)
     *   - limit: Max events to return (default: 100, max: 1000)
     */
    private void handleDownloadLog(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        // Parse filters
        Long sinceTimestamp = null;
        String sinceStr = params.get("since");
        if (sinceStr != null && !sinceStr.isEmpty()) {
            try {
                sinceTimestamp = Long.parseLong(sinceStr);
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Invalid 'since' parameter: " + sinceStr);
                return;
            }
        }
        // No default since filter — return the last N entries (governed by limit param)

        DataType dataType = null;
        String dataTypeStr = params.get("dataType");
        if (dataTypeStr != null && !dataTypeStr.isEmpty()) {
            try {
                dataType = DataType.valueOf(dataTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "Invalid dataType: " + dataTypeStr +
                    ". Valid options: CANDLES, FUNDING, OPEN_INTEREST, AGG_TRADES, PREMIUM_INDEX");
                return;
            }
        }

        DownloadEvent.EventType eventType = null;
        String eventTypeStr = params.get("eventType");
        if (eventTypeStr != null && !eventTypeStr.isEmpty()) {
            try {
                eventType = DownloadEvent.EventType.valueOf(eventTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "Invalid eventType: " + eventTypeStr +
                    ". Valid options: PAGE_CREATED, LOAD_STARTED, LOAD_COMPLETED, ERROR, UPDATE_STARTED, UPDATE_COMPLETED, PAGE_RELEASED, LISTENER_ADDED, LISTENER_REMOVED");
                return;
            }
        }

        String pageKey = params.get("pageKey");
        int limit = Integer.parseInt(params.getOrDefault("limit", "100"));
        limit = Math.min(Math.max(limit, 1), 1000);

        // Query the log store
        DownloadLogStore logStore = DownloadLogStore.getInstance();
        List<DownloadEvent> events = logStore.query(sinceTimestamp, dataType, eventType, pageKey, limit);

        // Build response
        ObjectNode response = mapper.createObjectNode();
        response.put("count", events.size());
        response.put("since", sinceTimestamp);
        if (dataType != null) response.put("dataTypeFilter", dataType.name());
        if (eventType != null) response.put("eventTypeFilter", eventType.name());
        if (pageKey != null) response.put("pageKeyFilter", pageKey);

        ArrayNode eventsArray = response.putArray("events");
        for (DownloadEvent event : events) {
            ObjectNode eventNode = eventsArray.addObject();
            eventNode.put("timestamp", event.timestamp());
            eventNode.put("pageKey", event.pageKey());
            eventNode.put("dataType", event.dataType().name());
            eventNode.put("eventType", event.eventType().name());
            eventNode.put("message", event.message());

            // Add metadata fields if present
            Long duration = event.getDurationMs();
            if (duration != null) eventNode.put("durationMs", duration);

            Integer recordCount = event.getRecordCount();
            if (recordCount != null) eventNode.put("recordCount", recordCount);

            String errorMsg = event.getErrorMessage();
            if (errorMsg != null) eventNode.put("errorMessage", errorMsg);

            String consumer = event.getConsumerName();
            if (consumer != null) eventNode.put("consumerName", consumer);
        }

        sendJson(exchange, 200, response);
    }

    /**
     * Handle download stats endpoint - get statistics about download activity.
     *
     * GET /download-stats
     *
     * Returns statistics including total events, recent activity, errors, and load times.
     */
    private void handleDownloadStats(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

        ApplicationContext ctx = ApplicationContext.getInstance();

        // Count active pages and total records
        int activePages = 0;
        int totalRecords = 0;

        var candlePageMgr = ctx.getCandlePageManager();
        if (candlePageMgr != null) {
            activePages += candlePageMgr.getActivePageCount();
            totalRecords += candlePageMgr.getTotalRecordCount();
        }

        var fundingPageMgr = ctx.getFundingPageManager();
        if (fundingPageMgr != null) {
            activePages += fundingPageMgr.getActivePageCount();
            totalRecords += fundingPageMgr.getTotalRecordCount();
        }

        var oiPageMgr = ctx.getOIPageManager();
        if (oiPageMgr != null) {
            activePages += oiPageMgr.getActivePageCount();
            totalRecords += oiPageMgr.getTotalRecordCount();
        }

        var aggTradesPageMgr = ctx.getAggTradesPageManager();
        if (aggTradesPageMgr != null) {
            activePages += aggTradesPageMgr.getActivePageCount();
            totalRecords += aggTradesPageMgr.getTotalRecordCount();
        }

        var premiumPageMgr = ctx.getPremiumPageManager();
        if (premiumPageMgr != null) {
            activePages += premiumPageMgr.getActivePageCount();
            totalRecords += premiumPageMgr.getTotalRecordCount();
        }

        // Get statistics
        DownloadLogStore logStore = DownloadLogStore.getInstance();
        DownloadStatistics stats = logStore.getStatistics(activePages, totalRecords);

        // Build response
        ObjectNode response = mapper.createObjectNode();
        response.put("totalEvents", stats.totalEvents());
        response.put("eventsLast5Minutes", stats.eventsLast5Minutes());
        response.put("errorsLast5Minutes", stats.errorsLast5Minutes());
        response.put("avgLoadTimeMs", Math.round(stats.avgLoadTimeMs() * 100) / 100.0);
        response.put("activePages", stats.activePages());
        response.put("totalRecordCount", stats.totalRecordCount());
        response.put("healthStatus", stats.getHealthStatus());

        // Events by data type
        ObjectNode byDataType = response.putObject("eventsByDataType");
        for (var entry : stats.eventsByType().entrySet()) {
            byDataType.put(entry.getKey().name(), entry.getValue());
        }

        // Events by event type
        ObjectNode byEventType = response.putObject("eventsByEventType");
        for (var entry : stats.eventsByEventType().entrySet()) {
            byEventType.put(entry.getKey().name(), entry.getValue());
        }

        sendJson(exchange, 200, response);
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
            var generator = new com.tradery.forge.data.SubMinuteCandleGenerator();
            candles = generator.generate(aggTrades, subMinuteSeconds, startMs, endMs);
        } else {
            // Standard timeframe: load from SQLite
            candles = dataStore.getCandles(symbol, timeframe, startMs, endMs);

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

        // ========== Stochastic Oscillator ==========

        // STOCHASTIC(kPeriod) or STOCHASTIC(kPeriod,dPeriod) with optional .k or .d
        Pattern stochPattern = Pattern.compile("STOCHASTIC\\((\\d+)(?:,(\\d+))?\\)(?:\\.(k|d))?", Pattern.CASE_INSENSITIVE);
        Matcher stochMatcher = stochPattern.matcher(name);
        if (stochMatcher.matches()) {
            int kPeriod = Integer.parseInt(stochMatcher.group(1));
            int dPeriod = stochMatcher.group(2) != null ? Integer.parseInt(stochMatcher.group(2)) : 3;
            String component = stochMatcher.group(3);

            var stoch = engine.getStochastic(kPeriod, dPeriod);
            double[] values = "d".equalsIgnoreCase(component) ? stoch.d() : stoch.k();
            System.arraycopy(values, 0, result, 0, Math.min(values.length, size));
            return result;
        }

        // ========== Range Position ==========

        // RANGE_POSITION(period) or RANGE_POSITION(period,skip)
        Pattern rangePosPattern = Pattern.compile("RANGE_POSITION\\((\\d+)(?:,(\\d+))?\\)", Pattern.CASE_INSENSITIVE);
        Matcher rangePosMatcher = rangePosPattern.matcher(name);
        if (rangePosMatcher.matches()) {
            int period = Integer.parseInt(rangePosMatcher.group(1));
            int skip = rangePosMatcher.group(2) != null ? Integer.parseInt(rangePosMatcher.group(2)) : 0;
            double[] rangePos = engine.getRangePosition(period, skip);
            System.arraycopy(rangePos, 0, result, 0, Math.min(rangePos.length, size));
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
