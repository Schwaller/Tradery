package com.tradery.ai.pipeline.schema;

import java.util.Map;

/**
 * Describes the start entity for a discovery request.
 */
public record EntityDescriptor(
    String id,
    String name,
    String symbol,
    String typeId,
    Map<String, String> attributes
) {
    public EntityDescriptor(String id, String name, String symbol, String typeId) {
        this(id, name, symbol, typeId, Map.of());
    }
}
