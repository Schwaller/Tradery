package com.tradery.dataclient;

import com.tradery.model.Candle;
import com.tradery.model.AggTrade;
import com.tradery.model.FundingRate;
import com.tradery.model.OpenInterest;
import com.tradery.model.PremiumIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the Tradery Data Service.
 * Provides synchronous methods to request pages and fetch data.
 */
public class DataServiceClient {
    private static final Logger LOG = LoggerFactory.getLogger(DataServiceClient.class);

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final ObjectMapper msgpackMapper;

    public DataServiceClient(String host, int port) {
        this.baseUrl = String.format("http://%s:%d", host, port);
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.jsonMapper = new ObjectMapper();
        this.msgpackMapper = new ObjectMapper(new MessagePackFactory());
    }

    /**
     * Check if the data service is healthy.
     */
    public boolean isHealthy() {
        try {
            Request request = new Request.Builder()
                .url(baseUrl + "/health")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Request a page from the data service.
     */
    public PageResponse requestPage(PageRequest request, String consumerId, String consumerName) throws IOException {
        String json = jsonMapper.writeValueAsString(new PageRequestBody(
            request.dataType(),
            request.symbol(),
            request.timeframe(),
            request.startTime(),
            request.endTime(),
            consumerId,
            consumerName
        ));

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request httpRequest = new Request.Builder()
            .url(baseUrl + "/pages/request")
            .post(body)
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to request page: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), PageResponse.class);
        }
    }

    /**
     * Release a page.
     */
    public boolean releasePage(String pageKey, String consumerId) throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/pages/" + pageKey + "?consumerId=" + consumerId)
            .delete()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    /**
     * Get page status.
     */
    public PageStatus getPageStatus(String pageKey) throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/pages/" + pageKey + "/status")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            return jsonMapper.readValue(response.body().string(), PageStatus.class);
        }
    }

    /**
     * Get page data as raw bytes (MessagePack).
     */
    public byte[] getPageData(String pageKey) throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/pages/" + pageKey + "/data")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            return response.body().bytes();
        }
    }

    /**
     * Fetch candles from a page.
     */
    public List<Candle> getCandles(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, Candle.class));
    }

    /**
     * Fetch candles directly (without page lifecycle).
     */
    public List<Candle> getCandles(String symbol, String timeframe, Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/candles").newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("timeframe", timeframe);

        if (start != null) urlBuilder.addQueryParameter("start", start.toString());
        if (end != null) urlBuilder.addQueryParameter("end", end.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return List.of();
            byte[] data = response.body().bytes();
            return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
                .constructCollectionType(List.class, Candle.class));
        }
    }

    /**
     * Fetch aggregated trades from a page.
     */
    public List<AggTrade> getAggTrades(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, AggTrade.class));
    }

    /**
     * Fetch aggregated trades directly (without page lifecycle).
     */
    public List<AggTrade> getAggTrades(String symbol, Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/aggtrades").newBuilder()
            .addQueryParameter("symbol", symbol);

        if (start != null) urlBuilder.addQueryParameter("start", start.toString());
        if (end != null) urlBuilder.addQueryParameter("end", end.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return List.of();
            byte[] data = response.body().bytes();
            return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
                .constructCollectionType(List.class, AggTrade.class));
        }
    }

    /**
     * Fetch funding rates from a page.
     */
    public List<FundingRate> getFundingRates(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, FundingRate.class));
    }

    /**
     * Fetch funding rates directly (without page lifecycle).
     */
    public List<FundingRate> getFundingRates(String symbol, Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/funding").newBuilder()
            .addQueryParameter("symbol", symbol);

        if (start != null) urlBuilder.addQueryParameter("start", start.toString());
        if (end != null) urlBuilder.addQueryParameter("end", end.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return List.of();
            byte[] data = response.body().bytes();
            return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
                .constructCollectionType(List.class, FundingRate.class));
        }
    }

    /**
     * Fetch open interest from a page.
     */
    public List<OpenInterest> getOpenInterest(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, OpenInterest.class));
    }

    /**
     * Fetch open interest directly (without page lifecycle).
     */
    public List<OpenInterest> getOpenInterest(String symbol, Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/openinterest").newBuilder()
            .addQueryParameter("symbol", symbol);

        if (start != null) urlBuilder.addQueryParameter("start", start.toString());
        if (end != null) urlBuilder.addQueryParameter("end", end.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return List.of();
            byte[] data = response.body().bytes();
            return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
                .constructCollectionType(List.class, OpenInterest.class));
        }
    }

    /**
     * Fetch premium index from a page.
     */
    public List<PremiumIndex> getPremiumIndex(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, PremiumIndex.class));
    }

    /**
     * Fetch premium index directly (without page lifecycle).
     */
    public List<PremiumIndex> getPremiumIndex(String symbol, String timeframe, Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/premium").newBuilder()
            .addQueryParameter("symbol", symbol);

        if (timeframe != null) urlBuilder.addQueryParameter("timeframe", timeframe);
        if (start != null) urlBuilder.addQueryParameter("start", start.toString());
        if (end != null) urlBuilder.addQueryParameter("end", end.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return List.of();
            byte[] data = response.body().bytes();
            return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
                .constructCollectionType(List.class, PremiumIndex.class));
        }
    }

    /**
     * Close the client.
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    // Request/Response DTOs
    public record PageRequest(String dataType, String symbol, String timeframe, long startTime, long endTime) {}

    public record PageRequestBody(String dataType, String symbol, String timeframe, long startTime, long endTime,
                                   String consumerId, String consumerName) {}

    public record PageResponse(String pageKey, String state, int progress, boolean isNew) {}

    public record PageStatus(String pageKey, String state, int progress, long recordCount,
                              Long lastSyncTime, List<Consumer> consumers, Coverage coverage) {}

    public record Consumer(String id, String name) {}

    public record Coverage(long requestedStart, long requestedEnd, Long actualStart, Long actualEnd, List<Gap> gaps) {}

    public record Gap(long start, long end) {}
}
