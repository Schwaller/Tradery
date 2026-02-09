package com.tradery.news.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.tradery.news.ui.IntelLogPanel;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaAttribute;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.coin.SchemaType;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Handler for ERD schema type endpoints.
 *
 * Endpoints:
 *   GET    /schema/types                       - List all types with full metadata
 *   POST   /schema/type                        - Create entity or relationship type
 *   GET    /schema/type/{id}                   - Get single type
 *   POST   /schema/type/{id}                   - Update type
 *   DELETE /schema/type/{id}                   - Delete type
 *   POST   /schema/type/{id}/attribute         - Add/update attribute
 *   DELETE /schema/type/{id}/attribute/{name}  - Remove attribute
 */
public class SchemaHandler extends IntelApiHandlerBase {

    private final EntityStore entityStore;
    private final SchemaRegistry schemaRegistry;

    public SchemaHandler(EntityStore entityStore, SchemaRegistry schemaRegistry) {
        this.entityStore = entityStore;
        this.schemaRegistry = schemaRegistry;
    }

    // GET /schema/types
    public void handleSchema(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "GET")) return;

        List<SchemaType> types = entityStore.loadSchemaTypes();
        ArrayNode arr = mapper.createArrayNode();

        for (SchemaType type : types) {
            arr.add(serializeType(type));
        }

        ObjectNode result = mapper.createObjectNode();
        result.set("types", arr);
        sendJson(exchange, 200, result);
    }

    // Route /schema/type and /schema/type/{id}[/attribute[/{name}]]
    public void routeSchemaType(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;

        String[] parts = pathParts(exchange);
        String method = exchange.getRequestMethod().toUpperCase();

        // POST /schema/type (create)
        if (parts.length == 3 && "POST".equals(method)) {
            handleCreateType(exchange);
            return;
        }

        if (parts.length < 4) {
            sendError(exchange, 400, "Missing type ID");
            return;
        }

        String typeId = parts[3];

        // /schema/type/{id}/attribute[/{name}]
        if (parts.length >= 5 && "attribute".equals(parts[4])) {
            if ("POST".equals(method)) {
                handleAddAttribute(exchange, typeId);
            } else if ("DELETE".equals(method) && parts.length >= 6) {
                handleRemoveAttribute(exchange, typeId, parts[5]);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
            return;
        }

        // GET /schema/type/{id}
        if ("GET".equals(method)) {
            handleGetType(exchange, typeId);
            return;
        }

        // POST /schema/type/{id} (update)
        if ("POST".equals(method)) {
            handleUpdateType(exchange, typeId);
            return;
        }

        // DELETE /schema/type/{id}
        if ("DELETE".equals(method)) {
            handleDeleteType(exchange, typeId);
            return;
        }

        sendError(exchange, 405, "Method not allowed");
    }

    // GET /schema/type/{id}
    private void handleGetType(HttpExchange exchange, String typeId) throws IOException {
        SchemaType type = schemaRegistry.getType(typeId);
        if (type == null) {
            sendError(exchange, 404, "Type not found: " + typeId);
            return;
        }
        sendJson(exchange, 200, serializeType(type));
    }

    // POST /schema/type — create new type
    private void handleCreateType(HttpExchange exchange) throws IOException {
        JsonNode body = readJsonBody(exchange);

        String id = body.path("id").asText(null);
        String name = body.path("name").asText(null);
        String kind = body.path("kind").asText(null);
        String colorHex = body.path("color").asText("#808080");

        if (id == null || id.isEmpty()) {
            sendError(exchange, 400, "Missing required field: id");
            return;
        }
        if (name == null || name.isEmpty()) {
            sendError(exchange, 400, "Missing required field: name");
            return;
        }
        if (kind == null || (!SchemaType.KIND_ENTITY.equals(kind) && !SchemaType.KIND_RELATIONSHIP.equals(kind))) {
            sendError(exchange, 400, "kind must be 'entity' or 'relationship'");
            return;
        }
        if (schemaRegistry.getType(id) != null) {
            sendError(exchange, 409, "Type already exists: " + id);
            return;
        }

        Color color;
        try {
            color = Color.decode(colorHex);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid color hex: " + colorHex);
            return;
        }

        SchemaType type = new SchemaType(id, name, color, kind);
        type.setDisplayOrder(body.path("displayOrder").asInt(0));
        type.setHasMarketCap(body.path("hasMarketCap").asBoolean(false));

        // Relationship-specific fields
        if (SchemaType.KIND_RELATIONSHIP.equals(kind)) {
            if (body.has("fromTypeId")) type.setFromTypeId(body.get("fromTypeId").asText());
            if (body.has("toTypeId")) type.setToTypeId(body.get("toTypeId").asText());
            if (body.has("label")) type.setLabel(body.get("label").asText());
            if (body.has("inverseLabel")) type.setInverseLabel(body.get("inverseLabel").asText());
            if (body.has("pluralLabel")) type.setPluralLabel(body.get("pluralLabel").asText());
            if (body.has("inversePluralLabel")) type.setInversePluralLabel(body.get("inversePluralLabel").asText());
            if (body.has("searchDescription")) type.setSearchDescription(body.get("searchDescription").asText());
            if (body.has("inverseSearchDescription")) type.setInverseSearchDescription(body.get("inverseSearchDescription").asText());
        }

        // Create with default name attribute for entity types
        if (SchemaType.KIND_ENTITY.equals(kind)) {
            type.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0));
        }

        schemaRegistry.save(type);
        for (SchemaAttribute attr : type.attributes()) {
            schemaRegistry.addAttribute(id, attr);
        }

        IntelLogPanel.logData("API: Created schema type '" + name + "' (" + kind + ")");

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        result.put("id", id);
        sendJson(exchange, 201, result);
    }

    // POST /schema/type/{id} — update existing type
    private void handleUpdateType(HttpExchange exchange, String typeId) throws IOException {
        SchemaType type = schemaRegistry.getType(typeId);
        if (type == null) {
            sendError(exchange, 404, "Type not found: " + typeId);
            return;
        }

        JsonNode body = readJsonBody(exchange);

        if (body.has("name")) type.setName(body.get("name").asText());
        if (body.has("color")) {
            try {
                type.setColor(Color.decode(body.get("color").asText()));
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Invalid color hex");
                return;
            }
        }
        if (body.has("displayOrder")) type.setDisplayOrder(body.get("displayOrder").asInt());
        if (body.has("hasMarketCap")) type.setHasMarketCap(body.get("hasMarketCap").asBoolean());

        // Relationship-specific fields
        if (type.isRelationship()) {
            if (body.has("fromTypeId")) type.setFromTypeId(body.get("fromTypeId").asText());
            if (body.has("toTypeId")) type.setToTypeId(body.get("toTypeId").asText());
            if (body.has("label")) type.setLabel(body.get("label").asText());
            if (body.has("inverseLabel")) type.setInverseLabel(body.get("inverseLabel").asText());
            if (body.has("pluralLabel")) type.setPluralLabel(body.get("pluralLabel").asText());
            if (body.has("inversePluralLabel")) type.setInversePluralLabel(body.get("inversePluralLabel").asText());
            if (body.has("searchDescription")) type.setSearchDescription(body.get("searchDescription").asText());
            if (body.has("inverseSearchDescription")) type.setInverseSearchDescription(body.get("inverseSearchDescription").asText());
        }

        schemaRegistry.save(type);
        IntelLogPanel.logData("API: Updated schema type '" + typeId + "'");

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        result.set("type", serializeType(type));
        sendJson(exchange, 200, result);
    }

    // DELETE /schema/type/{id}
    private void handleDeleteType(HttpExchange exchange, String typeId) throws IOException {
        SchemaType type = schemaRegistry.getType(typeId);
        if (type == null) {
            sendError(exchange, 404, "Type not found: " + typeId);
            return;
        }

        schemaRegistry.deleteType(typeId);
        IntelLogPanel.logData("API: Deleted schema type '" + typeId + "'");

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        result.put("id", typeId);
        sendJson(exchange, 200, result);
    }

    // POST /schema/type/{id}/attribute
    private void handleAddAttribute(HttpExchange exchange, String typeId) throws IOException {
        if (!checkMethod(exchange, "POST")) return;

        SchemaType type = schemaRegistry.getType(typeId);
        if (type == null) {
            sendError(exchange, 404, "Type not found: " + typeId);
            return;
        }

        JsonNode body = readJsonBody(exchange);
        String name = body.path("name").asText(null);
        String dataType = body.path("dataType").asText(SchemaAttribute.TEXT);
        boolean required = body.path("required").asBoolean(false);
        int displayOrder = body.path("displayOrder").asInt(type.attributes().size());

        if (name == null || name.isEmpty()) {
            sendError(exchange, 400, "Missing required field: name");
            return;
        }

        SchemaAttribute attr = new SchemaAttribute(name, dataType, required, displayOrder);
        schemaRegistry.addAttribute(typeId, attr);
        IntelLogPanel.logData("API: Added attribute '" + name + "' to type '" + typeId + "'");

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        result.put("typeId", typeId);
        result.put("attribute", name);
        sendJson(exchange, 201, result);
    }

    // DELETE /schema/type/{id}/attribute/{name}
    private void handleRemoveAttribute(HttpExchange exchange, String typeId, String attrName) throws IOException {
        if (!checkMethod(exchange, "DELETE")) return;

        SchemaType type = schemaRegistry.getType(typeId);
        if (type == null) {
            sendError(exchange, 404, "Type not found: " + typeId);
            return;
        }

        boolean exists = type.attributes().stream().anyMatch(a -> a.name().equals(attrName));
        if (!exists) {
            sendError(exchange, 404, "Attribute not found: " + attrName);
            return;
        }

        schemaRegistry.removeAttribute(typeId, attrName);
        IntelLogPanel.logData("API: Removed attribute '" + attrName + "' from type '" + typeId + "'");

        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        result.put("typeId", typeId);
        result.put("attribute", attrName);
        sendJson(exchange, 200, result);
    }

    // ========== Serialization ==========

    private ObjectNode serializeType(SchemaType type) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", type.id());
        node.put("name", type.name());
        node.put("color", type.colorHex());
        node.put("kind", type.kind());
        node.put("hasMarketCap", type.hasMarketCap());
        if (type.fromTypeId() != null) node.put("fromTypeId", type.fromTypeId());
        if (type.toTypeId() != null) node.put("toTypeId", type.toTypeId());
        if (type.label() != null) node.put("label", type.label());
        if (type.inverseLabel() != null) node.put("inverseLabel", type.inverseLabel());
        if (type.pluralLabel() != null) node.put("pluralLabel", type.pluralLabel());
        if (type.inversePluralLabel() != null) node.put("inversePluralLabel", type.inversePluralLabel());
        if (type.searchDescription() != null) node.put("searchDescription", type.searchDescription());
        if (type.inverseSearchDescription() != null) node.put("inverseSearchDescription", type.inverseSearchDescription());
        node.put("displayOrder", type.displayOrder());

        ArrayNode attrs = mapper.createArrayNode();
        for (SchemaAttribute attr : type.attributes()) {
            ObjectNode attrNode = mapper.createObjectNode();
            attrNode.put("name", attr.name());
            attrNode.put("dataType", attr.dataType());
            attrNode.put("required", attr.required());
            attrNode.put("displayOrder", attr.displayOrder());
            attrNode.put("mutability", attr.mutability().name());
            attrs.add(attrNode);
        }
        node.set("attributes", attrs);

        return node;
    }
}
