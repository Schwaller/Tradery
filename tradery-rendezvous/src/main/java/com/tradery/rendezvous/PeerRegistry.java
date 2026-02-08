package com.tradery.rendezvous;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory peer registry with lazy TTL eviction.
 * Peers that haven't re-announced within the TTL are removed on next access.
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

    /** Upsert a peer announcement. */
    public void announce(String peerId, String host, int port, List<String> documentIds) {
        peers.put(peerId, new PeerEntry(peerId, host, port, documentIds, Instant.now()));
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

    /** Remove a peer from the registry. */
    public void depart(String peerId) {
        peers.remove(peerId);
    }

    /** Number of currently registered (possibly expired) peers. */
    public int size() {
        evictExpired();
        return peers.size();
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minusMillis(ttlMillis);
        peers.entrySet().removeIf(e -> e.getValue().lastSeen().isBefore(cutoff));
    }

    record PeerEntry(String peerId, String host, int port, List<String> documentIds, Instant lastSeen) {}
}
