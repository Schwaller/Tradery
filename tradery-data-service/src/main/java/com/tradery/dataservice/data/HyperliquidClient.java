package com.tradery.dataservice.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradery.core.model.Candle;
import com.tradery.core.model.FetchProgress;
import com.tradery.core.model.FundingRate;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Hyperliquid API client for fetching candle and funding rate data.
 * All endpoints use POST https://api.hyperliquid.xyz/info with JSON body.
 */
public class HyperliquidClient {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidClient.class);
    private static final String API_URL = "https://api.hyperliquid.xyz/info";
    private static final MediaType JSON = MediaType.get("application/json");
    private static final int MAX_CANDLES_PER_REQUEST = 5000;
    private static final int MAX_FUNDING_PER_REQUEST = 500;
    private static final int MAX_RETRIES = 3;
    private static final long RATE_LIMIT_BACKOFF_MS = 2000;

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public HyperliquidClient() {
        this.client = HttpClientFactory.getClient();
        this.mapper = HttpClientFactory.getMapper();
    }

    /**
     * Fetch candle data from Hyperliquid, paginated.
     *
     * @param coin      Coin name (e.g., "BTC", "ETH", "xyz:GOLD")
     * @param interval  Timeframe (e.g., "1h", "4h", "1d")
     * @param startTime Start time in milliseconds (inclusive)
     * @param endTime   End time in milliseconds (inclusive)
     * @param cancelled Cancellation flag
     * @param onProgress Progress callback
     * @return List of candles sorted by time ascending
     */
    public List<Candle> fetchAllKlines(String coin, String interval,
                                        long startTime, long endTime,
                                        AtomicBoolean cancelled,
                                        Consumer<FetchProgress> onProgress) throws IOException {

        List<Candle> allCandles = new ArrayList<>();
        long currentStart = startTime;
        long intervalMs = getIntervalMs(interval);
        int estimatedTotal = (int) ((endTime - startTime) / intervalMs);

        log.info("Fetching {} {} candles from Hyperliquid...", coin, interval);

        if (onProgress != null) {
            onProgress.accept(FetchProgress.starting(coin, interval));
        }

        while (currentStart < endTime) {
            if (cancelled != null && cancelled.get()) {
                log.debug("Fetch cancelled. Returning {} candles.", allCandles.size());
                if (onProgress != null) {
                    onProgress.accept(FetchProgress.cancelled(allCandles.size()));
                }
                return allCandles;
            }

            List<Candle> batch = fetchCandleBatchWithRetry(coin, interval, currentStart, endTime);

            if (batch.isEmpty()) {
                break;
            }

            // HL always returns the latest candle even when startTime is beyond available data.
            // Detect no-progress: if last candle timestamp hasn't advanced, we've got all data.
            Candle last = batch.get(batch.size() - 1);
            long newStart = last.timestamp() + intervalMs;
            if (newStart <= currentStart) {
                // No progress — add any new candles and stop
                allCandles.addAll(batch);
                break;
            }

            allCandles.addAll(batch);
            currentStart = newStart;

            if (onProgress != null) {
                String msg = "Fetching " + coin + " " + interval + ": " + allCandles.size() + " candles...";
                onProgress.accept(new FetchProgress(allCandles.size(), estimatedTotal, msg));
            }

            log.debug("Fetched {} candles so far...", allCandles.size());

            // Rate limiting — HL is more sensitive than Binance
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (onProgress != null) {
                    onProgress.accept(FetchProgress.cancelled(allCandles.size()));
                }
                return allCandles;
            }
        }

        log.info("Fetch complete. Total: {} candles", allCandles.size());

        if (onProgress != null) {
            onProgress.accept(FetchProgress.complete(allCandles.size()));
        }

        return allCandles;
    }

    /**
     * Fetch a batch of candles with retry on 429 rate limit.
     */
    private List<Candle> fetchCandleBatchWithRetry(String coin, String interval,
                                                    long startTime, long endTime) throws IOException {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return fetchCandleBatch(coin, interval, startTime, endTime);
            } catch (RateLimitException e) {
                if (attempt < MAX_RETRIES - 1) {
                    long backoff = RATE_LIMIT_BACKOFF_MS * (attempt + 1);
                    log.warn("Rate limited by Hyperliquid, backing off {}ms (attempt {}/{})", backoff, attempt + 1, MAX_RETRIES);
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new ArrayList<>();
                    }
                } else {
                    log.error("Rate limited by Hyperliquid after {} retries, returning partial data", MAX_RETRIES);
                    return new ArrayList<>();
                }
            }
        }
        return new ArrayList<>();
    }

    private static class RateLimitException extends IOException {
        RateLimitException(String msg) { super(msg); }
    }

    /**
     * Fetch a single batch of candles from Hyperliquid.
     * POST {"type":"candleSnapshot","req":{"coin":"BTC","interval":"1h","startTime":...,"endTime":...}}
     */
    private List<Candle> fetchCandleBatch(String coin, String interval,
                                           long startTime, long endTime) throws IOException {

        ObjectNode reqBody = mapper.createObjectNode();
        reqBody.put("type", "candleSnapshot");
        ObjectNode req = reqBody.putObject("req");
        req.put("coin", coin);
        req.put("interval", interval);
        req.put("startTime", startTime);
        req.put("endTime", endTime);

        Request request = new Request.Builder()
            .url(API_URL)
            .post(RequestBody.create(reqBody.toString(), JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                // HL returns 500 for unknown coins — treat as empty, not error
                if (response.code() == 500) {
                    log.warn("Hyperliquid returned 500 for coin '{}' interval '{}' — treating as no data", coin, interval);
                    return new ArrayList<>();
                }
                if (response.code() == 429) {
                    throw new RateLimitException("Hyperliquid rate limited for " + coin + " " + interval);
                }
                throw new IOException("Hyperliquid API error: " + response.code() + " " + response.message() + " - " + body);
            }

            JsonNode root = mapper.readTree(body);

            List<Candle> candles = new ArrayList<>();
            if (root == null || root.isNull() || !root.isArray()) {
                return candles;
            }
            for (JsonNode node : root) {
                long timestamp = node.get("t").asLong();
                double open = node.get("o").asDouble();
                double high = node.get("h").asDouble();
                double low = node.get("l").asDouble();
                double close = node.get("c").asDouble();
                double volume = node.get("v").asDouble();
                int tradeCount = node.has("n") ? node.get("n").asInt() : -1;
                // Hyperliquid doesn't provide quote volume separately — approximate as volume * close
                double quoteVolume = volume * close;
                // Taker buy/sell breakdown not available
                candles.add(new Candle(timestamp, open, high, low, close, volume,
                    tradeCount, quoteVolume, -1, -1));
            }

            return candles;
        }
    }

    /**
     * Fetch funding rate history from Hyperliquid, paginated.
     * POST {"type":"fundingHistory","coin":"BTC","startTime":...}
     *
     * @param coin      Coin name (e.g., "BTC")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds (used to stop pagination)
     * @return List of funding rates sorted by time ascending
     */
    public List<FundingRate> fetchFundingRates(String coin, long startTime, long endTime)
            throws IOException {

        List<FundingRate> allRates = new ArrayList<>();
        long currentStart = startTime;

        log.info("Fetching {} funding rates from Hyperliquid...", coin);

        while (currentStart < endTime) {
            List<FundingRate> batch = fetchFundingBatchWithRetry(coin, currentStart);

            if (batch.isEmpty()) {
                break;
            }

            // Filter out entries beyond endTime
            for (FundingRate rate : batch) {
                if (rate.fundingTime() > endTime) {
                    break;
                }
                allRates.add(rate);
            }

            // Advance past last entry
            FundingRate last = batch.get(batch.size() - 1);
            currentStart = last.fundingTime() + 1;

            // If last batch entry is beyond endTime, we're done
            if (last.fundingTime() >= endTime) {
                break;
            }

            // If batch smaller than max, no more data available
            if (batch.size() < MAX_FUNDING_PER_REQUEST) {
                break;
            }

            // Rate limiting
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("Fetch complete. Total: {} funding rates", allRates.size());
        return allRates;
    }

    private List<FundingRate> fetchFundingBatchWithRetry(String coin, long startTime) throws IOException {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return fetchFundingBatch(coin, startTime);
            } catch (RateLimitException e) {
                if (attempt < MAX_RETRIES - 1) {
                    long backoff = RATE_LIMIT_BACKOFF_MS * (attempt + 1);
                    log.warn("Rate limited by Hyperliquid (funding), backing off {}ms (attempt {}/{})", backoff, attempt + 1, MAX_RETRIES);
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new ArrayList<>();
                    }
                } else {
                    log.error("Rate limited by Hyperliquid after {} retries for funding", MAX_RETRIES);
                    return new ArrayList<>();
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * Fetch a single batch of funding rates.
     * POST {"type":"fundingHistory","coin":"BTC","startTime":...}
     */
    private List<FundingRate> fetchFundingBatch(String coin, long startTime) throws IOException {
        ObjectNode reqBody = mapper.createObjectNode();
        reqBody.put("type", "fundingHistory");
        reqBody.put("coin", coin);
        reqBody.put("startTime", startTime);

        Request request = new Request.Builder()
            .url(API_URL)
            .post(RequestBody.create(reqBody.toString(), JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                if (response.code() == 500) {
                    log.warn("Hyperliquid returned 500 for coin '{}' funding — treating as no data", coin);
                    return new ArrayList<>();
                }
                if (response.code() == 429) {
                    throw new RateLimitException("Hyperliquid rate limited for " + coin + " funding");
                }
                throw new IOException("Hyperliquid API error: " + response.code() + " " + response.message() + " - " + body);
            }

            JsonNode root = mapper.readTree(body);

            List<FundingRate> rates = new ArrayList<>();
            if (root == null || root.isNull() || !root.isArray()) {
                return rates;
            }
            for (JsonNode node : root) {
                String symbol = node.get("coin").asText();
                double fundingRate = Double.parseDouble(node.get("fundingRate").asText());
                long fundingTime = node.get("time").asLong();
                double premium = node.has("premium") ? Double.parseDouble(node.get("premium").asText()) : 0.0;

                rates.add(new FundingRate(symbol, fundingRate, fundingTime, premium));
            }

            return rates;
        }
    }

    private long getIntervalMs(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "1h" -> 3_600_000L;
            case "4h" -> 14_400_000L;
            case "1d" -> 86_400_000L;
            default -> 3_600_000L;
        };
    }
}
