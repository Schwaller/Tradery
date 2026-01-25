package com.tradery.forge.data.log;

import com.tradery.forge.data.DataType;

import java.util.Map;

/**
 * Statistics about download activity for MCP/API consumption.
 */
public record DownloadStatistics(
    int totalEvents,
    int eventsLast5Minutes,
    int errorsLast5Minutes,
    Map<DataType, Integer> eventsByType,
    Map<DownloadEvent.EventType, Integer> eventsByEventType,
    double avgLoadTimeMs,
    int activePages,
    int totalRecordCount
) {

    /**
     * Check if there are recent errors.
     */
    public boolean hasRecentErrors() {
        return errorsLast5Minutes > 0;
    }

    /**
     * Get a health status string.
     */
    public String getHealthStatus() {
        if (errorsLast5Minutes > 5) {
            return "unhealthy";
        } else if (errorsLast5Minutes > 0) {
            return "degraded";
        }
        return "healthy";
    }
}
