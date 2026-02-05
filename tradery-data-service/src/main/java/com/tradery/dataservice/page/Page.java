package com.tradery.dataservice.page;

import com.tradery.core.model.Candle;
import com.tradery.dataservice.config.DataServiceConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a loaded data page with its consumers and data.
 *
 * Pages can be:
 * - Anchored: Fixed time range, static historical view (data stored as byte[])
 * - Live: Sliding window that moves with current time, receives live updates
 *
 * For live pages, data is stored as a List for efficient append/trim operations.
 */
public class Page {
    private final PageKey key;
    private final DataServiceConfig config;
    private final Map<String, String> consumers = new ConcurrentHashMap<>(); // id -> name
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong recordCount = new AtomicLong(0);

    private volatile PageState state = PageState.PENDING;
    private volatile byte[] data;
    private volatile Long lastSyncTime;

    // Live page support
    private final ReentrantReadWriteLock liveDataLock = new ReentrantReadWriteLock();
    private final List<Candle> liveCandles = new ArrayList<>();
    private volatile Candle incompleteCandle;
    private final List<LiveUpdateListener> liveListeners = new CopyOnWriteArrayList<>();

    public Page(PageKey key, DataServiceConfig config) {
        this.key = key;
        this.config = config;
    }

    public PageKey getKey() {
        return key;
    }

    /**
     * Add a consumer to this page.
     * @return true if this is the first time this consumer is added
     */
    public boolean addConsumer(String consumerId, String consumerName) {
        lastAccessTime.set(System.currentTimeMillis());
        return consumers.put(consumerId, consumerName) == null;
    }

    /**
     * Remove a consumer from this page.
     */
    public void removeConsumer(String consumerId) {
        consumers.remove(consumerId);
    }

    /**
     * Get the number of consumers.
     */
    public int getConsumerCount() {
        return consumers.size();
    }

    /**
     * Get list of consumers.
     */
    public List<PageStatus.Consumer> getConsumers() {
        return consumers.entrySet().stream()
            .map(e -> new PageStatus.Consumer(e.getKey(), e.getValue()))
            .toList();
    }

    /**
     * Get the current state.
     */
    public PageState getState() {
        return state;
    }

    /**
     * Set the state and progress.
     */
    public void setState(PageState state, int progress) {
        this.state = state;
        this.progress.set(progress);
        lastAccessTime.set(System.currentTimeMillis());
    }

    /**
     * Get the loading progress (0-100).
     */
    public int getProgress() {
        return progress.get();
    }

    /**
     * Set the loading progress.
     */
    public void setProgress(int progress) {
        this.progress.set(progress);
    }

    /**
     * Get the data.
     */
    public byte[] getData() {
        lastAccessTime.set(System.currentTimeMillis());
        return data;
    }

    /**
     * Set the data.
     */
    public void setData(byte[] data) {
        this.data = data;
        this.lastSyncTime = System.currentTimeMillis();
    }

    /**
     * Get the record count.
     */
    public long getRecordCount() {
        return recordCount.get();
    }

    /**
     * Set the record count.
     */
    public void setRecordCount(long count) {
        recordCount.set(count);
    }

    /**
     * Get time since last access in minutes.
     */
    public long getIdleTimeMinutes() {
        return (System.currentTimeMillis() - lastAccessTime.get()) / (60 * 1000);
    }

    /**
     * Get the current status.
     */
    public PageStatus getStatus() {
        PageStatus.Coverage coverage = new PageStatus.Coverage(
            key.getEffectiveStartTime(),
            key.getEffectiveEndTime(),
            key.getEffectiveStartTime(), // actualStart - would need to track this
            key.getEffectiveEndTime(),   // actualEnd - would need to track this
            List.of()        // gaps - would need to track this
        );

        return new PageStatus(
            state,
            progress.get(),
            recordCount.get(),
            lastSyncTime,
            getConsumers(),
            coverage,
            key.isLive()
        );
    }

    // ========== Live Page Support ==========

    /**
     * Check if this is a live page.
     */
    public boolean isLive() {
        return key.isLive();
    }

    /**
     * Set initial candle data for a live page.
     */
    public void setLiveCandles(List<Candle> candles) {
        liveDataLock.writeLock().lock();
        try {
            liveCandles.clear();
            liveCandles.addAll(candles);
            recordCount.set(candles.size());
        } finally {
            liveDataLock.writeLock().unlock();
        }
    }

    /**
     * Get live candle data.
     */
    public List<Candle> getLiveCandles() {
        liveDataLock.readLock().lock();
        try {
            lastAccessTime.set(System.currentTimeMillis());
            return Collections.unmodifiableList(new ArrayList<>(liveCandles));
        } finally {
            liveDataLock.readLock().unlock();
        }
    }

    /**
     * Get the incomplete (forming) candle.
     */
    public Candle getIncompleteCandle() {
        return incompleteCandle;
    }

    /**
     * Update the incomplete (forming) candle.
     */
    public void updateIncomplete(Candle candle) {
        this.incompleteCandle = candle;
        notifyUpdate(candle);
    }

    /**
     * Append a completed candle and trim old data outside the window duration.
     *
     * @param candle The new completed candle
     * @return List of candles that were removed (fell outside window)
     */
    public List<Candle> appendAndTrim(Candle candle) {
        liveDataLock.writeLock().lock();
        try {
            // Append the new candle
            liveCandles.add(candle);

            // Clear incomplete since it's now complete
            this.incompleteCandle = null;

            // Trim data outside the duration
            long windowStart = System.currentTimeMillis() - key.windowDurationMillis();
            List<Candle> removed = new ArrayList<>();

            while (!liveCandles.isEmpty() && liveCandles.get(0).timestamp() < windowStart) {
                removed.add(liveCandles.remove(0));
            }

            recordCount.set(liveCandles.size());

            if (!removed.isEmpty()) {
                notifyAppend(candle, removed);
            } else {
                notifyAppend(candle, Collections.emptyList());
            }

            return removed;
        } finally {
            liveDataLock.writeLock().unlock();
        }
    }

    /**
     * Add a listener for live updates.
     */
    public void addLiveListener(LiveUpdateListener listener) {
        liveListeners.add(listener);
    }

    /**
     * Remove a live update listener.
     */
    public void removeLiveListener(LiveUpdateListener listener) {
        liveListeners.remove(listener);
    }

    private void notifyUpdate(Candle candle) {
        for (LiveUpdateListener listener : liveListeners) {
            try {
                listener.onUpdate(this, candle);
            } catch (Exception e) {
                // Log but don't propagate
            }
        }
    }

    private void notifyAppend(Candle candle, List<Candle> removed) {
        for (LiveUpdateListener listener : liveListeners) {
            try {
                listener.onAppend(this, candle, removed);
            } catch (Exception e) {
                // Log but don't propagate
            }
        }
    }

    /**
     * Listener for live page updates.
     */
    public interface LiveUpdateListener {
        /**
         * Called when the incomplete/forming candle is updated.
         */
        void onUpdate(Page page, Candle candle);

        /**
         * Called when a new completed candle is appended.
         * @param removed Candles that were removed to maintain window size
         */
        void onAppend(Page page, Candle candle, List<Candle> removed);
    }
}
