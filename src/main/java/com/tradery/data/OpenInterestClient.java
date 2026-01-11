package com.tradery.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.model.OpenInterest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Binance Futures API client for fetching open interest historical data.
 * Uses the public API (no authentication required).
 *
 * API Endpoint: GET /futures/data/openInterestHist
 * Rate Limit: 500 requests per 5 minutes
 * Historical Limit: 30 days (data older than 30 days is not available)
 */
public class OpenInterestClient {

    private static final String BASE_URL = "https://fapi.binance.com/futures/data";
    private static final int MAX_RECORDS_PER_REQUEST = 500;
    private static final String DEFAULT_PERIOD = "5m";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d HH:mm");

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public OpenInterestClient() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Fetch open interest history from Binance Futures API.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param startTime Start time in milliseconds (inclusive)
     * @param endTime   End time in milliseconds (inclusive)
     * @return List of open interest data sorted by time ascending
     */
    public List<OpenInterest> fetchOpenInterest(String symbol, long startTime, long endTime)
            throws IOException {
        return fetchOpenInterest(symbol, startTime, endTime, DEFAULT_PERIOD, null);
    }

    /**
     * Fetch open interest history with progress callback.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param startTime Start time in milliseconds (inclusive)
     * @param endTime   End time in milliseconds (inclusive)
     * @param progress  Progress callback for status updates
     * @return List of open interest data sorted by time ascending
     */
    public List<OpenInterest> fetchOpenInterest(String symbol, long startTime, long endTime,
                                                 Consumer<String> progress) throws IOException {
        return fetchOpenInterest(symbol, startTime, endTime, DEFAULT_PERIOD, progress);
    }

    /**
     * Fetch open interest history with custom period.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param startTime Start time in milliseconds (inclusive)
     * @param endTime   End time in milliseconds (inclusive)
     * @param period    Data period: "5m", "15m", "30m", "1h", "2h", "4h", "6h", "12h", "1d"
     * @param progress  Progress callback for status updates (can be null)
     * @return List of open interest data sorted by time ascending
     */
    public List<OpenInterest> fetchOpenInterest(String symbol, long startTime, long endTime,
                                                 String period, Consumer<String> progress) throws IOException {
        // Calculate expected from time range if not provided externally
        long durationMs = endTime - startTime;
        int expectedRecords = (int) (durationMs / (5 * 60 * 1000));
        return fetchOpenInterest(symbol, startTime, endTime, period, expectedRecords, progress);
    }

    /**
     * Fetch open interest history with custom period and known expected count.
     *
     * @param symbol          Trading pair (e.g., "BTCUSDT")
     * @param startTime       Start time in milliseconds (inclusive)
     * @param endTime         End time in milliseconds (inclusive)
     * @param period          Data period: "5m", "15m", "30m", "1h", "2h", "4h", "6h", "12h", "1d"
     * @param expectedRecords Pre-calculated expected record count (based on gaps analysis)
     * @param progress        Progress callback for status updates (can be null)
     * @return List of open interest data sorted by time ascending
     */
    public List<OpenInterest> fetchOpenInterest(String symbol, long startTime, long endTime,
                                                 String period, int expectedRecords,
                                                 Consumer<String> progress) throws IOException {

        List<OpenInterest> allData = new ArrayList<>();
        long currentStart = startTime;

        String startStr = formatTime(startTime);
        String endStr = formatTime(endTime);

        if (progress != null) {
            progress.accept("Fetching OI: " + startStr + " to " + endStr + " (~" + expectedRecords + " records)");
        }

        // 5-minute data: 288 records per day, need pagination for long ranges
        // Save original bounds for filtering
        final long originalStart = startTime;
        final long originalEnd = endTime;
        long previousLastTimestamp = -1;

        while (currentStart < originalEnd) {
            List<OpenInterest> batch = fetchBatch(symbol, currentStart, originalEnd, period);

            if (batch.isEmpty()) {
                break;
            }

            // Get first and last timestamps
            long firstTs = batch.get(0).timestamp();
            long lastTs = batch.get(batch.size() - 1).timestamp();

            // Stop if we're getting the same data (no progress) - API doesn't have newer data
            if (lastTs == previousLastTimestamp) {
                break;
            }
            previousLastTimestamp = lastTs;

            // Only add records within our requested time range
            for (OpenInterest oi : batch) {
                if (oi.timestamp() >= originalStart && oi.timestamp() <= originalEnd) {
                    allData.add(oi);
                }
            }

            // Move start time to after the last fetched record
            currentStart = lastTs + 1;

            // Stop if we've reached or passed the end time
            if (lastTs >= originalEnd) {
                break;
            }

            // Progress: loaded vs expected (expected is pre-calculated based on actual gaps)
            int pct = expectedRecords > 0 ? Math.min(100, (allData.size() * 100) / expectedRecords) : 0;

            if (progress != null) {
                progress.accept("Fetching OI: " + allData.size() + "/" + expectedRecords + " (" + pct + "%)");
            }

            // Small delay to respect rate limits
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (progress != null) {
            progress.accept("OI: Fetched " + allData.size() + " records");
        }

        return allData;
    }

    private String formatTime(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).format(DATE_FORMAT);
    }

    /**
     * Fetch a single batch of open interest data.
     */
    private List<OpenInterest> fetchBatch(String symbol, long startTime, long endTime, String period)
            throws IOException {

        StringBuilder url = new StringBuilder(BASE_URL + "/openInterestHist")
            .append("?symbol=").append(symbol)
            .append("&period=").append(period)
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
                throw new IOException("Binance Futures API error: " + response.code() + " " + response.message() + " - " + body);
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            List<OpenInterest> data = new ArrayList<>();

            for (JsonNode node : root) {
                // Response format: { symbol, sumOpenInterest, sumOpenInterestValue, timestamp }
                String sym = node.get("symbol").asText();
                double openInterest = Double.parseDouble(node.get("sumOpenInterest").asText());
                double openInterestValue = Double.parseDouble(node.get("sumOpenInterestValue").asText());
                long timestamp = node.get("timestamp").asLong();

                data.add(new OpenInterest(sym, timestamp, openInterest, openInterestValue));
            }

            return data;
        }
    }

    /**
     * Fetch the latest open interest for a symbol.
     * Uses the current OI endpoint (not historical).
     */
    public OpenInterest fetchLatestOpenInterest(String symbol) throws IOException {
        String url = "https://fapi.binance.com/fapi/v1/openInterest?symbol=" + symbol;

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Binance Futures API error: " + response.code());
            }

            String body = response.body().string();
            JsonNode node = mapper.readTree(body);

            if (node == null || node.isEmpty()) {
                return null;
            }

            // Response format: { symbol, openInterest, time }
            return new OpenInterest(
                node.get("symbol").asText(),
                node.get("time").asLong(),
                Double.parseDouble(node.get("openInterest").asText()),
                0.0  // Current endpoint doesn't include value
            );
        }
    }
}
