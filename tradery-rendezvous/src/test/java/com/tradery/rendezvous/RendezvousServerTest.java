package com.tradery.rendezvous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RendezvousServerTest {

    private static RendezvousServer server;
    private static OkHttpClient http;
    private static ObjectMapper mapper;
    private static String baseUrl;

    /** Device credential for authenticated endpoints. */
    private static String deviceCredential;
    private static String deviceId;

    @BeforeAll
    static void startServer() throws Exception {
        server = new RendezvousServer(0); // random port, test mode
        http = new OkHttpClient();
        mapper = new ObjectMapper();
        baseUrl = "http://localhost:" + server.port();

        // Enroll a test device to get a credential
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair deviceKeyPair = kpg.generateKeyPair();
        String devicePubKey = Base64.getEncoder().encodeToString(deviceKeyPair.getPublic().getEncoded());

        String enrollJson = mapper.writeValueAsString(
                new EnrollRequest("test-user@example.com", devicePubKey, "test-device"));
        Request enrollReq = new Request.Builder()
                .url(baseUrl + "/enroll-device")
                .header("Authorization", "Bearer test-user@example.com")
                .post(RequestBody.create(enrollJson, MediaType.get("application/json")))
                .build();

        try (Response resp = http.newCall(enrollReq).execute()) {
            assertEquals(200, resp.code(), "Enrollment should succeed");
            JsonNode body = mapper.readTree(resp.body().string());
            deviceCredential = body.get("deviceCredential").asText();
            deviceId = body.get("deviceId").asText();
            assertNotNull(deviceCredential);
            assertNotNull(deviceId);
        }
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
    void backendKeyEndpointNoAuth() throws IOException {
        Request req = new Request.Builder().url(baseUrl + "/backend-key").build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code());
            JsonNode body = mapper.readTree(resp.body().string());
            assertNotNull(body.get("publicKey").asText());
        }
    }

    @Test
    void enrollDeviceRequiresAuth() throws IOException {
        String json = mapper.writeValueAsString(new EnrollRequest("token", "key", "device"));
        Request req = new Request.Builder()
                .url(baseUrl + "/enroll-device")
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(401, resp.code());
        }
    }

    @Test
    void announceRequiresDeviceCredential() throws IOException {
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
    void announceWithBearerTokenFails() throws IOException {
        String body = mapper.writeValueAsString(new AnnounceRequest("peer-1", 9000, List.of("doc-1")));
        Request req = new Request.Builder()
                .url(baseUrl + "/announce")
                .header("Authorization", "Bearer some-token")
                .post(RequestBody.create(body, MediaType.get("application/json")))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(401, resp.code()); // Bearer tokens don't work for device-auth endpoints
        }
    }

    @Test
    void announceDiscoverDepartFlow() throws IOException {
        // Announce sharing doc-A and doc-B
        announce(9001, List.of("doc-A", "doc-B"));

        // Discover peers for doc-A — should find our device
        JsonNode peers = discover("doc-A");
        assertEquals(1, peers.size());

        // Discover peers for doc-B — should also find our device
        JsonNode peersB = discover("doc-B");
        assertEquals(1, peersB.size());

        // Depart using userId from credential
        depart("test-user@example.com");

        // Now doc-A should be empty
        JsonNode peersAfter = discover("doc-A");
        assertEquals(0, peersAfter.size());
    }

    @Test
    void announceUpsertOverwritesPrevious() throws IOException {
        announce(9010, List.of("doc-1"));
        assertEquals(1, discover("doc-1").size());

        // Re-announce with different docs
        announce(9010, List.of("doc-2"));
        assertEquals(0, discover("doc-1").size());
        assertEquals(1, discover("doc-2").size());

        depart("test-user@example.com");
    }

    @Test
    void ttlExpiry() throws Exception {
        // Test via PeerRegistry directly since we can't inject the registry
        PeerRegistry registry = new PeerRegistry(100); // 100ms TTL
        registry.announce("peer-ttl", "127.0.0.1", 9999, List.of("doc-ttl"));
        assertEquals(1, registry.findByDocument("doc-ttl").size());

        Thread.sleep(200); // Wait for expiry

        assertEquals(0, registry.findByDocument("doc-ttl").size());
    }

    @Test
    void peersEndpointRequiresDocumentId() throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + "/peers")
                .header("X-Device-Credential", deviceCredential)
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(400, resp.code());
        }
    }

    @Test
    void rotateCredential() throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + "/rotate-credential")
                .header("X-Device-Credential", deviceCredential)
                .post(RequestBody.create("", MediaType.get("application/json")))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code());
            JsonNode body = mapper.readTree(resp.body().string());
            String newCredential = body.get("deviceCredential").asText();
            assertNotNull(newCredential);
            assertNotEquals(deviceCredential, newCredential);
        }
    }

    private void announce(int port, List<String> docIds) throws IOException {
        String body = mapper.writeValueAsString(new AnnounceRequest("peer", port, docIds));
        Request req = new Request.Builder()
                .url(baseUrl + "/announce")
                .header("X-Device-Credential", deviceCredential)
                .post(RequestBody.create(body, MediaType.get("application/json")))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code(), "Announce should succeed: " + resp.message());
        }
    }

    private JsonNode discover(String documentId) throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + "/peers?documentId=" + documentId)
                .header("X-Device-Credential", deviceCredential)
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code());
            return mapper.readTree(resp.body().string());
        }
    }

    private void depart(String peerId) throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + "/depart?peerId=" + peerId)
                .header("X-Device-Credential", deviceCredential)
                .delete()
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code());
        }
    }
}
