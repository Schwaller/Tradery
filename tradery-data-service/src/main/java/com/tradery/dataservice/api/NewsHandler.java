package com.tradery.dataservice.api;

import com.tradery.dataservice.news.NewsManager;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * HTTP handler for news article and feed source endpoints.
 */
public class NewsHandler {

    private static final Logger log = LoggerFactory.getLogger(NewsHandler.class);

    private final NewsManager newsManager;

    public NewsHandler(NewsManager newsManager) {
        this.newsManager = newsManager;
    }

    /**
     * GET /news/articles?since={epochMs}
     * Returns articles published since the given timestamp (default: last 24h).
     */
    public void getArticles(Context ctx) {
        long defaultSince = System.currentTimeMillis() - 86400000L; // 24h ago
        long since = ctx.queryParamAsClass("since", Long.class).getOrDefault(defaultSince);
        ctx.json(newsManager.getArticlesSince(since));
    }

    /**
     * GET /news/sources
     * Returns all feed configs with enabled/builtIn status.
     */
    public void getSources(Context ctx) {
        ctx.json(newsManager.getAllFeeds());
    }

    /**
     * POST /news/sources
     * Add a custom feed. Body: {sourceId, sourceName, feedUrl}
     */
    public void addSource(Context ctx) {
        try {
            var body = ctx.bodyAsClass(AddSourceRequest.class);
            if (body.sourceId == null || body.sourceId.isBlank()
                || body.sourceName == null || body.sourceName.isBlank()
                || body.feedUrl == null || body.feedUrl.isBlank()) {
                ctx.status(400).json(Map.of("error", "sourceId, sourceName, and feedUrl are required"));
                return;
            }
            var record = newsManager.addFeed(body.sourceId, body.sourceName, body.feedUrl);
            log.info("Added custom feed: {}", body.sourceId);
            ctx.status(201).json(record);
        } catch (Exception e) {
            log.error("Failed to add feed: {}", e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /news/sources/{id}
     * Remove a custom feed. Returns 400 if built-in.
     */
    public void deleteSource(Context ctx) {
        String sourceId = ctx.pathParam("id");
        try {
            boolean deleted = newsManager.removeFeed(sourceId);
            if (deleted) {
                log.info("Deleted feed: {}", sourceId);
                ctx.json(Map.of("status", "deleted"));
            } else {
                ctx.status(404).json(Map.of("error", "Feed not found: " + sourceId));
            }
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete feed {}: {}", sourceId, e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PUT /news/sources/{id}
     * Enable/disable a feed. Body: {enabled: true/false}
     */
    public void updateSource(Context ctx) {
        String sourceId = ctx.pathParam("id");
        try {
            var body = ctx.bodyAsClass(UpdateSourceRequest.class);
            boolean updated = newsManager.setFeedEnabled(sourceId, body.enabled);
            if (updated) {
                log.info("Updated feed {}: enabled={}", sourceId, body.enabled);
                ctx.json(Map.of("status", "updated", "enabled", body.enabled));
            } else {
                ctx.status(404).json(Map.of("error", "Feed not found: " + sourceId));
            }
        } catch (Exception e) {
            log.error("Failed to update feed {}: {}", sourceId, e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /news/poll
     * Trigger an immediate poll cycle.
     */
    public void triggerPoll(Context ctx) {
        log.info("Manual news poll triggered");
        newsManager.pollAll();
        ctx.json(Map.of("status", "ok"));
    }

    // Request DTOs
    private static class AddSourceRequest {
        public String sourceId;
        public String sourceName;
        public String feedUrl;
    }

    private static class UpdateSourceRequest {
        public boolean enabled;
    }
}
