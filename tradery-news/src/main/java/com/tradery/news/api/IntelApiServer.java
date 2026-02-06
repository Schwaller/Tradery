package com.tradery.news.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Lightweight HTTP API server for Intel app.
 *
 * Endpoints:
 *   GET  /status      - Health check
 *   POST /ui/open     - Open a window (query param: window=data-structure|settings)
 *   GET  /thread-dump - All thread stack traces
 */
public class IntelApiServer {

    private static final Logger log = LoggerFactory.getLogger(IntelApiServer.class);
    private static final int DEFAULT_PORT = 7862;
    private static final int MAX_PORT_ATTEMPTS = 10;
    private static final Path PORT_FILE = Path.of(System.getProperty("user.home"), ".tradery", "intel-api.port");

    private HttpServer server;
    private int actualPort;

    private final Consumer<String> windowOpener;

    public IntelApiServer(Consumer<String> windowOpener) {
        this.windowOpener = windowOpener;
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
        server.createContext("/ui/open", this::handleUiOpen);
        server.createContext("/thread-dump", this::handleThreadDump);

        server.start();

        // Write port file
        try {
            Files.createDirectories(PORT_FILE.getParent());
            Files.writeString(PORT_FILE, String.valueOf(actualPort));
        } catch (IOException e) {
            log.warn("Failed to write port file: {}", e.getMessage());
        }

        log.info("Intel API server started on http://localhost:{}", actualPort);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("Intel API server stopped");
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
        sendJson(exchange, 200, "{\"status\":\"running\",\"app\":\"tradery-intel\"}");
    }

    private void handleUiOpen(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) &&
            !"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String window = null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "window".equals(kv[0])) {
                    window = kv[1];
                }
            }
        }

        if (window == null || window.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"Missing 'window' parameter\"}");
            return;
        }

        String windowName = window;
        javax.swing.SwingUtilities.invokeLater(() -> windowOpener.accept(windowName));
        sendJson(exchange, 200, "{\"ok\":true,\"window\":\"" + escape(windowName) + "\"}");
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

            boolean interesting = t.getState() == Thread.State.BLOCKED ||
                t.getState() == Thread.State.RUNNABLE ||
                t.getName().contains("AWT") ||
                t.getName().contains("Intel") ||
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
