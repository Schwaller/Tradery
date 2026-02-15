package com.tradery.news.ui.coin;

import com.tradery.ai.AiClient;
import com.tradery.ai.AiProfile;
import com.tradery.ai.pipeline.DiscoveryPipeline;
import com.tradery.ai.pipeline.DiscoveryRequest;
import com.tradery.ai.pipeline.DiscoveryResult;
import com.tradery.ai.pipeline.schema.EntityDescriptor;
import com.tradery.ai.pipeline.schema.EntityTypeDescriptor;
import com.tradery.ai.pipeline.schema.RelationshipTypeDescriptor;
import com.tradery.ai.pipeline.schema.SchemaSuggestion;
import com.tradery.news.ui.IntelLogPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * AI processor for entity discovery using the discovery pipeline.
 * Thin adapter that converts between domain types and pipeline schema descriptors.
 * All type resolution is schema-driven — no hardcoded entity/relationship type enums.
 */
public class EntitySearchProcessor {

    private static final Logger log = LoggerFactory.getLogger(EntitySearchProcessor.class);

    private final AiClient aiClient;
    private final SchemaRegistry schemaRegistry;

    public EntitySearchProcessor() {
        this(null);
    }

    public EntitySearchProcessor(SchemaRegistry schemaRegistry) {
        this.aiClient = AiClient.getInstance();
        this.schemaRegistry = schemaRegistry;
    }

    public boolean isAvailable() {
        return aiClient.isAvailable();
    }

    public String getProviderName() {
        return aiClient.getProviderName();
    }

    /**
     * Search for entities related to the given entity.
     * @param relTypeId schema relationship type ID (e.g. "founded_by"), or null for all
     */
    public SearchResult searchRelated(CoinEntity entity, String relTypeId) {
        return searchRelated(entity, relTypeId, null);
    }

    /**
     * Search for entities related to the given entity with progress logging.
     */
    public SearchResult searchRelated(CoinEntity entity, String relTypeId, Consumer<String> logger) {
        String searchType = relTypeId != null ? relTypeId : "all relationships";
        String provider = getProviderName();
        if (logger != null) {
            logger.accept("[" + provider + "] Finding " + searchType + " for " + entity.name());
        }
        IntelLogPanel.logAI("Pipeline: Find " + searchType + " for " + entity.name() + " (" + entity.type() + ")");

        try {
            DiscoveryRequest request = buildRequest(entity, relTypeId);
            DiscoveryPipeline pipeline = DiscoveryPipeline.create("quick");

            if (logger != null) logger.accept("Running discovery pipeline...");
            DiscoveryResult result = pipeline.execute(request, (stepName, message, fraction) -> {
                if (logger != null) logger.accept("[" + stepName + "] " + message);
            });

            SearchResult searchResult = toSearchResult(result);
            logResults(searchResult, logger);
            return searchResult;
        } catch (Exception e) {
            log.error("Failed to search for related entities: {}", e.getMessage());
            IntelLogPanel.logError("AI error: " + e.getMessage());
            return new SearchResult(List.of(), "Error: " + e.getMessage());
        }
    }

    /**
     * Deep search using the default AI profile.
     */
    public SearchResult searchRelatedDeep(CoinEntity entity, String relTypeId, Consumer<String> logger) {
        return searchRelatedDeep(entity, relTypeId, null, logger);
    }

    /**
     * Deep search using a specific AI profile.
     * Uses the "deep" pipeline which includes web research.
     */
    public SearchResult searchRelatedDeep(CoinEntity entity, String relTypeId,
                                           AiProfile profile, Consumer<String> logger) {
        String searchType = relTypeId != null ? relTypeId : "all relationships";
        String providerName = profile != null ? profile.getProvider().name() : getProviderName();
        if (logger != null) {
            logger.accept("[" + providerName + "] Deep search for " + searchType);
        }
        IntelLogPanel.logAI("Deep pipeline: Find " + searchType + " for " + entity.name());

        try {
            DiscoveryRequest request = buildRequest(entity, relTypeId);
            DiscoveryPipeline pipeline = DiscoveryPipeline.create("deep");

            if (logger != null) logger.accept("Running deep discovery pipeline...");
            DiscoveryResult result = pipeline.execute(request, (stepName, message, fraction) -> {
                if (logger != null) logger.accept("[" + stepName + "] " + message);
            });

            SearchResult searchResult = toSearchResult(result);
            logResults(searchResult, logger);
            return searchResult;
        } catch (Exception e) {
            log.error("Deep search failed: {}", e.getMessage());
            IntelLogPanel.logError("AI error: " + e.getMessage());
            return new SearchResult(List.of(), "Error: " + e.getMessage());
        }
    }

    // ==================== Conversion helpers ====================

