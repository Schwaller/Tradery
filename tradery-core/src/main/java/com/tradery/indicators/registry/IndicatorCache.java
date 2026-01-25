package com.tradery.indicators.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Unified indicator cache with background computation.
 *
 * This is the central computation/caching layer used by both:
 * - IndicatorEngine (sync access for backtest)
 * - IndicatorPageManager (async access for charts)
 *
 * Thread-safe. Handles deduplication of concurrent requests.
 */
public class IndicatorCache {

    private static final Logger log = LoggerFactory.getLogger(IndicatorCache.class);

    // Cache storage
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    // Pending computations (for deduplication)
    private final Map<String, CompletableFuture<?>> pending = new ConcurrentHashMap<>();

    // Listeners for async notifications
    private final Map<String, Set<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    // Background computation thread pool
    private final ExecutorService executor;

    // Current data context
    private volatile IndicatorContext context;

    public IndicatorCache() {
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "IndicatorCache-Compute");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Update the data context. Clears cache.
     */
    public void setContext(IndicatorContext context) {
        this.context = context;
        clearCache();
    }

    /**
     * Get current context.
     */
    public IndicatorContext getContext() {
        return context;
    }

    /**
     * Clear all cached values and pending computations.
     */
    public void clearCache() {
        cache.clear();
        pending.values().forEach(f -> f.cancel(true));
        pending.clear();
    }

    /**
     * Clear specific cache entry.
     */
    public void clearEntry(String cacheKey) {
        cache.remove(cacheKey);
        CompletableFuture<?> future = pending.remove(cacheKey);
        if (future != null) {
            future.cancel(true);
        }
    }

    // ========== Synchronous Access (for backtest) ==========

    /**
     * Get indicator value synchronously. Blocks until computed.
     * For use by backtest engine on non-EDT thread.
     *
     * @param id Indicator ID
     * @param params Indicator parameters
     * @return Computed result
     */
    @SuppressWarnings("unchecked")
    public <T> T getSync(String id, Object... params) {
        IndicatorSpec<T> spec = IndicatorRegistry.getInstance().getSpec(id);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown indicator: " + id);
        }
        String cacheKey = spec.cacheKey(params);

        // Check cache first
        T cached = (T) cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Check dependencies
        if (context == null || !context.dependenciesMet(spec.dependencies())) {
            log.debug("Dependencies not met for {} - context: {}", id, context);
            return null;
        }

        // Compute synchronously (we're not on EDT for backtest)
        T result = spec.compute(context, params);
        cache.put(cacheKey, result);
        return result;
    }

    /**
     * Get single value at bar index synchronously.
     * Convenience method for backtest per-bar evaluation.
     */
    @SuppressWarnings("unchecked")
    public double getValueAt(String id, int barIndex, Object... params) {
        IndicatorSpec<Object> spec = IndicatorRegistry.getInstance().getSpec(id);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown indicator: " + id);
        }
        Object result = getSync(id, params);
        if (result == null) {
            return Double.NaN;
        }
        return spec.valueAt(result, barIndex);
    }

    // ========== Asynchronous Access (for charts) ==========

    /**
     * Request indicator asynchronously. Returns immediately.
     * Listener is notified on EDT when computation completes.
     *
     * @param id Indicator ID
     * @param params Indicator parameters
     * @param listener Callback when ready (on EDT)
     * @return true if already cached, false if computation started
     */
    @SuppressWarnings("unchecked")
    public <T> boolean requestAsync(String id, Object[] params, Consumer<T> listener) {
        IndicatorSpec<T> spec = IndicatorRegistry.getInstance().getSpec(id);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown indicator: " + id);
        }
        String cacheKey = spec.cacheKey(params);

        // Check cache first
        T cached = (T) cache.get(cacheKey);
        if (cached != null) {
            if (listener != null) {
                SwingUtilities.invokeLater(() -> listener.accept(cached));
            }
            return true;
        }

        // Register listener
        if (listener != null) {
            listeners.computeIfAbsent(cacheKey, k -> ConcurrentHashMap.newKeySet())
                .add((Consumer<Object>) listener);
        }

        // Check if already computing
        if (pending.containsKey(cacheKey)) {
            return false;
        }

        // Check dependencies
        if (context == null || !context.dependenciesMet(spec.dependencies())) {
            log.debug("Dependencies not met for async {} - skipping", id);
            return false;
        }

        // Start background computation
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return spec.compute(context, params);
            } catch (Exception e) {
                log.warn("Indicator computation failed for {}: {}", id, e.getMessage());
                throw e;
            }
        }, executor);

        pending.put(cacheKey, future);

        future.whenComplete((result, error) -> {
            pending.remove(cacheKey);
            if (result != null) {
                cache.put(cacheKey, result);
                notifyListeners(cacheKey, result);
            }
        });

        return false;
    }

    /**
     * Check if an indicator result is cached.
     */
    public boolean isCached(String id, Object... params) {
        IndicatorSpec<?> spec = IndicatorRegistry.getInstance().getSpec(id);
        if (spec == null) return false;
        return cache.containsKey(spec.cacheKey(params));
    }

    /**
     * Check if an indicator is currently being computed.
     */
    public boolean isComputing(String id, Object... params) {
        IndicatorSpec<?> spec = IndicatorRegistry.getInstance().getSpec(id);
        if (spec == null) return false;
        return pending.containsKey(spec.cacheKey(params));
    }

    /**
     * Get cached result without computing.
     */
    @SuppressWarnings("unchecked")
    public <T> T getCached(String id, Object... params) {
        IndicatorSpec<T> spec = IndicatorRegistry.getInstance().getSpec(id);
        if (spec == null) return null;
        return (T) cache.get(spec.cacheKey(params));
    }

    // ========== Listener Management ==========

    private void notifyListeners(String cacheKey, Object result) {
        Set<Consumer<Object>> keyListeners = listeners.remove(cacheKey);
        if (keyListeners == null || keyListeners.isEmpty()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            for (Consumer<Object> listener : keyListeners) {
                try {
                    listener.accept(result);
                } catch (Exception e) {
                    log.warn("Listener error: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * Remove a specific listener.
     */
    public void removeListener(String id, Object[] params, Consumer<?> listener) {
        IndicatorSpec<?> spec = IndicatorRegistry.getInstance().getSpec(id);
        if (spec == null) return;
        String cacheKey = spec.cacheKey(params);
        Set<Consumer<Object>> keyListeners = listeners.get(cacheKey);
        if (keyListeners != null) {
            keyListeners.remove(listener);
        }
    }

    // ========== Lifecycle ==========

    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        return new CacheStats(cache.size(), pending.size(), listeners.size());
    }

    public record CacheStats(int cachedCount, int pendingCount, int listenerCount) {}
}
