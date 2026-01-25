package com.tradery.model;

/**
 * Interface for entities that have a unique string identifier.
 * Used by JsonStore for generic persistence operations.
 */
public interface Identifiable {
    /**
     * Get the unique identifier for this entity.
     */
    String getId();
}
