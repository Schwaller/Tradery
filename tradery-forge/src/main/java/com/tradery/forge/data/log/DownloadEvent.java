package com.tradery.forge.data.log;

import com.tradery.data.page.DataType;

import java.util.Map;

/**
 * Immutable event record for download/data loading activities.
 * Used by DownloadLogStore to track page manager events.
 */
public record DownloadEvent(
    long timestamp,
    String pageKey,
    DataType dataType,
    EventType eventType,
    String message,
    Map<String, Object> metadata
) {

    /**
     * Types of download events.
     */
    public enum EventType {
        /** New page created */
        PAGE_CREATED,
        /** Data load started */
        LOAD_STARTED,
        /** Data load completed successfully */
        LOAD_COMPLETED,
        /** Data load or update failed */
        ERROR,
        /** Background update started */
        UPDATE_STARTED,
        /** Background update completed */
        UPDATE_COMPLETED,
        /** Page released (ref count reached 0) */
        PAGE_RELEASED,
        /** Listener added to page */
        LISTENER_ADDED,
        /** Listener removed from page */
        LISTENER_REMOVED,
        /** WebSocket/HTTP connection opened */
        CONNECTION_OPENED,
        /** WebSocket/HTTP connection closed */
        CONNECTION_CLOSED,
        /** Live update received */
        LIVE_UPDATE,
        /** Live candle closed */
        LIVE_CANDLE_CLOSED,
        /** Vision download started */
        VISION_DOWNLOAD_STARTED,
        /** Vision download progress */
        VISION_DOWNLOAD_PROGRESS,
        /** Vision download completed */
        VISION_DOWNLOAD_COMPLETED,
        /** API request started */
        API_REQUEST_STARTED,
        /** API request completed */
        API_REQUEST_COMPLETED
    }

    /**
     * Create an event with no metadata.
     */
    public static DownloadEvent of(String pageKey, DataType dataType, EventType eventType, String message) {
        return new DownloadEvent(System.currentTimeMillis(), pageKey, dataType, eventType, message, Map.of());
    }

    /**
     * Create an event with metadata.
     */
    public static DownloadEvent of(String pageKey, DataType dataType, EventType eventType,
                                   String message, Map<String, Object> metadata) {
        return new DownloadEvent(System.currentTimeMillis(), pageKey, dataType, eventType, message, metadata);
    }

    /**
     * Get duration from metadata if present.
     */
    public Long getDurationMs() {
        Object val = metadata.get("durationMs");
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return null;
    }

    /**
     * Get record count from metadata if present.
     */
    public Integer getRecordCount() {
        Object val = metadata.get("recordCount");
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return null;
    }

    /**
     * Get error message from metadata if present.
     */
    public String getErrorMessage() {
        Object val = metadata.get("errorMessage");
        return val != null ? val.toString() : null;
    }

    /**
     * Get consumer name from metadata if present.
     */
    public String getConsumerName() {
        Object val = metadata.get("consumerName");
        return val != null ? val.toString() : null;
    }

    /**
     * Check if this is an error event.
     */
    public boolean isError() {
        return eventType == EventType.ERROR;
    }

    /**
     * Check if this is a loading event (load or update started/completed).
     */
    public boolean isLoadingEvent() {
        return eventType == EventType.LOAD_STARTED ||
               eventType == EventType.LOAD_COMPLETED ||
               eventType == EventType.UPDATE_STARTED ||
               eventType == EventType.UPDATE_COMPLETED;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s %s: %s",
            eventType.name(), dataType.getDisplayName(), pageKey, message);
    }
}
