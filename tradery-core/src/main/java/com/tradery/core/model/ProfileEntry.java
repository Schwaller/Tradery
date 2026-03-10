package com.tradery.core.model;

import java.util.Map;

/**
 * Wire format for volume profile data, shared between data service and clients.
 * Each entry represents one profile window (e.g., a 5m or 1h window).
 * Levels are keyed by tick index (String) with [buyVolume, sellVolume] values.
 */
public record ProfileEntry(
    long windowStart,
    double tickSize,
    double totalBuyVolume,
    double totalSellVolume,
    Map<String, double[]> levels
) {}
