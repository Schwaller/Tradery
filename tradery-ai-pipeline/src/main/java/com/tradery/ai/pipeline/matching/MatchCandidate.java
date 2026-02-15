package com.tradery.ai.pipeline.matching;

/**
 * A potential match between a discovered entity and an existing entity.
 */
public record MatchCandidate(
    String existingId,
    String existingName,
    String existingTypeId,
    double score,
    MatchReason reason
) {}
