package com.tradery;

import com.tradery.data.CandleStore;
import com.tradery.io.StrategyStore;

import java.io.File;

/**
 * Singleton application context holding shared resources.
 * Provides access to shared stores that can be used across multiple windows.
 */
public class ApplicationContext {

    private static ApplicationContext instance;

    private final CandleStore candleStore;
    private final StrategyStore strategyStore;

    private ApplicationContext() {
        this.candleStore = new CandleStore();
        this.strategyStore = new StrategyStore(new File(TraderyApp.USER_DIR, "strategies"));

        // Install any missing preset strategies on startup
        this.strategyStore.installMissingPresets();
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

    public StrategyStore getStrategyStore() {
        return strategyStore;
    }
}
