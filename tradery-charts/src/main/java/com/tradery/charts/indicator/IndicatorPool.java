package com.tradery.charts.indicator;

import com.tradery.core.indicators.IndicatorEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin infrastructure for running indicator computations asynchronously.
 * Has ZERO indicator-specific knowledge - it just runs {@link IndicatorCompute} instances.
 *
 * <p>Uses a single background thread to avoid thread-safety issues with
 * IndicatorEngine's internal HashMap cache.</p>
 *
 * <p>Provides:
 * <ul>
 *   <li>Single background thread for computation</li>
 *   <li>Cache by key for deduplication</li>
 *   <li>Recomputation when data context changes</li>
 * </ul>
 */
public class IndicatorPool {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "indicator-pool");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, CachedComputation<?>> cache = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ActiveSubscription<?>> activeSubscriptions = new CopyOnWriteArrayList<>();

    private volatile IndicatorEngine engine;

    /**
     * Subscribe to an indicator computation.
     * If the same key is already cached and data hasn't changed, returns cached result.
     * Otherwise, schedules background computation.
     */
    @SuppressWarnings("unchecked")
    public <T> IndicatorSubscription<T> subscribe(IndicatorCompute<T> compute) {
        String key = compute.key();

        IndicatorSubscription<T> subscription = new IndicatorSubscription<>(() -> {
            // On close: remove from active list (but keep cache)
            activeSubscriptions.removeIf(a -> a.key.equals(key) && a.subscription.getData() == null);
        });

        // Track active subscription for recomputation
        activeSubscriptions.add(new ActiveSubscription<>(key, compute, subscription));

        // Check cache
        CachedComputation<?> cached = cache.get(key);
        if (cached != null && cached.dataVersion == dataVersion()) {
            subscription.setResult((T) cached.result);
            return subscription;
        }

        // Schedule computation
        IndicatorEngine currentEngine = this.engine;
        if (currentEngine == null) {
            return subscription; // No data yet - will compute on setDataContext
        }

        long version = dataVersion();
        executor.submit(() -> {
            try {
                T result = compute.compute(currentEngine);
                cache.put(key, new CachedComputation<>(result, version));
                subscription.setResult(result);
            } catch (Exception e) {
                // Log but don't crash - subscription stays with null data
                System.err.println("Indicator computation failed for " + key + ": " + e.getMessage());
            }
        });

        return subscription;
    }

    /**
     * Update data context with a fully-configured IndicatorEngine.
     * Triggers recomputation of all active subscriptions.
     */
    public void setDataContext(IndicatorEngine engine) {
        this.engine = engine;

        // Invalidate cache
        cache.clear();

        if (engine == null) return;

        // Recompute all active subscriptions
        long version = dataVersion();
        for (ActiveSubscription<?> active : activeSubscriptions) {
            recompute(active, engine, version);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void recompute(ActiveSubscription<T> active, IndicatorEngine engine, long version) {
        executor.submit(() -> {
            try {
                T result = active.compute.compute(engine);
                cache.put(active.key, new CachedComputation<>(result, version));
                active.subscription.setResult(result);
            } catch (Exception e) {
                System.err.println("Indicator recomputation failed for " + active.key + ": " + e.getMessage());
            }
        });
    }

    private long dataVersion() {
        IndicatorEngine e = engine;
        if (e == null) return 0;
        var candles = e.getCandles();
        if (candles == null || candles.isEmpty()) return 0;
        return candles.size() * 31L + candles.get(candles.size() - 1).timestamp();
    }

    /**
     * Shutdown the thread pool.
     */
    public void shutdown() {
        executor.shutdownNow();
        activeSubscriptions.clear();
        cache.clear();
    }

    private record CachedComputation<T>(T result, long dataVersion) {}

    private record ActiveSubscription<T>(String key, IndicatorCompute<T> compute,
                                          IndicatorSubscription<T> subscription) {}
}
