package com.tradery.news.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tradery.ai.challenges.execution.ChallengeExecutor;
import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.store.ChallengeStore;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.ui.IntelLogPanel;
import com.tradery.news.ui.coin.EntitySearchProcessor;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.challenges.StandaloneChallengeSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.tradery.news.ui.SharingService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.tradery.news.ui.IntelFrame;
import com.tradery.news.ui.PanelConfig;
import com.tradery.news.ui.IntelConfig;
import com.tradery.news.ui.coin.CoinEntity;
import com.tradery.news.ui.coin.SchemaAttribute;
import com.tradery.news.ui.coin.SchemaType;

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
 *   GET  /network      - Network/sharing infrastructure status (no sensitive data)
 */
public class IntelApiServer {

    private static final Logger log = LoggerFactory.getLogger(IntelApiServer.class);
    private static final int DEFAULT_PORT = 7862;
    private static final int MAX_PORT_ATTEMPTS = 10;
    private static final Path PORT_FILE = Path.of(System.getProperty("user.home"), ".tradery", "intel-api.port");

    private HttpServer server;
    private int actualPort;

    private final Consumer<String> windowOpener;
    private final SharingService sharingService;
    private final Supplier<IntelFrame.UiState> uiStateProvider;
    private final SchemaRegistry schemaRegistry;

    // Challenge infrastructure (optional, set after construction)
    private ChallengeStore challengeStore;
    private ChallengeExecutor challengeExecutor;

    private static final ObjectMapper challengeMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

    // Handlers
    private final StatsHandler statsHandler;
    private final EntityHandler entityHandler;
    private final DiscoverHandler discoverHandler;
    private final ArticleHandler articleHandler;
    private final SchemaHandler schemaHandler;

    public IntelApiServer(Consumer<String> windowOpener,
                          EntityStore entityStore,
                          SqliteNewsStore newsStore,
                          EntitySearchProcessor searchProcessor,
                          SchemaRegistry schemaRegistry,
                          SharingService sharingService,
                          Supplier<IntelFrame.UiState> uiStateProvider) {
        this.windowOpener = windowOpener;
        this.sharingService = sharingService;
        this.uiStateProvider = uiStateProvider;
        this.schemaRegistry = schemaRegistry;
        this.statsHandler = new StatsHandler(entityStore, newsStore);
        this.entityHandler = new EntityHandler(entityStore, schemaRegistry);
        this.discoverHandler = new DiscoverHandler(entityStore, searchProcessor, schemaRegistry);
        this.articleHandler = new ArticleHandler(newsStore);
        this.schemaHandler = new SchemaHandler(entityStore, schemaRegistry);
    }

