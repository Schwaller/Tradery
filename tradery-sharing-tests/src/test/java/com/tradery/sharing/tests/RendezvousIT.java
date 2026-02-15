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
 * End-to-end rendezvous test.
 * Two peers enroll devices, announce to rendezvous, discover each other, connect, and sync.
 */
@Testcontainers
class RendezvousIT {

    static Network network = Network.newNetwork();
    static RendezvousContainer rendezvous = new RendezvousContainer(network);
    static PeerContainer peerA = new PeerContainer("rdv-a", network);
    static PeerContainer peerB = new PeerContainer("rdv-b", network);

    static PeerClient clientA;
    static PeerClient clientB;
    static OkHttpClient http = new OkHttpClient();
    static ObjectMapper mapper = new ObjectMapper();

    /** Device credentials for rendezvous API calls. */
    static String credentialA;
    static String credentialB;

    @BeforeAll
    static void start() throws Exception {
        rendezvous.start();
        peerA.start();
        peerB.start();
        clientA = new PeerClient(peerA.controlUrl());
        clientB = new PeerClient(peerB.controlUrl());

        // Establish mutual friendship (required for sync)
        clientA.addFriend("rdv-b", "B");
        clientB.addFriend("rdv-a", "A");
        PeerClient.exchangeFriendshipCerts(clientA, "rdv-a", clientB, "rdv-b");

        // Enroll devices with rendezvous server
        credentialA = enrollDevice("rdv-a");
        credentialB = enrollDevice("rdv-b");
    }

    @AfterAll
    static void stop() {
        peerA.stop();
        peerB.stop();
        rendezvous.stop();
        network.close();
    }

    @Test
    void announceDiscoverConnectSync() throws Exception {
        String docId = "rdv-doc";

        // Both peers create the document
        clientA.createDocument(docId, "Rendezvous Test");
        clientB.createDocument(docId, "Rendezvous Test");

        // Set up cross-membership so both peers pass membership checks
        var members = List.of(
                Map.of("user_id", "rdv-a", "role", "OWNER"),
                Map.of("user_id", "rdv-b", "role", "MEMBER"));
        clientA.setMembers(docId, members);
        clientB.setMembers(docId, members);

        // Peer A creates data
        clientA.appendFacts(docId, List.of(
                Map.of("entityId", "rdv-entity", "attribute", "name", "value", "RendezvousData", "source", "test")
        ));

        // Get P2P ports
        int aP2pPort = clientA.getP2pPort();
        int bP2pPort = clientB.getP2pPort();

        // Both announce to the rendezvous server
        announceToRendezvous(credentialA, "rdv-a", aP2pPort, List.of(docId));
        announceToRendezvous(credentialB, "rdv-b", bP2pPort, List.of(docId));

        // Discover peers sharing the document
        JsonNode peers = discoverFromRendezvous(credentialA, docId);
        assertTrue(peers.size() >= 2, "Should find at least 2 peers sharing the doc, found: " + peers.size());

        // Connect A → B using Docker network alias
        clientA.connect("rdv-b", bP2pPort);

        // Wait for B to receive the data
        String value = clientB.waitForValue(docId, "rdv-entity", "name", "RendezvousData", 15);
        assertEquals("RendezvousData", value,
                "Peer B should have received data after rendezvous-facilitated connection");
    }

    // ==================== Rendezvous helpers ====================

    /** Enroll a device with the rendezvous server and return the device credential JWT. */
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
            assertEquals(200, resp.code());
            return mapper.readTree(resp.body().string());
        }
    }
}
