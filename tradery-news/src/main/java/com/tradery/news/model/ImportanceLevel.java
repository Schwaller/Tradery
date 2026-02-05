package com.tradery.news.model;

public enum ImportanceLevel {
    CRITICAL(5),    // ETF-level news, major hacks, regulatory actions
    HIGH(4),        // Significant moves, listings, partnerships
    MEDIUM(3),      // Notable updates, minor news
    LOW(2),         // Background noise
    NOISE(1);       // Filtered out

    private final int weight;

    ImportanceLevel(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