    private DiscoveryRequest buildRequest(CoinEntity entity, String relTypeId) {
        EntityDescriptor descriptor = toDescriptor(entity);

        // Build relationship type descriptors
        List<RelationshipTypeDescriptor> relTypes = new ArrayList<>();
        List<EntityTypeDescriptor> entityTypes = new ArrayList<>();

        if (relTypeId != null && schemaRegistry != null) {
            SchemaType schema = schemaRegistry.getType(relTypeId);
            if (schema != null) {
                relTypes.add(toRelTypeDescriptor(schema));
            }
        } else if (schemaRegistry != null) {
            String sourceTypeId = entity.type().name().toLowerCase();
            for (SchemaType rel : schemaRegistry.getRelationshipTypesFor(sourceTypeId)) {
                relTypes.add(toRelTypeDescriptor(rel));
            }
        }

        // All entity types from schema
        if (schemaRegistry != null) {
            for (SchemaType et : schemaRegistry.entityTypes()) {
                entityTypes.add(new EntityTypeDescriptor(et.id(), et.name()));
            }
        }

        // Add all relationship type IDs too
        if (schemaRegistry != null && relTypeId == null) {
            for (SchemaType rel : schemaRegistry.relationshipTypes()) {
                // Only add if not already present
                if (relTypes.stream().noneMatch(r -> r.id().equals(rel.id()))) {
                    relTypes.add(toRelTypeDescriptor(rel));
                }
            }
        }

        return DiscoveryRequest.builder()
            .entity(descriptor)
            .relationshipTypes(relTypes.isEmpty() ? null : relTypes)
            .targetEntityTypes(entityTypes.isEmpty() ? null : entityTypes)
            .systemContext("You are a cryptocurrency and financial research assistant.")
            .build();
    }

    private EntityDescriptor toDescriptor(CoinEntity entity) {
        var attrs = new java.util.LinkedHashMap<String, String>();
        if (!entity.categories().isEmpty()) {
            attrs.put("categories", String.join(", ", entity.categories()));
        }
        if (entity.marketCap() > 0) {
            attrs.put("marketCap", "$" + formatMarketCap(entity.marketCap()));
        }
        return new EntityDescriptor(
            entity.id(),
            entity.name(),
            entity.symbol(),
            entity.type().name().toLowerCase(),
            attrs
        );
    }

    private RelationshipTypeDescriptor toRelTypeDescriptor(SchemaType schema) {
        return new RelationshipTypeDescriptor(
            schema.id(),
            schema.fromTypeId(),
            schema.toTypeId(),
            schema.searchDescription(),
            schema.inverseSearchDescription(),
            schema.searchHints(),
            schema.inverseSearchHints()
        );
    }

    private SearchResult toSearchResult(DiscoveryResult result) {
        if (!result.isSuccess() && result.entities().isEmpty()) {
            return new SearchResult(List.of(), List.of(), result.error());
        }

        // Pass through string type IDs directly — no enum conversion
        List<DiscoveredEntity> entities = new ArrayList<>();
        for (var de : result.entities()) {
            entities.add(new DiscoveredEntity(
                de.name(), de.symbol(), de.typeId(), de.relationshipTypeId(),
                de.reason(), de.confidence()
            ));
        }

        return new SearchResult(entities, result.schemaSuggestions(), result.error());
    }

    private void logResults(SearchResult result, Consumer<String> logger) {
        if (!result.entities().isEmpty()) {
            StringBuilder sb = new StringBuilder("Found: ");
            int count = Math.min(5, result.entities().size());
            for (int i = 0; i < count; i++) {
                if (i > 0) sb.append(", ");
                DiscoveredEntity e = result.entities().get(i);
                sb.append(e.name());
                if (e.symbol() != null) sb.append(" (").append(e.symbol()).append(")");
            }
            if (result.entities().size() > 5) {
                sb.append(" +").append(result.entities().size() - 5).append(" more");
            }
            IntelLogPanel.logData(sb.toString());
            if (logger != null) logger.accept(sb.toString());
        }
    }

    private String formatMarketCap(double marketCap) {
        if (marketCap >= 1_000_000_000_000L) {
            return String.format("%.1fT", marketCap / 1_000_000_000_000L);
        } else if (marketCap >= 1_000_000_000L) {
            return String.format("%.1fB", marketCap / 1_000_000_000L);
        } else if (marketCap >= 1_000_000L) {
            return String.format("%.1fM", marketCap / 1_000_000L);
        }
        return String.format("%.0f", marketCap);
    }

    /**
     * Result of an entity search operation.
     */
    public record SearchResult(
        List<DiscoveredEntity> entities,
        List<SchemaSuggestion> schemaSuggestions,
        String error
    ) {
        public SearchResult(List<DiscoveredEntity> entities, String error) {
            this(entities, List.of(), error);
        }

        public boolean hasError() {
            return error != null && !error.isEmpty();
        }
    }

    /**
     * An entity discovered through AI search.
     * Uses string-based type IDs resolved from the SchemaRegistry, not hardcoded enums.
     */
    public record DiscoveredEntity(
        String name,
        String symbol,
        String typeId,
        String relationshipTypeId,
        String reason,
        double confidence
    ) {
        /**
         * Generate a unique ID for this entity based on name.
         */
        public String generateId() {
            String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
            return base.length() > 50 ? base.substring(0, 50) : base;
        }

        /**
         * Resolve the display color from the schema registry.
         * Falls back to a neutral gray if the type isn't found.
         */
        public Color resolveColor(SchemaRegistry registry) {
            if (registry == null) return Color.GRAY;
            SchemaType type = registry.getType(typeId);
            return type != null ? type.color() : Color.GRAY;
        }

        /**
         * Resolve the display name of the entity type from the schema registry.
         */
        public String resolveTypeName(SchemaRegistry registry) {
            if (registry == null) return typeId;
            SchemaType type = registry.getType(typeId);
            return type != null ? type.name() : typeId;
        }

        /**
         * Try to resolve to a CoinEntity.Type enum. Returns null if no match.
         */
        public CoinEntity.Type resolveCoinEntityType() {
            if (typeId == null) return null;
            try {
                return CoinEntity.Type.valueOf(typeId.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

    }
}
