package com.tradery.news.fetch;

import com.tradery.news.model.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry that manages multiple news fetchers and deduplicates articles.
 */
public class FetcherRegistry {

    private static final Logger log = LoggerFactory.getLogger(FetcherRegistry.class);

    private final Map<String, NewsFetcher> fetchers = new ConcurrentHashMap<>();
    private final Set<String> seenArticleIds = ConcurrentHashMap.newKeySet();

    public FetcherRegistry() {
    }

    /**
     * Register a fetcher.
     */
    public void register(NewsFetcher fetcher) {
        fetchers.put(fetcher.getSourceId(), fetcher);
        log.info("Registered fetcher: {} ({})", fetcher.getSourceId(), fetcher.getSourceType());
    }

    /**
     * Register multiple fetchers.
     */
    public void registerAll(Collection<? extends NewsFetcher> fetchers) {
        fetchers.forEach(this::register);
    }

    /**
     * Register default RSS sources.
     */
    public void registerDefaults() {
        registerAll(RssFetcher.defaultSources());
    }

    /**
     * Get a fetcher by source ID.
     */
    public Optional<NewsFetcher> getFetcher(String sourceId) {
        return Optional.ofNullable(fetchers.get(sourceId));
    }

    /**
     * Get all registered fetchers.
     */
    public Collection<NewsFetcher> getAllFetchers() {
        return Collections.unmodifiableCollection(fetchers.values());
    }

    /**
     * Get enabled fetchers only.
     */
    public List<NewsFetcher> getEnabledFetchers() {
        return fetchers.values().stream()
            .filter(NewsFetcher::isEnabled)
            .toList();
    }

    /**
     * Fetch from all enabled sources, deduplicated.
     */
    public List<Article> fetchAll(int limitPerSource) {
        List<Article> all = new ArrayList<>();

        for (NewsFetcher fetcher : getEnabledFetchers()) {
            try {
                List<Article> articles = fetcher.fetchLatest(limitPerSource);
                for (Article article : articles) {
                    if (seenArticleIds.add(article.id())) {
                        all.add(article);
                    }
                }
            } catch (Exception e) {
                log.error("Error fetching from {}: {}", fetcher.getSourceId(), e.getMessage());
            }
        }

        // Sort by publish time, newest first
        all.sort(Comparator.comparing(Article::publishedAt).reversed());

        log.info("Fetched {} unique articles from {} sources",
            all.size(), getEnabledFetchers().size());

        return all;
    }

    /**
     * Fetch from all enabled sources in parallel.
     */
    public List<Article> fetchAllParallel(int limitPerSource) {
        List<Article> all = getEnabledFetchers().parallelStream()
            .flatMap(fetcher -> {
                try {
                    return fetcher.fetchLatest(limitPerSource).stream();
                } catch (Exception e) {
                    log.error("Error fetching from {}: {}", fetcher.getSourceId(), e.getMessage());
                    return java.util.stream.Stream.empty();
                }
            })
            .filter(article -> seenArticleIds.add(article.id()))
            .sorted(Comparator.comparing(Article::publishedAt).reversed())
            .collect(Collectors.toList());

        log.info("Fetched {} unique articles from {} sources (parallel)",
            all.size(), getEnabledFetchers().size());

        return all;
    }

    /**
     * Clear the seen articles cache (for testing or forced refetch).
     */
    public void clearSeenCache() {
        seenArticleIds.clear();
    }

    /**
     * Check if an article has been seen before.
     */
    public boolean hasSeen(String articleId) {
        return seenArticleIds.contains(articleId);
    }
}
