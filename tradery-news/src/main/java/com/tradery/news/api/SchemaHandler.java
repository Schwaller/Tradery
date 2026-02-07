package com.tradery.news.api;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaAttribute;
import com.tradery.news.ui.coin.SchemaType;

import java.io.IOException;
import java.util.List;

/**
 * Handler for ERD schema type endpoints.
 */
public class SchemaHandler extends IntelApiHandlerBase {

    private final EntityStore entityStore;

    public SchemaHandler(EntityStore entityStore) {
        this.entityStore = entityStore;
    }

    public void handleSchema(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "GET")) return;

        List<SchemaType> types = entityStore.loadSchemaTypes();
        ArrayNode arr = mapper.createArrayNode();

        for (SchemaType type : types) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", type.id());
            node.put("name", type.name());
            node.put("color", type.colorHex());
            node.put("kind", type.kind());
            if (type.fromTypeId() != null) node.put("fromTypeId", type.fromTypeId());
            if (type.toTypeId() != null) node.put("toTypeId", type.toTypeId());
            if (type.label() != null) node.put("label", type.label());
            node.put("displayOrder", type.displayOrder());

            ArrayNode attrs = mapper.createArrayNode();
            for (SchemaAttribute attr : type.attributes()) {
                ObjectNode attrNode = mapper.createObjectNode();
                attrNode.put("name", attr.name());
                attrNode.put("dataType", attr.dataType());
                attrNode.put("required", attr.required());
                attrNode.put("displayOrder", attr.displayOrder());
                attrs.add(attrNode);
            }
            node.set("attributes", attrs);

            arr.add(node);
        }

        ObjectNode result = mapper.createObjectNode();
        result.set("types", arr);
        sendJson(exchange, 200, result);
    }
}
