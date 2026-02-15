package com.tradery.rendezvous;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory peer registry with lazy TTL eviction.
 * Keyed by deviceId (unique per device) so multiple devices per user are supported.
 * Each entry stores userId (Keycloak UUID) for same-user device discovery,
 * and peerId (email) for document-based peer discovery.
 */
public class PeerRegistry {

    private static final long DEFAULT_TTL_MILLIS = 2 * 60 * 1000; // 2 minutes

    private final long ttlMillis;
    private final ConcurrentHashMap<String, PeerEntry> peers = new ConcurrentHashMap<>();

    public PeerRegistry() {
        this(DEFAULT_TTL_MILLIS);
    }

    public PeerRegistry(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /** Upsert a peer announcement, keyed by deviceId. */
    public void announce(String deviceId, String userId, String peerId, String host, int port,
                         List<String> documentIds) {
        peers.put(deviceId, new PeerEntry(deviceId, userId, peerId, host, port, documentIds, Instant.now()));
    }

    /** Find all non-expired peers sharing a given document. */
    public List<PeerResponse> findByDocument(String documentId) {
        evictExpired();
        List<PeerResponse> result = new ArrayList<>();
        for (PeerEntry entry : peers.values()) {
            if (entry.documentIds().contains(documentId)) {
                result.add(new PeerResponse(entry.peerId(), entry.host(), entry.port()));
            }
        }
        return result;
    }

    /** Find all non-expired peers belonging to the same user, excluding the given deviceId. */
    public List<PeerResponse> findByUser(String userId, String excludeDeviceId) {
        evictExpired();
        List<PeerResponse> result = new ArrayList<>();
        for (PeerEntry entry : peers.values()) {
            if (entry.userId().equals(userId) && !entry.deviceId().equals(excludeDeviceId)) {
                result.add(new PeerResponse(entry.peerId(), entry.host(), entry.port()));
            }
        }
        return result;
    }

    /** Remove a peer by deviceId. */
    public void depart(String deviceId) {
        peers.remove(deviceId);
    }

    /** Number of currently registered (non-expired) peers. */
    public int size() {
        evictExpired();
        return peers.size();
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minusMillis(ttlMillis);
        peers.entrySet().removeIf(e -> e.getValue().lastSeen().isBefore(cutoff));
    }

    record PeerEntry(String deviceId, String userId, String peerId, String host, int port,
                     List<String> documentIds, Instant lastSeen) {}
}
