package com.tradery.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.model.Candle;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Binance API client for fetching OHLC kline data.
 * Uses the public API (no authentication required).
 */
public class BinanceClient {

    private static final String BASE_URL = "https://api.binance.com/api/v3";
    private static final int MAX_KLINES_PER_REQUEST = 1000;

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public BinanceClient() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        this.mapper = new ObjectMapper();
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
                // Kline format: [openTime, open, high, low, close, volume, closeTime, ...]
                long timestamp = kline.get(0).asLong();
                double open = Double.parseDouble(kline.get(1).asText());
                double high = Double.parseDouble(kline.get(2).asText());
                double low = Double.parseDouble(kline.get(3).asText());
                double close = Double.parseDouble(kline.get(4).asText());
                double volume = Double.parseDouble(kline.get(5).asText());

                candles.add(new Candle(timestamp, open, high, low, close, volume));
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

        List<Candle> allCandles = new ArrayList<>();
        long currentStart = startTime;

        System.out.println("Fetching " + symbol + " " + interval + " data from Binance...");

        while (currentStart < endTime) {
            List<Candle> batch = fetchKlines(symbol, interval, currentStart, endTime, MAX_KLINES_PER_REQUEST);

            if (batch.isEmpty()) {
                break;
            }

            allCandles.addAll(batch);

            // Update start time for next batch
            Candle lastCandle = batch.get(batch.size() - 1);
            currentStart = lastCandle.timestamp() + getIntervalMs(interval);

            System.out.println("  Fetched " + allCandles.size() + " candles so far...");

            // Rate limiting - Binance allows 1200 requests per minute
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("  Done! Total: " + allCandles.size() + " candles");
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
