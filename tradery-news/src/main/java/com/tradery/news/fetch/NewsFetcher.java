package com.tradery.news.fetch;

import com.tradery.news.model.Article;

import java.util.List;

/**
 * Interface for news source fetchers.
 */
public interface NewsFetcher {

    /**
     * Unique identifier for this source.
     */
    String getSourceId();

    /**
     * Human-readable source name.
     */
    String getSourceName();

    /**
     * Type of source (RSS, SEARCH, SOCIAL).
     */
    SourceType getSourceType();

    /**
     * Whether this fetcher is enabled.
     */
    boolean isEnabled();

    /**
     * Fetch latest articles from this source.
     */
    List<Article> fetchLatest(int limit);
}
