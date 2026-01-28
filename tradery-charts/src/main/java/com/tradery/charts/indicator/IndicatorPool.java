package com.tradery.charts.indicator;

import com.tradery.core.model.Candle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin infrastructure for running indicator computations asynchronously.
 * Has ZERO indicator-specific knowledge - it just runs {@link IndicatorCompute} instances.
 *
 * <p>Provides:
 * <ul>
 *   <li>Thread pool for background computation</li>
 *   <li>Cache by key for deduplication</li>
 *   <li>Recomputation when data context changes</li>
 * </ul>
 */
public class IndicatorPool {

    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "indicator-pool");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, CachedComputation<?>> cache = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ActiveSubscription<?>> activeSubscriptions = new CopyOnWriteArrayList<>();

    private volatile List<Candle> candles;
    private volatile String timeframe;

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
        List<Candle> currentCandles = this.candles;
        String currentTimeframe = this.timeframe;
        if (currentCandles == null || currentCandles.isEmpty()) {
            return subscription; // No data yet - will compute on setDataContext
        }

        long version = dataVersion();
        executor.submit(() -> {
            try {
                T result = compute.compute(currentCandles, currentTimeframe);
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
     * Update data context. Triggers recomputation of all active subscriptions.
     */
    public void setDataContext(List<Candle> candles, String symbol, String timeframe,
                               long startTime, long endTime) {
        this.candles = candles;
        this.timeframe = timeframe;

        // Invalidate cache
        cache.clear();

        if (candles == null || candles.isEmpty()) return;

        // Recompute all active subscriptions
        long version = dataVersion();
        for (ActiveSubscription<?> active : activeSubscriptions) {
            recompute(active, candles, timeframe, version);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void recompute(ActiveSubscription<T> active, List<Candle> candles,
                                String timeframe, long version) {
        executor.submit(() -> {
            try {
                T result = active.compute.compute(candles, timeframe);
                cache.put(active.key, new CachedComputation<>(result, version));
                active.subscription.setResult(result);
            } catch (Exception e) {
                System.err.println("Indicator recomputation failed for " + active.key + ": " + e.getMessage());
            }
        });
    }

    private long dataVersion() {
        List<Candle> c = candles;
        if (c == null || c.isEmpty()) return 0;
        return c.size() * 31L + c.get(c.size() - 1).timestamp();
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
