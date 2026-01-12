package com.tradery.data;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/**
 * Factory for shared HTTP client and JSON mapper instances.
 * Eliminates duplicate client creation across API classes.
 *
 * Thread-safe singleton pattern for shared resources:
 * - OkHttpClient with connection pooling
 * - ObjectMapper with standard configuration
 */
public final class HttpClientFactory {

    private static final OkHttpClient SHARED_CLIENT;
    private static final ObjectMapper SHARED_MAPPER;

    static {
        // Shared HTTP client with connection pooling
        SHARED_CLIENT = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

        // Shared JSON mapper with standard configuration
        SHARED_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private HttpClientFactory() {
        // Prevent instantiation
    }

    /**
     * Get the shared OkHttpClient instance.
     * Uses connection pooling for efficiency across all API clients.
     */
    public static OkHttpClient getClient() {
        return SHARED_CLIENT;
    }

    /**
     * Get the shared ObjectMapper instance.
     * Configured with JavaTimeModule and lenient unknown property handling.
     */
    public static ObjectMapper getMapper() {
        return SHARED_MAPPER;
    }
}
