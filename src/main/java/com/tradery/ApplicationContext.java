package com.tradery;

import com.tradery.api.ApiServer;
import com.tradery.data.AggTradesStore;
import com.tradery.data.BinanceVisionClient;
import com.tradery.data.DataConfig;
import com.tradery.data.DataInventory;
import com.tradery.data.FundingRateStore;
import com.tradery.data.OpenInterestStore;
import com.tradery.data.PreloadScheduler;
import com.tradery.data.PremiumIndexStore;
import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.data.page.*;
import com.tradery.io.HoopPatternStore;
import com.tradery.io.PhaseStore;
import com.tradery.io.StrategyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

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


    // Data preloading infrastructure
    private final DataInventory dataInventory;
    private final PreloadScheduler preloadScheduler;

    // Event-driven page managers (clean architecture)
    private final CandlePageManager candlePageManager;
    private final FundingPageManager fundingPageManager;
    private final OIPageManager oiPageManager;
    private final AggTradesPageManager aggTradesPageManager;
    private final PremiumPageManager premiumPageManager;
    private final IndicatorPageManager indicatorPageManager;

    private ApplicationContext() {
        // Initialize SQLite data store
        this.sqliteDataStore = new SqliteDataStore();

        // CSV stores
        this.aggTradesStore = new AggTradesStore();
        this.fundingRateStore = new FundingRateStore();
        this.openInterestStore = new OpenInterestStore();
        this.premiumIndexStore = new PremiumIndexStore();

        // Initialize Binance Vision client (uses SQLite store)
        this.binanceVisionClient = new BinanceVisionClient(sqliteDataStore);

        // Initialize event-driven page managers
        this.candlePageManager = new CandlePageManager(sqliteDataStore);
        this.fundingPageManager = new FundingPageManager(fundingRateStore);
        this.oiPageManager = new OIPageManager(openInterestStore);
        this.aggTradesPageManager = new AggTradesPageManager(aggTradesStore);
        this.premiumPageManager = new PremiumPageManager(premiumIndexStore);
        this.indicatorPageManager = new IndicatorPageManager(
            candlePageManager, fundingPageManager, oiPageManager, aggTradesPageManager, premiumPageManager);

        this.strategyStore = new StrategyStore(new File(TraderyApp.USER_DIR, "strategies"));
        this.phaseStore = new PhaseStore(new File(TraderyApp.USER_DIR, "phases"));
        this.hoopPatternStore = new HoopPatternStore(new File(TraderyApp.USER_DIR, "hoops"));

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

    public IndicatorPageManager getIndicatorPageManager() {
        return indicatorPageManager;
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

        // Close SQLite connections
        if (sqliteDataStore != null) {
            sqliteDataStore.close();
        }

        // Stop API server
        if (apiServer != null) {
            apiServer.stop();
        }

        log.info("ApplicationContext shutdown complete");
    }
}
