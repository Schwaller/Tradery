package com.tradery.dataservice.live;

import com.tradery.core.model.AggTrade;
import com.tradery.dataservice.data.SpectrumAggregator;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Persists live Binance aggTrades to SQLite so they are available for
 * volume profile computation. Buffers trades and flushes periodically.
 *
 * When a client subscribes to live candle data for a symbol, the persister
 * also starts collecting aggTrades for that symbol. This ensures that when
 * the footprint overlay requests profiles, the latest trades are included.
 */
public class LiveAggTradePersister {

    private static final Logger LOG = LoggerFactory.getLogger(LiveAggTradePersister.class);
    private static final int FLUSH_THRESHOLD = 500;
    private static final long FLUSH_INTERVAL_MS = 5000;

    private final SqliteDataStore dataStore;
    private final LiveAggTradeManager aggTradeManager;
    private final SpectrumAggregator spectrumAggregator = new SpectrumAggregator();

    // Per-symbol state
    private final Map<String, SymbolCollector> collectors = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LiveAggTradePersister-Flush");
        t.setDaemon(true);
        return t;
    });

    public LiveAggTradePersister(SqliteDataStore dataStore, LiveAggTradeManager aggTradeManager) {
        this.dataStore = dataStore;
        this.aggTradeManager = aggTradeManager;

        // Periodic flush
        scheduler.scheduleAtFixedRate(this::flushAll, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Start collecting aggTrades for a symbol/marketType.
     * Reference-counted: multiple callers can start, only stops when all stop.
     */
    public void startCollecting(String symbol, String marketType) {
        String key = symbol.toUpperCase() + ":" + (marketType != null ? marketType : "perp");

        collectors.computeIfAbsent(key, k -> {
            String sym = symbol.toUpperCase();
            String mt = marketType != null ? marketType : "perp";

            LOG.info("Starting live aggTrade persistence for {} ({})", sym, mt);

            BiConsumer<String, AggTrade> callback = (s, trade) -> onTrade(key, trade);
            aggTradeManager.subscribe(sym, mt, callback);

            return new SymbolCollector(sym, mt, callback);
        }).refCount.incrementAndGet();
    }

    /**
     * Stop collecting aggTrades for a symbol/marketType.
     * Only actually stops when all callers have stopped.
     */
    public void stopCollecting(String symbol, String marketType) {
        String key = symbol.toUpperCase() + ":" + (marketType != null ? marketType : "perp");

        SymbolCollector collector = collectors.get(key);
        if (collector == null) return;

        if (collector.refCount.decrementAndGet() <= 0) {
            collectors.remove(key);
            aggTradeManager.unsubscribe(collector.symbol, collector.marketType, collector.callback);
            flushBuffer(collector);
            LOG.info("Stopped live aggTrade persistence for {} ({})", collector.symbol, collector.marketType);
        }
    }

    private void onTrade(String key, AggTrade trade) {
        SymbolCollector collector = collectors.get(key);
        if (collector == null) return;

        synchronized (collector.buffer) {
            collector.buffer.add(trade);
            if (collector.buffer.size() >= FLUSH_THRESHOLD) {
                List<AggTrade> batch = new ArrayList<>(collector.buffer);
                collector.buffer.clear();
                persistBatch(collector.symbol, collector.marketType, batch);
            }
        }
    }

    private void flushAll() {
        for (SymbolCollector collector : collectors.values()) {
            flushBuffer(collector);
        }
    }

    private void flushBuffer(SymbolCollector collector) {
        List<AggTrade> batch;
        synchronized (collector.buffer) {
            if (collector.buffer.isEmpty()) return;
            batch = new ArrayList<>(collector.buffer);
            collector.buffer.clear();
        }
        persistBatch(collector.symbol, collector.marketType, batch);
    }

    private void persistBatch(String symbol, String marketType, List<AggTrade> trades) {
        if (trades.isEmpty()) return;

        try {
            String exchange = "binance";
            dataStore.saveAggTrades(symbol, exchange, marketType, trades);

            // Update aggTrades coverage so ProfileStore.ensureCoverage() can find the data
            long batchStart = trades.get(0).timestamp();
            long batchEnd = trades.get(trades.size() - 1).timestamp();
            String subKey = "spot".equals(marketType) ? "spot" : "default";
            dataStore.addCoverage(symbol, "agg_trades", subKey, batchStart, batchEnd, false);

            // Invalidate profile coverage for the current window so profiles get recomputed
            // Use the batch range — ProfileStore will recompute profiles from available aggTrades
            dataStore.removeCoverage(symbol, "volume_profiles", marketType, batchStart, batchEnd);

            // Compute spectrum inline (like AggTradesStore does)
            var spectrumRows = spectrumAggregator.aggregate(trades);
            if (!spectrumRows.isEmpty()) {
                dataStore.saveSpectrum(symbol, spectrumRows);
            }

            LOG.debug("Persisted {} live trades for {} ({}) [{}-{}]",
                trades.size(), symbol, marketType, batchStart, batchEnd);
        } catch (Exception e) {
            LOG.warn("Failed to persist live trades for {}: {}", symbol, e.getMessage());
        }
    }

    public int getActiveSymbolCount() {
        return collectors.size();
    }

    public void shutdown() {
        LOG.info("Shutting down LiveAggTradePersister");
        scheduler.shutdown();
        // Flush remaining buffers
        for (SymbolCollector collector : collectors.values()) {
            flushBuffer(collector);
            aggTradeManager.unsubscribe(collector.symbol, collector.marketType, collector.callback);
        }
        collectors.clear();
    }

    private static class SymbolCollector {
        final String symbol;
        final String marketType;
        final BiConsumer<String, AggTrade> callback;
        final List<AggTrade> buffer = new ArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger refCount = new java.util.concurrent.atomic.AtomicInteger(0);

        SymbolCollector(String symbol, String marketType, BiConsumer<String, AggTrade> callback) {
            this.symbol = symbol;
            this.marketType = marketType;
            this.callback = callback;
        }
    }
}
