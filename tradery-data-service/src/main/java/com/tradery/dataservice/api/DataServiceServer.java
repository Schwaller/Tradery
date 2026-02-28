package com.tradery.dataservice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.dataservice.ConsumerRegistry;
import com.tradery.dataservice.coingecko.CoinGeckoClient;
import com.tradery.dataservice.config.DataServiceConfig;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.SymbolsConnection;
import com.tradery.dataservice.live.LiveAggTradeManager;
import com.tradery.dataservice.live.LiveCandleManager;
import com.tradery.dataservice.live.LiveMarkPriceManager;
import com.tradery.dataservice.live.LiveOpenInterestPoller;
import com.tradery.dataservice.news.NewsManager;
import com.tradery.dataservice.page.PageManager;
import com.tradery.dataservice.profile.TickSizeResolver;
import com.tradery.dataservice.profile.VolumeProfileAnalyzer;
import com.tradery.dataservice.profile.VolumeProfileComputer;
import com.tradery.dataservice.symbols.SymbolSyncService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import io.javalin.websocket.WsConfig;
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
    private final SqliteDataStore dataStore;
    private final WebSocketHandler webSocketHandler;
    private final SymbolHandler symbolHandler;
    private final InventoryHandler inventoryHandler;
    private final TickSizeResolver tickSizeResolver;
    private final VolumeProfileComputer profileComputer;
    private final VolumeProfileAnalyzer profileAnalyzer;
    private NewsManager newsManager;
    private Javalin app;

    public DataServiceServer(DataServiceConfig config, ConsumerRegistry consumerRegistry, SqliteDataStore dataStore,
                             SymbolSyncService symbolSyncService, SymbolsConnection symbolsConnection,
                             CoinGeckoClient coingeckoClient) {
        this.config = config;
        this.consumerRegistry = consumerRegistry;
        this.dataStore = dataStore;
        this.objectMapper = createObjectMapper();
        this.liveCandleManager = new LiveCandleManager();
        this.liveAggTradeManager = new LiveAggTradeManager();
        this.liveMarkPriceManager = new LiveMarkPriceManager();
        this.liveOpenInterestPoller = new LiveOpenInterestPoller();
        // PageManager gets LiveCandleManager for live page support
        this.pageManager = new PageManager(config, dataStore, liveCandleManager);
        this.webSocketHandler = new WebSocketHandler(pageManager, consumerRegistry,
            liveCandleManager, liveAggTradeManager, liveMarkPriceManager, liveOpenInterestPoller,
            pageManager.getAggTradesStore(), objectMapper);
        this.symbolHandler = new SymbolHandler(symbolSyncService, symbolsConnection, coingeckoClient);
        this.inventoryHandler = new InventoryHandler(dataStore);
        this.tickSizeResolver = new TickSizeResolver(symbolsConnection);
        this.profileComputer = new VolumeProfileComputer(dataStore, tickSizeResolver);
        this.profileAnalyzer = new VolumeProfileAnalyzer();
        this.pageManager.setProfileComputer(profileComputer);
    }

    public void setNewsManager(NewsManager newsManager) {
        this.newsManager = newsManager;
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
        configurePageRoutes();
        configureDataRoutes();
        configureProfileRoutes();
        configureInventoryRoutes();
        configureCoverageRoutes();
        configureSymbolRoutes();
        configureWebSocket();
        configureNewsRoutes();
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

        // AggTrades must use direct streaming (page system returns null — data too large for memory)
        app.get("/aggtrades", dataHandler::getAggTrades);
    }

    private void configureProfileRoutes() {
        ProfileHandler profileHandler = new ProfileHandler(dataStore, tickSizeResolver, profileComputer, profileAnalyzer);

        app.get("/profile", profileHandler::getProfile);
        app.get("/profile/binned", profileHandler::getBinnedProfile);
        app.get("/profile/poc-series", profileHandler::getPocSeries);
        app.get("/profile/daily-levels", profileHandler::getDailyLevels);
    }

    private void configureInventoryRoutes() {
        app.get("/inventory", inventoryHandler::getInventory);
        app.get("/inventory/disk-usage", inventoryHandler::getDiskUsage);
        app.delete("/data", inventoryHandler::deleteData);
    }

    private void configureCoverageRoutes() {
        CoverageHandler coverageHandler = new CoverageHandler(pageManager, dataStore);

        app.get("/coverage", coverageHandler::getCoverage);
        app.get("/coverage/ranges", coverageHandler::getCoverageRanges);
        app.get("/coverage/symbols", coverageHandler::getAvailableSymbols);
    }

    private void configureSymbolRoutes() {
        // Symbol resolution endpoints
        app.get("/symbols/resolve", symbolHandler::resolve);
        app.get("/symbols/reverse", symbolHandler::reverse);
        app.get("/symbols/search", symbolHandler::search);
        app.post("/symbols/sync", symbolHandler::sync);
        app.get("/symbols/stats", symbolHandler::stats);
        app.get("/symbols/categories", symbolHandler::categories);
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

    private void configureNewsRoutes() {
        if (newsManager == null) {
            LOG.warn("NewsManager not set, skipping news routes");
            return;
        }
        NewsHandler newsHandler = new NewsHandler(newsManager);

        app.get("/news/articles", newsHandler::getArticles);
        app.get("/news/sources", newsHandler::getSources);
        app.post("/news/sources", newsHandler::addSource);
        app.delete("/news/sources/{id}", newsHandler::deleteSource);
        app.put("/news/sources/{id}", newsHandler::updateSource);
        app.post("/news/poll", newsHandler::triggerPoll);
    }

    private void configureHealthRoutes() {
        app.get("/health", ctx -> ctx.json(new HealthResponse("ok",
            pageManager.getActivePageCount(), consumerRegistry.getConsumerCount())));
        app.get("/", ctx -> ctx.json(new ServiceInfo("Plaiiin Data Service", "1.0.0", config.getPort())));
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
    public record HealthResponse(String status, int activePages, int consumers) {}
    public record ServiceInfo(String name, String version, int port) {}
}
