package com.tradery.news.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.tradery.news.ui.coin.CoinEntity;
import com.tradery.news.ui.coin.CoinRelationship;
import com.tradery.news.ui.coin.EntitySearchProcessor;
import com.tradery.news.ui.coin.EntitySearchProcessor.DiscoveredEntity;
import com.tradery.news.ui.coin.EntityStore;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Handler for AI entity discovery endpoints.
 */
public class DiscoverHandler extends IntelApiHandlerBase {

    private final EntityStore entityStore;
    private final EntitySearchProcessor searchProcessor;

    public DiscoverHandler(EntityStore entityStore, EntitySearchProcessor searchProcessor) {
        this.entityStore = entityStore;
        this.searchProcessor = searchProcessor;
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

    // POST /entity/{id}/discover?type=INVESTED_IN
    private void handleSearch(HttpExchange exchange, CoinEntity entity) throws IOException {
        if (!checkMethod(exchange, "POST")) return;

        if (!searchProcessor.isAvailable()) {
            sendError(exchange, 503, "AI provider not available");
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        CoinRelationship.Type relType = null;
        if (params.containsKey("type")) {
            try {
                relType = CoinRelationship.Type.valueOf(params.get("type").toUpperCase());
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "Invalid relationship type: " + params.get("type"));
                return;
            }
        }

        EntitySearchProcessor.SearchResult result = searchProcessor.searchRelated(entity, relType);

        if (result.hasError()) {
            sendError(exchange, 500, result.error());
            return;
        }

        ArrayNode arr = mapper.createArrayNode();
        for (DiscoveredEntity de : result.entities()) {
            ObjectNode node = mapper.createObjectNode();
            node.put("name", de.name());
            if (de.symbol() != null) node.put("symbol", de.symbol());
            node.put("type", de.type().name());
            node.put("relationshipType", de.relationshipType().name());
            node.put("reason", de.reason());
            node.put("confidence", de.confidence());
            node.put("generatedId", de.generateId());
            node.put("alreadyExists", entityStore.entityExists(de.generateId()));
            arr.add(node);
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("entityId", entity.id());
        response.put("entityName", entity.name());
        response.put("count", result.entities().size());
        response.set("discovered", arr);
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
            String typeStr = node.path("type").asText("COIN");
            String relTypeStr = node.path("relationshipType").asText("PARTNER");
            String reason = node.path("reason").asText(null);

            if (name == null || name.isEmpty()) continue;

            CoinEntity.Type type;
            CoinRelationship.Type relType;
            try {
                type = CoinEntity.Type.valueOf(typeStr.toUpperCase());
                relType = CoinRelationship.Type.valueOf(relTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                continue; // Skip invalid types
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

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        result.put("addedEntities", addedEntities);
        result.put("addedRelationships", addedRelationships);
        sendJson(exchange, 200, result);
    }

    /**
     * Create relationship with correct direction based on relationship type,
     * mirroring EntitySearchDialog.createRelationship logic.
     */
    private CoinRelationship createDirectedRelationship(CoinEntity source, String targetId,
                                                         CoinRelationship.Type relType, String note) {
        return switch (relType) {
            case ETF_TRACKS, ETP_TRACKS ->
                // ETF tracks COIN: ETF -> COIN
                new CoinRelationship(targetId, source.id(), relType, note);
            case INVESTED_IN ->
                // VC invested in COIN: VC -> COIN
                source.type() == CoinEntity.Type.VC
                    ? new CoinRelationship(source.id(), targetId, relType, note)
                    : new CoinRelationship(targetId, source.id(), relType, note);
            case L2_OF ->
                // L2 built on L1: L2 -> L1
                source.type() == CoinEntity.Type.L2
                    ? new CoinRelationship(source.id(), targetId, relType, note)
                    : new CoinRelationship(targetId, source.id(), relType, note);
            default ->
                new CoinRelationship(source.id(), targetId, relType, note);
        };
    }
}
