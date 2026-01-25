package com.tradery.dataservice.page;

import com.tradery.dataservice.config.DataServiceConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a loaded data page with its consumers and data.
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
            key.startTime(),
            key.endTime(),
            key.startTime(), // actualStart - would need to track this
            key.endTime(),   // actualEnd - would need to track this
            List.of()        // gaps - would need to track this
        );

        return new PageStatus(
            state,
            progress.get(),
            recordCount.get(),
            lastSyncTime,
            getConsumers(),
            coverage,
            false
        );
    }
}
