package com.tradery.news.fetch;

import java.util.List;

/**
 * RSS feed source metadata. Used by IntelSettingsDialog for feed display.
 * Actual RSS fetching is handled by the data service.
 */
public class RssFetcher {

    private final String sourceId;
    private final String sourceName;
    private final String feedUrl;
    private final boolean enabled;

    public RssFetcher(String sourceId, String sourceName, String feedUrl) {
        this(sourceId, sourceName, feedUrl, true);
    }

    public RssFetcher(String sourceId, String sourceName, String feedUrl, boolean enabled) {
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.feedUrl = feedUrl;
        this.enabled = enabled;
    }

    public String getSourceId() { return sourceId; }
    public String getSourceName() { return sourceName; }
    public String getFeedUrl() { return feedUrl; }
    public boolean isEnabled() { return enabled; }

    // === Pre-configured sources ===

    public static RssFetcher coinDesk() {
        return new RssFetcher("coindesk", "CoinDesk",
            "https://www.coindesk.com/arc/outboundfeeds/rss/");
    }

    public static RssFetcher coinTelegraph() {
        return new RssFetcher("cointelegraph", "CoinTelegraph",
            "https://cointelegraph.com/rss");
    }

    public static RssFetcher theBlock() {
        return new RssFetcher("theblock", "The Block",
            "https://www.theblock.co/rss.xml");
    }

    public static RssFetcher decrypt() {
        return new RssFetcher("decrypt", "Decrypt",
            "https://decrypt.co/feed");
    }

    public static RssFetcher bitcoinMagazine() {
        return new RssFetcher("bitcoinmagazine", "Bitcoin Magazine",
            "https://bitcoinmagazine.com/feed");
    }

    public static List<RssFetcher> defaultSources() {
        return List.of(
            coinDesk(),
            coinTelegraph(),
            theBlock(),
            decrypt(),
            bitcoinMagazine()
        );
    }
}
