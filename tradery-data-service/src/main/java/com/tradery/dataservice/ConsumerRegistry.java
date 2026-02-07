package com.tradery.dataservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Tracks active consumers (apps) using the data service.
 * Triggers shutdown when no consumers remain for a configured idle period.
 */
public class ConsumerRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumerRegistry.class);

    private static final long HEARTBEAT_TIMEOUT_MS = 30_000;  // 30 seconds without heartbeat = dead
    private static final long IDLE_SHUTDOWN_DELAY_MS = 10_000; // 10 seconds with no consumers = shutdown

    private final Map<String, ConsumerInfo> consumers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Runnable shutdownCallback;

    private ScheduledFuture<?> shutdownTask;

    public ConsumerRegistry(Runnable shutdownCallback) {
        this.shutdownCallback = shutdownCallback;

        // Periodically check for dead consumers
        scheduler.scheduleAtFixedRate(this::checkHeartbeats, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * Register a consumer (app) with the service.
     */
    public synchronized void register(String consumerId, String consumerName, int pid) {
        LOG.info("Consumer registered: {} ({}) PID={}", consumerName, consumerId, pid);
        consumers.put(consumerId, new ConsumerInfo(consumerId, consumerName, pid, System.currentTimeMillis()));

        // Cancel any pending shutdown
        if (shutdownTask != null) {
            shutdownTask.cancel(false);
            shutdownTask = null;
            LOG.info("Shutdown cancelled - consumer registered");
        }
    }

    /**
     * Unregister a consumer.
     */
    public synchronized void unregister(String consumerId) {
        ConsumerInfo removed = consumers.remove(consumerId);
        if (removed != null) {
            LOG.info("Consumer unregistered: {} ({})", removed.name, consumerId);
        }
        checkForShutdown();
    }

    /**
     * Mark a consumer as WS-connected. WS-managed consumers skip heartbeat
     * timeout checks — the WS connection itself proves liveness.
     */
    public void setWsConnected(String consumerId, boolean wsConnected) {
        ConsumerInfo info = consumers.get(consumerId);
        if (info != null) {
            info.wsConnected = wsConnected;
            info.lastHeartbeat = System.currentTimeMillis();
        }
    }

    /**
     * Update heartbeat for a consumer.
     */
    public void heartbeat(String consumerId) {
        ConsumerInfo info = consumers.get(consumerId);
        if (info != null) {
            info.lastHeartbeat = System.currentTimeMillis();
        }
    }

    /**
     * Get count of active consumers.
     */
    public int getConsumerCount() {
        return consumers.size();
    }

    /**
     * Check if any consumers are registered.
     */
    public boolean hasConsumers() {
        return !consumers.isEmpty();
    }

    /**
     * Shutdown the registry.
     */
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void checkHeartbeats() {
        long now = System.currentTimeMillis();
        boolean anyRemoved = false;

        for (var entry : consumers.entrySet()) {
            ConsumerInfo info = entry.getValue();
            // Skip heartbeat check for WS-connected consumers — WS ping/pong handles liveness
            if (info.wsConnected) continue;
            if (now - info.lastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                LOG.warn("Consumer {} timed out (no heartbeat for {}ms)", info.name, now - info.lastHeartbeat);
                consumers.remove(entry.getKey());
                anyRemoved = true;
            }
        }

        if (anyRemoved) {
            checkForShutdown();
        }
    }

    private synchronized void checkForShutdown() {
        if (consumers.isEmpty() && shutdownTask == null) {
            LOG.info("No consumers remaining. Scheduling shutdown in {}ms", IDLE_SHUTDOWN_DELAY_MS);
            shutdownTask = scheduler.schedule(() -> {
                if (consumers.isEmpty()) {
                    LOG.info("Idle shutdown triggered - no consumers");
                    shutdownCallback.run();
                }
            }, IDLE_SHUTDOWN_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private static class ConsumerInfo {
        final String id;
        final String name;
        final int pid;
        volatile long lastHeartbeat;
        volatile boolean wsConnected; // WS-managed consumers skip heartbeat timeout

        ConsumerInfo(String id, String name, int pid, long lastHeartbeat) {
            this.id = id;
            this.name = name;
            this.pid = pid;
            this.lastHeartbeat = lastHeartbeat;
        }
    }
}
