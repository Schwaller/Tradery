package com.tradery.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.model.Candle;
import com.tradery.model.FetchProgress;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Binance API client for fetching OHLC kline data.
 * Uses the futures API to match aggTrades data source.
 */
public class BinanceClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceClient.class);
    private static final String BASE_URL = "https://fapi.binance.com/fapi/v1";
    private static final int MAX_KLINES_PER_REQUEST = 1000;

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public BinanceClient() {
        this.client = HttpClientFactory.getClient();
        this.mapper = HttpClientFactory.getMapper();
    }

    /**
     * Fetch klines (candlestick data) from Binance.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param interval  Kline interval (e.g., "1h", "4h", "1d")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds (optional, 0 for current)
     * @param limit     Max number of candles (max 1000)
     * @return List of candles
     */
    public List<Candle> fetchKlines(String symbol, String interval, long startTime, long endTime, int limit)
            throws IOException {

        StringBuilder url = new StringBuilder(BASE_URL + "/klines")
            .append("?symbol=").append(symbol)
            .append("&interval=").append(interval)
            .append("&limit=").append(Math.min(limit, MAX_KLINES_PER_REQUEST));

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

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Binance API error: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            List<Candle> candles = new ArrayList<>();

            for (JsonNode kline : root) {
                // Kline format: [openTime, open, high, low, close, volume, closeTime,
                //                quoteVolume, numTrades, takerBuyBase, takerBuyQuote, ignore]
                long timestamp = kline.get(0).asLong();
                double open = Double.parseDouble(kline.get(1).asText());
                double high = Double.parseDouble(kline.get(2).asText());
                double low = Double.parseDouble(kline.get(3).asText());
                double close = Double.parseDouble(kline.get(4).asText());
                double volume = Double.parseDouble(kline.get(5).asText());
                // Extended fields
                double quoteVolume = kline.has(7) ? Double.parseDouble(kline.get(7).asText()) : -1;
                int tradeCount = kline.has(8) ? kline.get(8).asInt() : -1;
                double takerBuyVolume = kline.has(9) ? Double.parseDouble(kline.get(9).asText()) : -1;
                double takerBuyQuoteVolume = kline.has(10) ? Double.parseDouble(kline.get(10).asText()) : -1;

                candles.add(new Candle(timestamp, open, high, low, close, volume,
                    tradeCount, quoteVolume, takerBuyVolume, takerBuyQuoteVolume));
            }

            return candles;
        }
    }

    /**
     * Fetch all klines between start and end time.
     * Handles pagination automatically.
     */
    public List<Candle> fetchAllKlines(String symbol, String interval, long startTime, long endTime)
            throws IOException {
        return fetchAllKlines(symbol, interval, startTime, endTime, null, null);
    }

    /**
     * Fetch all klines between start and end time with cancellation and progress support.
     * Handles pagination automatically.
     *
     * @param symbol     Trading pair (e.g., "BTCUSDT")
     * @param interval   Kline interval (e.g., "1h", "4h", "1d")
     * @param startTime  Start time in milliseconds
     * @param endTime    End time in milliseconds
     * @param cancelled  Optional AtomicBoolean to signal cancellation
     * @param onProgress Optional callback for progress updates
     * @return List of candles (may be partial if cancelled)
     */
    public List<Candle> fetchAllKlines(String symbol, String interval, long startTime, long endTime,
                                        AtomicBoolean cancelled, Consumer<FetchProgress> onProgress)
            throws IOException {

        List<Candle> allCandles = new ArrayList<>();
        long currentStart = startTime;
        long intervalMs = getIntervalMs(interval);

        // Estimate total candles
        int estimatedTotal = (int) ((endTime - startTime) / intervalMs);

        log.info("Fetching {} {} data from Binance...", symbol, interval);

        // Report starting
        if (onProgress != null) {
            onProgress.accept(FetchProgress.starting(symbol, interval));
        }

        while (currentStart < endTime) {
            // Check for cancellation before each request
            if (cancelled != null && cancelled.get()) {
                log.debug("Fetch cancelled. Returning {} candles.", allCandles.size());
                if (onProgress != null) {
                    onProgress.accept(FetchProgress.cancelled(allCandles.size()));
                }
                return allCandles;  // Return what we have so far
            }

            List<Candle> batch = fetchKlines(symbol, interval, currentStart, endTime, MAX_KLINES_PER_REQUEST);

            if (batch.isEmpty()) {
                break;
            }

            allCandles.addAll(batch);

            // Update start time for next batch
            Candle lastCandle = batch.get(batch.size() - 1);
            currentStart = lastCandle.timestamp() + intervalMs;

            // Report progress
            if (onProgress != null) {
                String msg = "Fetching " + symbol + " " + interval + ": " + allCandles.size() + " candles...";
                onProgress.accept(new FetchProgress(allCandles.size(), estimatedTotal, msg));
            }

            log.debug("Fetched {} candles so far...", allCandles.size());

            // Rate limiting - Binance allows 1200 requests per minute
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (onProgress != null) {
                    onProgress.accept(FetchProgress.cancelled(allCandles.size()));
                }
                return allCandles;
            }
        }

        log.info("Fetch complete. Total: {} candles", allCandles.size());

        // Report completion
        if (onProgress != null) {
            onProgress.accept(FetchProgress.complete(allCandles.size()));
        }

        return allCandles;
    }

    /**
     * Get interval in milliseconds
     */
    private long getIntervalMs(String interval) {
        return switch (interval) {
            case "1m" -> Duration.ofMinutes(1).toMillis();
            case "3m" -> Duration.ofMinutes(3).toMillis();
            case "5m" -> Duration.ofMinutes(5).toMillis();
            case "15m" -> Duration.ofMinutes(15).toMillis();
            case "30m" -> Duration.ofMinutes(30).toMillis();
            case "1h" -> Duration.ofHours(1).toMillis();
            case "2h" -> Duration.ofHours(2).toMillis();
            case "4h" -> Duration.ofHours(4).toMillis();
            case "6h" -> Duration.ofHours(6).toMillis();
            case "8h" -> Duration.ofHours(8).toMillis();
            case "12h" -> Duration.ofHours(12).toMillis();
            case "1d" -> Duration.ofDays(1).toMillis();
            case "3d" -> Duration.ofDays(3).toMillis();
            case "1w" -> Duration.ofDays(7).toMillis();
            case "1M" -> Duration.ofDays(30).toMillis();
            default -> Duration.ofHours(1).toMillis();
        };
    }

    /**
     * Get server time from Binance
     */
    public long getServerTime() throws IOException {
        Request request = new Request.Builder()
            .url(BASE_URL + "/time")
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Binance API error: " + response.code());
            }

            JsonNode root = mapper.readTree(response.body().string());
            return root.get("serverTime").asLong();
        }
    }

    /**
     * Check if Binance API is accessible
     */
    public boolean ping() {
        Request request = new Request.Builder()
            .url(BASE_URL + "/ping")
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }
}
