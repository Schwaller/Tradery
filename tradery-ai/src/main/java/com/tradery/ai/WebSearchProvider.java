package com.tradery.ai;

import java.util.List;

/**
 * Interface for web search providers used by deep entity discovery.
 * Implementations can scrape search engines or use proper APIs.
 */
public interface WebSearchProvider {

    record SearchResult(String title, String snippet, String url) {}

    /**
     * Search the web and return results.
     *
     * @param query      The search query
     * @param maxResults Maximum number of results to return
     * @return List of search results
     * @throws WebSearchException If the search fails
     */
    List<SearchResult> search(String query, int maxResults) throws WebSearchException;

    /**
     * Get the name of this search provider.
     */
    String getName();
}
