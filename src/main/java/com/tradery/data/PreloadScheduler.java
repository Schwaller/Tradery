package com.tradery.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background scheduler for preloading data at low priority.
 * Single thread processes queue, pauses when trading load is active.
 * Thread-safe for concurrent queue/pause operations.
 */
public final class PreloadScheduler {

    private static final Logger log = LoggerFactory.getLogger(PreloadScheduler.class);
    private static final long RATE_LIMIT_DELAY_MS = 500;  // Delay between API calls
    private static final long PAUSE_CHECK_INTERVAL_MS = 100;

    private final PriorityBlockingQueue<PreloadRequest> queue;
    private final Set<String> queuedKeys;  // For deduplication
    private final DataInventory inventory;
    private final AtomicBoolean tradingLoadActive;
    private final AtomicBoolean running;
    private final AtomicBoolean shuttingDown;

    // Store references (set via setStores after construction)
    private CandleStore candleStore;
    private AggTradesStore aggTradesStore;
    private FundingRateStore fundingRateStore;
    private OpenInterestStore openInterestStore;

    private Thread workerThread;

    public PreloadScheduler(DataInventory inventory) {
        this.queue = new PriorityBlockingQueue<>();
        this.queuedKeys = new HashSet<>();
        this.inventory = inventory;
        this.tradingLoadActive = new AtomicBoolean(false);
        this.running = new AtomicBoolean(false);
        this.shuttingDown = new AtomicBoolean(false);
    }

    /**
     * Set store references for actual data loading.
     * Must be called before start().
     */
    public void setStores(CandleStore candleStore, AggTradesStore aggTradesStore,
                          FundingRateStore fundingRateStore, OpenInterestStore openInterestStore) {
        this.candleStore = candleStore;
        this.aggTradesStore = aggTradesStore;
        this.fundingRateStore = fundingRateStore;
        this.openInterestStore = openInterestStore;
    }

    /**
     * Start the background worker thread.
     */
    public void start() {
        if (running.get()) {
            return;  // Already running
        }

        shuttingDown.set(false);
        running.set(true);

        workerThread = new Thread(this::workerLoop, "Preload-Scheduler");
        workerThread.setDaemon(true);
        workerThread.start();

        log.info("PreloadScheduler started");
    }

    /**
     * Stop the background worker thread.
     */
    public void shutdown() {
        if (!running.get()) {
            return;
        }

        log.info("PreloadScheduler shutting down...");
        shuttingDown.set(true);
        running.set(false);

        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("PreloadScheduler stopped");
    }

    /**
     * Queue a preload request.
     * Duplicate requests (same key) are ignored.
     */
    public void queuePreload(PreloadRequest request) {
        String key = request.getDedupeKey();
        synchronized (queuedKeys) {
            if (queuedKeys.contains(key)) {
                return;  // Already queued
            }
            queuedKeys.add(key);
        }

        queue.offer(request);
        log.debug("Preload queued: {}", request);
    }

    /**
     * Convenience method: queue candle preload.
     */
    public void queueCandles(String symbol, String timeframe, long start, long end,
                             PreloadRequest.Priority priority) {
        queuePreload(PreloadRequest.candles(symbol, timeframe, start, end, priority));
    }

    /**
     * Convenience method: queue aggTrades preload.
     */
    public void queueAggTrades(String symbol, long start, long end,
                               PreloadRequest.Priority priority) {
        queuePreload(PreloadRequest.aggTrades(symbol, start, end, priority));
    }

    /**
     * Convenience method: queue OI preload.
     */
    public void queueOI(String symbol, long start, long end,
                        PreloadRequest.Priority priority) {
        queuePreload(PreloadRequest.oi(symbol, start, end, priority));
    }

    /**
     * Pause preloading while trading-tier load is active.
     */
    public void pauseForTradingLoad() {
        tradingLoadActive.set(true);
        log.debug("PreloadScheduler paused for trading load");
    }

    /**
     * Resume preloading after trading load completes.
     */
    public void resumeAfterTradingLoad() {
        tradingLoadActive.set(false);
        log.debug("PreloadScheduler resumed");
    }

    /**
     * Check if preloader is paused.
     */
    public boolean isPaused() {
        return tradingLoadActive.get();
    }

    /**
     * Get current queue size.
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Clear the queue (for testing).
     */
    public void clearQueue() {
        queue.clear();
        synchronized (queuedKeys) {
            queuedKeys.clear();
        }
    }

