package com.tradery.rendezvous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RendezvousServerTest {

    private static RendezvousServer server;
    private static OkHttpClient http;
    private static ObjectMapper mapper;
    private static String baseUrl;

    @BeforeAll
    static void startServer() {
        server = new RendezvousServer(0); // random port
        http = new OkHttpClient();
        mapper = new ObjectMapper();
        baseUrl = "http://localhost:" + server.port();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void healthEndpointNoAuth() throws IOException {
        Request req = new Request.Builder().url(baseUrl + "/health").build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code());
        }
    }

    @Test
    void announceRequiresAuth() throws IOException {
        String body = mapper.writeValueAsString(new AnnounceRequest("peer-1", 9000, List.of("doc-1")));
        Request req = new Request.Builder()
                .url(baseUrl + "/announce")
                .post(RequestBody.create(body, MediaType.get("application/json")))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(401, resp.code());
        }
    }

    @Test
    void announceDiscoverDepartFlow() throws IOException {
        // Announce peer-1 sharing doc-A
        announce("peer-1", 9001, List.of("doc-A", "doc-B"));

        // Announce peer-2 sharing doc-A and doc-C
        announce("peer-2", 9002, List.of("doc-A", "doc-C"));

        // Discover peers for doc-A — should find both
        JsonNode peers = discover("doc-A");
        assertEquals(2, peers.size());

        // Discover peers for doc-B — should find only peer-1
        JsonNode peersB = discover("doc-B");
        assertEquals(1, peersB.size());
        assertEquals("peer-1", peersB.get(0).get("peerId").asText());

        // Discover peers for doc-C — should find only peer-2
        JsonNode peersC = discover("doc-C");
        assertEquals(1, peersC.size());
        assertEquals("peer-2", peersC.get(0).get("peerId").asText());

        // Depart peer-1
        depart("peer-1");

        // Now doc-A should only have peer-2
        JsonNode peersAfter = discover("doc-A");
        assertEquals(1, peersAfter.size());
        assertEquals("peer-2", peersAfter.get(0).get("peerId").asText());

        // doc-B should be empty
        JsonNode peersEmpty = discover("doc-B");
        assertEquals(0, peersEmpty.size());

        // Cleanup
        depart("peer-2");
    }

    @Test
    void announceUpsertOverwritesPrevious() throws IOException {
        announce("peer-X", 9010, List.of("doc-1"));
        assertEquals(1, discover("doc-1").size());

        // Re-announce with different docs
        announce("peer-X", 9010, List.of("doc-2"));
        assertEquals(0, discover("doc-1").size());
        assertEquals(1, discover("doc-2").size());

        depart("peer-X");
    }

    @Test
    void ttlExpiry() throws Exception {
        // Create a server with very short TTL for testing
        RendezvousServer shortTtlServer = new RendezvousServer(0);
        String shortUrl = "http://localhost:" + shortTtlServer.port();

        try {
            // Use a custom registry with 100ms TTL
            // Since we can't inject the registry, we test via the PeerRegistry directly
            PeerRegistry registry = new PeerRegistry(100); // 100ms TTL
            registry.announce("peer-ttl", "127.0.0.1", 9999, List.of("doc-ttl"));
            assertEquals(1, registry.findByDocument("doc-ttl").size());

            Thread.sleep(200); // Wait for expiry

            assertEquals(0, registry.findByDocument("doc-ttl").size());
        } finally {
            shortTtlServer.stop();
        }
    }

    @Test
    void peersEndpointRequiresDocumentId() throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + "/peers")
                .header("Authorization", "Bearer test-token")
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(400, resp.code());
        }
    }

    private void announce(String peerId, int port, List<String> docIds) throws IOException {
        String body = mapper.writeValueAsString(new AnnounceRequest(peerId, port, docIds));
        Request req = new Request.Builder()
                .url(baseUrl + "/announce")
                .header("Authorization", "Bearer test-token")
                .post(RequestBody.create(body, MediaType.get("application/json")))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code());
        }
    }

    private JsonNode discover(String documentId) throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + "/peers?documentId=" + documentId)
                .header("Authorization", "Bearer test-token")
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code());
            return mapper.readTree(resp.body().string());
        }
    }

    private void depart(String peerId) throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + "/depart?peerId=" + peerId)
                .header("Authorization", "Bearer test-token")
                .delete()
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code());
        }
    }
}
