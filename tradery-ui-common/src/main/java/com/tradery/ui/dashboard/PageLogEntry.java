package com.tradery.ui.dashboard;

/**
 * A single log entry for a page. Apps provide these via
 * {@link DashboardWindow#getPageLog(String)}.
 */
public record PageLogEntry(
    long timestamp,
    String type,
    String message
) {}
