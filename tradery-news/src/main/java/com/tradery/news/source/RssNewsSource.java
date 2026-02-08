package com.tradery.news.source;

import com.tradery.news.ai.ClaudeCliProcessor;
import com.tradery.news.fetch.FetchScheduler;
import com.tradery.news.fetch.FetcherRegistry;
import com.tradery.news.fetch.RssFetcher;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.topic.TopicRegistry;
import com.tradery.news.ui.IntelConfig;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Data source that fetches news articles from RSS feeds with optional AI processing.
 * Keeps SqliteNewsStore as its backing store (bridge source — not EntityStore).
 */
public class RssNewsSource implements DataSource {

    private final SqliteNewsStore newsStore;
    private final Path dataDir;

    public RssNewsSource(SqliteNewsStore newsStore, Path dataDir) {
        this.newsStore = newsStore;
        this.dataDir = dataDir;
    }

    @Override
    public String id() { return "rss"; }

    @Override
    public String name() { return "RSS News Feeds"; }

    @Override
    public List<String> producedEntityTypes() {
        return List.of("news_article", "topic");
    }

    @Override
    public List<String> producedRelationshipTypes() {
        return List.of("mentions", "tagged", "published_by");
    }

    @Override
    public Duration cacheTTL() { return Duration.ZERO; }

    @Override
    public FetchResult fetch(FetchContext ctx) {
        ProgressCallback progress = ctx.progress();

        try {
            progress.update("Preparing RSS fetchers...", 10);

            FetcherRegistry fetchers = new FetcherRegistry();
            IntelConfig fetchConfig = IntelConfig.get();
            for (RssFetcher source : RssFetcher.defaultSources()) {
                if (!fetchConfig.isFeedDisabled(source.getSourceId())) {
                    fetchers.register(source);
                }
            }

            TopicRegistry topics = new TopicRegistry(dataDir.resolve("topics.json"));
            ClaudeCliProcessor ai = new ClaudeCliProcessor();

            if (!ai.isAvailable()) {
                ai = null;
            }

            progress.update("Fetching articles...", 30);

            try (var scheduler = new FetchScheduler(fetchers, topics, newsStore, ai)) {
                scheduler.withAiEnabled(ai != null).withArticlesPerSource(ai != null ? 5 : 10);
                FetchScheduler.FetchResult result = scheduler.fetchAndProcess();

                progress.update("Done", 100);
                return new FetchResult(
                    result.newArticles(),
                    0,
                    result.newArticles() + " new articles (" + result.aiProcessed() + " AI processed)"
                );
            }
        } catch (Exception e) {
            return new FetchResult(0, 0, "Error: " + e.getMessage());
        }
    }
}
