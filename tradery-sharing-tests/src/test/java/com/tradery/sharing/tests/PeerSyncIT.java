package com.tradery.sharing.tests;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic 2-peer sync test.
 * Creates data on peer A, connects A→B, verifies B receives it.
 * Then B creates data, and verifies A receives it via the existing connection.
 */
@Testcontainers
class PeerSyncIT {

    static Network network = Network.newNetwork();
    static PeerContainer peerA = new PeerContainer("peer-a", network);
    static PeerContainer peerB = new PeerContainer("peer-b", network);

    static PeerClient clientA;
    static PeerClient clientB;

    @BeforeAll
    static void start() {
        peerA.start();
        peerB.start();
        clientA = new PeerClient(peerA.controlUrl());
        clientB = new PeerClient(peerB.controlUrl());
    }

    @AfterAll
    static void stop() {
        peerA.stop();
        peerB.stop();
        network.close();
    }

    @Test
    void twoWaySync() throws Exception {
        String docId = "sync-test-doc";

        // Both peers create the same document
        clientA.createDocument(docId, "Sync Test");
        clientB.createDocument(docId, "Sync Test");

        // Peer A creates data
        clientA.appendFacts(docId, List.of(
                Map.of("entityId", "entity-1", "attribute", "name", "value", "Alice", "source", "test"),
                Map.of("entityId", "entity-1", "attribute", "type", "value", "person", "source", "test")
        ));

        // Verify A has the data
        assertEquals("Alice", clientA.getCurrent(docId, "entity-1", "name"));

        // Get B's P2P port (internal to Docker network)
        int bP2pPort = clientB.getP2pPort();

        // A connects to B using Docker network alias
        clientA.connect("peer-b", bP2pPort);

        // Wait for B to receive the data via sync
        String value = clientB.waitForValue(docId, "entity-1", "name", "Alice", 15);
        assertEquals("Alice", value, "Peer B should have received entity-1 name from peer A");

        String type = clientB.waitForValue(docId, "entity-1", "type", "person", 15);
        assertEquals("person", type, "Peer B should have received entity-1 type from peer A");

        // Now B creates data
        clientB.appendFacts(docId, List.of(
                Map.of("entityId", "entity-2", "attribute", "name", "value", "Bob", "source", "test")
        ));

        // Trigger re-sync so B pushes new data to A
        clientB.requestSync();

        // Wait for A to receive B's data
        String bobValue = clientA.waitForValue(docId, "entity-2", "name", "Bob", 15);
        assertEquals("Bob", bobValue, "Peer A should have received entity-2 from peer B");
    }
}
