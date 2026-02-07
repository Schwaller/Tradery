package com.tradery.news.api;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.tradery.news.model.*;
import com.tradery.news.store.NewsStore.ArticleQuery;
import com.tradery.news.store.NewsStore.EventQuery;
import com.tradery.news.store.SqliteNewsStore;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handler for news articles, topics, stories, and events endpoints.
 */
public class ArticleHandler extends IntelApiHandlerBase {

    private final SqliteNewsStore newsStore;

    public ArticleHandler(SqliteNewsStore newsStore) {
        this.newsStore = newsStore;
    }

    // GET /articles?coin=SOL&topic=defi&limit=50&since=ts
    public void handleArticles(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "GET")) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        List<String> coins = params.containsKey("coin") ? List.of(params.get("coin").split(",")) : null;
        List<String> topics = params.containsKey("topic") ? List.of(params.get("topic").split(",")) : null;
        int limit = Integer.parseInt(params.getOrDefault("limit", "50"));
        Long since = params.containsKey("since") ? Long.parseLong(params.get("since")) : null;

        ArticleQuery query = new ArticleQuery(coins, topics, null, since, null, limit);
        List<Article> articles = newsStore.getArticles(query);

        sendObject(exchange, 200, articles);
    }

    // GET /article/{id}
    public void handleArticle(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "GET")) return;

        String[] parts = pathParts(exchange);
        if (parts.length < 3) {
            sendError(exchange, 400, "Missing article ID");
            return;
        }
        String articleId = parts[2];

        Optional<Article> article = newsStore.getArticle(articleId);
        if (article.isEmpty()) {
            sendError(exchange, 404, "Article not found: " + articleId);
            return;
        }

        sendObject(exchange, 200, article.get());
    }

    // GET /topics
    public void handleTopics(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "GET")) return;

        List<SqliteNewsStore.TopicCount> counts = newsStore.getTopicCounts();

        ArrayNode arr = mapper.createArrayNode();
        for (SqliteNewsStore.TopicCount tc : counts) {
            ObjectNode node = mapper.createObjectNode();
            node.put("topic", tc.topic());
            node.put("count", tc.count());
            arr.add(node);
        }

        ObjectNode result = mapper.createObjectNode();
        result.set("topics", arr);
        sendJson(exchange, 200, result);
    }

    // GET /stories
    public void handleStories(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "GET")) return;

        List<Story> stories = newsStore.getActiveStories();
        sendObject(exchange, 200, stories);
    }

    // GET /events?type=HACK&limit=20
    public void handleEvents(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!checkMethod(exchange, "GET")) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        List<EventType> types = null;
        if (params.containsKey("type")) {
            try {
                types = List.of(EventType.valueOf(params.get("type").toUpperCase()));
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "Invalid event type: " + params.get("type"));
                return;
            }
        }

        int limit = Integer.parseInt(params.getOrDefault("limit", "20"));
        Long since = params.containsKey("since") ? Long.parseLong(params.get("since")) : null;

        EventQuery query = new EventQuery(types, null, since, limit);
        List<NewsEvent> events = newsStore.getEvents(query);

        sendObject(exchange, 200, events);
    }
}
