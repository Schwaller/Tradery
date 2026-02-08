package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a Fear & Greed Index data point from Alternative.me.
 * Daily sentiment score from 0 (extreme fear) to 100 (extreme greed).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FearGreedIndex(
    int value,                // 0-100 sentiment score
    String classification,    // "Extreme Fear", "Fear", "Neutral", "Greed", "Extreme Greed"
    long timestamp            // Unix timestamp in milliseconds
) {}