    public void setChallengeInfrastructure(ChallengeStore store, ChallengeExecutor executor) {
        this.challengeStore = store;
        this.challengeExecutor = executor;
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
        server.createContext("/schema/type/", schemaHandler::routeSchemaType);
        server.createContext("/schema/type", schemaHandler::routeSchemaType);
        server.createContext("/network", this::handleNetwork);

        // UI control endpoints
        server.createContext("/ui/select-entity", this::handleSelectEntity);
        server.createContext("/ui/switch-view", this::handleSwitchView);
        server.createContext("/ui/views", this::handleViews);
        server.createContext("/context", this::handleContext);
        server.createContext("/logs", this::handleLogs);

        // Challenge endpoints
        server.createContext("/challenges", this::handleChallenges);
        server.createContext("/challenge/", this::handleChallenge);

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
        IntelLogPanel.logInfo("API: Opening window '" + windowName + "'");
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

    private void handleNetwork(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;

        if (sharingService == null) {
            sendJson(exchange, 200, "{\"available\":false}");
            return;
        }

        SharingService.NetworkStatus ns = sharingService.getNetworkStatus();
        if (ns == null) {
            sendJson(exchange, 200, "{\"available\":false}");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"available\":true");
        sb.append(",\"identity\":{");
        sb.append("\"email\":").append(jsonString(ns.email()));
        sb.append(",\"deviceEnrolled\":").append(ns.deviceEnrolled());
        sb.append('}');
        sb.append(",\"server\":{");
        sb.append("\"running\":").append(ns.serverPort() > 0);
        sb.append(",\"port\":").append(ns.serverPort());
        sb.append('}');
        sb.append(",\"portMapping\":{");
        sb.append("\"method\":").append(jsonString(ns.portMapping()));
        sb.append(",\"publicIp\":").append(jsonString(ns.publicIp()));
        sb.append('}');
        sb.append(",\"lan\":{");
        sb.append("\"active\":").append(ns.lanActive());
        sb.append(",\"peerCount\":").append(ns.lanPeerCount());
        sb.append('}');
        sb.append(",\"rendezvous\":{");
        sb.append("\"available\":").append(ns.rendezvousAvailable());
        sb.append('}');
        sb.append(",\"connections\":{");
        sb.append("\"peerCount\":").append(ns.connectedPeers());
        sb.append(",\"deviceCount\":").append(ns.connectedDevices());
        sb.append('}');
        sb.append('}');
        sendJson(exchange, 200, sb.toString());
    }

    // ========== UI Control Handlers ==========

    private void handleSelectEntity(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String id = null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "id".equals(kv[0])) {
                    id = kv[1];
                }
            }
        }

        if (id == null || id.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"Missing 'id' parameter\"}");
            return;
        }

        String entityId = id;
        IntelLogPanel.logInfo("API: Selected entity '" + entityId + "'");
        javax.swing.SwingUtilities.invokeLater(() -> windowOpener.accept("select-entity:" + entityId));
        sendJson(exchange, 200, "{\"ok\":true,\"entityId\":\"" + escape(entityId) + "\"}");
    }

    private void handleSwitchView(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String index = null;
        String name = null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    if ("index".equals(kv[0])) index = kv[1];
                    else if ("name".equals(kv[0])) name = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }

        if (index == null && name == null) {
            sendJson(exchange, 400, "{\"error\":\"Missing 'index' or 'name' parameter\"}");
            return;
        }

        String viewArg = index != null ? index : name;
        IntelLogPanel.logInfo("API: Switched to view '" + viewArg + "'");
        javax.swing.SwingUtilities.invokeLater(() -> windowOpener.accept("view:" + viewArg));
        sendJson(exchange, 200, "{\"ok\":true,\"view\":\"" + escape(viewArg) + "\"}");
    }

    private void handleViews(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;

        List<PanelConfig> panels = IntelConfig.get().getPanels();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"views\":[");
        for (int i = 0; i < panels.size(); i++) {
            if (i > 0) sb.append(',');
            PanelConfig p = panels.get(i);
            sb.append("{\"index\":").append(i);
            sb.append(",\"name\":\"").append(escape(p.getName())).append('"');
            sb.append(",\"type\":\"").append(p.getType().name()).append('"');
            sb.append('}');
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleContext(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;

        IntelLogPanel.logInfo("API: Context requested");

        StringBuilder sb = new StringBuilder();
        sb.append('{');

        // Views with active flag
        List<PanelConfig> panels = IntelConfig.get().getPanels();
        IntelFrame.UiState uiState = uiStateProvider != null ? uiStateProvider.get() : null;
        int activeIndex = uiState != null ? uiState.activeViewIndex() : -1;

        sb.append("\"views\":[");
        for (int i = 0; i < panels.size(); i++) {
            if (i > 0) sb.append(',');
            PanelConfig p = panels.get(i);
            sb.append("{\"index\":").append(i);
            sb.append(",\"name\":\"").append(escape(p.getName())).append('"');
            sb.append(",\"type\":\"").append(p.getType().name()).append('"');
            sb.append(",\"active\":").append(i == activeIndex);
            sb.append('}');
        }
        sb.append(']');

        // Selected entity
        CoinEntity selected = uiState != null ? uiState.selectedEntity() : null;
        if (selected != null) {
            sb.append(",\"selectedEntity\":{");
            sb.append("\"id\":\"").append(escape(selected.id())).append('"');
            sb.append(",\"name\":\"").append(escape(selected.name())).append('"');
            sb.append(",\"type\":\"").append(selected.type().name()).append('"');
            sb.append('}');
        } else {
            sb.append(",\"selectedEntity\":null");
        }

        // Counts from stats handler
        sb.append(",\"entityCount\":").append(statsHandler.getEntityCount());
        sb.append(",\"relationshipCount\":").append(statsHandler.getRelationshipCount());
        sb.append(",\"articleCount\":").append(statsHandler.getArticleCount());

        // Schema
        sb.append(",\"schema\":{");
        appendSchemaJson(sb);
        sb.append('}');

        sb.append('}');
        sendJson(exchange, 200, sb.toString());
    }

    /**
     * GET /logs?lines=50 — Activity Log entries (same as UI panel).
     */
    private void handleLogs(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;

        int limit = 50;
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "lines".equals(kv[0])) {
                    try { limit = Integer.parseInt(kv[1]); } catch (NumberFormatException ignored) {}
                }
            }
        }

        IntelLogPanel panel = IntelLogPanel.getInstance();
        if (panel == null) {
            sendJson(exchange, 200, "{\"entries\":[]}");
            return;
        }

        java.util.List<IntelLogPanel.LogEntry> entries = panel.getEntries(limit);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"entries\":[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(',');
            IntelLogPanel.LogEntry e = entries.get(i);
            sb.append("{\"time\":\"").append(e.time().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))).append('"');
            sb.append(",\"type\":\"").append(e.type().tag()).append('"');
            sb.append(",\"message\":\"").append(escape(e.message())).append('"');
            if (e.hasDetail()) {
                sb.append(",\"detail\":\"").append(escape(e.detail())).append('"');
            }
            sb.append('}');
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void appendSchemaJson(StringBuilder sb) {
        if (schemaRegistry == null) {
            sb.append("\"entityTypes\":[],\"relationshipTypes\":[]");
            return;
        }

        // Entity types
        sb.append("\"entityTypes\":[");
        List<SchemaType> entityTypes = schemaRegistry.entityTypes();
        for (int i = 0; i < entityTypes.size(); i++) {
            if (i > 0) sb.append(',');
            SchemaType t = entityTypes.get(i);
            sb.append("{\"id\":\"").append(escape(t.id())).append('"');
            sb.append(",\"name\":\"").append(escape(t.name())).append('"');
            sb.append(",\"hasMarketCap\":").append(t.hasMarketCap());
            sb.append(",\"attributes\":[");
            for (int j = 0; j < t.attributes().size(); j++) {
                if (j > 0) sb.append(',');
                SchemaAttribute a = t.attributes().get(j);
                sb.append("{\"name\":\"").append(escape(a.name())).append('"');
                sb.append(",\"dataType\":\"").append(escape(a.dataType())).append('"');
                sb.append('}');
            }
            sb.append("]}");
        }
        sb.append(']');

        // Relationship types
        sb.append(",\"relationshipTypes\":[");
        List<SchemaType> relTypes = schemaRegistry.relationshipTypes();
        for (int i = 0; i < relTypes.size(); i++) {
            if (i > 0) sb.append(',');
            SchemaType t = relTypes.get(i);
            sb.append("{\"id\":\"").append(escape(t.id())).append('"');
            sb.append(",\"name\":\"").append(escape(t.name())).append('"');
            if (t.label() != null) sb.append(",\"label\":\"").append(escape(t.label())).append('"');
            if (t.inverseLabel() != null) sb.append(",\"inverseLabel\":\"").append(escape(t.inverseLabel())).append('"');
            if (t.fromTypeId() != null) sb.append(",\"fromType\":\"").append(escape(t.fromTypeId())).append('"');
            if (t.toTypeId() != null) sb.append(",\"toType\":\"").append(escape(t.toTypeId())).append('"');
            if (t.pluralLabel() != null) sb.append(",\"pluralLabel\":\"").append(escape(t.pluralLabel())).append('"');
            if (t.inversePluralLabel() != null) sb.append(",\"inversePluralLabel\":\"").append(escape(t.inversePluralLabel())).append('"');
            if (t.searchDescription() != null) sb.append(",\"searchDescription\":\"").append(escape(t.searchDescription())).append('"');
            if (t.inverseSearchDescription() != null) sb.append(",\"inverseSearchDescription\":\"").append(escape(t.inverseSearchDescription())).append('"');
            sb.append('}');
        }
        sb.append(']');
    }

    // ========== Challenge Handlers ==========

    /**
     * GET  /challenges — list all challenges with latest results.
     * POST /challenges — create a new challenge from JSON body.
     */
    private void handleChallenges(HttpExchange exchange) throws IOException {
        if (challengeStore == null) {
            sendJson(exchange, 503, "{\"error\":\"Challenge infrastructure not available\"}");
            return;
        }
        String method = exchange.getRequestMethod();

        if ("POST".equalsIgnoreCase(method)) {
            // Create challenge
            try {
                byte[] body = exchange.getRequestBody().readAllBytes();
                Challenge challenge = challengeMapper.readValue(body, Challenge.class);
                if (challenge.id() == null || challenge.id().isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Challenge must have an id\"}");
                    return;
                }
                if (challengeStore.getChallenge(challenge.id()) != null) {
                    sendJson(exchange, 409, "{\"error\":\"Challenge already exists: " + escape(challenge.id()) + "\"}");
                    return;
                }
                challengeStore.saveChallenge(challenge);
                String json = challengeMapper.writeValueAsString(challenge);
                sendJson(exchange, 201, "{\"ok\":true,\"challenge\":" + json + "}");
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"Invalid challenge JSON: " + escape(e.getMessage()) + "\"}");
            }
            return;
        }

        if (!"GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 405, "{\"error\":\"Use GET or POST\"}");
            return;
        }

        java.util.List<Challenge> challenges = challengeStore.listChallenges();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"challenges\":[");
        for (int i = 0; i < challenges.size(); i++) {
            if (i > 0) sb.append(',');
            Challenge c = challenges.get(i);
            sb.append("{\"id\":\"").append(escape(c.id())).append('"');
            sb.append(",\"title\":\"").append(escape(c.title())).append('"');
            sb.append(",\"outputType\":\"").append(c.output().type().name()).append('"');
            sb.append(",\"enabled\":").append(c.enabled());

            // Latest result
            ChallengeResult latest = challengeStore.getLatestResult(c.id(), c.id());
            if (latest != null) {
                sb.append(",\"latestResult\":{");
                sb.append("\"timestamp\":").append(latest.timestamp());
                sb.append(",\"outputType\":\"").append(latest.outputType().name()).append('"');
                if (latest.hasError()) {
                    sb.append(",\"error\":\"").append(escape(latest.error())).append('"');
                }
                if (latest.textResult() != null) {
                    sb.append(",\"text\":\"").append(escape(latest.textResult())).append('"');
                }
                if (latest.fields() != null && !latest.fields().isEmpty()) {
                    sb.append(",\"fields\":{");
                    boolean first = true;
                    for (Map.Entry<String, String> entry : latest.fields().entrySet()) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append('"').append(escape(entry.getKey())).append("\":\"")
                            .append(escape(entry.getValue())).append('"');
                    }
                    sb.append('}');
                }
                if (latest.hasSignal()) {
                    sb.append(",\"signal\":").append(latest.signalValue());
                }
                sb.append('}');
            }
            sb.append('}');
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    /**
     * GET    /challenge/{id}         — get full challenge definition as JSON.
     * PUT    /challenge/{id}         — update challenge from JSON body (partial merge).
     * DELETE /challenge/{id}         — delete challenge.
     * POST   /challenge/{id}/run     — execute and return result.
     * GET    /challenge/{id}/results — list results.
     */
    private void handleChallenge(HttpExchange exchange) throws IOException {
        if (challengeStore == null) {
            sendJson(exchange, 503, "{\"error\":\"Challenge infrastructure not available\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length < 3) {
            sendJson(exchange, 400, "{\"error\":\"Missing challenge ID\"}");
            return;
        }
        String challengeId = parts[2];
        String action = parts.length >= 4 ? parts[3] : "";
        String method = exchange.getRequestMethod();

        // DELETE /challenge/{id}
        if ("DELETE".equalsIgnoreCase(method) && action.isEmpty()) {
            Challenge existing = challengeStore.getChallenge(challengeId);
            if (existing == null) {
                sendJson(exchange, 404, "{\"error\":\"Challenge not found: " + escape(challengeId) + "\"}");
                return;
            }
            challengeStore.deleteChallenge(challengeId);
            sendJson(exchange, 200, "{\"ok\":true,\"deleted\":\"" + escape(challengeId) + "\"}");
            return;
        }

        // GET /challenge/{id} — full definition
        if ("GET".equalsIgnoreCase(method) && action.isEmpty()) {
            Challenge challenge = challengeStore.getChallenge(challengeId);
            if (challenge == null) {
                sendJson(exchange, 404, "{\"error\":\"Challenge not found: " + escape(challengeId) + "\"}");
                return;
            }
            String json = challengeMapper.writeValueAsString(challenge);
            sendJson(exchange, 200, json);
            return;
        }

        // PUT /challenge/{id} — update (merge)
        if ("PUT".equalsIgnoreCase(method) && action.isEmpty()) {
            Challenge existing = challengeStore.getChallenge(challengeId);
            if (existing == null) {
                sendJson(exchange, 404, "{\"error\":\"Challenge not found: " + escape(challengeId) + "\"}");
                return;
            }
            try {
                byte[] body = exchange.getRequestBody().readAllBytes();
                Challenge updates = challengeMapper.readValue(body, Challenge.class);
                // Merge non-null fields
                if (updates.title() != null) existing.setTitle(updates.title());
                if (updates.description() != null) existing.setDescription(updates.description());
                if (updates.output() != null) existing.setOutput(updates.output());
                if (updates.escalations() != null && !updates.escalations().isEmpty()) existing.setEscalations(updates.escalations());
                if (updates.signalConfig() != null) existing.setSignalConfig(updates.signalConfig());
                if (updates.refreshInterval() != null) existing.setRefreshInterval(updates.refreshInterval());
                if (updates.displayOrder() != 0) existing.setDisplayOrder(updates.displayOrder());
                // enabled is a primitive — always apply from body
                existing.setEnabled(updates.enabled());
                challengeStore.saveChallenge(existing);
                String json = challengeMapper.writeValueAsString(existing);
                sendJson(exchange, 200, "{\"ok\":true,\"challenge\":" + json + "}");
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"Invalid challenge JSON: " + escape(e.getMessage()) + "\"}");
            }
            return;
        }

        Challenge challenge = challengeStore.getChallenge(challengeId);
        if (challenge == null) {
            sendJson(exchange, 404, "{\"error\":\"Challenge not found: " + escape(challengeId) + "\"}");
            return;
        }

        if ("run".equals(action) && "POST".equalsIgnoreCase(method)) {
            if (challengeExecutor == null) {
                sendJson(exchange, 503, "{\"error\":\"Challenge executor not available\"}");
                return;
            }
            IntelLogPanel.logAI("API: Running challenge '" + challenge.title() + "'");
            StandaloneChallengeSubject subject = new StandaloneChallengeSubject(challenge);
            ChallengeResult prev = challengeStore.getLatestResult(challenge.id(), subject.id());
            ChallengeResult result = challengeExecutor.execute(challenge, subject, 0,
                msg -> IntelLogPanel.logAI(msg), prev);
            challengeStore.saveResult(result);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"result\":{");
            sb.append("\"challengeId\":\"").append(escape(result.challengeId())).append('"');
            sb.append(",\"outputType\":\"").append(result.outputType().name()).append('"');
            sb.append(",\"timestamp\":").append(result.timestamp());
            sb.append(",\"durationMs\":").append(result.durationMs());
            if (result.hasError()) {
                sb.append(",\"error\":\"").append(escape(result.error())).append('"');
            }
            if (result.textResult() != null) {
                sb.append(",\"text\":\"").append(escape(result.textResult())).append('"');
            }
            if (result.fields() != null && !result.fields().isEmpty()) {
                sb.append(",\"fields\":{");
                boolean first = true;
                for (Map.Entry<String, String> entry : result.fields().entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('"').append(escape(entry.getKey())).append("\":\"")
                        .append(escape(entry.getValue())).append('"');
                }
                sb.append('}');
            }
            if (result.itemResults() != null && !result.itemResults().isEmpty()) {
                sb.append(",\"items\":[");
                for (int i = 0; i < result.itemResults().size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append('{');
                    boolean first = true;
                    for (Map.Entry<String, String> entry : result.itemResults().get(i).entrySet()) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append('"').append(escape(entry.getKey())).append("\":\"")
                            .append(escape(entry.getValue())).append('"');
                    }
                    sb.append('}');
                }
                sb.append(']');
            }
            if (result.listResult() != null) {
                sb.append(",\"list\":[");
                for (int i = 0; i < result.listResult().size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append('"').append(escape(result.listResult().get(i))).append('"');
                }
                sb.append(']');
            }
            if (result.hasSignal()) {
                sb.append(",\"signal\":").append(result.signalValue());
            }
            sb.append("}}");
            sendJson(exchange, 200, sb.toString());
        } else if ("results".equals(action) && "GET".equalsIgnoreCase(method)) {
            java.util.List<ChallengeResult> results = challengeStore.getResultsForChallenge(challengeId, 20);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"results\":[");
            for (int i = 0; i < results.size(); i++) {
                if (i > 0) sb.append(',');
                ChallengeResult r = results.get(i);
                sb.append("{\"timestamp\":").append(r.timestamp());
                sb.append(",\"durationMs\":").append(r.durationMs());
                sb.append(",\"outputType\":\"").append(r.outputType().name()).append('"');
                if (r.hasError()) sb.append(",\"error\":\"").append(escape(r.error())).append('"');
                if (r.textResult() != null) sb.append(",\"text\":\"").append(escape(r.textResult())).append('"');
                if (r.fields() != null && !r.fields().isEmpty()) {
                    sb.append(",\"fields\":{");
                    boolean first = true;
                    for (Map.Entry<String, String> entry : r.fields().entrySet()) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append('"').append(escape(entry.getKey())).append("\":\"")
                            .append(escape(entry.getValue())).append('"');
                    }
                    sb.append('}');
                }
                if (r.itemResults() != null && !r.itemResults().isEmpty()) {
                    sb.append(",\"items\":").append(r.itemResults().size());
                }
                if (r.hasSignal()) sb.append(",\"signal\":").append(r.signalValue());
                sb.append('}');
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        } else {
            sendJson(exchange, 405, "{\"error\":\"Use GET/PUT/DELETE /challenge/{id} or POST /challenge/{id}/run or GET /challenge/{id}/results\"}");
        }
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

    private static String jsonString(String s) {
        return s == null ? "null" : "\"" + escape(s) + "\"";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
