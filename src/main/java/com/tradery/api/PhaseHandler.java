package com.tradery.api;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.tradery.data.CandleStore;
import com.tradery.dsl.Parser;
import com.tradery.engine.ConditionEvaluator;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.io.PhaseStore;
import com.tradery.model.Candle;
import com.tradery.model.Phase;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Handles phase-related API endpoints:
 *   GET /phases                         - List all phases
 *   GET /phase/{id}                     - Get phase JSON
 *   GET /phase/{id}/bounds              - Find time ranges where phase is active
 */
public class PhaseHandler extends ApiHandlerBase {

    private final PhaseStore phaseStore;
    private final CandleStore candleStore;

    public PhaseHandler(PhaseStore phaseStore, CandleStore candleStore) {
        this.phaseStore = phaseStore;
        this.candleStore = candleStore;
    }

    /**
     * Handle GET /phases - list all phases.
     */
    public void handlePhases(HttpExchange exchange) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

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

    /**
     * Handle /phase/{id} endpoints - route to appropriate handler.
     */
    public void handlePhase(HttpExchange exchange) throws IOException {
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
            sendJsonString(exchange, 200, json);
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

    private List<Candle> loadCandles(String symbol, String timeframe, int bars) throws IOException {
        Instant end = Instant.now();
        long endMs = end.toEpochMilli();
        long startMs = end.minus((long) bars * getTimeframeMinutes(timeframe), ChronoUnit.MINUTES).toEpochMilli();

        List<Candle> candles = candleStore.getCandles(symbol, timeframe, startMs, endMs);

        // Limit to requested bars (from end)
        if (candles.size() > bars) {
            candles = candles.subList(candles.size() - bars, candles.size());
        }

        return candles;
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
}
