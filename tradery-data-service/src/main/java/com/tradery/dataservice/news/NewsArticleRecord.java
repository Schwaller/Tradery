package com.tradery.dataservice.news;

/**
 * A news article fetched from an RSS feed.
 */
public record NewsArticleRecord(
    String id,           // SHA-256 prefix of URL
    String sourceId,     // e.g. "coindesk"
    String sourceName,   // e.g. "CoinDesk"
    String title,
    String content,      // HTML-stripped
    String author,
    String sourceUrl,
    long publishedAt,    // epoch ms
    long fetchedAt       // epoch ms
) {}
