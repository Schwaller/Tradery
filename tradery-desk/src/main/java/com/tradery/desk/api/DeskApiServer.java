package com.tradery.desk.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tradery.desk.signal.SignalEvent;
import com.tradery.desk.strategy.DeskStrategyStore;
import com.tradery.desk.strategy.PublishedStrategy;
import com.tradery.desk.ui.DeskFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Lightweight HTTP API server for Desk debugging.
 *
 * Endpoints:
 *   GET /status       - Health check
 *   GET /strategies   - Active strategies
 *   GET /signals      - Recent signals from signal log
 *   GET /connection   - Connection state info
 *   GET /thread-dump  - All thread stack traces
 */
public class DeskApiServer {

    private static final Logger log = LoggerFactory.getLogger(DeskApiServer.class);
    private static final int DEFAULT_PORT = 7852;
    private static final int MAX_PORT_ATTEMPTS = 10;
    private static final Path PORT_FILE = Path.of(System.getProperty("user.home"), ".tradery", "desk-api.port");

    private HttpServer server;
    private int actualPort;

    private final DeskStrategyStore strategyStore;
    private final Supplier<DeskFrame> frameSupplier;

    public DeskApiServer(DeskStrategyStore strategyStore, Supplier<DeskFrame> frameSupplier) {
        this.strategyStore = strategyStore;
        this.frameSupplier = frameSupplier;
    }

    public void start() throws IOException {
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
            throw new IOException("Could not find free port in range " +
                DEFAULT_PORT + "-" + (DEFAULT_PORT + MAX_PORT_ATTEMPTS - 1), lastException);
        }

        server.setExecutor(Executors.newFixedThreadPool(2));
        server.createContext("/status", this::handleStatus);
        server.createContext("/strategies", this::handleStrategies);
        server.createContext("/signals", this::handleSignals);
        server.createContext("/connection", this::handleConnection);
        server.createContext("/thread-dump", this::handleThreadDump);

        server.start();

        // Write port file
        try {
            Files.createDirectories(PORT_FILE.getParent());
            Files.writeString(PORT_FILE, String.valueOf(actualPort));
        } catch (IOException e) {
            log.warn("Failed to write port file: {}", e.getMessage());
        }

        log.info("Desk API server started on http://localhost:{}", actualPort);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("Desk API server stopped");
        }
        try {
            Files.deleteIfExists(PORT_FILE);
        } catch (IOException ignored) {}
    }

    public int getPort() {
        return actualPort;
    }

    // ========== Handlers ==========

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;
        sendJson(exchange, 200, """
            {"status":"running","app":"tradery-desk"}""");
    }

    private void handleStrategies(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;

        List<PublishedStrategy> strategies = strategyStore.getAll();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"count\":").append(strategies.size()).append(",\"strategies\":[");

        for (int i = 0; i < strategies.size(); i++) {
            PublishedStrategy s = strategies.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"").append(escape(s.getId())).append('"');
            sb.append(",\"name\":\"").append(escape(s.getName())).append('"');
            sb.append(",\"symbol\":\"").append(escape(s.getSymbol())).append('"');
            sb.append(",\"timeframe\":\"").append(escape(s.getTimeframe())).append('"');
            sb.append(",\"version\":").append(s.getVersion());
            sb.append(",\"enabled\":").append(s.isEnabled());
            sb.append('}');
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleSignals(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;

        DeskFrame frame = frameSupplier.get();
        if (frame == null) {
            sendJson(exchange, 200, "{\"count\":0,\"signals\":[]}");
            return;
        }

        List<SignalEvent> signals = frame.getSignals();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"count\":").append(signals.size()).append(",\"signals\":[");

        for (int i = 0; i < signals.size(); i++) {
            SignalEvent s = signals.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"type\":\"").append(s.type().name()).append('"');
            sb.append(",\"strategyId\":\"").append(escape(s.strategyId())).append('"');
            sb.append(",\"strategyName\":\"").append(escape(s.strategyName())).append('"');
            sb.append(",\"symbol\":\"").append(escape(s.symbol())).append('"');
            sb.append(",\"timeframe\":\"").append(escape(s.timeframe())).append('"');
            sb.append(",\"price\":").append(s.price());
            sb.append(",\"timestamp\":\"").append(s.timestamp()).append('"');
            sb.append('}');
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleConnection(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;

        // Report page connection and data service status from available info
        boolean hasFrame = frameSupplier.get() != null;
        int strategyCount = strategyStore.getAll().size();

        StringBuilder sb = new StringBuilder();
        sb.append("{\"uiReady\":").append(hasFrame);
        sb.append(",\"activeStrategies\":").append(strategyCount);
        sb.append('}');
        sendJson(exchange, 200, sb.toString());
    }

    private void handleThreadDump(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;

        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"threadCount\":").append(allThreads.size()).append(",\"threads\":[");

        boolean first = true;
        for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
            Thread t = entry.getKey();
            StackTraceElement[] stack = entry.getValue();

            if (!first) sb.append(',');
            first = false;

            sb.append("{\"name\":\"").append(escape(t.getName())).append('"');
            sb.append(",\"state\":\"").append(t.getState().name()).append('"');
            sb.append(",\"daemon\":").append(t.isDaemon());

            // Include stack for interesting threads
            boolean interesting = t.getState() == Thread.State.BLOCKED ||
                t.getState() == Thread.State.RUNNABLE ||
                t.getName().contains("AWT") ||
                t.getName().contains("Tradery") ||
                t.getName().contains("pool");

            if (interesting && stack.length > 0) {
                sb.append(",\"stackTrace\":[");
                int limit = Math.min(stack.length, 20);
                for (int i = 0; i < limit; i++) {
                    if (i > 0) sb.append(',');
                    sb.append('"').append(escape(stack[i].toString())).append('"');
                }
                sb.append(']');
            }
            sb.append('}');
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    // ========== Helpers ==========

    private boolean checkGet(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return false;
        }
        return true;
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
