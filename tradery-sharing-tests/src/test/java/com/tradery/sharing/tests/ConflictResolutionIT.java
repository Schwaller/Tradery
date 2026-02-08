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
 * Conflict resolution test.
 * Both peers write the same entity/attribute independently, then sync.
 * LWW (Last Writer Wins via Lamport clock) should ensure convergence.
 */
@Testcontainers
class ConflictResolutionIT {

    static Network network = Network.newNetwork();
    static PeerContainer peerA = new PeerContainer("conflict-a", network);
    static PeerContainer peerB = new PeerContainer("conflict-b", network);

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
    void lwwConvergence() throws Exception {
        String docId = "conflict-doc";

        clientA.createDocument(docId, "Conflict Test");
        clientB.createDocument(docId, "Conflict Test");

        // Both peers write the same entity/attribute independently
        clientA.appendFacts(docId, List.of(
                Map.of("entityId", "shared-entity", "attribute", "status", "value", "value-from-A", "source", "test")
        ));

        clientB.appendFacts(docId, List.of(
                Map.of("entityId", "shared-entity", "attribute", "status", "value", "value-from-B", "source", "test")
        ));

        // Verify each peer has its own value before sync
        assertEquals("value-from-A", clientA.getCurrent(docId, "shared-entity", "status"));
        assertEquals("value-from-B", clientB.getCurrent(docId, "shared-entity", "status"));

        // Connect A → B to trigger sync
        int bP2pPort = clientB.getP2pPort();
        clientA.connect("conflict-b", bP2pPort);

        // Wait for sync to complete
        Thread.sleep(5000);

        // Both peers should now agree on the same value (LWW resolution)
        String valueA = clientA.getCurrent(docId, "shared-entity", "status");
        String valueB = clientB.getCurrent(docId, "shared-entity", "status");

        assertEquals(valueA, valueB,
                "Both peers should converge to the same value. A=" + valueA + " B=" + valueB);
        assertTrue(valueA.equals("value-from-A") || valueA.equals("value-from-B"),
                "Converged value should be one of the original values: " + valueA);
    }
}
