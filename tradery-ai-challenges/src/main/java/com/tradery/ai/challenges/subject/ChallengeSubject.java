package com.tradery.ai.challenges.subject;

import com.tradery.ai.pipeline.schema.EntityDescriptor;

import java.util.Map;

/**
 * The thing an AI challenge is run against.
 * Implement this for your domain (entity, document, asset, etc.)
 * to make it challengeable.
 */
public interface ChallengeSubject {

    /** Unique identifier. */
    String id();

    /** Display name (used in prompts). */
    String name();

    /** Type identifier (e.g., "coin", "company", "document"). Used to filter applicable challenges. */
    String typeId();

    /** Key attributes that provide context for prompt building. */
    Map<String, String> attributes();

    /**
     * Optional symbol (e.g., ticker). May return null.
     */
    default String symbol() { return null; }

    /**
     * Convert to a pipeline EntityDescriptor for ENTITY_SET challenges.
     * Default implementation builds from the interface methods.
     */
    default EntityDescriptor toEntityDescriptor() {
        return new EntityDescriptor(id(), name(), symbol(), typeId(), attributes());
    }
}
