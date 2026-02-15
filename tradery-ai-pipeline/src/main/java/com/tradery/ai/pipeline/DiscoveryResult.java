package com.tradery.ai.pipeline;

import com.tradery.ai.pipeline.schema.DiscoveredEntity;
import com.tradery.ai.pipeline.schema.SchemaSuggestion;

import java.util.List;

/**
 * Output from the discovery pipeline.
 */
public record DiscoveryResult(
    List<DiscoveredEntity> entities,
    List<SchemaSuggestion> schemaSuggestions,
    PipelineMetadata metadata,
    String error
) {
    public boolean isSuccess() {
        return error == null;
    }

    public static DiscoveryResult success(List<DiscoveredEntity> entities,
                                           List<SchemaSuggestion> schemaSuggestions,
                                           PipelineMetadata metadata) {
        return new DiscoveryResult(entities, schemaSuggestions, metadata, null);
    }

    public static DiscoveryResult failure(String error, PipelineMetadata metadata) {
        return new DiscoveryResult(List.of(), List.of(), metadata, error);
    }
}
