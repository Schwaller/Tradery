package com.tradery.news.fetch;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.tradery.news.model.Article;
import com.tradery.news.model.ImportanceLevel;
import com.tradery.news.model.ProcessingStatus;
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
 * Fetches news articles from RSS feeds.
 */
public class RssFetcher implements NewsFetcher {

    private static final Logger log = LoggerFactory.getLogger(RssFetcher.class);
    private static final Pattern MAX_AGE_PATTERN = Pattern.compile("max-age=(\\d+)");
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(15))
        .followRedirects(true)
        .build();

    private final String sourceId;
    private final String sourceName;
    private final String feedUrl;
    private final boolean enabled;

    // HTTP conditional request state
    private String lastEtag;
    private String lastModified;
    private Instant cacheExpiresAt;

    public RssFetcher(String sourceId, String sourceName, String feedUrl) {
        this(sourceId, sourceName, feedUrl, true);
    }

    public RssFetcher(String sourceId, String sourceName, String feedUrl, boolean enabled) {
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.feedUrl = feedUrl;
        this.enabled = enabled;
    }

    @Override
    public String getSourceId() {
        return sourceId;
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    public String getFeedUrl() {
        return feedUrl;
    }

    @Override
    public SourceType getSourceType() {
        return SourceType.RSS;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<Article> fetchLatest(int limit) {
        // Respect Cache-Control max-age
        if (cacheExpiresAt != null && Instant.now().isBefore(cacheExpiresAt)) {
            log.debug("{}: cache still fresh, skipping", sourceId);
            return List.of();
        }

        List<Article> articles = new ArrayList<>();

        Request.Builder reqBuilder = new Request.Builder()
            .url(feedUrl)
            .header("User-Agent", "Plaiiin/1.0 (RSS Reader)");

        if (lastEtag != null) {
            reqBuilder.header("If-None-Match", lastEtag);
        }
        if (lastModified != null) {
            reqBuilder.header("If-Modified-Since", lastModified);
        }

        try (Response response = HTTP_CLIENT.newCall(reqBuilder.build()).execute()) {
            if (response.code() == 304) {
                log.debug("{}: not modified (304)", sourceId);
                return List.of();
            }

            if (!response.isSuccessful()) {
                log.error("Failed to fetch RSS feed from {}: HTTP {}", feedUrl, response.code());
                return List.of();
            }

            // Store conditional headers for next request
            String etag = response.header("ETag");
            if (etag != null) lastEtag = etag;

            String modified = response.header("Last-Modified");
            if (modified != null) lastModified = modified;

            // Parse Cache-Control max-age
            String cacheControl = response.header("Cache-Control");
            if (cacheControl != null) {
                Matcher m = MAX_AGE_PATTERN.matcher(cacheControl);
                if (m.find()) {
                    long maxAge = Long.parseLong(m.group(1));
                    if (maxAge > 0) {
                        cacheExpiresAt = Instant.now().plusSeconds(maxAge);
                    }
                }
            }

            // Parse feed from response body
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(response.body().byteStream()));

            int count = 0;
            for (SyndEntry entry : feed.getEntries()) {
                if (count >= limit) break;

                Article article = parseEntry(entry);
                if (article != null) {
                    articles.add(article);
                    count++;
                }
            }

            log.info("Fetched {} articles from {}", articles.size(), sourceId);

        } catch (Exception e) {
            log.error("Failed to fetch RSS feed from {}: {}", feedUrl, e.getMessage());
        }

        return articles;
    }

    private Article parseEntry(SyndEntry entry) {
        try {
            String url = entry.getLink();
            if (url == null || url.isBlank()) return null;

            String id = hashUrl(url);
            String title = entry.getTitle();
            String content = extractContent(entry);
            String author = entry.getAuthor();
            Instant publishedAt = entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant()
                : Instant.now();

            return Article.builder()
                .id(id)
                .sourceUrl(url)
                .sourceId(sourceId)
                .sourceName(sourceName)
                .title(title != null ? title.trim() : "")
                .content(content)
                .author(author)
                .publishedAt(publishedAt)
                .fetchedAt(Instant.now())
                .status(ProcessingStatus.PENDING)
                .importance(ImportanceLevel.MEDIUM)
                .coins(List.of())
                .topics(List.of())
                .categories(List.of())
                .tags(List.of())
                .eventIds(List.of())
                .entityIds(List.of())
                .build();

        } catch (Exception e) {
            log.warn("Failed to parse RSS entry: {}", e.getMessage());
            return null;
        }
    }

    private String extractContent(SyndEntry entry) {
        // Try description first
        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            return stripHtml(entry.getDescription().getValue());
        }

        // Try contents
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            var content = entry.getContents().get(0);
            if (content.getValue() != null) {
                return stripHtml(content.getValue());
            }
        }

        return "";
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        // Basic HTML stripping - for full parsing use JSoup
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

    private String hashUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return url.hashCode() + "";
        }
    }

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
