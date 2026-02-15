package com.tradery.news.source;

import com.tradery.news.ai.ClaudeCliProcessor;
import com.tradery.news.fetch.FetchScheduler;
import com.tradery.news.fetch.FetcherRegistry;
import com.tradery.news.fetch.RssFetcher;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.topic.TopicRegistry;
import com.tradery.news.ui.IntelConfig;

import com.tradery.news.ui.coin.SchemaAttribute;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.coin.SchemaType;

import java.awt.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Data source that fetches news articles from RSS feeds with optional AI processing.
 * Keeps SqliteNewsStore as its backing store (bridge source — not EntityStore).
 */
public class RssNewsSource implements DataSource {

    private final SqliteNewsStore newsStore;
    private final Path dataDir;
    private FetcherRegistry fetcherRegistry;

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
    public void seedSchemaTypes(SchemaRegistry registry) {
        int order = 200; // After CoinGecko and Core types

        if (registry.getType("news_article") == null) {
            SchemaType na = new SchemaType("news_article", "News Article", new Color(220, 180, 100), SchemaType.KIND_ENTITY);
            na.setDisplayOrder(order++);
            na.addAttribute(new SchemaAttribute("title", SchemaAttribute.TEXT, true, 0, null, null, SchemaAttribute.Mutability.SOURCE));
            na.addAttribute(new SchemaAttribute("url", SchemaAttribute.URL, false, 1, null, null, SchemaAttribute.Mutability.SOURCE));
            na.addAttribute(new SchemaAttribute("published_at", SchemaAttribute.DATETIME, false, 2,
                Map.of("en", "Published At"), Map.of("format", "yyyy-MM-dd HH:mm"), SchemaAttribute.Mutability.SOURCE));
            na.addAttribute(new SchemaAttribute("source", SchemaAttribute.TEXT, false, 3, null, null, SchemaAttribute.Mutability.SOURCE));
            registry.save(na);
        }

        if (registry.getType("topic") == null) {
            SchemaType topic = new SchemaType("topic", "Topic", new Color(140, 180, 220), SchemaType.KIND_ENTITY);
            topic.setDisplayOrder(order++);
            topic.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0));
            registry.save(topic);
        }

        order = 200;

        if (registry.getType("mentions") == null) {
            SchemaType m = new SchemaType("mentions", "Mentions", new Color(210, 170, 90), SchemaType.KIND_RELATIONSHIP);
            m.setLabel("mentions"); m.setFromTypeId("news_article"); m.setToTypeId("coin");
            m.setInverseLabel("mentioned in"); m.setPluralLabel("Coins"); m.setInversePluralLabel("Articles");
            m.setDisplayOrder(order++);
            m.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(m);
        }

        if (registry.getType("tagged") == null) {
            SchemaType tagged = new SchemaType("tagged", "Tagged", new Color(130, 170, 210), SchemaType.KIND_RELATIONSHIP);
            tagged.setLabel("tagged"); tagged.setFromTypeId("news_article"); tagged.setToTypeId("topic");
            tagged.setDisplayOrder(order++);
            tagged.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(tagged);
        }

        if (registry.getType("published_by") == null) {
            SchemaType pb = new SchemaType("published_by", "Published By", new Color(200, 180, 120), SchemaType.KIND_RELATIONSHIP);
            pb.setLabel("published by"); pb.setFromTypeId("news_article"); pb.setToTypeId("news_source");
            pb.setDisplayOrder(order++);
            pb.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(pb);
        }
    }

    @Override
    public FetchResult fetch(FetchContext ctx) {
        ProgressCallback progress = ctx.progress();

        try {
            progress.update("Preparing RSS fetchers...", 10);

            ensureFetcherRegistry();

            TopicRegistry topics = new TopicRegistry(dataDir.resolve("topics.json"));
            ClaudeCliProcessor ai = new ClaudeCliProcessor();

            if (!ai.isAvailable()) {
                ai = null;
            }

            progress.update("Fetching articles...", 30);

            try (var scheduler = new FetchScheduler(fetcherRegistry, topics, newsStore, ai)) {
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

    private void ensureFetcherRegistry() {
        if (fetcherRegistry == null) {
            fetcherRegistry = new FetcherRegistry();
        }
        // Sync enabled/disabled state from config each cycle
        IntelConfig fetchConfig = IntelConfig.get();
        for (RssFetcher source : RssFetcher.defaultSources()) {
            if (!fetchConfig.isFeedDisabled(source.getSourceId())) {
                if (fetcherRegistry.getFetcher(source.getSourceId()).isEmpty()) {
                    fetcherRegistry.register(source);
                }
            }
        }
    }
}
