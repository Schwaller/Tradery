package com.tradery.forge;

import com.tradery.core.indicators.registry.IndicatorRegistryInitializer;
import com.tradery.core.model.Candle;
import com.tradery.core.model.FundingRate;
import com.tradery.core.model.OpenInterest;
import com.tradery.core.model.PremiumIndex;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.dataclient.DataServiceLauncher;
import com.tradery.dataclient.DataServiceLocator;
import com.tradery.dataclient.page.DataServiceConnection;
import com.tradery.forge.api.ApiServer;
import com.tradery.forge.data.*;
import com.tradery.forge.data.page.*;
import com.tradery.forge.data.sqlite.SqliteDataStore;
import com.tradery.forge.io.HoopPatternStore;
import com.tradery.forge.io.PhaseStore;
import com.tradery.forge.io.StrategyStore;
import com.tradery.symbols.service.SymbolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton application context holding shared resources.
 * Provides access to shared stores that can be used across multiple windows.
 */
public class ApplicationContext {

    private static final Logger log = LoggerFactory.getLogger(ApplicationContext.class);

    private static ApplicationContext instance;

    // SQLite-based data store (primary storage)
    private final SqliteDataStore sqliteDataStore;

    // CSV stores (AggTrades, Funding, OI, Premium)
    private final AggTradesStore aggTradesStore;
    private final FundingRateStore fundingRateStore;
    private final OpenInterestStore openInterestStore;
    private final PremiumIndexStore premiumIndexStore;

    // Binance Vision client for bulk historical downloads
    private final BinanceVisionClient binanceVisionClient;

    private final StrategyStore strategyStore;
    private final PhaseStore phaseStore;
    private final HoopPatternStore hoopPatternStore;
    private final ApiServer apiServer;
    private final SymbolService symbolService;


    // Data preloading infrastructure
    private final DataInventory dataInventory;
    private final PreloadScheduler preloadScheduler;

    // Event-driven page managers (clean architecture)
    private final CandlePageManager candlePageManager;
    private final FundingPageManager fundingPageManager;
    private final OIPageManager oiPageManager;
    private final AggTradesPageManager aggTradesPageManager;
    private final PremiumPageManager premiumPageManager;
    private final FearGreedPageManager fearGreedPageManager;
    private final IndicatorPageManager indicatorPageManager;

    // Data service client (for remote data access)
    private DataServiceClient dataServiceClient;
    private DataServiceConnection dataServiceConnection;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    // Volume profile status tracking for status badge
    public enum ProfileStatus { IDLE, LOADING, READY, ERROR }
    private volatile ProfileStatus profileStatus = ProfileStatus.IDLE;
    private volatile String profileStatusDetail = "";
    private volatile int profileDayCount = 0;

    public ProfileStatus getProfileStatus() { return profileStatus; }
    public String getProfileStatusDetail() { return profileStatusDetail; }
    public int getProfileDayCount() { return profileDayCount; }

    public void setProfileStatus(ProfileStatus status, String detail, int dayCount) {
        this.profileStatus = status;
        this.profileStatusDetail = detail;
        this.profileDayCount = dayCount;
    }

