package com.tradery.indicators.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all indicator specifications.
 *
 * Thread-safe singleton that holds all indicator definitions.
 * Both IndicatorPageManager (async/charts) and IndicatorEngine (sync/backtest) use this.
 */
public final class IndicatorRegistry {

    private static final Logger log = LoggerFactory.getLogger(IndicatorRegistry.class);
    private static final IndicatorRegistry INSTANCE = new IndicatorRegistry();

    private final Map<String, IndicatorSpec<?>> specs = new ConcurrentHashMap<>();

    private IndicatorRegistry() {
        // Singleton - use getInstance()
    }

    /**
     * Get the singleton instance.
     */
    public static IndicatorRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register an indicator spec.
     * Overwrites any existing spec with the same ID.
     *
     * @param spec The indicator specification
     */
    public void register(IndicatorSpec<?> spec) {
        specs.put(spec.id(), spec);
        log.debug("Registered indicator: {}", spec.id());
    }

    /**
     * Register multiple specs at once.
     */
    public void registerAll(IndicatorSpec<?>... specsToRegister) {
        for (IndicatorSpec<?> spec : specsToRegister) {
            register(spec);
        }
    }

    /**
     * Get a spec by ID.
     *
     * @param id Indicator ID (e.g., "RSI", "MACD")
     * @return The spec, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> IndicatorSpec<T> get(String id) {
        return (IndicatorSpec<T>) specs.get(id);
    }

    /**
     * Get a spec by ID, throwing if not found.
     */
    public <T> IndicatorSpec<T> getOrThrow(String id) {
        IndicatorSpec<T> spec = get(id);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown indicator: " + id);
        }
        return spec;
    }

    /**
     * Check if an indicator is registered.
     */
    public boolean contains(String id) {
        return specs.containsKey(id);
    }

    /**
     * Get all registered indicator IDs.
     */
    public Collection<String> getIds() {
        return specs.keySet();
    }

    /**
     * Get all registered specs.
     */
    public Collection<IndicatorSpec<?>> getAll() {
        return specs.values();
    }

    /**
     * Get count of registered indicators.
     */
    public int size() {
        return specs.size();
    }

    /**
     * Clear all registrations (mainly for testing).
     */
    public void clear() {
        specs.clear();
    }
}
