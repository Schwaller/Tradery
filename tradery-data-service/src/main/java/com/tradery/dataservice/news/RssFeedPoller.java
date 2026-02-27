package com.tradery.dataservice.news;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polls a single RSS feed for articles, with conditional HTTP support.
 */
public class RssFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(RssFeedPoller.class);
    private static final Pattern MAX_AGE_PATTERN = Pattern.compile("max-age=(\\d+)");
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(15))
        .followRedirects(true)
        .build();

    private final String sourceId;
    private final String sourceName;
    private final String feedUrl;

    // Conditional HTTP state
    private String lastEtag;
    private String lastModified;
    private Instant cacheExpiresAt;

    public RssFeedPoller(String sourceId, String sourceName, String feedUrl) {
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.feedUrl = feedUrl;
    }

    public String getSourceId() { return sourceId; }
    public String getSourceName() { return sourceName; }
    public String getFeedUrl() { return feedUrl; }

    /**
     * Poll the feed. Returns empty list on 304/cache-fresh.
     */
    public List<NewsArticleRecord> poll(int limit) {
        if (cacheExpiresAt != null && Instant.now().isBefore(cacheExpiresAt)) {
            log.debug("{}: cache still fresh, skipping", sourceId);
            return List.of();
        }

        Request.Builder reqBuilder = new Request.Builder()
            .url(feedUrl)
            .header("User-Agent", "Plaiiin/1.0 (RSS Reader)");

        if (lastEtag != null) reqBuilder.header("If-None-Match", lastEtag);
        if (lastModified != null) reqBuilder.header("If-Modified-Since", lastModified);

        try (Response response = HTTP_CLIENT.newCall(reqBuilder.build()).execute()) {
            if (response.code() == 304) {
                log.debug("{}: not modified (304)", sourceId);
                return List.of();
            }

            if (!response.isSuccessful()) {
                log.error("Failed to fetch RSS from {}: HTTP {}", feedUrl, response.code());
                return List.of();
            }

            // Store conditional headers
            String etag = response.header("ETag");
            if (etag != null) lastEtag = etag;
            String modified = response.header("Last-Modified");
            if (modified != null) lastModified = modified;

            // Parse Cache-Control
            String cacheControl = response.header("Cache-Control");
            if (cacheControl != null) {
                Matcher m = MAX_AGE_PATTERN.matcher(cacheControl);
                if (m.find()) {
                    long maxAge = Long.parseLong(m.group(1));
                    if (maxAge > 0) cacheExpiresAt = Instant.now().plusSeconds(maxAge);
                }
            }

            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(response.body().byteStream()));

            List<NewsArticleRecord> articles = new ArrayList<>();
            long now = Instant.now().toEpochMilli();

            for (SyndEntry entry : feed.getEntries()) {
                if (articles.size() >= limit) break;
                NewsArticleRecord article = parseEntry(entry, now);
                if (article != null) articles.add(article);
            }

            log.info("Fetched {} articles from {}", articles.size(), sourceId);
            return articles;

        } catch (Exception e) {
            log.error("Failed to fetch RSS from {}: {}", feedUrl, e.getMessage());
            return List.of();
        }
    }

    private NewsArticleRecord parseEntry(SyndEntry entry, long fetchedAt) {
        try {
            String url = entry.getLink();
            if (url == null || url.isBlank()) return null;

            String title = entry.getTitle();
            String content = extractContent(entry);
            String author = entry.getAuthor();
            long publishedAt = entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant().toEpochMilli()
                : fetchedAt;

            return new NewsArticleRecord(
                hashUrl(url),
                sourceId,
                sourceName,
                title != null ? title.trim() : "",
                content,
                author,
                url,
                publishedAt,
                fetchedAt
            );
        } catch (Exception e) {
            log.warn("Failed to parse RSS entry: {}", e.getMessage());
            return null;
        }
    }

    private String extractContent(SyndEntry entry) {
        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            return stripHtml(entry.getDescription().getValue());
        }
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            var content = entry.getContents().get(0);
            if (content.getValue() != null) return stripHtml(content.getValue());
        }
        return "";
    }

    static String stripHtml(String html) {
        if (html == null) return "";
        return html
            .replaceAll("<[^>]+>", " ")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("&quot;", "\"")
            .replaceAll("\\s+", " ")
            .trim();
    }

    static String hashUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return url.hashCode() + "";
        }
    }

    // === Pre-configured feed sources ===

    public static List<RssFeedPoller> defaultFeeds() {
        return List.of(
            new RssFeedPoller("coindesk", "CoinDesk",
                "https://www.coindesk.com/arc/outboundfeeds/rss/"),
            new RssFeedPoller("cointelegraph", "CoinTelegraph",
                "https://cointelegraph.com/rss"),
            new RssFeedPoller("theblock", "The Block",
                "https://www.theblock.co/rss.xml"),
            new RssFeedPoller("decrypt", "Decrypt",
                "https://decrypt.co/feed"),
            new RssFeedPoller("bitcoinmagazine", "Bitcoin Magazine",
                "https://bitcoinmagazine.com/feed")
        );
    }
}
