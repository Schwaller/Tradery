package com.tradery.news.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.ui.coin.EntitySearchProcessor;
import com.tradery.news.ui.coin.EntityStore;
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
 *   GET  /stats       - Entity, relationship, article counts
 *   GET  /entities    - List entities with optional type/search filter
 *   GET  /entity/{id} - Single entity + relationships
 *   GET  /entity/{id}/graph?depth=1 - BFS neighborhood
 *   POST /entity      - Create entity
 *   DELETE /entity/{id} - Delete entity
 *   POST /entity/{id}/discover - AI entity discovery
 *   POST /entity/{id}/discover/apply - Apply discovered entities
 *   POST /relationship - Create relationship
 *   DELETE /relationship - Delete relationship
 *   GET  /articles    - List articles with filters
 *   GET  /article/{id} - Single article
 *   GET  /topics      - Topic counts
 *   GET  /stories     - Active stories
 *   GET  /events      - Recent events
 *   GET  /schema/types - Schema type definitions
 */
public class IntelApiServer {

    private static final Logger log = LoggerFactory.getLogger(IntelApiServer.class);
    private static final int DEFAULT_PORT = 7862;
    private static final int MAX_PORT_ATTEMPTS = 10;
    private static final Path PORT_FILE = Path.of(System.getProperty("user.home"), ".tradery", "intel-api.port");

    private HttpServer server;
    private int actualPort;

    private final Consumer<String> windowOpener;

    // Handlers
    private final StatsHandler statsHandler;
    private final EntityHandler entityHandler;
    private final DiscoverHandler discoverHandler;
    private final ArticleHandler articleHandler;
    private final SchemaHandler schemaHandler;

    public IntelApiServer(Consumer<String> windowOpener,
                          EntityStore entityStore,
                          SqliteNewsStore newsStore,
                          EntitySearchProcessor searchProcessor) {
        this.windowOpener = windowOpener;
        this.statsHandler = new StatsHandler(entityStore, newsStore);
        this.entityHandler = new EntityHandler(entityStore);
        this.discoverHandler = new DiscoverHandler(entityStore, searchProcessor);
        this.articleHandler = new ArticleHandler(newsStore);
        this.schemaHandler = new SchemaHandler(entityStore);
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

        server.setExecutor(Executors.newFixedThreadPool(4));

        // Original endpoints
        server.createContext("/status", this::handleStatus);
        server.createContext("/ui/open", this::handleUiOpen);
        server.createContext("/thread-dump", this::handleThreadDump);

        // New endpoints
        server.createContext("/stats", statsHandler::handleStats);
        server.createContext("/entities", entityHandler::handleEntities);
        server.createContext("/entity/", this::routeEntity);
        server.createContext("/entity", entityHandler::handleEntity);
        server.createContext("/relationship", this::routeRelationship);
        server.createContext("/articles", articleHandler::handleArticles);
        server.createContext("/article/", articleHandler::handleArticle);
        server.createContext("/topics", articleHandler::handleTopics);
        server.createContext("/stories", articleHandler::handleStories);
        server.createContext("/events", articleHandler::handleEvents);
        server.createContext("/schema/types", schemaHandler::handleSchema);

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

    /**
     * Route /entity/{id}/... to the correct handler.
     * Discover paths go to DiscoverHandler, others to EntityHandler.
     */
    private void routeEntity(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");

        // /entity/{id}/discover or /entity/{id}/discover/apply
        if (parts.length >= 4 && "discover".equals(parts[3])) {
            discoverHandler.handleDiscover(exchange);
        } else {
            entityHandler.handleEntity(exchange);
        }
    }

    /**
     * Route /relationship to create or delete.
     */
    private void routeRelationship(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if ("POST".equals(method)) {
            entityHandler.handleCreateRelationship(exchange);
        } else if ("DELETE".equals(method)) {
            entityHandler.handleDeleteRelationship(exchange);
        } else {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
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

    // ========== Original Handlers ==========

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
