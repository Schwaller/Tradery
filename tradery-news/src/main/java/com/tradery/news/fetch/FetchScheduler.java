package com.tradery.news.fetch;

import com.tradery.news.ai.AiProcessor;
import com.tradery.news.model.Article;
import com.tradery.news.model.ProcessingStatus;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.topic.TopicRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Scheduler for periodic news fetching and processing.
 */
public class FetchScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FetchScheduler.class);

    private final FetcherRegistry fetchers;
    private final TopicRegistry topics;
    private final SqliteNewsStore store;
    private final AiProcessor aiProcessor;

    private final ScheduledExecutorService scheduler;
    private final ExecutorService aiExecutor;

    private Duration fetchInterval = Duration.ofMinutes(5);
    private int articlesPerSource = 10;
    private boolean aiEnabled = false;
    private Consumer<FetchResult> onFetchComplete;

    private volatile boolean running = false;
    private ScheduledFuture<?> scheduledTask;

    public FetchScheduler(FetcherRegistry fetchers, TopicRegistry topics,
                          SqliteNewsStore store, AiProcessor aiProcessor) {
        this.fetchers = fetchers;
        this.topics = topics;
        this.store = store;
        this.aiProcessor = aiProcessor;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "news-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.aiExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "news-ai-processor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Set fetch interval (default: 5 minutes).
     */
    public FetchScheduler withInterval(Duration interval) {
        this.fetchInterval = interval;
        return this;
    }

    /**
     * Set articles to fetch per source (default: 10).
     */
    public FetchScheduler withArticlesPerSource(int count) {
        this.articlesPerSource = count;
        return this;
    }

    /**
     * Enable AI processing for new articles.
     */
    public FetchScheduler withAiEnabled(boolean enabled) {
        this.aiEnabled = enabled;
        return this;
    }

    /**
     * Set callback for fetch completion.
     */
    public FetchScheduler onFetchComplete(Consumer<FetchResult> callback) {
        this.onFetchComplete = callback;
        return this;
    }

    /**
     * Start the scheduler.
     */
    public void start() {
        if (running) return;
        running = true;

        log.info("Starting news fetch scheduler (interval: {}, AI: {})",
            fetchInterval, aiEnabled);

        // Run immediately, then at fixed intervals
        scheduledTask = scheduler.scheduleAtFixedRate(
            this::fetchAndProcess,
            0,
            fetchInterval.toSeconds(),
            TimeUnit.SECONDS
        );
    }

    /**
     * Stop the scheduler.
     */
    public void stop() {
        if (!running) return;
        running = false;

        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        log.info("Stopped news fetch scheduler");
    }

    /**
     * Run a single fetch cycle (can be called manually).
     */
    public FetchResult fetchAndProcess() {
        Instant startTime = Instant.now();
        int newArticles = 0;
        int existingArticles = 0;
        int aiProcessed = 0;
        int errors = 0;

        try {
            log.info("Fetching news from {} sources...", fetchers.getEnabledFetchers().size());

            List<Article> articles = fetchers.fetchAllParallel(articlesPerSource);
            log.info("Fetched {} articles", articles.size());

            for (Article article : articles) {
                try {
                    if (store.articleExists(article.id())) {
                        existingArticles++;
                        continue;
                    }

                    // Classify with topics
                    List<String> matchedTopics = topics.classify(
                        article.title() + " " + article.content());

                    Article processed;
                    if (aiEnabled && aiProcessor != null && aiProcessor.isAvailable()) {
                        // AI extraction
                        var result = aiProcessor.process(article);
                        processed = new Article(
                            article.id(),
                            article.sourceUrl(),
                            article.sourceId(),
                            article.sourceName(),
                            article.title(),
                            article.content(),
                            article.author(),
                            article.publishedAt(),
                            result.summary(),
                            result.importance(),
                            result.coins(),
                            matchedTopics,
                            result.categories(),
                            result.tags(),
                            result.sentimentScore(),
                            article.eventIds(),
                            article.entityIds(),
                            article.fetchedAt(),
                            Instant.now(),
                            ProcessingStatus.COMPLETE
                        );
                        aiProcessed++;
                    } else {
                        // Topic classification only
                        processed = new Article(
                            article.id(),
                            article.sourceUrl(),
                            article.sourceId(),
                            article.sourceName(),
                            article.title(),
                            article.content(),
                            article.author(),
                            article.publishedAt(),
                            null,
                            article.importance(),
                            article.coins(),
                            matchedTopics,
                            article.categories(),
                            article.tags(),
                            article.sentimentScore(),
                            article.eventIds(),
                            article.entityIds(),
                            article.fetchedAt(),
                            null,
                            ProcessingStatus.PENDING
                        );
                    }

                    store.saveArticle(processed);
                    newArticles++;

                    log.debug("Stored: {}", article.title());

                } catch (Exception e) {
                    log.error("Error processing article {}: {}", article.id(), e.getMessage());
                    errors++;
                }
            }

        } catch (Exception e) {
            log.error("Fetch cycle failed: {}", e.getMessage());
            errors++;
        }

        Duration duration = Duration.between(startTime, Instant.now());
        FetchResult result = new FetchResult(newArticles, existingArticles, aiProcessed, errors, duration);

        log.info("Fetch complete: {} new, {} existing, {} AI processed, {} errors ({}ms)",
            newArticles, existingArticles, aiProcessed, errors, duration.toMillis());

        if (onFetchComplete != null) {
            try {
                onFetchComplete.accept(result);
            } catch (Exception e) {
                log.error("Callback error: {}", e.getMessage());
            }
        }

        return result;
    }

    /**
     * Check if scheduler is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get current settings.
     */
    public Settings getSettings() {
        return new Settings(fetchInterval, articlesPerSource, aiEnabled);
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdown();
        aiExecutor.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            aiExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Result of a fetch cycle.
     */
    public record FetchResult(
        int newArticles,
        int existingArticles,
        int aiProcessed,
        int errors,
        Duration duration
    ) {}

    /**
     * Current scheduler settings.
     */
    public record Settings(
        Duration fetchInterval,
        int articlesPerSource,
        boolean aiEnabled
    ) {}
}
