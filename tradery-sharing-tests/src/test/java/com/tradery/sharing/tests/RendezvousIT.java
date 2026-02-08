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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end rendezvous test.
 * Two peers announce to rendezvous, discover each other, connect, and sync.
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

    @BeforeAll
    static void start() {
        rendezvous.start();
        peerA.start();
        peerB.start();
        clientA = new PeerClient(peerA.controlUrl());
        clientB = new PeerClient(peerB.controlUrl());
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

        // Peer A creates data
        clientA.appendFacts(docId, List.of(
                Map.of("entityId", "rdv-entity", "attribute", "name", "value", "RendezvousData", "source", "test")
        ));

        // Get P2P ports
        int aP2pPort = clientA.getP2pPort();
        int bP2pPort = clientB.getP2pPort();

        // Both announce to the rendezvous server (using host URL for tests hitting from host)
        String rdvUrl = rendezvous.hostUrl();
        announceToRendezvous(rdvUrl, "rdv-a", aP2pPort, List.of(docId));
        announceToRendezvous(rdvUrl, "rdv-b", bP2pPort, List.of(docId));

        // Discover peers sharing the document
        JsonNode peers = discoverFromRendezvous(rdvUrl, docId);
        assertTrue(peers.size() >= 2, "Should find at least 2 peers sharing the doc, found: " + peers.size());

        // Connect A → B using Docker network alias (simulating what a real client would do after discovery)
        clientA.connect("rdv-b", bP2pPort);

        // Wait for B to receive the data
        String value = clientB.waitForValue(docId, "rdv-entity", "name", "RendezvousData", 15);
        assertEquals("RendezvousData", value,
                "Peer B should have received data after rendezvous-facilitated connection");
    }

    private void announceToRendezvous(String rdvUrl, String peerId, int port, List<String> docIds) throws IOException {
        String body = mapper.writeValueAsString(Map.of("peerId", peerId, "port", port, "documentIds", docIds));
        Request req = new Request.Builder()
                .url(rdvUrl + "/announce")
                .header("Authorization", "Bearer test-token")
                .post(RequestBody.create(body, MediaType.get("application/json")))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code(), "Announce should succeed");
        }
    }

    private JsonNode discoverFromRendezvous(String rdvUrl, String docId) throws IOException {
        Request req = new Request.Builder()
                .url(rdvUrl + "/peers?documentId=" + docId)
                .header("Authorization", "Bearer test-token")
                .build();
        try (Response resp = http.newCall(req).execute()) {
            assertEquals(200, resp.code());
            return mapper.readTree(resp.body().string());
        }
    }
}
