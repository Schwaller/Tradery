package com.tradery.sharing.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full end-to-end test: rendezvous discovery + mutual friendship gating + sync + chat.
 *
 * 3 containers: rendezvous server + 2 peer clients.
 *
 * Flow:
 * 1. Both peers enroll devices with rendezvous
 * 2. Both create a document and announce to rendezvous
 * 3. Discover peers via rendezvous
 * 4. Connect WITHOUT friendship — no sync, but chat works
 * 5. Establish mutual friendship with cert exchange
 * 6. Reconnect — sync triggers, data flows bidirectionally
 */
@Testcontainers
class FriendshipSyncIT {

    static Network network = Network.newNetwork();
    static RendezvousContainer rendezvous = new RendezvousContainer(network);
    static PeerContainer peerA = new PeerContainer("friend-a", network);
    static PeerContainer peerB = new PeerContainer("friend-b", network);

    static PeerClient clientA;
    static PeerClient clientB;
    static OkHttpClient http = new OkHttpClient();
    static ObjectMapper mapper = new ObjectMapper();

    static String credentialA;
    static String credentialB;

    @BeforeAll
    static void start() throws Exception {
        rendezvous.start();
        peerA.start();
        peerB.start();
        clientA = new PeerClient(peerA.controlUrl());
        clientB = new PeerClient(peerB.controlUrl());

        // Enroll devices with rendezvous server (no friendship yet)
        credentialA = enrollDevice("friend-a");
        credentialB = enrollDevice("friend-b");
    }

    @AfterAll
    static void stop() {
        peerA.stop();
        peerB.stop();
        rendezvous.stop();
        network.close();
    }

    @Test
    void fullEndToEnd() throws Exception {
        String docId = "friendship-sync-doc";

        // === Phase 1: Setup documents and data ===
        clientA.createDocument(docId, "Friendship Test");
        clientB.createDocument(docId, "Friendship Test");

        // Set up cross-membership so both peers pass membership checks
        var members = List.of(
                Map.of("user_id", "friend-a", "role", "OWNER"),
                Map.of("user_id", "friend-b", "role", "MEMBER"));
        clientA.setMembers(docId, members);
        clientB.setMembers(docId, members);

        clientA.appendFacts(docId, List.of(
                Map.of("entityId", "e1", "attribute", "name", "value", "from-A", "source", "test")
        ));
        assertEquals("from-A", clientA.getCurrent(docId, "e1", "name"));

        // === Phase 2: Rendezvous discovery ===
        int aP2pPort = clientA.getP2pPort();
        int bP2pPort = clientB.getP2pPort();

        announceToRendezvous(credentialA, "friend-a", aP2pPort, List.of(docId));
        announceToRendezvous(credentialB, "friend-b", bP2pPort, List.of(docId));

        // Both peers should be discoverable
        JsonNode peers = discoverFromRendezvous(credentialA, docId);
        assertTrue(peers.size() >= 2,
                "Should find at least 2 peers via rendezvous, found: " + peers.size());

        // === Phase 3: Establish mutual friendship with cert exchange ===
        clientA.addFriend("friend-b", "Friend B");
        clientB.addFriend("friend-a", "Friend A");
        PeerClient.exchangeFriendshipCerts(clientA, "friend-a", clientB, "friend-b");

        // === Phase 4: Connect — mutual friendship verified at handshake, sync triggers ===
        clientA.connect("friend-b", bP2pPort);

        assertTrue(clientA.waitForMutual("friend-b", true, 10),
                "A should see B as mutual friend");

        String synced = clientB.waitForValue(docId, "e1", "name", "from-A", 15);
        assertEquals("from-A", synced,
                "Data should sync after connect with mutual friendship");

        // === Phase 5: Chat flows ===
        clientA.sendChat("friend-b", "Hello from A!");
        assertTrue(clientB.waitForChat("Hello from A!", 10),
                "Chat should flow between mutual friends");

        clientB.sendChat("friend-a", "Hello back from B!");
        assertTrue(clientA.waitForChat("Hello back from B!", 10),
                "Chat should flow in both directions");

        // === Phase 6: Bidirectional sync ===
        clientB.appendFacts(docId, List.of(
                Map.of("entityId", "e2", "attribute", "name", "value", "from-B", "source", "test")
        ));
        clientB.requestSync();

        String fromB = clientA.waitForValue(docId, "e2", "name", "from-B", 15);
        assertEquals("from-B", fromB, "Peer A should receive B's data via bidirectional sync");
    }

    // ==================== Rendezvous helpers ====================

    static String enrollDevice(String peerId) throws Exception {
        String rdvUrl = rendezvous.hostUrl();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair deviceKeyPair = kpg.generateKeyPair();
        String devicePubKey = Base64.getEncoder().encodeToString(deviceKeyPair.getPublic().getEncoded());

        String body = mapper.writeValueAsString(Map.of(
                "keycloakToken", peerId,
                "devicePublicKey", devicePubKey,
                "deviceName", "test-" + peerId));

        Request req = new Request.Builder()
                .url(rdvUrl + "/enroll-device")
                .header("Authorization", "Bearer " + peerId)
                .post(RequestBody.create(body, MediaType.get("application/json")))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code(), "Device enrollment should succeed");
            JsonNode json = mapper.readTree(resp.body().string());
            return json.get("deviceCredential").asText();
        }
    }

    private void announceToRendezvous(String credential, String peerId, int port, List<String> docIds) throws IOException {
        String body = mapper.writeValueAsString(Map.of("peerId", peerId, "port", port, "documentIds", docIds));
        Request req = new Request.Builder()
                .url(rendezvous.hostUrl() + "/announce")
                .header("X-Device-Credential", credential)
                .post(RequestBody.create(body, MediaType.get("application/json")))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code(), "Announce should succeed");
        }
    }

    private JsonNode discoverFromRendezvous(String credential, String docId) throws IOException {
        Request req = new Request.Builder()
                .url(rendezvous.hostUrl() + "/peers?documentId=" + docId)
                .header("X-Device-Credential", credential)
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code(), "Discovery should succeed");
            return mapper.readTree(resp.body().string());
        }
    }
}
