package com.tradery;

import com.tradery.api.ApiServer;
import com.tradery.data.AggTradesStore;
import com.tradery.data.CandleStore;
import com.tradery.data.DataConfig;
import com.tradery.data.DataInventory;
import com.tradery.data.DataRequirementsTracker;
import com.tradery.data.FundingRateStore;
import com.tradery.data.OpenInterestStore;
import com.tradery.data.PreloadScheduler;
import com.tradery.io.HoopPatternStore;
import com.tradery.io.PhaseStore;
import com.tradery.io.StrategyStore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Singleton application context holding shared resources.
 * Provides access to shared stores that can be used across multiple windows.
 */
public class ApplicationContext {

    private static ApplicationContext instance;

    private final CandleStore candleStore;
    private final AggTradesStore aggTradesStore;
    private final FundingRateStore fundingRateStore;
    private final OpenInterestStore openInterestStore;
    private final StrategyStore strategyStore;
    private final PhaseStore phaseStore;
    private final HoopPatternStore hoopPatternStore;
    private final ApiServer apiServer;

    // Global tracker for preview data loading (phases, hoops, etc.)
    private final DataRequirementsTracker previewTracker;

    // Data preloading infrastructure
    private final DataInventory dataInventory;
    private final PreloadScheduler preloadScheduler;

    private ApplicationContext() {
        this.candleStore = new CandleStore();
        this.aggTradesStore = new AggTradesStore();
        this.fundingRateStore = new FundingRateStore();
        this.openInterestStore = new OpenInterestStore();
        this.strategyStore = new StrategyStore(new File(TraderyApp.USER_DIR, "strategies"));
        this.phaseStore = new PhaseStore(new File(TraderyApp.USER_DIR, "phases"));
        this.hoopPatternStore = new HoopPatternStore(new File(TraderyApp.USER_DIR, "hoops"));
        this.previewTracker = new DataRequirementsTracker();

        // Initialize data preloading infrastructure
        this.dataInventory = new DataInventory(DataConfig.getInstance().getDataDir());
        this.dataInventory.load();  // Load saved inventory from disk

        this.preloadScheduler = new PreloadScheduler(dataInventory);
        this.preloadScheduler.setStores(candleStore, aggTradesStore, fundingRateStore, openInterestStore);
        this.preloadScheduler.start();

        // Install/update presets on startup
        this.strategyStore.installMissingPresets();
        this.phaseStore.installBuiltInPresets();  // Always update built-in phases

        // Start API server for Claude Code integration
        this.apiServer = new ApiServer(candleStore, aggTradesStore, fundingRateStore, openInterestStore, strategyStore, phaseStore);
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

    public CandleStore getCandleStore() {
        return candleStore;
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
     * Get the global preview tracker for phase/hoop preview data loading.
     * This allows preview windows to share a common tracker visible in status UI.
     */
    public DataRequirementsTracker getPreviewTracker() {
        return previewTracker;
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

    /**
     * Write port file for MCP server discovery.
     * Simple format - just the port number, no auth token needed.
     */
    private void writePortFile() {
        File portFile = new File(TraderyApp.USER_DIR, "api.port");
        try (FileWriter writer = new FileWriter(portFile)) {
            writer.write(String.valueOf(apiServer.getPort()));
        } catch (IOException e) {
            System.err.println("Failed to write port file: " + e.getMessage());
        }
    }
}
