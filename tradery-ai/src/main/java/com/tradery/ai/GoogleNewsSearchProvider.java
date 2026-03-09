package com.tradery.ai;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web search provider using Google News RSS feed.
 * Returns actual article descriptions — much richer than DDG snippets.
 * Free, no API key needed.
 */
public class GoogleNewsSearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleNewsSearchProvider.class);
    private static final String RSS_URL = "https://news.google.com/rss/search";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";

    private static final Pattern TITLE_PATTERN = Pattern.compile("<title><!\\[CDATA\\[(.+?)]]></title>|<title>(.+?)</title>");
    private static final Pattern LINK_PATTERN = Pattern.compile("<link>(.+?)</link>");
    private static final Pattern DESC_PATTERN = Pattern.compile("<description><!\\[CDATA\\[(.+?)]]></description>|<description>(.+?)</description>");
    private static final Pattern SOURCE_PATTERN = Pattern.compile("<source[^>]*>(.+?)</source>");
    private static final Pattern ITEM_PATTERN = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);

    private final OkHttpClient httpClient;

    public GoogleNewsSearchProvider() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .followRedirects(true)
            .build();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) throws WebSearchException {
        String url = RSS_URL + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&hl=en-US&gl=US&ceid=US:en";

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new WebSearchException("Google News RSS returned HTTP " + response.code());
            }

            String xml = response.body() != null ? response.body().string() : "";
            List<SearchResult> results = parseRss(xml, maxResults);
            log.info("Google News returned {} results for query: {}", results.size(), query);
            return results;
        } catch (WebSearchException e) {
            throw e;
        } catch (Exception e) {
            throw new WebSearchException("Google News search failed: " + e.getMessage(), e);
        }
    }

    private List<SearchResult> parseRss(String xml, int maxResults) {
        List<SearchResult> results = new ArrayList<>();

        Matcher itemMatcher = ITEM_PATTERN.matcher(xml);
        while (itemMatcher.find() && results.size() < maxResults) {
            String item = itemMatcher.group(1);

            String title = extractFirst(TITLE_PATTERN, item);
            String link = extractFirst(LINK_PATTERN, item);
            String description = extractFirst(DESC_PATTERN, item);
            String source = extractFirst(SOURCE_PATTERN, item);

            if (title == null || title.isEmpty()) continue;

            // Clean HTML from description
            String snippet = "";
            if (description != null && !description.isEmpty()) {
                snippet = stripHtml(description);
            }

            // Append source to title if available
            if (source != null && !source.isEmpty()) {
                title = title + " (" + source + ")";
            }

            if (link == null) link = "";

            results.add(new SearchResult(title, snippet, link));
        }

        return results;
    }

    private static String extractFirst(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            // Return first non-null group (handles CDATA vs plain)
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) return m.group(i).trim();
            }
        }
        return null;
    }

    private static String stripHtml(String html) {
        // Remove HTML tags, decode common entities
        return html.replaceAll("<[^>]+>", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    @Override
    public String getName() {
        return "Google News";
    }
}
