package com.tradery.data.log;

import com.tradery.data.DataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Singleton ring buffer store for download events.
 * Maintains a global log and per-page logs for efficient querying.
 */
public class DownloadLogStore {

    private static final DownloadLogStore INSTANCE = new DownloadLogStore();

    /** Maximum events in global log */
    private static final int MAX_GLOBAL_EVENTS = 10_000;

    /** Maximum events per page log */
    private static final int MAX_PAGE_EVENTS = 500;

    /** Global log (all events) */
    private final ConcurrentLinkedDeque<DownloadEvent> globalLog = new ConcurrentLinkedDeque<>();

    /** Per-page logs keyed by pageKey */
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<DownloadEvent>> pageLogs = new ConcurrentHashMap<>();

    /** Listeners for new events */
    private final List<Consumer<DownloadEvent>> listeners = new ArrayList<>();

    private DownloadLogStore() {}

    /**
     * Get the singleton instance.
     */
    public static DownloadLogStore getInstance() {
        return INSTANCE;
    }

    // ========== Logging Methods ==========

    /**
     * Log a page created event.
     */
    public void logPageCreated(String pageKey, DataType dataType, String symbol, String timeframe) {
        String msg = timeframe != null
            ? String.format("Page created: %s/%s", symbol, timeframe)
            : String.format("Page created: %s", symbol);

        Map<String, Object> meta = new HashMap<>();
        meta.put("symbol", symbol);
        if (timeframe != null) {
            meta.put("timeframe", timeframe);
        }

        log(DownloadEvent.of(pageKey, dataType, DownloadEvent.EventType.PAGE_CREATED, msg, meta));
    }

    /**
     * Log a load started event.
     */
    public void logLoadStarted(String pageKey, DataType dataType, String symbol, String timeframe) {
        String msg = timeframe != null
            ? String.format("Loading %s/%s...", symbol, timeframe)
            : String.format("Loading %s...", symbol);

        log(DownloadEvent.of(pageKey, dataType, DownloadEvent.EventType.LOAD_STARTED, msg));
    }

    /**
     * Log a load completed event.
     */
    public void logLoadCompleted(String pageKey, DataType dataType, int recordCount, long durationMs) {
        String msg = String.format("Loaded %d records in %dms", recordCount, durationMs);

        Map<String, Object> meta = new HashMap<>();
        meta.put("recordCount", recordCount);
        meta.put("durationMs", durationMs);

        log(DownloadEvent.of(pageKey, dataType, DownloadEvent.EventType.LOAD_COMPLETED, msg, meta));
    }

    /**
     * Log an update started event.
     */
    public void logUpdateStarted(String pageKey, DataType dataType) {
        log(DownloadEvent.of(pageKey, dataType, DownloadEvent.EventType.UPDATE_STARTED, "Background update started"));
    }

    /**
     * Log an update completed event.
     */
    public void logUpdateCompleted(String pageKey, DataType dataType, int recordCount, long durationMs) {
        String msg = String.format("Updated to %d records in %dms", recordCount, durationMs);

        Map<String, Object> meta = new HashMap<>();
        meta.put("recordCount", recordCount);
        meta.put("durationMs", durationMs);

        log(DownloadEvent.of(pageKey, dataType, DownloadEvent.EventType.UPDATE_COMPLETED, msg, meta));
    }

    /**
     * Log an error event.
     */
    public void logError(String pageKey, DataType dataType, String errorMessage) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("errorMessage", errorMessage);

