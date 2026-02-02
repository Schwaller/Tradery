package com.tradery.dataservice.api;

import com.tradery.dataservice.coingecko.CoinGeckoClient;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.SymbolsConnection;
import com.tradery.dataservice.ConsumerRegistry;
import com.tradery.dataservice.config.DataServiceConfig;
import com.tradery.dataservice.live.LiveAggTradeManager;
import com.tradery.dataservice.live.LiveCandleManager;
import com.tradery.dataservice.live.LiveMarkPriceManager;
import com.tradery.dataservice.live.LiveOpenInterestPoller;
import com.tradery.dataservice.page.PageManager;
import com.tradery.dataservice.symbols.SymbolSyncService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import io.javalin.websocket.WsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * HTTP/WebSocket server for the Data Service API.
 * Provides endpoints for page lifecycle, data access, and real-time updates.
 */
public class DataServiceServer {
    private static final Logger LOG = LoggerFactory.getLogger(DataServiceServer.class);

    private final DataServiceConfig config;
    private final PageManager pageManager;
    private final ConsumerRegistry consumerRegistry;
    private final ObjectMapper objectMapper;
    private final LiveCandleManager liveCandleManager;
    private final LiveAggTradeManager liveAggTradeManager;
    private final LiveMarkPriceManager liveMarkPriceManager;
    private final LiveOpenInterestPoller liveOpenInterestPoller;
    private final WebSocketHandler webSocketHandler;
    private final SymbolHandler symbolHandler;
    private Javalin app;

