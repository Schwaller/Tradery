package com.tradery.indicators.registry;

import com.tradery.indicators.*;
import com.tradery.indicators.registry.specs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes the indicator registry with all available indicators.
 * Called once at application startup.
 */
public final class IndicatorRegistryInitializer {

    private static final Logger log = LoggerFactory.getLogger(IndicatorRegistryInitializer.class);
    private static volatile boolean initialized = false;

    private IndicatorRegistryInitializer() {}

    /**
     * Initialize the registry with all indicators.
     * Safe to call multiple times - only initializes once.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        IndicatorRegistry registry = IndicatorRegistry.getInstance();

        log.info("Initializing indicator registry...");

        // ===== NEW: Register indicator classes directly =====
        registry.registerAll(
            // Simple indicators
            SMA.INSTANCE,
            EMA.INSTANCE,
            RSI.INSTANCE,
            ATR.INSTANCE,

            // ADX family
            ADX.INSTANCE,
            ADX.PLUS_DI,
            ADX.MINUS_DI,

            // Composite indicators
            MACD.INSTANCE,
            BollingerBands.INSTANCE,
            Stochastic.INSTANCE,

            // Complex indicators
            Ichimoku.INSTANCE,
            Supertrend.INSTANCE
        );

        // ===== LEGACY: Specs (for indicators not yet migrated) =====

        // Simple indicators (some may duplicate - specs map takes precedence for now)
        SimpleIndicatorSpecs.registerAll(registry);

        // Composite indicators
        CompositeIndicatorSpecs.registerAll(registry);

        // Complex indicators (Rays, etc.)
        ComplexIndicatorSpecs.registerAll(registry);

        // Orderflow indicators (Delta, CVD, Whale, etc.)
        OrderflowIndicatorSpecs.registerAll(registry);

        // OHLCV volume indicators
        OhlcvVolumeIndicatorSpecs.registerAll(registry);

        initialized = true;
        log.info("Indicator registry initialized with {} indicators", registry.size());
    }

    /**
     * Check if registry has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Force re-initialization (mainly for testing).
     */
    public static synchronized void reset() {
        IndicatorRegistry.getInstance().clear();
        initialized = false;
    }
}
