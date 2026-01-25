package com.tradery.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.model.PremiumIndex;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Binance Futures API client for fetching premium index kline data.
 * Uses the public API (no authentication required).
 *
 * API Endpoint: GET /fapi/v1/premiumIndexKlines
 *
 * Premium Index = (Futures Price - Index Price) / Index Price
 * Measures the spread between futures and spot markets.
 */
public class PremiumIndexClient {

    private static final Logger log = LoggerFactory.getLogger(PremiumIndexClient.class);
    private static final String BASE_URL = "https://fapi.binance.com/fapi/v1";
    private static final int MAX_RECORDS_PER_REQUEST = 1500;

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public PremiumIndexClient() {
        this.client = HttpClientFactory.getClient();
        this.mapper = HttpClientFactory.getMapper();
    }

    /**
     * Fetch premium index kline data from Binance Futures API.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param interval  Kline interval (e.g., "1h", "5m", "1d")
     * @param startTime Start time in milliseconds (inclusive)
     * @param endTime   End time in milliseconds (inclusive)
     * @return List of premium index klines sorted by time ascending
     */
    public List<PremiumIndex> fetchPremiumIndexKlines(String symbol, String interval,
                                                       long startTime, long endTime)
            throws IOException {

        List<PremiumIndex> allKlines = new ArrayList<>();
        long currentStart = startTime;

        while (currentStart < endTime) {
            List<PremiumIndex> batch = fetchBatch(symbol, interval, currentStart, endTime);

            if (batch.isEmpty()) {
                break;
            }

            allKlines.addAll(batch);

            // Move start time to after the last fetched record
            PremiumIndex last = batch.get(batch.size() - 1);
            currentStart = last.closeTime() + 1;

            // Small delay to respect rate limits
            if (currentStart < endTime) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return allKlines;
    }

    /**
     * Fetch a single batch of premium index klines.
     */
    private List<PremiumIndex> fetchBatch(String symbol, String interval,
                                           long startTime, long endTime)
            throws IOException {

        StringBuilder url = new StringBuilder(BASE_URL + "/premiumIndexKlines")
            .append("?symbol=").append(symbol)
            .append("&interval=").append(interval)
            .append("&limit=").append(MAX_RECORDS_PER_REQUEST);

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
                String body = response.body() != null ? response.body().string() : "";
                throw new IOException("Binance Futures API error: " + response.code() +
                    " " + response.message() + " - " + body);
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            List<PremiumIndex> klines = new ArrayList<>();

            for (JsonNode node : root) {
                // Response format: [openTime, open, high, low, close, ignore, closeTime, ...]
                if (node.isArray() && node.size() >= 7) {
                    long openTime = node.get(0).asLong();
                    double open = Double.parseDouble(node.get(1).asText());
                    double high = Double.parseDouble(node.get(2).asText());
                    double low = Double.parseDouble(node.get(3).asText());
                    double close = Double.parseDouble(node.get(4).asText());
                    long closeTime = node.get(6).asLong();

                    klines.add(new PremiumIndex(openTime, open, high, low, close, closeTime));
                }
            }

            return klines;
        }
    }

    /**
     * Fetch the current premium index value.
     */
    public PremiumIndex fetchCurrentPremiumIndex(String symbol, String interval) throws IOException {
        StringBuilder url = new StringBuilder(BASE_URL + "/premiumIndexKlines")
            .append("?symbol=").append(symbol)
            .append("&interval=").append(interval)
            .append("&limit=1");

        Request request = new Request.Builder()
            .url(url.toString())
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Binance Futures API error: " + response.code());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            if (root.isEmpty() || !root.isArray()) {
                return null;
            }

            JsonNode node = root.get(0);
            if (node.isArray() && node.size() >= 7) {
                return new PremiumIndex(
                    node.get(0).asLong(),
                    Double.parseDouble(node.get(1).asText()),
                    Double.parseDouble(node.get(2).asText()),
                    Double.parseDouble(node.get(3).asText()),
                    Double.parseDouble(node.get(4).asText()),
                    node.get(6).asLong()
                );
            }
            return null;
        }
    }
}
