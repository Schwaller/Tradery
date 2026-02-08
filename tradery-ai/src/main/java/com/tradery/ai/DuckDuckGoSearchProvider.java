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
 */
public class DuckDuckGoSearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(DuckDuckGoSearchProvider.class);
    private static final String BASE_URL = "https://html.duckduckgo.com/html/";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final OkHttpClient httpClient;

    public DuckDuckGoSearchProvider() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
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
            return parseResults(html, maxResults);
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

            if (!title.isEmpty()) {
                results.add(new SearchResult(title, snippet, extractDomain(resultUrl)));
            }
        }

        return results;
    }

    private String extractDomain(String url) {
        try {
            // DDG wraps URLs in redirects — extract the actual domain from the URL
            if (url.contains("uddg=")) {
                String decoded = java.net.URLDecoder.decode(url, StandardCharsets.UTF_8);
                int start = decoded.indexOf("uddg=") + 5;
                url = decoded.substring(start);
                if (url.contains("&")) {
                    url = url.substring(0, url.indexOf("&"));
                }
            }
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }

    @Override
    public String getName() {
        return "DuckDuckGo";
    }
}
