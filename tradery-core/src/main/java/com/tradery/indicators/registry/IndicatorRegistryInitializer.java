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

        // ===== Simple Indicators =====
        registry.registerAll(
            SMA.INSTANCE,
            EMA.INSTANCE,
            RSI.INSTANCE,
            ATR.INSTANCE
        );

        // ===== ADX Family =====
        registry.registerAll(
            ADX.INSTANCE,
            ADX.PLUS_DI,
            ADX.MINUS_DI
        );

        // ===== Composite Indicators =====
        registry.registerAll(
            MACD.INSTANCE,
            BollingerBands.INSTANCE,
            Stochastic.INSTANCE
        );

        // ===== Complex Indicators =====
        registry.registerAll(
            Ichimoku.INSTANCE,
            Supertrend.INSTANCE,
            RotatingRays.RESISTANCE_RAYS,
            RotatingRays.SUPPORT_RAYS
        );

        // ===== Orderflow Indicators =====
        registry.registerAll(
            OrderflowIndicators.DELTA,
            OrderflowIndicators.CUM_DELTA,
            OrderflowIndicators.WHALE_DELTA,
            OrderflowIndicators.RETAIL_DELTA,
            OrderflowIndicators.AGG_BUY_VOLUME,
            OrderflowIndicators.AGG_SELL_VOLUME,
            OrderflowIndicators.AGG_TRADE_COUNT,
            OrderflowIndicators.LARGE_TRADE_COUNT,
            OrderflowIndicators.WHALE_BUY_VOLUME,
            OrderflowIndicators.WHALE_SELL_VOLUME
        );

        // ===== OHLCV Volume Indicators =====
        registry.registerAll(
            OhlcvVolumeIndicators.QUOTE_VOLUME,
            OhlcvVolumeIndicators.TAKER_BUY_VOLUME,
            OhlcvVolumeIndicators.TAKER_SELL_VOLUME,
            OhlcvVolumeIndicators.OHLCV_DELTA,
            OhlcvVolumeIndicators.OHLCV_CVD,
            OhlcvVolumeIndicators.BUY_RATIO
        );

        // ===== LEGACY: Specs (for non-migrated indicators) =====
        // These will be replaced as more indicators are migrated

        // Simple indicators (some may duplicate - new indicators take precedence)
        SimpleIndicatorSpecs.registerAll(registry);

        // Composite indicators
        CompositeIndicatorSpecs.registerAll(registry);

        // Complex indicators (Daily Volume Profile not yet migrated)
        ComplexIndicatorSpecs.registerAll(registry);

        // Orderflow specs (Daily Volume Profile)
        OrderflowIndicatorSpecs.registerAll(registry);

        // OHLCV volume specs
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
