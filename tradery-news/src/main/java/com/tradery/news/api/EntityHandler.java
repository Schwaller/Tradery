package com.tradery.news.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.tradery.news.ui.coin.CoinEntity;
import com.tradery.news.ui.coin.CoinRelationship;
import com.tradery.news.ui.coin.EntityStore;

import java.io.IOException;
import java.util.*;

/**
 * Handler for entity and relationship CRUD endpoints.
 */
public class EntityHandler extends IntelApiHandlerBase {

    private final EntityStore entityStore;

    public EntityHandler(EntityStore entityStore) {
        this.entityStore = entityStore;
    }

    // GET /entities?type=COIN&search=sol
    public void handleEntities(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "GET")) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String typeFilter = params.get("type");
        String search = params.get("search");

        List<CoinEntity> all = entityStore.loadAllEntities();

        List<CoinEntity> filtered = all.stream()
            .filter(e -> typeFilter == null || e.type().name().equalsIgnoreCase(typeFilter))
            .filter(e -> search == null || matchesSearch(e, search))
            .toList();

        ArrayNode arr = mapper.createArrayNode();
        for (CoinEntity entity : filtered) {
            arr.add(serializeEntity(entity));
        }
        sendJson(exchange, 200, mapper.createObjectNode().set("entities", arr));
    }

    // Routes under /entity/
    public void handleEntity(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;

        String[] parts = pathParts(exchange);
        String method = exchange.getRequestMethod().toUpperCase();

        // POST /entity (create)
        if (parts.length == 2 && "POST".equals(method)) {
            handleCreateEntity(exchange);
            return;
        }

        if (parts.length < 3) {
            sendError(exchange, 400, "Missing entity ID");
            return;
        }

        String entityId = parts[2];

        // GET /entity/{id}/graph?depth=1
        if (parts.length >= 4 && "graph".equals(parts[3])) {
            handleEntityGraph(exchange, entityId);
            return;
        }

        // Routes handled by DiscoverHandler: /entity/{id}/discover
        // (registered separately)

        // GET /entity/{id}
        if ("GET".equals(method)) {
            handleGetEntity(exchange, entityId);
            return;
        }

        // DELETE /entity/{id}
        if ("DELETE".equals(method)) {
            handleDeleteEntity(exchange, entityId);
            return;
        }

        sendError(exchange, 405, "Method not allowed");
    }

    private void handleGetEntity(HttpExchange exchange, String entityId) throws IOException {
        CoinEntity entity = entityStore.getEntity(entityId);
        if (entity == null) {
            sendError(exchange, 404, "Entity not found: " + entityId);
            return;
        }

        ObjectNode json = serializeEntity(entity);

        // Include relationships
        List<CoinRelationship> rels = entityStore.loadAllRelationships().stream()
            .filter(r -> r.fromId().equals(entityId) || r.toId().equals(entityId))
            .toList();

        ArrayNode relsArr = mapper.createArrayNode();
        for (CoinRelationship rel : rels) {
            relsArr.add(serializeRelationship(rel));
        }
        json.set("relationships", relsArr);

        sendJson(exchange, 200, json);
    }

    private void handleEntityGraph(HttpExchange exchange, String entityId) throws IOException {
        if (!checkMethod(exchange, "GET")) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        int depth = Integer.parseInt(params.getOrDefault("depth", "1"));
        depth = Math.min(depth, 3); // Cap at 3

        CoinEntity root = entityStore.getEntity(entityId);
        if (root == null) {
            sendError(exchange, 404, "Entity not found: " + entityId);
            return;
        }

        // BFS to collect neighborhood
        List<CoinRelationship> allRels = entityStore.loadAllRelationships();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        Queue<Integer> depths = new LinkedList<>();
        visited.add(entityId);
        queue.add(entityId);
        depths.add(0);

        List<CoinRelationship> graphRels = new ArrayList<>();

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depths.poll();

            for (CoinRelationship rel : allRels) {
                String neighbor = null;
                if (rel.fromId().equals(current)) neighbor = rel.toId();
                else if (rel.toId().equals(current)) neighbor = rel.fromId();

                if (neighbor != null) {
                    if (visited.contains(current) && (visited.contains(neighbor) || currentDepth < depth)) {
                        graphRels.add(rel);
                    }
                    if (!visited.contains(neighbor) && currentDepth < depth) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                        depths.add(currentDepth + 1);
                    }
                }
            }
        }

        // Collect entities
        ArrayNode entities = mapper.createArrayNode();
        for (String id : visited) {
            CoinEntity e = entityStore.getEntity(id);
            if (e != null) entities.add(serializeEntity(e));
        }

        // Deduplicate relationships
        Set<String> relKeys = new HashSet<>();
        ArrayNode relationships = mapper.createArrayNode();
        for (CoinRelationship rel : graphRels) {
            String key = rel.fromId() + "|" + rel.toId() + "|" + rel.type().name();
            if (relKeys.add(key)) {
                relationships.add(serializeRelationship(rel));
            }
        }

        ObjectNode result = mapper.createObjectNode();
        result.set("entities", entities);
        result.set("relationships", relationships);
        result.put("rootId", entityId);
        result.put("depth", depth);
        sendJson(exchange, 200, result);
    }

    private void handleCreateEntity(HttpExchange exchange) throws IOException {
        JsonNode body = readJsonBody(exchange);

        String name = body.path("name").asText(null);
        String symbol = body.path("symbol").asText(null);
        String typeStr = body.path("type").asText(null);

        if (name == null || name.isEmpty()) {
            sendError(exchange, 400, "Missing required field: name");
            return;
        }
        if (typeStr == null || typeStr.isEmpty()) {
            sendError(exchange, 400, "Missing required field: type");
            return;
        }

        CoinEntity.Type type;
        try {
            type = CoinEntity.Type.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Invalid entity type: " + typeStr);
            return;
        }

        // Generate ID from name
        String id = body.path("id").asText(null);
        if (id == null || id.isEmpty()) {
            id = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        }

        if (entityStore.entityExists(id)) {
            sendError(exchange, 409, "Entity already exists: " + id);
            return;
        }

        CoinEntity entity = new CoinEntity(id, name, symbol, type);
        entityStore.saveEntity(entity, "manual");

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        result.put("id", id);
        sendJson(exchange, 201, result);
    }

    private void handleDeleteEntity(HttpExchange exchange, String entityId) throws IOException {
        if (!entityStore.entityExists(entityId)) {
            sendError(exchange, 404, "Entity not found: " + entityId);
            return;
        }

        entityStore.deleteEntity(entityId);

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        result.put("id", entityId);
        sendJson(exchange, 200, result);
    }

    // POST /relationship
    public void handleCreateRelationship(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "POST")) return;

        JsonNode body = readJsonBody(exchange);

        String fromId = body.path("fromId").asText(null);
        String toId = body.path("toId").asText(null);
        String typeStr = body.path("type").asText(null);
        String note = body.path("note").asText(null);

        if (fromId == null || toId == null || typeStr == null) {
            sendError(exchange, 400, "Missing required fields: fromId, toId, type");
            return;
        }

        CoinRelationship.Type type;
        try {
            type = CoinRelationship.Type.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Invalid relationship type: " + typeStr);
            return;
        }

        if (!entityStore.entityExists(fromId)) {
            sendError(exchange, 404, "From entity not found: " + fromId);
            return;
        }
        if (!entityStore.entityExists(toId)) {
            sendError(exchange, 404, "To entity not found: " + toId);
            return;
        }

        CoinRelationship rel = new CoinRelationship(fromId, toId, type, note);
        entityStore.saveRelationship(rel, "manual");

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        sendJson(exchange, 201, result);
    }

    // DELETE /relationship?from=x&to=y&type=z
    public void handleDeleteRelationship(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "DELETE")) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String from = params.get("from");
        String to = params.get("to");
        String typeStr = params.get("type");

        if (from == null || to == null || typeStr == null) {
            sendError(exchange, 400, "Missing required params: from, to, type");
            return;
        }

        CoinRelationship.Type type;
        try {
            type = CoinRelationship.Type.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Invalid relationship type: " + typeStr);
            return;
        }

        entityStore.deleteRelationship(from, to, type);

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        sendJson(exchange, 200, result);
    }

    // ========== Serialization ==========

    private ObjectNode serializeEntity(CoinEntity entity) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", entity.id());
        node.put("name", entity.name());
        if (entity.symbol() != null) node.put("symbol", entity.symbol());
        node.put("type", entity.type().name());
        if (entity.parentId() != null) node.put("parentId", entity.parentId());
        if (entity.marketCap() > 0) node.put("marketCap", entity.marketCap());

        if (!entity.categories().isEmpty()) {
            ArrayNode cats = mapper.createArrayNode();
            entity.categories().forEach(cats::add);
            node.set("categories", cats);
        }

        return node;
    }

    private ObjectNode serializeRelationship(CoinRelationship rel) {
        ObjectNode node = mapper.createObjectNode();
        node.put("fromId", rel.fromId());
        node.put("toId", rel.toId());
        node.put("type", rel.type().name());
        if (rel.note() != null) node.put("note", rel.note());
        return node;
    }

    private boolean matchesSearch(CoinEntity entity, String search) {
        String lc = search.toLowerCase();
        return entity.name().toLowerCase().contains(lc)
            || (entity.symbol() != null && entity.symbol().toLowerCase().contains(lc))
            || entity.id().toLowerCase().contains(lc);
    }
}
