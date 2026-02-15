package com.tradery.ai.pipeline.schema;

/**
 * Represents an existing entity for deduplication matching.
 */
public record ExistingEntity(String id, String name, String symbol, String typeId) {}
