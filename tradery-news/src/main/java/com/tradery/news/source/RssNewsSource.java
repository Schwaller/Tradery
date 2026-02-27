package com.tradery.news.source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.news.ai.ClaudeCliProcessor;
import com.tradery.news.model.Article;
import com.tradery.news.model.ImportanceLevel;
import com.tradery.news.model.ProcessingStatus;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.topic.TopicRegistry;
import com.tradery.news.ui.coin.SchemaAttribute;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.coin.SchemaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data source that fetches news articles from the data service HTTP API.
 * The data service handles RSS polling; this source consumes articles and
 * optionally runs AI processing + topic classification.
 */
public class RssNewsSource implements DataSource {

    private static final Logger log = LoggerFactory.getLogger(RssNewsSource.class);
    private static final Path PORT_FILE = Path.of(
        System.getProperty("user.home"), ".tradery", "dataservice.port");

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(10))
        .build();

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final SqliteNewsStore newsStore;
    private final Path dataDir;
    private long lastFetchTimestamp;

    public RssNewsSource(SqliteNewsStore newsStore, Path dataDir) {
        this.newsStore = newsStore;
        this.dataDir = dataDir;
        // Start from 24h ago on first fetch
        this.lastFetchTimestamp = System.currentTimeMillis() - 86400000L;
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
            progress.update("Connecting to data service...", 10);

            String baseUrl = getDataServiceUrl();
            if (baseUrl == null) {
                return new FetchResult(0, 0, "Data service not running");
            }

            progress.update("Fetching articles from data service...", 30);

            // Fetch articles from data service
            String url = baseUrl + "/news/articles?since=" + lastFetchTimestamp;
            Request request = new Request.Builder().url(url).build();

            List<RawArticle> rawArticles;
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return new FetchResult(0, 0, "Data service error: HTTP " + response.code());
                }
                rawArticles = MAPPER.readValue(response.body().byteStream(),
                    new TypeReference<List<RawArticle>>() {});
            }

            if (rawArticles.isEmpty()) {
                progress.update("Done", 100);
                return new FetchResult(0, 0, "No new articles");
            }

            progress.update("Processing " + rawArticles.size() + " articles...", 50);

            // Setup topic classification and optional AI
            TopicRegistry topics = new TopicRegistry(dataDir.resolve("topics.json"));
            ClaudeCliProcessor ai = new ClaudeCliProcessor();
            boolean aiAvailable = ai.isAvailable();

            int newArticles = 0;
            int aiProcessed = 0;

            for (RawArticle raw : rawArticles) {
                if (newsStore.articleExists(raw.id)) continue;

                // Classify topics
                List<String> matchedTopics = topics.classify(raw.title + " " + raw.content);

                Article article;
                if (aiAvailable) {
                    Article base = toArticle(raw, matchedTopics);
                    var result = ai.process(base);
                    article = new Article(
                        raw.id, raw.sourceUrl, raw.sourceId, raw.sourceName,
                        raw.title, raw.content, raw.author,
                        Instant.ofEpochMilli(raw.publishedAt),
                        result.summary(), result.importance(), result.coins(),
                        matchedTopics, result.categories(), result.tags(),
                        result.sentimentScore(),
                        List.of(), List.of(),
                        Instant.ofEpochMilli(raw.fetchedAt), Instant.now(),
                        ProcessingStatus.COMPLETE
                    );
                    aiProcessed++;
                } else {
                    article = toArticle(raw, matchedTopics);
                }

                newsStore.saveArticle(article);
                newArticles++;
            }

            // Update timestamp for next incremental fetch
            lastFetchTimestamp = System.currentTimeMillis();

            progress.update("Done", 100);
            return new FetchResult(newArticles, 0,
                newArticles + " new articles" + (aiProcessed > 0 ? " (" + aiProcessed + " AI processed)" : ""));

        } catch (Exception e) {
            log.error("Failed to fetch from data service: {}", e.getMessage());
            return new FetchResult(0, 0, "Error: " + e.getMessage());
        }
    }

    private Article toArticle(RawArticle raw, List<String> topics) {
        return Article.builder()
            .id(raw.id)
            .sourceUrl(raw.sourceUrl)
            .sourceId(raw.sourceId)
            .sourceName(raw.sourceName)
            .title(raw.title)
            .content(raw.content)
            .author(raw.author)
            .publishedAt(Instant.ofEpochMilli(raw.publishedAt))
            .fetchedAt(Instant.ofEpochMilli(raw.fetchedAt))
            .status(ProcessingStatus.PENDING)
            .importance(ImportanceLevel.MEDIUM)
            .topics(topics)
            .coins(List.of())
            .categories(List.of())
            .tags(List.of())
            .eventIds(List.of())
            .entityIds(List.of())
            .build();
    }

    private String getDataServiceUrl() {
        try {
            if (!Files.exists(PORT_FILE)) return null;
            int port = Integer.parseInt(Files.readString(PORT_FILE).trim());
            return "http://localhost:" + port;
        } catch (Exception e) {
            log.warn("Could not read data service port: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Raw article from data service JSON response.
     */
    private static class RawArticle {
        public String id;
        public String sourceId;
        public String sourceName;
        public String title;
        public String content;
        public String author;
        public String sourceUrl;
        public long publishedAt;
        public long fetchedAt;
    }
}
