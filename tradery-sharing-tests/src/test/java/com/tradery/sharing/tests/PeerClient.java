package com.tradery.sharing.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * OkHttp client for the HeadlessPeer control API.
 * Provides typed methods + a polling helper for eventual consistency checks.
 */
public class PeerClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json");
    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public PeerClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = new OkHttpClient.Builder()
                .callTimeout(java.time.Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    public JsonNode status() throws IOException {
        return get("/status");
    }

    public JsonNode createDocument(String docId, String name) throws IOException {
        return createDocument(docId, name, "OPEN", 0.51);
    }

    public JsonNode createDocument(String docId, String name, String governanceType, double votingQuorum) throws IOException {
        Map<String, Object> body = Map.of(
                "docId", docId,
                "name", name,
                "governanceType", governanceType,
                "votingQuorum", votingQuorum
        );
        return post("/documents", body);
    }

    public void appendFacts(String docId, List<Map<String, String>> facts) throws IOException {
        Map<String, Object> body = Map.of("facts", facts);
        post("/documents/" + docId + "/facts", body);
    }

    public String getCurrent(String docId, String entityId, String attribute) throws IOException {
        JsonNode node = get("/documents/" + docId + "/current?entityId=" + entityId + "&attribute=" + attribute);
        return node.has("value") ? node.get("value").asText() : null;
    }

    public JsonNode getCurrentMap(String docId, String entityId) throws IOException {
        return get("/documents/" + docId + "/current?entityId=" + entityId);
    }

    public JsonNode getFactsSince(String docId, long since) throws IOException {
        return get("/documents/" + docId + "/facts?since=" + since);
    }

    public int getPendingCount(String docId) throws IOException {
        JsonNode node = get("/documents/" + docId + "/pending");
        return node.get("pendingCount").asInt();
    }

    public JsonNode getPendingPeerIds(String docId) throws IOException {
        return get("/documents/" + docId + "/pending-peer-ids");
    }

    public void approveSubmission(String docId, String submitterPeerId) throws IOException {
        post("/documents/" + docId + "/approve?submitterPeerId=" + submitterPeerId, Map.of());
    }

    public void rejectSubmission(String docId, String submitterPeerId) throws IOException {
        post("/documents/" + docId + "/reject?submitterPeerId=" + submitterPeerId, Map.of());
    }

    public void setMembers(String docId, List<Map<String, String>> members) throws IOException {
        post("/documents/" + docId + "/members", Map.of("members", members));
    }

    public void connect(String host, int port) throws IOException {
        post("/connect", Map.of("host", host, "port", port));
    }

    public void requestSync() throws IOException {
        post("/sync", Map.of());
    }

    public JsonNode getPeers() throws IOException {
        return get("/peers");
    }

    public int getP2pPort() throws IOException {
        return status().get("p2pPort").asInt();
    }

    /**
     * Poll getCurrent() until the expected value appears or timeout.
     * Returns the final value (may not match expected if timed out).
     */
    public String waitForValue(String docId, String entityId, String attribute,
                                String expected, int timeoutSecs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSecs * 1000L;
        String lastValue = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                lastValue = getCurrent(docId, entityId, attribute);
                if (expected.equals(lastValue)) return lastValue;
            } catch (IOException e) {
                // Transient error, keep polling
            }
            Thread.sleep(200);
        }
        return lastValue;
    }

    /**
     * Poll getPendingCount() until it reaches the expected count or timeout.
     */
    public int waitForPending(String docId, int expectedCount, int timeoutSecs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSecs * 1000L;
        int lastCount = -1;
        while (System.currentTimeMillis() < deadline) {
            try {
                lastCount = getPendingCount(docId);
                if (lastCount == expectedCount) return lastCount;
            } catch (IOException e) {
                // Transient error
            }
            Thread.sleep(200);
        }
        return lastCount;
    }

    private JsonNode get(String path) throws IOException {
        Request req = new Request.Builder().url(baseUrl + path).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GET " + path + " failed: " + resp.code() + " " + resp.body().string());
            }
            return mapper.readTree(resp.body().string());
        }
    }

    private JsonNode post(String path, Object body) throws IOException {
        String json = mapper.writeValueAsString(body);
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .post(RequestBody.create(json, JSON_TYPE))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            String responseBody = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("POST " + path + " failed: " + resp.code() + " " + responseBody);
            }
            if (responseBody.isEmpty() || !(responseBody.startsWith("{") || responseBody.startsWith("["))) {
                return mapper.createObjectNode();
            }
            return mapper.readTree(responseBody);
        }
    }
}
