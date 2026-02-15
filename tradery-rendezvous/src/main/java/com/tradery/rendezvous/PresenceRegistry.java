package com.tradery.rendezvous;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory presence registry with lazy TTL eviction.
 * Keyed by userId (email from device credential).
 * Entries expire after 90 seconds if not refreshed.
 */
public class PresenceRegistry {

    private static final long DEFAULT_TTL_MILLIS = 90_000; // 90 seconds

    private final long ttlMillis;
    private final ConcurrentHashMap<String, PresenceEntry> entries = new ConcurrentHashMap<>();

    public PresenceRegistry() {
        this(DEFAULT_TTL_MILLIS);
    }

    public PresenceRegistry(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /** Update or insert a user's presence state. */
    public void update(String userId, String state) {
        entries.put(userId, new PresenceEntry(state, Instant.now()));
    }

    /** Get a user's presence state, or "OFFLINE" if absent/expired. */
    public String getState(String userId) {
        PresenceEntry entry = entries.get(userId);
        if (entry == null) return "OFFLINE";
        if (isExpired(entry)) {
            entries.remove(userId);
            return "OFFLINE";
        }
        return entry.state();
    }

    /** Get the updatedAt timestamp (epoch millis), or 0 if offline. */
    public long getUpdatedAt(String userId) {
        PresenceEntry entry = entries.get(userId);
        if (entry == null || isExpired(entry)) return 0;
        return entry.updatedAt().toEpochMilli();
    }

    /** Remove a user's presence entry (e.g., on depart). */
    public void remove(String userId) {
        entries.remove(userId);
    }

    /** Count of currently registered (non-expired) entries. */
    public int size() {
        evictExpired();
        return entries.size();
    }

    private boolean isExpired(PresenceEntry entry) {
        return entry.updatedAt().plusMillis(ttlMillis).isBefore(Instant.now());
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minusMillis(ttlMillis);
        entries.entrySet().removeIf(e -> e.getValue().updatedAt().isBefore(cutoff));
    }

    record PresenceEntry(String state, Instant updatedAt) {}
}
