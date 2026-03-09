package com.tradery.ai;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Web search provider using DuckDuckGo's HTML endpoint.
 * Zero config — no API keys, no quotas.
 * Fetches page content from top results for richer context.
 */
public class DuckDuckGoSearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(DuckDuckGoSearchProvider.class);
    private static final String BASE_URL = "https://html.duckduckgo.com/html/";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** Max results to fetch page content for (the rest just use DDG snippets). */
    private static final int CONTENT_FETCH_LIMIT = 4;
    /** Max characters of page text to include per result. */
    private static final int PAGE_TEXT_LIMIT = 1000;

    private final OkHttpClient httpClient;
    private final OkHttpClient contentClient;

    public DuckDuckGoSearchProvider() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .followRedirects(true)
            .build();
        // Shorter timeouts for content fetching — don't block on slow pages
        this.contentClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(8))
            .followRedirects(true)
            .build();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) throws WebSearchException {
        String url = BASE_URL + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new WebSearchException("DuckDuckGo returned HTTP " + response.code());
            }

            String html = response.body() != null ? response.body().string() : "";
            List<SearchResult> results = parseResults(html, maxResults);
            log.debug("DDG returned {} results for query: {}", results.size(), query);

            // Enrich top results with actual page content when snippets are thin
            enrichWithPageContent(results);

            return results;
        } catch (WebSearchException e) {
            throw e;
        } catch (Exception e) {
            throw new WebSearchException("DuckDuckGo search failed: " + e.getMessage(), e);
        }
    }

    private List<SearchResult> parseResults(String html, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Elements resultElements = doc.select(".result");
        for (Element result : resultElements) {
            if (results.size() >= maxResults) break;

            Element titleLink = result.selectFirst(".result__a");
            Element snippetEl = result.selectFirst(".result__snippet");

            if (titleLink == null) continue;

            String title = titleLink.text().trim();
            String resultUrl = titleLink.attr("href").trim();
            String snippet = snippetEl != null ? snippetEl.text().trim() : "";

            String fullUrl = extractFullUrl(resultUrl);
            // Skip DDG ads
            if (fullUrl.contains("duckduckgo.com/y.js")) continue;
            if (!title.isEmpty()) {
                results.add(new SearchResult(title, snippet, fullUrl));
            }
        }

        return results;
    }

    /**
     * For top results with thin snippets, fetch the actual page and extract article text.
     */
    private void enrichWithPageContent(List<SearchResult> results) {
        int fetched = 0;
        for (int i = 0; i < results.size() && fetched < CONTENT_FETCH_LIMIT; i++) {
            SearchResult sr = results.get(i);
            if (sr.url().contains("duckduckgo.com/") || !sr.url().startsWith("http")) continue;

            int snippetLen = sr.snippet() != null ? sr.snippet().length() : 0;
            try {
                String pageText = fetchPageText(sr.url());
                if (pageText != null && pageText.length() > snippetLen) {
                    log.debug("Enriched '{}' with {} chars of page text", sr.title(), pageText.length());
                    results.set(i, new SearchResult(sr.title(), pageText, sr.url()));
                }
            } catch (Exception e) {
                log.debug("Failed to fetch page content from {}: {}", sr.url(), e.getMessage());
            }
            fetched++;
        }
    }

    /**
     * Fetch a page and extract its main text content.
     */
    private String fetchPageText(String pageUrl) {
        Request request = new Request.Builder()
            .url(pageUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html")
            .get()
            .build();

        try (Response response = contentClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;

            String contentType = response.header("Content-Type", "");
            if (!contentType.contains("text/html")) return null;

            String html = response.body() != null ? response.body().string() : "";
            if (html.isEmpty()) return null;

            return extractArticleText(html);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract readable article text from HTML, stripping nav, ads, scripts, etc.
     */
    private String extractArticleText(String html) {
        Document doc = Jsoup.parse(html);

        // Remove non-content elements
        doc.select("script, style, nav, header, footer, aside, .ad, .ads, .sidebar, " +
            ".menu, .navigation, .cookie, .popup, .modal, iframe, form, " +
            "[role=navigation], [role=banner], [role=complementary]").remove();

        // Try article-specific selectors first (most news sites)
        String text = extractFrom(doc, "article");
        if (text == null || text.length() < 100) {
            text = extractFrom(doc, "[role=main]");
        }
        if (text == null || text.length() < 100) {
            text = extractFrom(doc, ".article-body, .post-content, .entry-content, .story-body");
        }
        if (text == null || text.length() < 100) {
            // Fallback: use body paragraphs
            Elements paragraphs = doc.select("p");
            StringBuilder sb = new StringBuilder();
            for (Element p : paragraphs) {
                String pText = p.text().trim();
                if (pText.length() > 40) { // Skip short fragments (nav items, captions)
                    sb.append(pText).append(" ");
                    if (sb.length() > PAGE_TEXT_LIMIT) break;
                }
            }
            text = sb.toString().trim();
        }

        if (text == null || text.length() < 50) return null;

        // Truncate to limit
        if (text.length() > PAGE_TEXT_LIMIT) {
            int cutoff = text.lastIndexOf('.', PAGE_TEXT_LIMIT);
            if (cutoff > PAGE_TEXT_LIMIT / 2) {
                text = text.substring(0, cutoff + 1);
            } else {
                text = text.substring(0, PAGE_TEXT_LIMIT) + "...";
            }
        }
        return text;
    }

    private String extractFrom(Document doc, String selector) {
        Elements els = doc.select(selector);
        if (els.isEmpty()) return null;
        String text = els.first().text().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Extract the actual URL from DuckDuckGo's redirect-wrapped link.
     */
    private String extractFullUrl(String url) {
        try {
            if (url.contains("uddg=")) {
                String decoded = java.net.URLDecoder.decode(url, StandardCharsets.UTF_8);
                int start = decoded.indexOf("uddg=") + 5;
                String extracted = decoded.substring(start);
                if (extracted.contains("&")) {
                    extracted = extracted.substring(0, extracted.indexOf("&"));
                }
                return extracted;
            }
            return url;
        } catch (Exception e) {
            return url;
        }
    }

    @Override
    public String getName() {
        return "DuckDuckGo";
    }
}
