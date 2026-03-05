package com.tradery.dataservice.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.AggTrade;
import com.tradery.core.model.DataMarketType;
import com.tradery.core.model.Exchange;
import com.tradery.core.model.FetchProgress;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Binance API client for fetching aggregated trade data.
 * Uses the public API (no authentication required).
 */
public class AggTradesClient {

    private static final Logger log = LoggerFactory.getLogger(AggTradesClient.class);
    private static final String SPOT_BASE_URL = "https://api.binance.com/api/v3";
    private static final String FUTURES_BASE_URL = "https://fapi.binance.com/fapi/v1";
    private static final int MAX_TRADES_PER_REQUEST = 1000;

    /**
     * Get the correct API base URL for a market type.
     */
    private static String getBaseUrl(String marketType) {
        return "spot".equalsIgnoreCase(marketType) ? SPOT_BASE_URL : FUTURES_BASE_URL;
    }

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public AggTradesClient() {
        this.client = HttpClientFactory.getClient();
        this.mapper = HttpClientFactory.getMapper();
    }

    /**
     * Fetch aggregated trades from Binance (defaults to futures/perp).
     */
    public List<AggTrade> fetchAggTrades(String symbol, long startTime, long endTime, int limit)
            throws IOException {
        return fetchAggTrades(symbol, startTime, endTime, limit, "perp");
    }

    /**
     * Fetch aggregated trades from Binance.
     *
     * @param symbol     Trading pair (e.g., "BTCUSDT")
     * @param startTime  Start time in milliseconds
     * @param endTime    End time in milliseconds
     * @param limit      Max number of trades (max 1000)
     * @param marketType "spot" or "perp" (selects API endpoint)
     * @return List of aggregated trades
     */
    public List<AggTrade> fetchAggTrades(String symbol, long startTime, long endTime, int limit,
                                          String marketType) throws IOException {

        String baseUrl = getBaseUrl(marketType);
        DataMarketType dataMarketType = "spot".equalsIgnoreCase(marketType)
            ? DataMarketType.SPOT : DataMarketType.FUTURES_PERP;

        StringBuilder url = new StringBuilder(baseUrl + "/aggTrades")
            .append("?symbol=").append(symbol)
            .append("&limit=").append(Math.min(limit, MAX_TRADES_PER_REQUEST));

        if (startTime > 0) {
            url.append("&startTime=").append(startTime);
        }
        if (endTime > 0) {
            url.append("&endTime=").append(endTime);
        }

        Request request = new Request.Builder()
            .url(url.toString())
            .get()
            .build();

        Response response = executeWithRetry(request, marketType);
        try (response) {
            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            List<AggTrade> trades = new ArrayList<>();

            for (JsonNode trade : root) {
                // AggTrade format: {"a":id,"p":"price","q":"qty","f":firstId,"l":lastId,"T":time,"m":isBuyerMaker}
                long aggTradeId = trade.get("a").asLong();
                double price = Double.parseDouble(trade.get("p").asText());
                double quantity = Double.parseDouble(trade.get("q").asText());
                long firstTradeId = trade.get("f").asLong();
                long lastTradeId = trade.get("l").asLong();
                long timestamp = trade.get("T").asLong();
                boolean isBuyerMaker = trade.get("m").asBoolean();

                trades.add(AggTrade.withExchange(
                    aggTradeId, price, quantity, firstTradeId, lastTradeId, timestamp, isBuyerMaker,
                    Exchange.BINANCE, dataMarketType, null));
            }

            return trades;
        }
    }

    /**
     * Fetch all aggregated trades (defaults to futures/perp).
     * @deprecated Use {@link #streamAggTrades} for memory-efficient streaming
     */
    @Deprecated
    public List<AggTrade> fetchAllAggTrades(String symbol, long startTime, long endTime,
                                             AtomicBoolean cancelled, Consumer<FetchProgress> onProgress)
            throws IOException {
        return fetchAllAggTrades(symbol, startTime, endTime, cancelled, onProgress, "perp");
    }

    /**
     * Fetch all aggregated trades between start and end time.
     * @deprecated Use {@link #streamAggTrades} for memory-efficient streaming
     */
    @Deprecated
    public List<AggTrade> fetchAllAggTrades(String symbol, long startTime, long endTime,
                                             AtomicBoolean cancelled, Consumer<FetchProgress> onProgress,
                                             String marketType) throws IOException {

        List<AggTrade> allTrades = new ArrayList<>();
        streamAggTrades(symbol, startTime, endTime, cancelled, onProgress, allTrades::addAll, marketType);
        return allTrades;
    }

    /**
     * Stream aggregated trades (defaults to futures/perp).
     */
    public int streamAggTrades(String symbol, long startTime, long endTime,
                               AtomicBoolean cancelled, Consumer<FetchProgress> onProgress,
                               Consumer<List<AggTrade>> batchConsumer) throws IOException {
        return streamAggTrades(symbol, startTime, endTime, cancelled, onProgress, batchConsumer, "perp");
    }

