package com.tradery.ai.pipeline.schema;

import java.util.Map;

/**
 * An entity discovered by the pipeline.
 */
public record DiscoveredEntity(
    String name,
    String symbol,
    String typeId,
    String relationshipTypeId,
    String reason,
    double confidence,
    Map<String, String> attributes
) {
    public DiscoveredEntity(String name, String symbol, String typeId,
                            String relationshipTypeId, String reason, double confidence) {
        this(name, symbol, typeId, relationshipTypeId, reason, confidence, Map.of());
    }

    /**
     * Return a copy with a different typeId.
     */
    public DiscoveredEntity withTypeId(String newTypeId) {
        return new DiscoveredEntity(name, symbol, newTypeId, relationshipTypeId, reason, confidence, attributes);
    }

    /**
     * Generate a kebab-case ID from the entity name.
     */
    public String generateId() {
        String base = name.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
        return base.length() > 50 ? base.substring(0, 50) : base;
    }
}
