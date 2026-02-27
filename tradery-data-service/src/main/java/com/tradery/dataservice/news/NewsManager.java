package com.tradery.dataservice.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates RSS feed polling and article storage.
 * Reads feed configuration from FeedConfigDao on each poll cycle.
 */
public class NewsManager {

    private static final Logger log = LoggerFactory.getLogger(NewsManager.class);

    private final NewsArticleDao articleDao;
    private final FeedConfigDao feedConfigDao;
    private final Map<String, RssFeedPoller> activePollers = new HashMap<>();
    private ScheduledExecutorService scheduler;

    public NewsManager(NewsArticleDao articleDao, FeedConfigDao feedConfigDao) {
        this.articleDao = articleDao;
        this.feedConfigDao = feedConfigDao;
    }

    /**
     * Start polling feeds at the given interval.
     */
    public void startPolling(Duration interval) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "news-poller");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::pollAll, 0, interval.toSeconds(), TimeUnit.SECONDS);
        log.info("News polling started (interval {}s)", interval.toSeconds());
    }

    /**
     * Reload enabled feeds from DB and sync active pollers.
     */
    private void loadFeeds() {
        try {
            List<FeedConfigRecord> enabled = feedConfigDao.getEnabled();

            // Remove pollers for feeds no longer enabled
            activePollers.keySet().retainAll(
                enabled.stream().map(FeedConfigRecord::sourceId).collect(java.util.stream.Collectors.toSet())
            );

            // Add pollers for newly enabled feeds
            for (FeedConfigRecord feed : enabled) {
                activePollers.computeIfAbsent(feed.sourceId(),
                    id -> new RssFeedPoller(feed.sourceId(), feed.sourceName(), feed.feedUrl()));
            }
        } catch (Exception e) {
            log.error("Failed to load feed config: {}", e.getMessage());
        }
    }

    /**
     * Poll all enabled feeds once and store new articles.
     */
    public void pollAll() {
        loadFeeds();

        int totalNew = 0;
        for (RssFeedPoller feed : activePollers.values()) {
            try {
                List<NewsArticleRecord> articles = feed.poll(20);
                if (!articles.isEmpty()) {
                    int inserted = articleDao.insertBatch(articles);
                    totalNew += inserted;
                }
            } catch (Exception e) {
                log.error("Error polling {}: {}", feed.getSourceId(), e.getMessage());
            }
        }
        if (totalNew > 0) {
            log.info("News poll complete: {} new articles stored", totalNew);
        } else {
            log.debug("News poll complete: no new articles");
        }
    }

    /**
     * Get articles published since the given timestamp.
     */
    public List<NewsArticleRecord> getArticlesSince(long sinceEpochMs) {
        try {
            return articleDao.querySince(sinceEpochMs);
        } catch (Exception e) {
            log.error("Failed to query articles: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get all feed configs (enabled and disabled).
     */
    public List<FeedConfigRecord> getAllFeeds() {
        try {
            return feedConfigDao.getAll();
        } catch (Exception e) {
            log.error("Failed to get feeds: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Add a custom feed.
     */
    public FeedConfigRecord addFeed(String sourceId, String sourceName, String feedUrl) throws Exception {
        FeedConfigRecord record = new FeedConfigRecord(
            sourceId, sourceName, feedUrl, true, false, System.currentTimeMillis()
        );
        feedConfigDao.insert(record);
        return record;
    }

    /**
     * Remove a custom feed. Rejects built-in feeds.
     * @return true if deleted
     */
    public boolean removeFeed(String sourceId) throws Exception {
        if (feedConfigDao.isBuiltIn(sourceId)) {
            throw new IllegalArgumentException("Cannot delete built-in feed: " + sourceId);
        }
        boolean deleted = feedConfigDao.delete(sourceId);
        if (deleted) {
            activePollers.remove(sourceId);
        }
        return deleted;
    }

    /**
     * Enable or disable a feed.
     * @return true if updated
     */
    public boolean setFeedEnabled(String sourceId, boolean enabled) throws Exception {
        boolean updated = feedConfigDao.setEnabled(sourceId, enabled);
        if (updated && !enabled) {
            activePollers.remove(sourceId);
        }
        return updated;
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
