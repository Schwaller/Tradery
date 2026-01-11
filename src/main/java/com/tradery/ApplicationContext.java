package com.tradery;

import com.tradery.api.ApiServer;
import com.tradery.data.AggTradesStore;
import com.tradery.data.CandleStore;
import com.tradery.data.FundingRateStore;
import com.tradery.data.OpenInterestStore;
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

    private ApplicationContext() {
        this.candleStore = new CandleStore();
        this.aggTradesStore = new AggTradesStore();
        this.fundingRateStore = new FundingRateStore();
        this.openInterestStore = new OpenInterestStore();
        this.strategyStore = new StrategyStore(new File(TraderyApp.USER_DIR, "strategies"));
        this.phaseStore = new PhaseStore(new File(TraderyApp.USER_DIR, "phases"));
        this.hoopPatternStore = new HoopPatternStore(new File(TraderyApp.USER_DIR, "hoops"));

        // Install/update presets on startup
        this.strategyStore.installMissingPresets();
        this.phaseStore.installBuiltInPresets();  // Always update built-in phases

        // Start API server for Claude Code integration
        this.apiServer = new ApiServer(candleStore, aggTradesStore, fundingRateStore, openInterestStore, strategyStore, phaseStore);
        try {
            apiServer.start();
            writeApiMarkerFile();
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
     * Write api.json marker file with port and session token.
     * Claude Code reads this to connect to the running instance.
     */
    private void writeApiMarkerFile() {
        File apiFile = new File(TraderyApp.USER_DIR, "api.json");
        try (FileWriter writer = new FileWriter(apiFile)) {
            writer.write("{\n");
            writer.write("  \"port\": " + apiServer.getPort() + ",\n");
            writer.write("  \"token\": \"" + apiServer.getSessionToken() + "\",\n");
            writer.write("  \"baseUrl\": \"http://localhost:" + apiServer.getPort() + "\"\n");
            writer.write("}\n");
            System.out.println("API marker file written: " + apiFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write API marker file: " + e.getMessage());
        }
    }
}
