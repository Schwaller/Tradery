package com.tradery.dataservice.news;

/**
 * Configuration for an RSS feed source.
 */
public record FeedConfigRecord(
    String sourceId,
    String sourceName,
    String feedUrl,
    boolean enabled,
    boolean builtIn,
    long createdAt
) {}
