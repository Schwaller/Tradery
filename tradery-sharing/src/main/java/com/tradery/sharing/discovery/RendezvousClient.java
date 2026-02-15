package com.tradery.sharing.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * HTTP client for the rendezvous server — a tiny central service that stores
 * {userId, ip:port, lastSeen, documentIds} for online peers.
 * The rendezvous server never sees entity data, only connection metadata.
 *
 * Auth model:
 *   - enrollDevice() uses Keycloak Bearer token (one-time enrollment)
 *   - All other calls use X-Device-Credential header (offline-capable)
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

    // ==================== Enrollment (Keycloak Bearer auth) ====================

    /**
     * Enroll a device with the rendezvous server. Requires a Keycloak access token.
     * Returns the enrollment response with deviceId, deviceCredential, and backendPublicKey.
     */
    public EnrollResult enrollDevice(String keycloakAccessToken, String devicePublicKeyBase64,
                                     String deviceName) throws IOException {
        String json = mapper.writeValueAsString(
                new EnrollRequest(keycloakAccessToken, devicePublicKeyBase64, deviceName));
        Request request = new Request.Builder()
                .url(baseUrl + "/enroll-device")
                .header("Authorization", "Bearer " + keycloakAccessToken)
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Enrollment failed: " + response.code() + " " + response.message());
            }
            JsonNode body = mapper.readTree(response.body().string());
            return new EnrollResult(
                    body.get("deviceId").asText(),
                    body.get("deviceCredential").asText(),
                    body.get("backendPublicKey").asText());
        }
    }

    /**
     * Get the backend's public key for offline credential verification.
     * No auth required.
     */
    public String getBackendPublicKey() throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/backend-key")
                .get()
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Backend key fetch failed: " + response.code());
            }
            JsonNode body = mapper.readTree(response.body().string());
            return body.get("publicKey").asText();
        }
    }

    // ==================== Device credential auth endpoints ====================

    /**
     * Rotate the device credential (get a fresh one before expiry).
     */
    public EnrollResult rotateCredential(String deviceCredential) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/rotate-credential")
                .header("X-Device-Credential", deviceCredential)
                .post(RequestBody.create("", MediaType.get("application/json")))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Credential rotation failed: " + response.code());
            }
            JsonNode body = mapper.readTree(response.body().string());
            return new EnrollResult(
                    body.get("deviceId").asText(),
                    body.get("deviceCredential").asText(),
                    body.get("backendPublicKey").asText());
        }
    }

    /**
     * Announce this peer's availability to the rendezvous server.
     */
    public void announce(String deviceCredential, String peerId, int port,
                         List<String> documentIds) throws IOException {
        announce(deviceCredential, peerId, port, documentIds, null);
    }

    /**
     * Announce with optional IPv6 address. Old servers ignore the extra field.
     */
    public void announce(String deviceCredential, String peerId, int port,
                         List<String> documentIds, String ipv6Address) throws IOException {
        String json = mapper.writeValueAsString(new AnnouncePayload(peerId, port, documentIds, ipv6Address));
        Request request = new Request.Builder()
                .url(baseUrl + "/announce")
                .header("X-Device-Credential", deviceCredential)
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                throw new CredentialRejectedException("Announce rejected: " + response.code());
            }
            if (!response.isSuccessful()) {
                log.warn("Announce failed: {} {}", response.code(), response.message());
            }
        }
    }

    /**
     * Discover peers that share a given document.
     */
    public List<PeerInfo> discoverPeers(String deviceCredential, String documentId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/peers?documentId=" + documentId)
                .header("X-Device-Credential", deviceCredential)
                .get()
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                throw new CredentialRejectedException("Discover rejected: " + response.code());
            }
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Discover failed: {} {}", response.code(), response.message());
                return List.of();
            }

            JsonNode root = mapper.readTree(response.body().string());
            List<PeerInfo> peers = new ArrayList<>();
            for (JsonNode node : root) {
                String ipv6 = node.has("ipv6Host") && !node.get("ipv6Host").isNull()
                        ? node.get("ipv6Host").asText() : null;
                peers.add(new PeerInfo(
                        node.get("peerId").asText(),
                        node.get("host").asText(),
                        node.get("port").asInt(),
                        ipv6));
            }
            return peers;
        }
    }

    /**
     * Remove this device from the rendezvous server (going offline).
     * Server identifies the device from the credential — no peerId needed.
     */
    public void depart(String deviceCredential) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/depart")
                .header("X-Device-Credential", deviceCredential)
                .delete()
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Depart failed: {} {}", response.code(), response.message());
            }
        }
    }

    /**
     * Discover other devices belonging to the same user.
     * The server matches by userId from the credential — no parameters needed.
     * Returns all announced peers with the same userId, excluding the requesting device.
     */
    public List<PeerInfo> discoverMyDevices(String deviceCredential) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/my-devices")
                .header("X-Device-Credential", deviceCredential)
                .get()
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                throw new CredentialRejectedException("My-devices rejected: " + response.code());
            }
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("My-devices failed: {} {}", response.code(), response.message());
                return List.of();
            }

            JsonNode root = mapper.readTree(response.body().string());
            List<PeerInfo> devices = new ArrayList<>();
            for (JsonNode node : root) {
                String ipv6 = node.has("ipv6Host") && !node.get("ipv6Host").isNull()
                        ? node.get("ipv6Host").asText() : null;
                devices.add(new PeerInfo(
                        node.get("peerId").asText(),
                        node.get("host").asText(),
                        node.get("port").asInt(),
                        ipv6));
            }
            return devices;
        }
    }

    // ==================== Presence ====================

    /**
     * Publish a presence heartbeat to the rendezvous server.
     * @param state "ONLINE" or "IDLE"
     */
    public void publishPresence(String deviceCredential, String state) throws IOException {
        String json = "{\"state\":\"" + state + "\"}";
        Request request = new Request.Builder()
                .url(baseUrl + "/presence")
                .header("X-Device-Credential", deviceCredential)
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                throw new CredentialRejectedException("Presence publish rejected: " + response.code());
            }
            if (!response.isSuccessful()) {
                log.warn("Presence publish failed: {} {}", response.code(), response.message());
            }
        }
    }

    /**
     * Query a friend's presence. Requires a friendship cert signed by the target user.
     * @param friendshipCertJson the serialized FriendshipCertData JSON
     * @return PresenceInfo or null on 403/404
     */
    public PresenceInfo queryPresence(String deviceCredential, String targetUserId,
                                       String friendshipCertJson) throws IOException {
        String certBase64 = Base64.getEncoder().encodeToString(
                friendshipCertJson.getBytes(StandardCharsets.UTF_8));
        Request request = new Request.Builder()
                .url(baseUrl + "/presence/" + targetUserId)
                .header("X-Device-Credential", deviceCredential)
                .header("X-Friendship-Cert", certBase64)
                .get()
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 403 || response.code() == 404) {
                return null;
            }
            if (response.code() == 401) {
                throw new CredentialRejectedException("Presence query rejected: 401");
            }
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Presence query failed: {} {}", response.code(), response.message());
                return null;
            }
            JsonNode body = mapper.readTree(response.body().string());
            return new PresenceInfo(
                    body.get("userId").asText(),
                    body.get("state").asText(),
                    body.get("updatedAt").asLong());
        }
    }

    // ==================== DTOs ====================

    public record PresenceInfo(String userId, String state, long updatedAt) {}

    public record PeerInfo(String peerId, String host, int port, String ipv6Host) {
        /** Backward-compatible constructor for responses without IPv6. */
        public PeerInfo(String peerId, String host, int port) {
            this(peerId, host, port, null);
        }
    }
    public record EnrollResult(String deviceId, String deviceCredential, String backendPublicKey) {}
    private record EnrollRequest(String keycloakToken, String devicePublicKey, String deviceName) {}
    private record AnnouncePayload(String peerId, int port, List<String> documentIds, String ipv6Address) {
        /** Backward-compatible constructor without IPv6. */
        AnnouncePayload(String peerId, int port, List<String> documentIds) {
            this(peerId, port, documentIds, null);
        }
    }

    /** Thrown when the server rejects the device credential (401/403). */
    public static class CredentialRejectedException extends IOException {
        public CredentialRejectedException(String message) { super(message); }
    }
}