    public DataServiceServer(DataServiceConfig config, ConsumerRegistry consumerRegistry, SqliteDataStore dataStore,
                             SymbolSyncService symbolSyncService, SymbolsConnection symbolsConnection,
                             CoinGeckoClient coingeckoClient) {
        this.config = config;
        this.consumerRegistry = consumerRegistry;
        this.pageManager = new PageManager(config, dataStore);
        this.objectMapper = createObjectMapper();
        this.liveCandleManager = new LiveCandleManager();
        this.liveAggTradeManager = new LiveAggTradeManager();
        this.liveMarkPriceManager = new LiveMarkPriceManager();
        this.liveOpenInterestPoller = new LiveOpenInterestPoller();
        this.webSocketHandler = new WebSocketHandler(pageManager, liveCandleManager,
            liveAggTradeManager, liveMarkPriceManager, liveOpenInterestPoller, objectMapper);
        this.symbolHandler = new SymbolHandler(symbolSyncService, symbolsConnection, coingeckoClient);
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    public void start() {
        app = Javalin.create(javalinConfig -> {
            javalinConfig.jsonMapper(new JavalinJackson(objectMapper, true));
            javalinConfig.showJavalinBanner = false;
        });

        // Configure routes
        configureConsumerRoutes();
        configurePageRoutes();
        configureDataRoutes();
        configureCoverageRoutes();
        configureSymbolRoutes();
        configureWebSocket();
        configureHealthRoutes();

        // Start server
        app.start(config.getPort());
    }

    public int getActivePageCount() {
        return pageManager.getActivePageCount();
    }

    public int getLiveCandleCount() {
        return liveCandleManager.getConnectionCount();
    }

    public int getLiveAggTradeCount() {
        return liveAggTradeManager.getConnectionCount();
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
        pageManager.shutdown();
        liveCandleManager.shutdown();
        liveAggTradeManager.shutdown();
        liveMarkPriceManager.shutdown();
        liveOpenInterestPoller.shutdown();
    }

    /**
     * Consumer registration and heartbeat endpoints.
     * Apps must register on startup and send periodic heartbeats.
     */
    private void configureConsumerRoutes() {
        // Register a consumer (app)
        app.post("/consumers/register", ctx -> {
            RegisterRequest req = ctx.bodyAsClass(RegisterRequest.class);
            consumerRegistry.register(req.consumerId(), req.consumerName(), req.pid());
            ctx.json(new RegisterResponse(true, "Registered"));
        });

        // Unregister a consumer
        app.post("/consumers/unregister", ctx -> {
            UnregisterRequest req = ctx.bodyAsClass(UnregisterRequest.class);
            consumerRegistry.unregister(req.consumerId());
            ctx.json(new RegisterResponse(true, "Unregistered"));
        });

        // Heartbeat from a consumer
        app.post("/consumers/heartbeat", ctx -> {
            HeartbeatRequest req = ctx.bodyAsClass(HeartbeatRequest.class);
            consumerRegistry.heartbeat(req.consumerId());
            ctx.json(new HeartbeatResponse(true));
        });

        // Get consumer count
        app.get("/consumers/count", ctx -> {
            ctx.json(new ConsumerCountResponse(consumerRegistry.getConsumerCount()));
        });
    }

    private void configurePageRoutes() {
        PageHandler pageHandler = new PageHandler(pageManager);

        // Page lifecycle
        app.post("/pages/request", pageHandler::requestPage);
        app.post("/pages/batch-request", pageHandler::batchRequestPages);
        app.delete("/pages/{key}", pageHandler::releasePage);
        app.get("/pages/{key}/status", pageHandler::getPageStatus);
        app.get("/pages/{key}/data", pageHandler::getPageData);
        app.get("/pages/status", pageHandler::getAllPagesStatus);
    }

    private void configureDataRoutes() {
        DataHandler dataHandler = new DataHandler(pageManager);

        // Direct data access
        app.get("/candles", dataHandler::getCandles);
        app.get("/aggtrades", dataHandler::getAggTrades);
        app.get("/funding", dataHandler::getFunding);
        app.get("/openinterest", dataHandler::getOpenInterest);
        app.get("/premium", dataHandler::getPremium);
    }

    private void configureCoverageRoutes() {
        CoverageHandler coverageHandler = new CoverageHandler(pageManager);

        app.get("/coverage", coverageHandler::getCoverage);
        app.get("/coverage/symbols", coverageHandler::getAvailableSymbols);
    }

    private void configureSymbolRoutes() {
        // Symbol resolution endpoints
        app.get("/symbols/resolve", symbolHandler::resolve);
        app.get("/symbols/reverse", symbolHandler::reverse);
        app.get("/symbols/search", symbolHandler::search);
        app.post("/symbols/sync", symbolHandler::sync);
        app.get("/symbols/stats", symbolHandler::stats);
        app.get("/symbols/exchanges", symbolHandler::exchanges);
    }

    private void configureWebSocket() {
        Consumer<WsConfig> wsConfigConsumer = wsConfig -> {
            wsConfig.onConnect(webSocketHandler::onConnect);
            wsConfig.onMessage(webSocketHandler::onMessage);
            wsConfig.onClose(webSocketHandler::onClose);
            wsConfig.onError(webSocketHandler::onError);
        };

        app.ws("/subscribe", wsConfigConsumer);
    }

    private void configureHealthRoutes() {
        app.get("/health", ctx -> ctx.json(new HealthResponse("ok",
            pageManager.getActivePageCount(), consumerRegistry.getConsumerCount())));
        app.get("/", ctx -> ctx.json(new ServiceInfo("Tradery Data Service", "1.0.0", config.getPort())));
        app.get("/logs", this::handleLogs);
    }

    /**
     * GET /logs?lines=N
     * Returns the last N lines (default 200, max 1000) from the in-memory log buffer.
     */
    private void handleLogs(Context ctx) {
        int lines = Math.min(Math.max(ctx.queryParamAsClass("lines", Integer.class).getOrDefault(200), 1), 1000);
        var buffer = com.tradery.dataservice.log.InMemoryLogBuffer.getInstance();
        var tail = buffer.getLastLines(lines);
        ctx.json(java.util.Map.of("lines", tail, "buffered", buffer.size(), "returned", tail.size()));
    }

    // Request/Response records
    public record RegisterRequest(String consumerId, String consumerName, int pid) {}
    public record UnregisterRequest(String consumerId) {}
    public record HeartbeatRequest(String consumerId) {}
    public record RegisterResponse(boolean success, String message) {}
    public record HeartbeatResponse(boolean success) {}
    public record ConsumerCountResponse(int count) {}
    public record HealthResponse(String status, int activePages, int consumers) {}
    public record ServiceInfo(String name, String version, int port) {}
}
