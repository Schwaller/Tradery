package com.tradery.dataservice.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.FundingRate;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Binance Futures API client for fetching funding rate data.
 * Uses the public API (no authentication required).
 *
 * API Endpoint: GET /fapi/v1/fundingRate
 * Rate Limit: 500 requests per 5 minutes
 */
public class FundingRateClient {

    private static final Logger log = LoggerFactory.getLogger(FundingRateClient.class);
    private static final String BASE_URL = "https://fapi.binance.com/fapi/v1";
    private static final int MAX_RECORDS_PER_REQUEST = 1000;

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public FundingRateClient() {
        this.client = HttpClientFactory.getClient();
        this.mapper = HttpClientFactory.getMapper();
    }

    /**
     * Fetch funding rate history from Binance Futures API.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param startTime Start time in milliseconds (inclusive)
     * @param endTime   End time in milliseconds (inclusive)
     * @return List of funding rates sorted by time ascending
     */
    public List<FundingRate> fetchFundingRates(String symbol, long startTime, long endTime)
            throws IOException {

        List<FundingRate> allRates = new ArrayList<>();
        long currentStart = startTime;

        // Paginate through all data (funding happens every 8h, ~3 per day, so typically few requests needed)
        while (currentStart < endTime) {
            List<FundingRate> batch = fetchBatch(symbol, currentStart, endTime);

            if (batch.isEmpty()) {
                break;
            }

            allRates.addAll(batch);

            // Move start time to after the last fetched record
            FundingRate last = batch.get(batch.size() - 1);
            currentStart = last.fundingTime() + 1;

            // Small delay to respect rate limits
            if (currentStart < endTime) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return allRates;
    }

    /**
     * Fetch a single batch of funding rates.
     */
    private List<FundingRate> fetchBatch(String symbol, long startTime, long endTime)
            throws IOException {

        StringBuilder url = new StringBuilder(BASE_URL + "/fundingRate")
            .append("?symbol=").append(symbol)
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

            List<FundingRate> rates = new ArrayList<>();

            for (JsonNode node : root) {
                // Response format: { symbol, fundingRate, fundingTime, markPrice }
                String sym = node.get("symbol").asText();
                double fundingRate = Double.parseDouble(node.get("fundingRate").asText());
                long fundingTime = node.get("fundingTime").asLong();
                double markPrice = node.has("markPrice") ? Double.parseDouble(node.get("markPrice").asText()) : 0.0;

                rates.add(new FundingRate(sym, fundingRate, fundingTime, markPrice));
            }

            return rates;
        }
    }

    /**
     * Fetch the latest funding rate for a symbol.
     */
    public FundingRate fetchLatestFundingRate(String symbol) throws IOException {
        StringBuilder url = new StringBuilder(BASE_URL + "/fundingRate")
            .append("?symbol=").append(symbol)
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

            if (root.isEmpty()) {
                return null;
            }

            JsonNode node = root.get(0);
            return new FundingRate(
                node.get("symbol").asText(),
                Double.parseDouble(node.get("fundingRate").asText()),
                node.get("fundingTime").asLong(),
                node.has("markPrice") ? Double.parseDouble(node.get("markPrice").asText()) : 0.0
            );
        }
    }
}