    private ApplicationContext() {
        // Initialize indicator registry (must be done before IndicatorPageManager)
        IndicatorRegistryInitializer.initialize();

        // Initialize SQLite data store
        this.sqliteDataStore = new SqliteDataStore();

        // CSV stores
        this.aggTradesStore = new AggTradesStore();
        this.fundingRateStore = new FundingRateStore();
        this.openInterestStore = new OpenInterestStore();
        this.premiumIndexStore = new PremiumIndexStore();

        // Initialize Binance Vision client (uses SQLite store)
        this.binanceVisionClient = new BinanceVisionClient(sqliteDataStore);

        // Initialize event-driven page managers (all delegate to data service)
        this.candlePageManager = new CandlePageManager();
        this.fundingPageManager = new FundingPageManager();
        this.oiPageManager = new OIPageManager();
        this.aggTradesPageManager = new AggTradesPageManager();
        this.premiumPageManager = new PremiumPageManager();
        this.fearGreedPageManager = new FearGreedPageManager();
        this.indicatorPageManager = new IndicatorPageManager(
            candlePageManager, aggTradesPageManager);

        this.strategyStore = new StrategyStore(new File(TraderyApp.USER_DIR, "strategies"));
        this.phaseStore = new PhaseStore(new File(TraderyApp.USER_DIR, "phases"));
        this.hoopPatternStore = new HoopPatternStore(new File(TraderyApp.USER_DIR, "hoops"));
        this.symbolService = new SymbolService();

        // Initialize data preloading infrastructure
        this.dataInventory = new DataInventory(DataConfig.getInstance().getDataDir());
        this.dataInventory.load();

        this.preloadScheduler = new PreloadScheduler(dataInventory);
        this.preloadScheduler.setStores(sqliteDataStore, aggTradesStore, fundingRateStore, openInterestStore);
        this.preloadScheduler.start();

        // Install/update presets on startup
        this.strategyStore.installMissingPresets();
        this.phaseStore.installBuiltInPresets();

        // Start API server for Claude Code integration
        this.apiServer = new ApiServer(sqliteDataStore, aggTradesStore, fundingRateStore, openInterestStore, strategyStore, phaseStore);
        try {
            apiServer.start();
            writePortFile();
        } catch (IOException e) {
            System.err.println("Failed to start API server: " + e.getMessage());
        }

        // Initialize data service client if service is running
        initDataServiceClient();
    }

    /**
     * Initialize the data service client if the service is available.
     */
    private void initDataServiceClient() {
        DataServiceLauncher launcher = TraderyApp.getDataServiceLauncher();
        if (launcher != null && launcher.getPort() > 0) {
            int port = launcher.getPort();
            this.dataServiceClient = new DataServiceClient("localhost", port);
            if (dataServiceClient.isHealthy()) {
                log.info("DataServiceClient connected to port {}", port);

                // Create and connect WebSocket for push-based data delivery
                this.dataServiceConnection = new DataServiceConnection(
                    "localhost", port,
                    launcher.getConsumerId(), launcher.getConsumerName());
                dataServiceConnection.connect();
                dataServiceClient.setConnection(dataServiceConnection);

                log.info("DataServiceConnection established for WS push delivery");
            } else {
                log.warn("DataServiceClient created but service not healthy");
                this.dataServiceClient = null;
            }
        } else {
            log.info("Data service not available, using local data access only");
        }
    }

    /**
     * Attempt to reconnect to the data service.
     * Called periodically when the service is detected as offline.
     * @return true if reconnection succeeded
     */
    public boolean tryReconnectDataService() {
        if (!reconnecting.compareAndSet(false, true)) {
            return false; // already attempting
        }
        try {
            DataServiceLauncher launcher = TraderyApp.getDataServiceLauncher();
            if (launcher == null) {
                return false;
            }

            // 1. Try the launcher's known port (fast path — service restarted on same port)
            int port = launcher.getPort();
            if (port > 0) {
                DataServiceClient client = new DataServiceClient("localhost", port);
                if (client.isHealthy()) {
                    return connectClient(launcher, client, port);
                }
            }

            // 2. Locate a running service via port file or default port
            //    Covers the case where the service was started externally (e.g. via Gradle)
            var found = DataServiceLocator.findRunningService();
            if (found.isPresent()) {
                port = found.get();
                DataServiceClient client = new DataServiceClient("localhost", port);
                if (client.isHealthy()) {
                    return connectClient(launcher, client, port);
                }
            }

            return false;
        } finally {
            reconnecting.set(false);
        }
    }

    private boolean connectClient(DataServiceLauncher launcher, DataServiceClient client, int port) {
        log.info("Data service reconnected on port {}", port);

        // Disconnect old WebSocket if present
        if (dataServiceConnection != null) {
            dataServiceConnection.disconnect();
        }

        this.dataServiceClient = client;

        // Establish new WebSocket connection
        this.dataServiceConnection = new DataServiceConnection(
            "localhost", port,
            launcher.getConsumerId(), launcher.getConsumerName());
        dataServiceConnection.connect();
        dataServiceClient.setConnection(dataServiceConnection);

        log.info("DataServiceConnection re-established for WS push delivery");
        return true;
    }

