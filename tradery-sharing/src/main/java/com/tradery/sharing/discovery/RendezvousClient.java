package com.tradery.sharing.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for the rendezvous server — a tiny central service that stores
 * {userId, ip:port, lastSeen, documentIds} for online peers.
 * The rendezvous server never sees entity data, only connection metadata.
 */
public class RendezvousClient {

    private static final Logger log = LoggerFactory.getLogger(RendezvousClient.class);

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public RendezvousClient(String baseUrl, OkHttpClient http, ObjectMapper mapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
        this.mapper = mapper;
    }

    /**
     * Announce this peer's availability to the rendezvous server.
     */
    public void announce(String token, String peerId, int port, List<String> documentIds) throws IOException {
        String json = mapper.writeValueAsString(new AnnounceRequest(peerId, port, documentIds));
        Request request = new Request.Builder()
                .url(baseUrl + "/announce")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Announce failed: {} {}", response.code(), response.message());
            }
        }
    }

    /**
     * Discover peers that share a given document.
     */
    public List<PeerInfo> discoverPeers(String token, String documentId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/peers?documentId=" + documentId)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Discover failed: {} {}", response.code(), response.message());
                return List.of();
            }

            JsonNode root = mapper.readTree(response.body().string());
            List<PeerInfo> peers = new ArrayList<>();
            for (JsonNode node : root) {
                peers.add(new PeerInfo(
                        node.get("peerId").asText(),
                        node.get("host").asText(),
                        node.get("port").asInt()));
            }
            return peers;
        }
    }

    /**
     * Remove this peer from the rendezvous server (going offline).
     */
    public void depart(String token, String peerId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/depart?peerId=" + peerId)
                .header("Authorization", "Bearer " + token)
                .delete()
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Depart failed: {} {}", response.code(), response.message());
            }
        }
    }

    public record PeerInfo(String peerId, String host, int port) {}
    private record AnnounceRequest(String peerId, int port, List<String> documentIds) {}
}
