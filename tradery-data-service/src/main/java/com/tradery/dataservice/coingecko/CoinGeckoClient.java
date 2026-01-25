package com.tradery.dataservice.coingecko;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.dataservice.data.HttpClientFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CoinGecko API client with rate limiting and circuit breaker.
 *
 * Rate limiting: 8.5s between calls (free tier: ~10-30 calls/minute)
 * Circuit breaker: 10 consecutive failures opens circuit for 15 minutes
 * Retry: Exponential backoff with max 3 retries
 */
public class CoinGeckoClient {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);

    private static final String BASE_URL = "https://api.coingecko.com/api/v3";
    private static final long RATE_LIMIT_MS = 8500; // 8.5 seconds between calls
    private static final int CIRCUIT_BREAKER_THRESHOLD = 10;
    private static final Duration CIRCUIT_BREAKER_RESET_DURATION = Duration.ofMinutes(15);
    private static final int MAX_RETRIES = 3;
    private static final int TICKERS_PAGE_SIZE = 100;

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    // Rate limiting state
    private volatile long lastRequestTime = 0;
    private final Object rateLimitLock = new Object();

    // Circuit breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile boolean circuitOpen = false;
    private volatile Instant circuitOpenTime = null;

    public CoinGeckoClient() {
        this.client = HttpClientFactory.getClient();
        this.mapper = HttpClientFactory.getMapper();
    }

    /**
     * Check if circuit breaker is open.
     */
    public boolean isCircuitOpen() {
        if (!circuitOpen) {
            return false;
        }

        // Check if reset duration has passed
        if (circuitOpenTime != null &&
            Instant.now().isAfter(circuitOpenTime.plus(CIRCUIT_BREAKER_RESET_DURATION))) {
            log.info("Circuit breaker reset after timeout");
            resetCircuitBreaker();
            return false;
        }

        return true;
    }

    /**
     * Reset the circuit breaker.
     */
    public void resetCircuitBreaker() {
        circuitOpen = false;
        circuitOpenTime = null;
        consecutiveFailures.set(0);
    }

    /**
     * Fetch all tickers for an exchange (paginated).
     * CoinGecko returns max 100 tickers per page.
     *
     * @param exchangeId CoinGecko exchange ID (e.g., "binance", "binance_futures")
     * @return List of ticker JsonNodes
     */
    public List<JsonNode> fetchExchangeTickers(String exchangeId) throws IOException {
        if (isCircuitOpen()) {
            throw new IOException("Circuit breaker is open - CoinGecko API unavailable");
        }

        List<JsonNode> allTickers = new ArrayList<>();
        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            String url = String.format("%s/exchanges/%s/tickers?page=%d&depth=false",
                BASE_URL, exchangeId, page);

            JsonNode response = executeRequest(url);
            JsonNode tickers = response.get("tickers");

            if (tickers == null || tickers.isEmpty()) {
                hasMore = false;
            } else {
                for (JsonNode ticker : tickers) {
                    allTickers.add(ticker);
                }

                // Check if there are more pages
                if (tickers.size() < TICKERS_PAGE_SIZE) {
                    hasMore = false;
                } else {
                    page++;
                }
            }

            log.debug("Fetched page {} for {}: {} tickers", page - 1, exchangeId, tickers != null ? tickers.size() : 0);
        }

        log.info("Fetched {} total tickers for {}", allTickers.size(), exchangeId);
        return allTickers;
    }

    /**
     * Fetch the coins list from CoinGecko.
     * Contains ~14,000+ coins with symbol → id mapping.
     */
    public List<CoinInfo> fetchCoinsList() throws IOException {
        if (isCircuitOpen()) {
            throw new IOException("Circuit breaker is open - CoinGecko API unavailable");
        }

        String url = BASE_URL + "/coins/list";
        JsonNode response = executeRequest(url);

        List<CoinInfo> coins = new ArrayList<>();
        for (JsonNode coin : response) {
            String id = coin.get("id").asText();
            String symbol = coin.get("symbol").asText();
            String name = coin.get("name").asText();
            coins.add(new CoinInfo(id, symbol, name));
        }

        log.info("Fetched {} coins from CoinGecko", coins.size());
        return coins;
    }

    /**
     * Get exchange info from CoinGecko.
     */
    public JsonNode fetchExchangeInfo(String exchangeId) throws IOException {
        if (isCircuitOpen()) {
            throw new IOException("Circuit breaker is open - CoinGecko API unavailable");
        }

        String url = String.format("%s/exchanges/%s", BASE_URL, exchangeId);
        return executeRequest(url);
    }

    /**
     * Execute a request with rate limiting and retries.
     */
    private JsonNode executeRequest(String url) throws IOException {
        // Wait for rate limit
        waitForRateLimit();

        IOException lastException = null;
        int retryDelayMs = 1000;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                log.debug("Retry attempt {} for {}", attempt, url);
                try {
                    Thread.sleep(retryDelayMs);
                    retryDelayMs *= 2; // Exponential backoff
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during retry", e);
                }
            }

            try {
                Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        onSuccess();
                        String body = response.body() != null ? response.body().string() : "";
                        return mapper.readTree(body);
                    }

                    // Handle rate limiting (429)
                    if (response.code() == 429) {
                        log.warn("Rate limited by CoinGecko, waiting before retry");
                        String retryAfter = response.header("Retry-After");
                        int waitSeconds = retryAfter != null ? Integer.parseInt(retryAfter) : 60;
                        try {
                            Thread.sleep(waitSeconds * 1000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }

                    String errorBody = response.body() != null ? response.body().string() : "";
                    lastException = new IOException(
                        "CoinGecko API error: " + response.code() + " " + response.message() + " - " + errorBody
                    );
                    onFailure();

                    // Don't retry on 4xx errors (except 429)
                    if (response.code() >= 400 && response.code() < 500) {
                        throw lastException;
                    }
                }
            } catch (IOException e) {
                lastException = e;
                onFailure();
            }
        }

        throw lastException != null ? lastException : new IOException("Request failed after retries: " + url);
    }

    /**
     * Wait for rate limit.
     */
    private void waitForRateLimit() {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long timeSinceLastRequest = now - lastRequestTime;

            if (timeSinceLastRequest < RATE_LIMIT_MS) {
                long waitTime = RATE_LIMIT_MS - timeSinceLastRequest;
                log.debug("Rate limiting: waiting {}ms", waitTime);
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            lastRequestTime = System.currentTimeMillis();
        }
    }

    /**
     * Record successful request.
     */
    private void onSuccess() {
        consecutiveFailures.set(0);
        if (circuitOpen) {
            log.info("Circuit breaker reset after successful request");
            resetCircuitBreaker();
        }
    }

    /**
     * Record failed request.
     */
    private void onFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_BREAKER_THRESHOLD && !circuitOpen) {
            circuitOpen = true;
            circuitOpenTime = Instant.now();
            log.error("Circuit breaker opened after {} consecutive failures", failures);
        }
    }

    /**
     * Get circuit breaker status.
     */
    public CircuitBreakerStatus getCircuitBreakerStatus() {
        return new CircuitBreakerStatus(
            circuitOpen,
            consecutiveFailures.get(),
            circuitOpenTime,
            circuitOpen && circuitOpenTime != null
                ? circuitOpenTime.plus(CIRCUIT_BREAKER_RESET_DURATION)
                : null
        );
    }

    /**
     * Circuit breaker status.
     */
    public record CircuitBreakerStatus(
        boolean isOpen,
        int consecutiveFailures,
        Instant openedAt,
        Instant resetsAt
    ) {}
}
