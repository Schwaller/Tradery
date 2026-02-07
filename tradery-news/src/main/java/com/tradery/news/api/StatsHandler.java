package com.tradery.news.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.ui.coin.EntityStore;

import java.io.IOException;

/**
 * Handler for combined stats endpoint.
 */
public class StatsHandler extends IntelApiHandlerBase {

    private final EntityStore entityStore;
    private final SqliteNewsStore newsStore;

    public StatsHandler(EntityStore entityStore, SqliteNewsStore newsStore) {
        this.entityStore = entityStore;
        this.newsStore = newsStore;
    }

    public void handleStats(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "GET")) return;

        ObjectNode json = mapper.createObjectNode();
        json.put("entityCount", entityStore.getEntityCount());
        json.put("manualEntityCount", entityStore.getManualEntityCount());
        json.put("relationshipCount", entityStore.getRelationshipCount());
        json.put("manualRelationshipCount", entityStore.getManualRelationshipCount());
        json.put("articleCount", newsStore.getArticleCount());

        sendJson(exchange, 200, json);
    }
}