    /**
     * Main worker loop - processes queue in priority order.
     */
    private void workerLoop() {
        while (running.get() && !shuttingDown.get()) {
            try {
                // Wait while paused for trading load
                while (tradingLoadActive.get() && running.get()) {
                    Thread.sleep(PAUSE_CHECK_INTERVAL_MS);
                }

                if (!running.get()) break;

                // Get next request (blocking)
                PreloadRequest request = queue.poll();
                if (request == null) {
                    Thread.sleep(PAUSE_CHECK_INTERVAL_MS);
                    continue;
                }

                // Remove from dedupe set
                synchronized (queuedKeys) {
                    queuedKeys.remove(request.getDedupeKey());
                }

                // Process the request
                processRequest(request);

                // Rate limit to avoid hammering API
                Thread.sleep(RATE_LIMIT_DELAY_MS);

            } catch (InterruptedException e) {
                if (!shuttingDown.get()) {
                    log.debug("PreloadScheduler interrupted");
                }
                break;
            } catch (Exception e) {
                log.warn("PreloadScheduler error: {}", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Process a single preload request.
     */
    private void processRequest(PreloadRequest request) {
        try {
            switch (request.type()) {
                case CANDLES -> processCandles(request);
                case AGGTRADES -> processAggTrades(request);
                case FUNDING -> processFunding(request);
                case OI -> processOI(request);
            }
        } catch (Exception e) {
            log.warn("Preload failed for {}: {}", request, e.getMessage());
        }
    }

    private void processCandles(PreloadRequest request) throws Exception {
        // Check inventory for gaps
        List<DateRangeSet.Range> gaps = inventory.getCandleGaps(
            request.symbol(), request.timeframe(), request.startTime(), request.endTime());

        if (gaps.isEmpty()) {
            log.debug("Preload skipped (already cached): {}", request);
            return;
        }

        if (candleStore == null) {
            log.warn("CandleStore not set, skipping preload");
            return;
        }

        // Fetch data for each gap
        for (DateRangeSet.Range gap : gaps) {
            if (tradingLoadActive.get()) {
                // Pause triggered, requeue remaining work
                queuePreload(PreloadRequest.candles(
                    request.symbol(), request.timeframe(),
                    gap.start(), request.endTime(),
                    request.priority()));
                return;
            }

            log.info("Preloading candles: {}/{} gap {}", request.symbol(),
                request.timeframe(), formatDuration(gap.duration()));

            candleStore.getCandles(request.symbol(), request.timeframe(),
                gap.start(), gap.end());

            // Record coverage
            inventory.recordCandleData(request.symbol(), request.timeframe(),
                gap.start(), gap.end());
        }
    }

    private void processAggTrades(PreloadRequest request) throws Exception {
        // Check inventory for gaps
        List<DateRangeSet.Range> gaps = inventory.getAggTradesGaps(
            request.symbol(), request.startTime(), request.endTime());

        if (gaps.isEmpty()) {
            log.debug("Preload skipped (already cached): {}", request);
            return;
        }

        if (aggTradesStore == null) {
            log.warn("AggTradesStore not set, skipping preload");
            return;
        }

        // Fetch data for each gap
        for (DateRangeSet.Range gap : gaps) {
            if (tradingLoadActive.get()) {
                queuePreload(PreloadRequest.aggTrades(
                    request.symbol(), gap.start(), request.endTime(),
                    request.priority()));
                return;
            }

            log.info("Preloading aggTrades: {} gap {}", request.symbol(), formatDuration(gap.duration()));

            aggTradesStore.getAggTrades(request.symbol(), gap.start(), gap.end());

            inventory.recordAggTradesData(request.symbol(), gap.start(), gap.end());
        }
    }

    private void processFunding(PreloadRequest request) throws Exception {
        // Check inventory for gaps
        List<DateRangeSet.Range> gaps = inventory.getFundingGaps(
            request.symbol(), request.startTime(), request.endTime());

        if (gaps.isEmpty()) {
            log.debug("Preload skipped (already cached): {}", request);
            return;
        }

        if (fundingRateStore == null) {
            log.warn("FundingRateStore not set, skipping preload");
            return;
        }

        for (DateRangeSet.Range gap : gaps) {
            if (tradingLoadActive.get()) {
                queuePreload(PreloadRequest.funding(
                    request.symbol(), gap.start(), request.endTime(),
                    request.priority()));
                return;
            }

            log.info("Preloading funding: {} gap {}", request.symbol(), formatDuration(gap.duration()));

            fundingRateStore.getFundingRates(request.symbol(), gap.start(), gap.end());

            inventory.recordFundingData(request.symbol(), gap.start(), gap.end());
        }
    }

    private void processOI(PreloadRequest request) throws Exception {
        // Check inventory for gaps
        List<DateRangeSet.Range> gaps = inventory.getOIGaps(
            request.symbol(), request.startTime(), request.endTime());

        if (gaps.isEmpty()) {
            log.debug("Preload skipped (already cached): {}", request);
            return;
        }

        if (openInterestStore == null) {
            log.warn("OpenInterestStore not set, skipping preload");
            return;
        }

        // OI has 30-day Binance limit, cap fetch range
        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;

        for (DateRangeSet.Range gap : gaps) {
            if (tradingLoadActive.get()) {
                queuePreload(PreloadRequest.oi(
                    request.symbol(), gap.start(), request.endTime(),
                    request.priority()));
                return;
            }

            // Cap to 30-day limit
            long effectiveStart = Math.max(gap.start(), now - thirtyDaysMs);
            if (effectiveStart >= gap.end()) {
                continue;  // Gap is too old to fetch
            }

            log.info("Preloading OI: {} gap {}", request.symbol(), formatDuration(gap.end() - effectiveStart));

            openInterestStore.getOpenInterest(request.symbol(), effectiveStart, gap.end());

            inventory.recordOIData(request.symbol(), effectiveStart, gap.end());
        }
    }

    private String formatDuration(long ms) {
        long hours = ms / 3600000;
        if (hours >= 24) {
            return String.format("%.1fd", hours / 24.0);
        }
        return String.format("%dh", hours);
    }
}