        log(DownloadEvent.of(pageKey, dataType, DownloadEvent.EventType.ERROR,
            "Error: " + errorMessage, meta));
    }

    /**
     * Log a page released event.
     */
    public void logPageReleased(String pageKey, DataType dataType) {
        log(DownloadEvent.of(pageKey, dataType, DownloadEvent.EventType.PAGE_RELEASED, "Page released (ref count = 0)"));
    }

    /**
     * Log a listener added event.
     */
    public void logListenerAdded(String pageKey, DataType dataType, String consumerName) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("consumerName", consumerName);

        log(DownloadEvent.of(pageKey, dataType, DownloadEvent.EventType.LISTENER_ADDED,
            "Listener added: " + consumerName, meta));
    }

    /**
     * Log a listener removed event.
     */
    public void logListenerRemoved(String pageKey, DataType dataType, String consumerName) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("consumerName", consumerName);

        log(DownloadEvent.of(pageKey, dataType, DownloadEvent.EventType.LISTENER_REMOVED,
            "Listener removed: " + consumerName, meta));
    }

    /**
     * Add an event to the logs.
     */
    private void log(DownloadEvent event) {
        // Add to global log
        globalLog.addFirst(event);
        while (globalLog.size() > MAX_GLOBAL_EVENTS) {
            globalLog.removeLast();
        }

        // Add to page log
        ConcurrentLinkedDeque<DownloadEvent> pageLog = pageLogs.computeIfAbsent(
            event.pageKey(), k -> new ConcurrentLinkedDeque<>());
        pageLog.addFirst(event);
        while (pageLog.size() > MAX_PAGE_EVENTS) {
            pageLog.removeLast();
        }

        // Notify listeners
        for (Consumer<DownloadEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    // ========== Query Methods ==========

    /**
     * Get global log (newest first).
     */
    public List<DownloadEvent> getGlobalLog() {
        return new ArrayList<>(globalLog);
    }

    /**
     * Get global log with limit.
     */
    public List<DownloadEvent> getGlobalLog(int limit) {
        return globalLog.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Get log for a specific page.
     */
    public List<DownloadEvent> getPageLog(String pageKey) {
        ConcurrentLinkedDeque<DownloadEvent> pageLog = pageLogs.get(pageKey);
        if (pageLog == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(pageLog);
    }

    /**
     * Get logs filtered by data type.
     */
    public List<DownloadEvent> getLogsByDataType(DataType dataType, int limit) {
        return globalLog.stream()
            .filter(e -> e.dataType() == dataType)
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Get logs filtered by event type.
     */
    public List<DownloadEvent> getLogsByEventType(DownloadEvent.EventType eventType, int limit) {
        return globalLog.stream()
            .filter(e -> e.eventType() == eventType)
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Get logs since a timestamp.
     */
    public List<DownloadEvent> getLogsSince(long sinceTimestamp) {
        return globalLog.stream()
            .filter(e -> e.timestamp() >= sinceTimestamp)
            .collect(Collectors.toList());
    }

    /**
     * Get logs since a timestamp with limit.
     */
    public List<DownloadEvent> getLogsSince(long sinceTimestamp, int limit) {
        return globalLog.stream()
            .filter(e -> e.timestamp() >= sinceTimestamp)
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Get error logs only.
     */
    public List<DownloadEvent> getErrorLogs(int limit) {
        return globalLog.stream()
            .filter(DownloadEvent::isError)
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Query logs with multiple filters.
     */
    public List<DownloadEvent> query(Long sinceTimestamp, DataType dataType,
                                     DownloadEvent.EventType eventType, String pageKey, int limit) {
        var stream = globalLog.stream();

        if (sinceTimestamp != null) {
            stream = stream.filter(e -> e.timestamp() >= sinceTimestamp);
        }
        if (dataType != null) {
            stream = stream.filter(e -> e.dataType() == dataType);
        }
        if (eventType != null) {
            stream = stream.filter(e -> e.eventType() == eventType);
        }
        if (pageKey != null && !pageKey.isEmpty()) {
            stream = stream.filter(e -> e.pageKey().contains(pageKey));
        }

        return stream.limit(limit).collect(Collectors.toList());
    }

    /**
     * Get statistics about download activity.
     */
    public DownloadStatistics getStatistics(int activePages, int totalRecordCount) {
        long now = System.currentTimeMillis();
        long fiveMinutesAgo = now - 5 * 60 * 1000;

        int totalEvents = globalLog.size();
        int eventsLast5Minutes = 0;
        int errorsLast5Minutes = 0;
        long totalLoadTime = 0;
        int loadCount = 0;

        Map<DataType, Integer> byDataType = new EnumMap<>(DataType.class);
        Map<DownloadEvent.EventType, Integer> byEventType = new EnumMap<>(DownloadEvent.EventType.class);

        for (DownloadEvent event : globalLog) {
            // Count by type
            byDataType.merge(event.dataType(), 1, Integer::sum);
            byEventType.merge(event.eventType(), 1, Integer::sum);

            // Recent events
            if (event.timestamp() >= fiveMinutesAgo) {
                eventsLast5Minutes++;
                if (event.isError()) {
                    errorsLast5Minutes++;
                }
            }

            // Load times
            if (event.eventType() == DownloadEvent.EventType.LOAD_COMPLETED ||
                event.eventType() == DownloadEvent.EventType.UPDATE_COMPLETED) {
                Long duration = event.getDurationMs();
                if (duration != null) {
                    totalLoadTime += duration;
                    loadCount++;
                }
            }
        }

        double avgLoadTime = loadCount > 0 ? (double) totalLoadTime / loadCount : 0.0;

        return new DownloadStatistics(
            totalEvents,
            eventsLast5Minutes,
            errorsLast5Minutes,
            byDataType,
            byEventType,
            avgLoadTime,
            activePages,
            totalRecordCount
        );
    }

    // ========== Listener Management ==========

    /**
     * Add a listener for new events.
     */
    public void addListener(Consumer<DownloadEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(Consumer<DownloadEvent> listener) {
        listeners.remove(listener);
    }

    // ========== Maintenance ==========

    /**
     * Clear all logs.
     */
    public void clear() {
        globalLog.clear();
        pageLogs.clear();
    }

    /**
     * Clear logs for a specific page.
     */
    public void clearPageLog(String pageKey) {
        pageLogs.remove(pageKey);
    }

    /**
     * Get the number of tracked pages.
     */
    public int getTrackedPageCount() {
        return pageLogs.size();
    }

    /**
     * Get all tracked page keys.
     */
    public Set<String> getTrackedPageKeys() {
        return new HashSet<>(pageLogs.keySet());
    }
}
