package com.tradery.news.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.tradery.ai.pipeline.schema.SchemaSuggestion;
import com.tradery.news.ui.coin.CoinEntity;
import com.tradery.news.ui.coin.CoinRelationship;
import com.tradery.news.ui.coin.EntitySearchProcessor;
import com.tradery.news.ui.coin.EntitySearchProcessor.DiscoveredEntity;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.coin.SchemaType;

import com.tradery.news.ui.IntelLogPanel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Handler for AI entity discovery endpoints.
 */
public class DiscoverHandler extends IntelApiHandlerBase {

    private final EntityStore entityStore;
    private final EntitySearchProcessor searchProcessor;
    private final SchemaRegistry schemaRegistry;

    public DiscoverHandler(EntityStore entityStore, EntitySearchProcessor searchProcessor, SchemaRegistry schemaRegistry) {
        this.entityStore = entityStore;
        this.searchProcessor = searchProcessor;
        this.schemaRegistry = schemaRegistry;
    }

    // Routes under /entity/{id}/discover
    public void handleDiscover(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;

        String[] parts = pathParts(exchange);
        // /entity/{id}/discover or /entity/{id}/discover/apply
        if (parts.length < 4) {
            sendError(exchange, 400, "Invalid path");
            return;
        }

        String entityId = parts[2];
        CoinEntity entity = entityStore.getEntity(entityId);
        if (entity == null) {
            sendError(exchange, 404, "Entity not found: " + entityId);
            return;
        }

        boolean isApply = parts.length >= 5 && "apply".equals(parts[4]);

        if (isApply) {
            handleApply(exchange, entity);
        } else {
            handleSearch(exchange, entity);
        }
    }

    // POST /entity/{id}/discover?type=invested_in
    private void handleSearch(HttpExchange exchange, CoinEntity entity) throws IOException {
        if (!checkMethod(exchange, "POST")) return;

        if (!searchProcessor.isAvailable()) {
            sendError(exchange, 503, "AI provider not available");
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String relTypeId = params.get("type");  // Schema type ID directly (e.g. "invested_in")

        IntelLogPanel.logAI("API: Discovering entities related to '" + entity.name() + "'" +
            (relTypeId != null ? " (" + relTypeId + ")" : ""));
        EntitySearchProcessor.SearchResult result = searchProcessor.searchRelated(entity, relTypeId);

        if (result.hasError()) {
            IntelLogPanel.logError("API: Discovery failed for '" + entity.name() + "': " + result.error());
            sendError(exchange, 500, result.error());
            return;
        }

        ArrayNode arr = mapper.createArrayNode();
        for (DiscoveredEntity de : result.entities()) {
            ObjectNode node = mapper.createObjectNode();
            node.put("name", de.name());
            if (de.symbol() != null) node.put("symbol", de.symbol());
            node.put("type", de.typeId());
            node.put("relationshipType", de.relationshipTypeId());
            node.put("reason", de.reason());
            node.put("confidence", de.confidence());
            node.put("generatedId", de.generateId());
            node.put("alreadyExists", entityStore.entityExists(de.generateId()));
            arr.add(node);
        }

        IntelLogPanel.logAI("API: Discovered " + result.entities().size() + " entities related to '" + entity.name() + "'");

        // Schema suggestions (types the AI wants but don't exist yet)
        ArrayNode suggestionsArr = mapper.createArrayNode();
        for (SchemaSuggestion s : result.schemaSuggestions()) {
            ObjectNode sNode = mapper.createObjectNode();
            sNode.put("typeId", s.typeId());
            sNode.put("suggestedName", s.suggestedName());
            sNode.put("entityCount", s.entityCount());
            ArrayNode examples = mapper.createArrayNode();
            s.exampleEntityNames().forEach(examples::add);
            sNode.set("exampleEntityNames", examples);
            suggestionsArr.add(sNode);
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("entityId", entity.id());
        response.put("entityName", entity.name());
        response.put("count", result.entities().size());
        response.set("discovered", arr);
        if (!suggestionsArr.isEmpty()) {
            response.set("schemaSuggestions", suggestionsArr);
        }
        sendJson(exchange, 200, response);
    }

    // POST /entity/{id}/discover/apply  body: {entities: [...]}
    private void handleApply(HttpExchange exchange, CoinEntity sourceEntity) throws IOException {
        if (!checkMethod(exchange, "POST")) return;

        JsonNode body = readJsonBody(exchange);
        JsonNode entitiesNode = body.path("entities");

        if (!entitiesNode.isArray() || entitiesNode.isEmpty()) {
            sendError(exchange, 400, "Missing or empty 'entities' array");
            return;
        }

        int addedEntities = 0;
        int addedRelationships = 0;

        for (JsonNode node : entitiesNode) {
            String name = node.path("name").asText(null);
            String symbol = node.path("symbol").asText(null);
            String typeId = node.path("type").asText("coin");
            String relTypeId = node.path("relationshipType").asText("partner");
            String reason = node.path("reason").asText(null);

            if (name == null || name.isEmpty()) continue;

            // Resolve CoinEntity.Type from string — fall back to COIN
            CoinEntity.Type type;
            try {
                type = CoinEntity.Type.valueOf(typeId.toUpperCase());
            } catch (IllegalArgumentException e) {
                type = CoinEntity.Type.COIN;
            }

            // Resolve CoinRelationship.Type from string — fall back to PARTNER
            CoinRelationship.Type relType;
            try {
                relType = CoinRelationship.Type.valueOf(relTypeId.toUpperCase());
            } catch (IllegalArgumentException e) {
                relType = CoinRelationship.Type.PARTNER;
            }

            // Generate ID
            String entityId = node.path("generatedId").asText(null);
            if (entityId == null || entityId.isEmpty()) {
                entityId = name.toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
            }

            // Create entity if it doesn't exist
            if (!entityStore.entityExists(entityId)) {
                CoinEntity newEntity = new CoinEntity(entityId, name, symbol, type);
                entityStore.saveEntity(newEntity, "ai-discovery");
                addedEntities++;
            }

            // Create relationship with correct direction
            CoinRelationship rel = createDirectedRelationship(
                sourceEntity, entityId, relType, reason);

            if (!entityStore.relationshipExists(rel.fromId(), rel.toId(), rel.type())) {
                entityStore.saveRelationship(rel, "ai-discovery");
                addedRelationships++;
            }
        }

        IntelLogPanel.logData("API: Applied discovery for '" + sourceEntity.name() + "': " +
            addedEntities + " entities, " + addedRelationships + " relationships");

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        result.put("addedEntities", addedEntities);
        result.put("addedRelationships", addedRelationships);
        sendJson(exchange, 200, result);
    }

    /**
     * Create relationship with correct direction based on SchemaType metadata.
     */
    private CoinRelationship createDirectedRelationship(CoinEntity source, String targetId,
                                                         CoinRelationship.Type relType, String note) {
        String sourceTypeId = source.type().name().toLowerCase();
        SchemaType relSchema = schemaRegistry != null ? schemaRegistry.getType(relType.name().toLowerCase()) : null;
        if (relSchema != null) {
            return relSchema.createDirected(source.id(), sourceTypeId, targetId, relType, note);
        }
        return new CoinRelationship(source.id(), targetId, relType, note);
    }
}