    /**
     * Stream aggregated trades between start and end time.
     * Calls batchConsumer with each batch as it's fetched, avoiding memory accumulation.
     *
     * @param symbol        Trading pair (e.g., "BTCUSDT")
     * @param startTime     Start time in milliseconds
     * @param endTime       End time in milliseconds
     * @param cancelled     Optional AtomicBoolean to signal cancellation
     * @param onProgress    Optional callback for progress updates
     * @param batchConsumer Consumer called with each batch of trades (up to 1000 per batch)
     * @param marketType    "spot" or "perp" (selects API endpoint)
     * @return Total number of trades fetched
     */
    public int streamAggTrades(String symbol, long startTime, long endTime,
                               AtomicBoolean cancelled, Consumer<FetchProgress> onProgress,
                               Consumer<List<AggTrade>> batchConsumer, String marketType) throws IOException {

        int totalCount = 0;
        long currentStart = startTime;

        // Estimate total trades for progress (rough estimate based on symbol)
        long estimatedTradesPerDay = SyncEstimator.getTradesPerDay(symbol);
        long days = Math.max(1, (endTime - startTime) / (24 * 60 * 60 * 1000));
        long estimatedTotal = days * estimatedTradesPerDay;

        log.info("Streaming {} {} aggTrades from Binance ({})...", symbol, marketType, getBaseUrl(marketType));

        // Report starting
        if (onProgress != null) {
            onProgress.accept(FetchProgress.starting(symbol, "aggTrades"));
        }

        while (currentStart < endTime) {
            // Check for cancellation before each request
            if (cancelled != null && cancelled.get()) {
                log.debug("Fetch cancelled after {} trades.", totalCount);
                if (onProgress != null) {
                    onProgress.accept(FetchProgress.cancelled(totalCount));
                }
                return totalCount;
            }

            List<AggTrade> batch = fetchAggTrades(symbol, currentStart, endTime, MAX_TRADES_PER_REQUEST, marketType);

            if (batch.isEmpty()) {
                break;
            }

            // Stream batch to consumer immediately
            batchConsumer.accept(batch);
            totalCount += batch.size();

            // Update start time for next batch - use last trade timestamp + 1ms
            AggTrade lastTrade = batch.get(batch.size() - 1);
            currentStart = lastTrade.timestamp() + 1;

            // If we got fewer than max, we're done
            if (batch.size() < MAX_TRADES_PER_REQUEST) {
                break;
            }

            // Report progress
            if (onProgress != null) {
                int percent = (int) Math.min(99, (totalCount * 100) / estimatedTotal);
                String msg = "Fetching " + symbol + " " + marketType + " trades: " + formatCount(totalCount) + "...";
                onProgress.accept(new FetchProgress(totalCount, (int) estimatedTotal, msg));
            }

            if (totalCount % 50000 == 0) {
                log.debug("Streamed {} trades so far...", formatCount(totalCount));
            }

            // Rate limiting - Binance allows 1200 requests per minute
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (onProgress != null) {
                    onProgress.accept(FetchProgress.cancelled(totalCount));
                }
                return totalCount;
            }
        }

        log.info("Stream complete. Total: {} trades", formatCount(totalCount));

        // Report completion
        if (onProgress != null) {
            onProgress.accept(FetchProgress.complete(totalCount));
        }

        return totalCount;
    }

    /**
     * Execute an HTTP request with exponential backoff on 429 (Too Many Requests).
     * Retries up to 5 times with delays: 5s, 15s, 30s, 60s, 120s.
     * Reads and logs Binance Retry-After header when available.
     */
    private Response executeWithRetry(Request request, String marketType) throws IOException {
        int maxRetries = 5;
        long[] backoffMs = {5_000, 15_000, 30_000, 60_000, 120_000};

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            Response response = client.newCall(request).execute();

            if (response.code() != 429) {
                if (!response.isSuccessful()) {
                    response.close();
                    throw new IOException("Binance " + marketType + " API error: " + response.code() + " " + response.message());
                }
                return response;
            }

            // 429 — rate limited
            response.close();

            if (attempt == maxRetries) {
                throw new IOException("Binance " + marketType + " API rate limit (429) persists after " + maxRetries + " retries");
            }

            long waitMs = backoffMs[attempt];

            // Check Retry-After header (Binance sometimes sends it)
            String retryAfter = response.header("Retry-After");
            if (retryAfter != null) {
                try {
                    long retryAfterMs = Long.parseLong(retryAfter) * 1000;
                    waitMs = Math.max(waitMs, retryAfterMs);
                } catch (NumberFormatException ignored) {}
            }

            log.warn("Binance {} API returned 429, waiting {}s before retry {}/{}",
                marketType, waitMs / 1000, attempt + 1, maxRetries);

            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Retry interrupted for " + marketType + " API");
            }
        }

        // Unreachable, but compiler needs it
        throw new IOException("Binance " + marketType + " API rate limit exhausted");
    }

    private String formatCount(int count) {
        if (count >= 1_000_000) {
            return String.format("%.1fM", count / 1_000_000.0);
        } else if (count >= 1_000) {
            return String.format("%.1fK", count / 1_000.0);
        }
        return String.valueOf(count);
    }
}
