package com.tradery.news.source;

import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaRegistry;

import java.time.Duration;
import java.util.*;

/**
 * Central registry and orchestrator for pluggable data sources.
 * Manages source registration, cache validity, and refresh coordination.
 */
public class DataSourceRegistry {

    private final Map<String, DataSource> sources = new LinkedHashMap<>();
    private final EntityStore entityStore;
    private final SchemaRegistry schemaRegistry;

    public DataSourceRegistry(EntityStore entityStore, SchemaRegistry schemaRegistry) {
        this.entityStore = entityStore;
        this.schemaRegistry = schemaRegistry;
    }

    /** Register a source. Calls seedSchemaTypes on registration. */
    public void register(DataSource source) {
        sources.put(source.id(), source);
        source.seedSchemaTypes(schemaRegistry);
    }

    /** Refresh a single source. Returns null if source not found. */
    public DataSource.FetchResult refresh(String sourceId, boolean force, DataSource.ProgressCallback progress) {
        DataSource source = sources.get(sourceId);
        if (source == null) return null;

        Duration ttl = source.cacheTTL();
        if (!force && !ttl.isZero() && entityStore.isSourceCacheValid(sourceId, ttl)) {
            return new DataSource.FetchResult(0, 0, "Cache still valid");
        }

        return source.fetch(new DataSource.FetchContext(entityStore, schemaRegistry, progress));
    }

    /** Refresh all sources, checking cache for each. */
    public Map<String, DataSource.FetchResult> refreshAll(boolean force, DataSource.ProgressCallback progress) {
        Map<String, DataSource.FetchResult> results = new LinkedHashMap<>();
        for (DataSource source : sources.values()) {
            results.put(source.id(), refresh(source.id(), force, progress));
        }
        return results;
    }

    /** Get all registered sources. */
    public Collection<DataSource> getSources() {
        return Collections.unmodifiableCollection(sources.values());
    }

    /** Get a source by ID. */
    public DataSource getSource(String sourceId) {
        return sources.get(sourceId);
    }

    /** Find which sources produce a given schema type ID. */
    public List<DataSource> getSourcesForType(String schemaTypeId) {
        List<DataSource> result = new ArrayList<>();
        for (DataSource source : sources.values()) {
            if (source.producedEntityTypes().contains(schemaTypeId)
                || source.producedRelationshipTypes().contains(schemaTypeId)) {
                result.add(source);
            }
        }
        return result;
    }
}