    public static synchronized ApplicationContext getInstance() {
        if (instance == null) {
            instance = new ApplicationContext();
        }
        return instance;
    }

    /**
     * Get the SQLite data store (primary storage).
     */
    public SqliteDataStore getSqliteDataStore() {
        return sqliteDataStore;
    }

    public AggTradesStore getAggTradesStore() {
        return aggTradesStore;
    }

    public FundingRateStore getFundingRateStore() {
        return fundingRateStore;
    }

    public OpenInterestStore getOpenInterestStore() {
        return openInterestStore;
    }

    public PremiumIndexStore getPremiumIndexStore() {
        return premiumIndexStore;
    }

    /**
     * Get the Binance Vision client for bulk historical downloads.
     */
    public BinanceVisionClient getBinanceVisionClient() {
        return binanceVisionClient;
    }

    public StrategyStore getStrategyStore() {
        return strategyStore;
    }

    public PhaseStore getPhaseStore() {
        return phaseStore;
    }

    public HoopPatternStore getHoopPatternStore() {
        return hoopPatternStore;
    }

    public ApiServer getApiServer() {
        return apiServer;
    }

    public SymbolService getSymbolService() {
        return symbolService;
    }


    /**
     * Get the data inventory for tracking cached data coverage.
     */
    public DataInventory getDataInventory() {
        return dataInventory;
    }

    /**
     * Get the preload scheduler for background data loading.
     */
    public PreloadScheduler getPreloadScheduler() {
        return preloadScheduler;
    }

    // ========== Page Managers ==========

    public CandlePageManager getCandlePageManager() {
        return candlePageManager;
    }

    public FundingPageManager getFundingPageManager() {
        return fundingPageManager;
    }

    public OIPageManager getOIPageManager() {
        return oiPageManager;
    }

    public AggTradesPageManager getAggTradesPageManager() {
        return aggTradesPageManager;
    }

    public PremiumPageManager getPremiumPageManager() {
        return premiumPageManager;
    }

    public FearGreedPageManager getFearGreedPageManager() {
        return fearGreedPageManager;
    }

    public IndicatorPageManager getIndicatorPageManager() {
        return indicatorPageManager;
    }

    /**
     * Get the data service client for remote data access.
     * @return the client, or null if service is not available
     */
    public DataServiceClient getDataServiceClient() {
        return dataServiceClient;
    }

    /**
     * Check if the data service is available.
     */
    public boolean isDataServiceAvailable() {
        return dataServiceClient != null && dataServiceClient.isHealthy();
    }

    /**
     * Write port file for MCP server discovery.
     */
    private void writePortFile() {
        File portFile = new File(TraderyApp.USER_DIR, "api.port");
        try (FileWriter writer = new FileWriter(portFile)) {
            writer.write(String.valueOf(apiServer.getPort()));
        } catch (IOException e) {
            System.err.println("Failed to write port file: " + e.getMessage());
        }
    }

    /**
     * Shutdown the application context.
     */
    public void shutdown() {
        log.info("Shutting down ApplicationContext...");

        // Stop preload scheduler
        if (preloadScheduler != null) {
            preloadScheduler.shutdown();
        }

        // Stop page managers
        if (indicatorPageManager != null) {
            indicatorPageManager.shutdown();
        }
        if (candlePageManager != null) {
            candlePageManager.shutdown();
        }
        if (fundingPageManager != null) {
            fundingPageManager.shutdown();
        }
        if (oiPageManager != null) {
            oiPageManager.shutdown();
        }
        if (aggTradesPageManager != null) {
            aggTradesPageManager.shutdown();
        }
        if (premiumPageManager != null) {
            premiumPageManager.shutdown();
        }
        if (fearGreedPageManager != null) {
            fearGreedPageManager.shutdown();
        }

        // Close WebSocket connection
        if (dataServiceConnection != null) {
            dataServiceConnection.disconnect();
        }

        // Close SQLite connections
        if (sqliteDataStore != null) {
            sqliteDataStore.close();
        }

        // Close symbols database
        if (symbolService != null) {
            symbolService.close();
        }

        // Stop API server
        if (apiServer != null) {
            apiServer.stop();
        }

        log.info("ApplicationContext shutdown complete");
    }
}
