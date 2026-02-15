package com.tradery.ai.pipeline.matching;

/**
 * Reason why a fuzzy match was detected.
 */
public enum MatchReason {
    EXACT_ID("Exact ID match"),
    SYMBOL_MATCH("Same symbol"),
    NORMALIZED_NAME("Same name"),
    FUZZY_NAME("Similar name");

    private final String description;

    MatchReason(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
