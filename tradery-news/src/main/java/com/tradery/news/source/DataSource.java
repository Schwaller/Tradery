package com.tradery.news.source;

import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaRegistry;

import java.time.Duration;
import java.util.List;

/**
 * Pluggable data source that produces entities and/or relationships.
 * Each source declares what schema types it produces and handles its own fetching.
 */
public interface DataSource {

    /** Unique ID, also used as EntityStore source tag. */
    String id();

    /** Display name for UI. */
    String name();

    /** SchemaType IDs this source produces (entity types). */
    List<String> producedEntityTypes();

    /** SchemaType IDs this source produces (relationship types). */
    List<String> producedRelationshipTypes();

    /** How long data is fresh. Duration.ZERO = manual trigger only. */
    Duration cacheTTL();

    /** Do the work — fetch data and store it. */
    FetchResult fetch(FetchContext ctx);

    /** Optional: seed missing schema types on registration. */
    default void seedSchemaTypes(SchemaRegistry registry) {}

    // ==================== Supporting types ====================

    record FetchContext(
        EntityStore entityStore,
        SchemaRegistry schemaRegistry,
        ProgressCallback progress
    ) {}

    record FetchResult(
        int entitiesAdded,
        int relationshipsAdded,
        String message
    ) {}

    @FunctionalInterface
    interface ProgressCallback {
        void update(String message, int percentComplete);

        static ProgressCallback noop() {
            return (msg, pct) -> {};
        }
    }
}
