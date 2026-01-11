package com.tradery;

import com.tradery.data.AggTradesStore;
import com.tradery.data.CandleStore;
import com.tradery.io.HoopPatternStore;
import com.tradery.io.PhaseStore;
import com.tradery.io.StrategyStore;

import java.io.File;

/**
 * Singleton application context holding shared resources.
 * Provides access to shared stores that can be used across multiple windows.
 */
public class ApplicationContext {

    private static ApplicationContext instance;

    private final CandleStore candleStore;
    private final AggTradesStore aggTradesStore;
    private final StrategyStore strategyStore;
    private final PhaseStore phaseStore;
    private final HoopPatternStore hoopPatternStore;

    private ApplicationContext() {
        this.candleStore = new CandleStore();
        this.aggTradesStore = new AggTradesStore();
        this.strategyStore = new StrategyStore(new File(TraderyApp.USER_DIR, "strategies"));
        this.phaseStore = new PhaseStore(new File(TraderyApp.USER_DIR, "phases"));
        this.hoopPatternStore = new HoopPatternStore(new File(TraderyApp.USER_DIR, "hoops"));

        // Install/update presets on startup
        this.strategyStore.installMissingPresets();
        this.phaseStore.installBuiltInPresets();  // Always update built-in phases
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

    public StrategyStore getStrategyStore() {
        return strategyStore;
    }

    public PhaseStore getPhaseStore() {
        return phaseStore;
    }

    public HoopPatternStore getHoopPatternStore() {
        return hoopPatternStore;
    }
}
