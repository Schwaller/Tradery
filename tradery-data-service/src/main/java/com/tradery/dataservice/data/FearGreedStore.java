package com.tradery.dataservice.data;

import com.tradery.core.model.FearGreedIndex;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Store for Fear & Greed Index data with caching.
 * Fetches all history from Alternative.me API and caches in SQLite.
 * Re-fetches if last record is older than 24 hours.
 */
public class FearGreedStore {

    private static final Logger log = LoggerFactory.getLogger(FearGreedStore.class);
    private static final long STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private final FearGreedClient client;
    private final SqliteDataStore dataStore;

    public FearGreedStore(FearGreedClient client, SqliteDataStore dataStore) {
        this.client = client;
        this.dataStore = dataStore;
    }

    /**
     * Get Fear & Greed data for a time range, fetching if stale.
     * Includes lookback records for averaging functions.
     */
    public List<FearGreedIndex> getFearGreedData(long startTime, long endTime) throws IOException {
        ensureFresh();
        // Include 90 days of lookback for FEAR_GREED_AVG calculations
        return dataStore.getFearGreedWithLookback(startTime, endTime, 90);
    }

    /**
     * Get cached data only (no fetch).
     */
    public List<FearGreedIndex> getCacheOnly(long startTime, long endTime) throws IOException {
        return dataStore.getFearGreed(startTime, endTime);
    }

    /**
     * Ensure data is fresh (fetch if stale or empty).
     */
    private void ensureFresh() throws IOException {
        FearGreedIndex latest = dataStore.getLatestFearGreed();

        if (latest == null) {
            // No data at all - fetch everything
            log.info("No Fear & Greed data cached, fetching all history...");
            fetchAndSave();
            return;
        }

        long age = System.currentTimeMillis() - latest.timestamp();
        if (age > STALE_THRESHOLD_MS) {
            log.info("Fear & Greed data stale ({}h old), refreshing...", age / 3600000);
            fetchAndSave();
        }
    }

    /**
     * Fetch all data from API and save to SQLite.
     */
    private void fetchAndSave() throws IOException {
        List<FearGreedIndex> data = client.fetchAll();
        if (!data.isEmpty()) {
            dataStore.saveFearGreed(data);
            log.info("Saved {} Fear & Greed records to SQLite", data.size());
        }
    }
}
