package com.tradery.dataservice.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.FearGreedIndex;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client for the Alternative.me Crypto Fear & Greed Index API.
 * Free, no auth required. Returns daily sentiment scores 0-100.
 */
public class FearGreedClient {

    private static final Logger log = LoggerFactory.getLogger(FearGreedClient.class);
    private static final String API_URL = "https://api.alternative.me/fng/?limit=0";

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public FearGreedClient() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Fetch all Fear & Greed Index history.
     * Returns ~2900 daily data points sorted ascending by timestamp.
     */
    public List<FearGreedIndex> fetchAll() throws IOException {
        Request request = new Request.Builder()
            .url(API_URL)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Fear & Greed API returned " + response.code());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);
            JsonNode dataArray = root.get("data");

            if (dataArray == null || !dataArray.isArray()) {
                throw new IOException("Fear & Greed API: unexpected response format");
            }

            List<FearGreedIndex> results = new ArrayList<>(dataArray.size());
            for (JsonNode entry : dataArray) {
                int value = entry.get("value").asInt();
                String classification = entry.get("value_classification").asText();
                // API returns timestamps in seconds, convert to milliseconds
                long timestamp = entry.get("timestamp").asLong() * 1000L;

                results.add(new FearGreedIndex(value, classification, timestamp));
            }

            // Sort ascending by timestamp
            results.sort(Comparator.comparingLong(FearGreedIndex::timestamp));

            log.info("Fetched {} Fear & Greed Index data points", results.size());
            return results;
        }
    }
}
