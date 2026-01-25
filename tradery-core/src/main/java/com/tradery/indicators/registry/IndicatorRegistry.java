package com.tradery.indicators.registry;

import com.tradery.indicators.Indicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all indicators.
 *
 * Thread-safe singleton. Both IndicatorPageManager (async/charts) and
 * IndicatorEngine (sync/backtest) use this.
 *
 * Registration is simple: registry.register(SMA.INSTANCE)
 */
public final class IndicatorRegistry {

    private static final Logger log = LoggerFactory.getLogger(IndicatorRegistry.class);
    private static final IndicatorRegistry INSTANCE = new IndicatorRegistry();

    private final Map<String, Indicator<?>> indicators = new ConcurrentHashMap<>();

    // Backward compatibility - also accept IndicatorSpec
    private final Map<String, IndicatorSpec<?>> specs = new ConcurrentHashMap<>();

    private IndicatorRegistry() {}

    public static IndicatorRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register an indicator.
     */
    public void register(Indicator<?> indicator) {
        indicators.put(indicator.id(), indicator);
        log.debug("Registered indicator: {}", indicator.id());
    }

    /**
     * Register an indicator spec (backward compatibility).
     */
    public void register(IndicatorSpec<?> spec) {
        specs.put(spec.id(), spec);
        log.debug("Registered spec: {}", spec.id());
    }

    /**
     * Register multiple indicators.
     */
    public void registerAll(Indicator<?>... indicatorsToRegister) {
        for (Indicator<?> indicator : indicatorsToRegister) {
            register(indicator);
        }
    }

    /**
     * Register multiple specs (backward compatibility).
     */
    public void registerAll(IndicatorSpec<?>... specsToRegister) {
        for (IndicatorSpec<?> spec : specsToRegister) {
            register(spec);
        }
    }

    /**
     * Get an indicator by ID.
     */
    @SuppressWarnings("unchecked")
    public <T> Indicator<T> get(String id) {
        Indicator<T> indicator = (Indicator<T>) indicators.get(id);
        if (indicator != null) {
            return indicator;
        }
        // Fall back to spec
        IndicatorSpec<T> spec = (IndicatorSpec<T>) specs.get(id);
        if (spec != null) {
            return specToIndicator(spec);
        }
        return null;
    }

    /**
     * Get a spec by ID (backward compatibility).
     */
    @SuppressWarnings("unchecked")
    public <T> IndicatorSpec<T> getSpec(String id) {
        IndicatorSpec<T> spec = (IndicatorSpec<T>) specs.get(id);
        if (spec != null) {
            return spec;
        }
        // Fall back to indicator (adapt it)
        Indicator<T> indicator = (Indicator<T>) indicators.get(id);
        if (indicator != null) {
            return indicatorToSpec(indicator);
        }
        return null;
    }

    /**
     * Get an indicator, throwing if not found.
     */
    public <T> Indicator<T> getOrThrow(String id) {
        Indicator<T> indicator = get(id);
        if (indicator == null) {
            throw new IllegalArgumentException("Unknown indicator: " + id);
        }
        return indicator;
    }

    public boolean contains(String id) {
        return indicators.containsKey(id) || specs.containsKey(id);
    }

    public Collection<String> getIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        ids.addAll(indicators.keySet());
        ids.addAll(specs.keySet());
        return ids;
    }

    public Collection<Indicator<?>> getAll() {
        return indicators.values();
    }

    public int size() {
        return indicators.size() + specs.size();
    }

    public void clear() {
        indicators.clear();
        specs.clear();
    }

    // Adapter: IndicatorSpec -> Indicator
    private <T> Indicator<T> specToIndicator(IndicatorSpec<T> spec) {
        return new Indicator<T>() {
            @Override public String id() { return spec.id(); }
            @Override public String name() { return spec.id(); }
            @Override public String description() { return ""; }
            @Override public int warmupBars(Object... params) { return 0; }
            @Override public String cacheKey(Object... params) { return spec.cacheKey(params); }
            @Override public T compute(IndicatorContext ctx, Object... params) { return spec.compute(ctx, params); }
            @Override public double valueAt(T result, int barIndex) { return spec.valueAt(result, barIndex); }
            @Override public Class<T> resultType() { return spec.resultType(); }
            @Override public java.util.Set<DataDependency> dependencies() { return spec.dependencies(); }
        };
    }

    // Adapter: Indicator -> IndicatorSpec
    private <T> IndicatorSpec<T> indicatorToSpec(Indicator<T> indicator) {
        return new IndicatorSpec<T>() {
            @Override public String id() { return indicator.id(); }
            @Override public String cacheKey(Object... params) { return indicator.cacheKey(params); }
            @Override public java.util.Set<DataDependency> dependencies() { return indicator.dependencies(); }
            @Override public T compute(IndicatorContext ctx, Object... params) { return indicator.compute(ctx, params); }
            @Override public double valueAt(T result, int barIndex) { return indicator.valueAt(result, barIndex); }
            @Override public Class<T> resultType() { return indicator.resultType(); }
        };
    }
}
