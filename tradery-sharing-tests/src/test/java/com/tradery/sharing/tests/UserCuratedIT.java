package com.tradery.sharing.tests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for USER_CURATED governance mode.
 * Verifies that data syncs fully (like OPEN), but entity acceptance is local-only.
 */
@Testcontainers
class UserCuratedIT {

    static Network network = Network.newNetwork();
    static PeerContainer peerA = new PeerContainer("curated-a", network);
    static PeerContainer peerB = new PeerContainer("curated-b", network);

    static PeerClient clientA;
    static PeerClient clientB;

    @BeforeAll
    static void start() throws Exception {
        peerA.start();
        peerB.start();
        clientA = new PeerClient(peerA.controlUrl());
        clientB = new PeerClient(peerB.controlUrl());

        // Establish mutual friendship (required for sync)
        clientA.addFriend("curated-b", "Peer B");
        clientB.addFriend("curated-a", "Peer A");
    }

    @AfterAll
    static void stop() {
        peerA.stop();
        peerB.stop();
        network.close();
    }

    @Test
    void userCuratedWorkflow() throws Exception {
        String docId = "curated-doc";

        // 1. Both create doc with USER_CURATED governance
        clientA.createDocument(docId, "Curated Doc", "USER_CURATED", 0.51);
        clientB.createDocument(docId, "Curated Doc", "USER_CURATED", 0.51);

        // 2. A creates entities e1, e2, e3
        clientA.appendFacts(docId, List.of(
                Map.of("entityId", "e1", "attribute", "type", "value", "COIN", "source", "manual"),
                Map.of("entityId", "e1", "attribute", "name", "value", "Entity One", "source", "manual"),
                Map.of("entityId", "e2", "attribute", "type", "value", "COIN", "source", "manual"),
                Map.of("entityId", "e2", "attribute", "name", "value", "Entity Two", "source", "manual"),
                Map.of("entityId", "e3", "attribute", "type", "value", "COIN", "source", "manual"),
                Map.of("entityId", "e3", "attribute", "name", "value", "Entity Three", "source", "manual")
        ));

        // Verify A has them locally
        assertEquals("Entity One", clientA.getCurrent(docId, "e1", "name"));
        assertEquals("Entity Two", clientA.getCurrent(docId, "e2", "name"));
        assertEquals("Entity Three", clientA.getCurrent(docId, "e3", "name"));

        // 3. Connect + sync — B receives all data (full sync like OPEN)
        int aP2pPort = clientA.getP2pPort();
        clientB.connect("curated-a", aP2pPort);

        // Wait for B to receive all entities
        String val = clientB.waitForValue(docId, "e3", "name", "Entity Three", 15);
        assertEquals("Entity Three", val, "B should have received all entities via sync");
        assertEquals("Entity One", clientB.getCurrent(docId, "e1", "name"));
        assertEquals("Entity Two", clientB.getCurrent(docId, "e2", "name"));

        // Data should NOT be in pending (USER_CURATED commits directly like OPEN)
        assertEquals(0, clientB.getPendingCount(docId),
                "USER_CURATED should commit directly, not stage to pending");

        // 4. B accepts only e1 — verify accepted set is {e1}
        clientB.acceptEntity(docId, "e1");
        JsonNode bAccepted = clientB.getAcceptedEntities(docId);
        Set<String> bSet = jsonArrayToSet(bAccepted);
        assertEquals(Set.of("e1"), bSet, "B should have only e1 accepted");

        // 5. A accepts e1 + e2 — verify A's accepted set is {e1, e2}
        clientA.acceptEntity(docId, "e1");
        clientA.acceptEntity(docId, "e2");
        JsonNode aAccepted = clientA.getAcceptedEntities(docId);
        Set<String> aSet = jsonArrayToSet(aAccepted);
        assertEquals(Set.of("e1", "e2"), aSet, "A should have e1 and e2 accepted");

        // 6. Re-sync — verify acceptance sets are local-only (B still has only e1)
        clientB.requestSync();
        Thread.sleep(3000);
        JsonNode bAcceptedAfterSync = clientB.getAcceptedEntities(docId);
        Set<String> bSetAfterSync = jsonArrayToSet(bAcceptedAfterSync);
        assertEquals(Set.of("e1"), bSetAfterSync,
                "Acceptance should be local-only — B's set should not change after sync");

        // 7. B unaccepts e1 — verify accepted set is empty
        clientB.unacceptEntity(docId, "e1");
        JsonNode bAcceptedFinal = clientB.getAcceptedEntities(docId);
        Set<String> bSetFinal = jsonArrayToSet(bAcceptedFinal);
        assertTrue(bSetFinal.isEmpty(), "B's accepted set should be empty after unaccept");

        // Verify the data itself is still present (unaccept only hides, doesn't delete)
        assertEquals("Entity One", clientB.getCurrent(docId, "e1", "name"),
                "Data should still exist after unaccept — filtering is display-layer only");
    }

    private static Set<String> jsonArrayToSet(JsonNode array) {
        Set<String> set = new HashSet<>();
        for (JsonNode node : array) {
            set.add(node.asText());
        }
        return set;
    }
}
