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
 * Three-peer full mesh sync test.
 * Each peer creates unique entities, all connect in a mesh, all 3 should converge.
 */
@Testcontainers
class MultiPeerIT {

    static Network network = Network.newNetwork();
    static PeerContainer peerA = new PeerContainer("multi-a", network);
    static PeerContainer peerB = new PeerContainer("multi-b", network);
    static PeerContainer peerC = new PeerContainer("multi-c", network);

    static PeerClient clientA;
    static PeerClient clientB;
    static PeerClient clientC;

    @BeforeAll
    static void start() {
        peerA.start();
        peerB.start();
        peerC.start();
        clientA = new PeerClient(peerA.controlUrl());
        clientB = new PeerClient(peerB.controlUrl());
        clientC = new PeerClient(peerC.controlUrl());
    }

    @AfterAll
    static void stop() {
        peerA.stop();
        peerB.stop();
        peerC.stop();
        network.close();
    }

    @Test
    void threeWayConvergence() throws Exception {
        String docId = "multi-doc";

        // All three create the same document
        clientA.createDocument(docId, "Multi Test");
        clientB.createDocument(docId, "Multi Test");
        clientC.createDocument(docId, "Multi Test");

        // Each peer creates unique data
        clientA.appendFacts(docId, List.of(
                Map.of("entityId", "from-A", "attribute", "name", "value", "Alpha", "source", "test")
        ));
        clientB.appendFacts(docId, List.of(
                Map.of("entityId", "from-B", "attribute", "name", "value", "Beta", "source", "test")
        ));
        clientC.appendFacts(docId, List.of(
                Map.of("entityId", "from-C", "attribute", "name", "value", "Gamma", "source", "test")
        ));

        // Connect in a chain: A→B, B→C
        int bPort = clientB.getP2pPort();
        int cPort = clientC.getP2pPort();

        clientA.connect("multi-b", bPort);
        Thread.sleep(3000); // Let A↔B sync complete
        clientB.connect("multi-c", cPort);
        Thread.sleep(3000); // Let B↔C sync complete

        // Re-sync to propagate C's data through B to A
        clientB.requestSync();

        // Wait for convergence — all three should have all data
        assertEquals("Alpha", clientA.waitForValue(docId, "from-A", "name", "Alpha", 15));
        assertEquals("Beta", clientA.waitForValue(docId, "from-B", "name", "Beta", 15));
        assertEquals("Gamma", clientA.waitForValue(docId, "from-C", "name", "Gamma", 15));

        assertEquals("Alpha", clientB.waitForValue(docId, "from-A", "name", "Alpha", 15));
        assertEquals("Beta", clientB.waitForValue(docId, "from-B", "name", "Beta", 15));
        assertEquals("Gamma", clientB.waitForValue(docId, "from-C", "name", "Gamma", 15));

        assertEquals("Alpha", clientC.waitForValue(docId, "from-A", "name", "Alpha", 15));
        assertEquals("Beta", clientC.waitForValue(docId, "from-B", "name", "Beta", 15));
        assertEquals("Gamma", clientC.waitForValue(docId, "from-C", "name", "Gamma", 15));
    }
}
