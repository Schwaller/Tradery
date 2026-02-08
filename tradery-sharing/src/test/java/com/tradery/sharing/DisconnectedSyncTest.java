package com.tradery.sharing;

import com.tradery.news.ui.coin.FactStore;
import com.tradery.sharing.sync.NetworkMessage;
import com.tradery.sharing.sync.SyncEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for out-of-order sync and bidirectional merge after extended disconnection.
 * Simulates peers making many independent changes while offline, then reconnecting.
 */
class DisconnectedSyncTest {

    private static final String DOC_ID = "disconnected-doc";

    private SyncEngine engine;
    private TestHelper.WorkspaceFixture fixtureA;
    private TestHelper.WorkspaceFixture fixtureB;
    private TestHelper.WorkspaceFixture fixtureC;

    @BeforeEach
    void setUp() throws IOException {
        engine = new SyncEngine();
        fixtureA = TestHelper.createWorkspace(DOC_ID);
        fixtureB = TestHelper.createWorkspace(DOC_ID);
        fixtureC = TestHelper.createWorkspace(DOC_ID);
    }

    @AfterEach
    void tearDown() {
        fixtureA.close();
        fixtureB.close();
        fixtureC.close();
    }

    private FactStore storeA() { return fixtureA.workspace().entityStore().factStore(); }
    private FactStore storeB() { return fixtureB.workspace().entityStore().factStore(); }
    private FactStore storeC() { return fixtureC.workspace().entityStore().factStore(); }

    private void sync(TestHelper.WorkspaceFixture src, TestHelper.WorkspaceFixture dst) {
        String srcPeerId = src.workspace().entityStore().factStore().peerId();
        var req = engine.createSyncRequest(dst.workspace(), DOC_ID, srcPeerId);
        var resp = engine.handleSyncRequest(src.workspace(), req);
        engine.handleSyncResponse(dst.workspace(), srcPeerId, resp);
    }

    private void syncBidirectional(TestHelper.WorkspaceFixture a, TestHelper.WorkspaceFixture b) {
        sync(a, b);
        sync(b, a);
    }

    @Test
    void extendedDisconnection_manyChangesOnBothSides_converge() {
        // Both peers work independently for a while
        for (int i = 0; i < 20; i++) {
            storeA().appendFact("a-entity-" + i, "name", "A-Entity-" + i, "user");
            storeA().appendFact("a-entity-" + i, "category", "crypto", "user");
        }
        for (int i = 0; i < 20; i++) {
            storeB().appendFact("b-entity-" + i, "name", "B-Entity-" + i, "user");
            storeB().appendFact("b-entity-" + i, "category", "defi", "user");
        }

        // Reconnect and sync bidirectionally
        syncBidirectional(fixtureA, fixtureB);

        // Both should have all 40 entities
        for (int i = 0; i < 20; i++) {
            assertEquals("A-Entity-" + i, storeA().getCurrent("a-entity-" + i, "name"));
            assertEquals("A-Entity-" + i, storeB().getCurrent("a-entity-" + i, "name"));
            assertEquals("B-Entity-" + i, storeA().getCurrent("b-entity-" + i, "name"));
            assertEquals("B-Entity-" + i, storeB().getCurrent("b-entity-" + i, "name"));
        }
    }

    @Test
    void concurrentEditsToSameEntity_LWWResolvesConsistently() {
        // Initial sync: both peers start with the same entity
        storeA().appendFact("coin-1", "name", "Bitcoin", "user");
        sync(fixtureA, fixtureB);

        // Disconnect. Both edit the same entity's same attribute independently.
        storeA().appendFact("coin-1", "name", "Bitcoin Core", "user");
        storeB().appendFact("coin-1", "name", "Bitcoin Cash", "user");

        // Also edit different attributes independently
        storeA().appendFact("coin-1", "ticker", "BTC", "user");
        storeB().appendFact("coin-1", "market_cap", "1T", "user");

        // Reconnect
        syncBidirectional(fixtureA, fixtureB);

        // name: one of them wins via LWW, but both agree
        String nameA = storeA().getCurrent("coin-1", "name");
        String nameB = storeB().getCurrent("coin-1", "name");
        assertEquals(nameA, nameB, "Both peers must agree on name after sync");

        // Non-conflicting attributes should both be present on both peers
        assertEquals("BTC", storeA().getCurrent("coin-1", "ticker"));
        assertEquals("BTC", storeB().getCurrent("coin-1", "ticker"));
        assertEquals("1T", storeA().getCurrent("coin-1", "market_cap"));
        assertEquals("1T", storeB().getCurrent("coin-1", "market_cap"));
    }

    @Test
    void multipleRoundsOfDisconnectedEdits_converge() {
        // Round 1: initial data
        storeA().appendFact("e1", "name", "v1", "user");
        sync(fixtureA, fixtureB);
        assertEquals("v1", storeB().getCurrent("e1", "name"));

        // Round 2: both edit while disconnected
        storeA().appendFact("e1", "name", "v2-A", "user");
        storeB().appendFact("e1", "name", "v2-B", "user");
        syncBidirectional(fixtureA, fixtureB);
        assertEquals(storeA().getCurrent("e1", "name"), storeB().getCurrent("e1", "name"));

        // Round 3: again both edit while disconnected
        storeA().appendFact("e1", "name", "v3-A", "user");
        storeB().appendFact("e1", "name", "v3-B", "user");
        syncBidirectional(fixtureA, fixtureB);
        assertEquals(storeA().getCurrent("e1", "name"), storeB().getCurrent("e1", "name"));

        // Round 4: only A edits
        storeA().appendFact("e1", "name", "v4-final", "user");
        syncBidirectional(fixtureA, fixtureB);
        assertEquals("v4-final", storeA().getCurrent("e1", "name"));
        assertEquals("v4-final", storeB().getCurrent("e1", "name"));
    }

    @Test
    void outOfOrderFactsByLclock_resolveCorrectly() {
        // Manually create facts with out-of-order lclocks to simulate receiving
        // facts from multiple peers in non-sequential order
        long now = System.currentTimeMillis();

        List<FactStore.Fact> outOfOrderFacts = List.of(
                // Newer fact arrives first (higher lclock)
                new FactStore.Fact("f3", "e1", "name", "Latest", "user", "peer-X", 30, now, "c3"),
                // Older fact arrives second (lower lclock)
                new FactStore.Fact("f1", "e1", "name", "Oldest", "user", "peer-X", 10, now - 1000, "c1"),
                // Middle fact arrives last
                new FactStore.Fact("f2", "e1", "name", "Middle", "user", "peer-X", 20, now - 500, "c2")
        );

        var response = new NetworkMessage.SyncResponse(DOC_ID, outOfOrderFacts);
        engine.handleSyncResponse(fixtureA.workspace(), "peer-X", response);

        // LWW: highest lclock (30) should win → "Latest"
        assertEquals("Latest", storeA().getCurrent("e1", "name"));
    }

    @Test
    void outOfOrderSync_factsFromMultiplePeers_resolveByLclock() {
        // Peer X has old data, Peer Y has newer data
        // Sync Peer X first, then Peer Y — Y's data should win
        long now = System.currentTimeMillis();

        List<FactStore.Fact> peerXFacts = List.of(
                new FactStore.Fact("fx1", "e1", "name", "OldValue", "user", "peer-X", 5, now - 5000, "cx")
        );
        List<FactStore.Fact> peerYFacts = List.of(
                new FactStore.Fact("fy1", "e1", "name", "NewValue", "user", "peer-Y", 50, now, "cy")
        );

        // Receive Y first (newer), then X (older)
        engine.handleSyncResponse(fixtureA.workspace(), "peer-Y",
                new NetworkMessage.SyncResponse(DOC_ID, peerYFacts));
        engine.handleSyncResponse(fixtureA.workspace(), "peer-X",
                new NetworkMessage.SyncResponse(DOC_ID, peerXFacts));

        // Newer data (higher lclock) should win regardless of receive order
        assertEquals("NewValue", storeA().getCurrent("e1", "name"));
    }

    @Test
    void threePeers_extendedDisconnection_fullMeshConverge() {
        // All three start with shared baseline
        storeA().appendFact("shared-1", "name", "Original", "user");
        sync(fixtureA, fixtureB);
        sync(fixtureA, fixtureC);

        // Extended disconnection: each peer works independently
        for (int i = 0; i < 10; i++) {
            storeA().appendFact("only-a-" + i, "name", "A-data-" + i, "user");
        }
        for (int i = 0; i < 10; i++) {
            storeB().appendFact("only-b-" + i, "name", "B-data-" + i, "user");
        }
        for (int i = 0; i < 10; i++) {
            storeC().appendFact("only-c-" + i, "name", "C-data-" + i, "user");
        }

        // All three also edit the shared entity
        storeA().appendFact("shared-1", "name", "Updated-by-A", "user");
        storeB().appendFact("shared-1", "name", "Updated-by-B", "user");
        storeC().appendFact("shared-1", "name", "Updated-by-C", "user");

        // Full mesh sync
        syncBidirectional(fixtureA, fixtureB);
        syncBidirectional(fixtureA, fixtureC);
        syncBidirectional(fixtureB, fixtureC);

        // All peers should have all 30 unique entities
        for (int i = 0; i < 10; i++) {
            for (var store : new FactStore[]{storeA(), storeB(), storeC()}) {
                assertEquals("A-data-" + i, store.getCurrent("only-a-" + i, "name"));
                assertEquals("B-data-" + i, store.getCurrent("only-b-" + i, "name"));
                assertEquals("C-data-" + i, store.getCurrent("only-c-" + i, "name"));
            }
        }

        // Shared entity: all three must agree (LWW)
        String sharedA = storeA().getCurrent("shared-1", "name");
        String sharedB = storeB().getCurrent("shared-1", "name");
        String sharedC = storeC().getCurrent("shared-1", "name");
        assertEquals(sharedA, sharedB);
        assertEquals(sharedB, sharedC);
    }

    @Test
    void deleteWhileDisconnected_mergesCorrectly() {
        // Both start with the same entity
        storeA().appendFact("e1", "name", "Bitcoin", "user");
        storeA().appendFact("e1", "category", "crypto", "user");
        sync(fixtureA, fixtureB);

        // A deletes the entity while B edits it
        storeA().appendFact("e1", "_deleted", "1", "user");
        storeB().appendFact("e1", "name", "Bitcoin 2.0", "user");

        syncBidirectional(fixtureA, fixtureB);

        // Both must agree on state
        String deletedA = storeA().getCurrent("e1", "_deleted");
        String deletedB = storeB().getCurrent("e1", "_deleted");
        assertEquals(deletedA, deletedB, "Both peers must agree on deleted state");

        String nameA = storeA().getCurrent("e1", "name");
        String nameB = storeB().getCurrent("e1", "name");
        assertEquals(nameA, nameB, "Both peers must agree on name");
    }

    @Test
    void rapidFireEdits_sameAttribute_highestClockWins() {
        // A rapidly edits the same attribute many times
        for (int i = 0; i < 50; i++) {
            storeA().appendFact("e1", "counter", String.valueOf(i), "user");
        }

        sync(fixtureA, fixtureB);

        // B should see the final value
        assertEquals("49", storeB().getCurrent("e1", "counter"));
    }

    @Test
    void interleaved_createAndUpdate_acrossPeers() {
        // A creates entities 1-5
        for (int i = 1; i <= 5; i++) {
            storeA().appendFact("e" + i, "name", "Entity-" + i, "user");
        }
        sync(fixtureA, fixtureB);

        // B updates entities 1-5 and creates 6-10
        for (int i = 1; i <= 5; i++) {
            storeB().appendFact("e" + i, "name", "Updated-" + i, "user");
        }
        for (int i = 6; i <= 10; i++) {
            storeB().appendFact("e" + i, "name", "Entity-" + i, "user");
        }

        // Meanwhile A creates 11-15
        for (int i = 11; i <= 15; i++) {
            storeA().appendFact("e" + i, "name", "Entity-" + i, "user");
        }

        syncBidirectional(fixtureA, fixtureB);

        // A should have B's updates to 1-5
        for (int i = 1; i <= 5; i++) {
            assertEquals("Updated-" + i, storeA().getCurrent("e" + i, "name"));
        }
        // Both should have all 15 entities
        for (int i = 1; i <= 15; i++) {
            assertNotNull(storeA().getCurrent("e" + i, "name"), "A missing entity " + i);
            assertNotNull(storeB().getCurrent("e" + i, "name"), "B missing entity " + i);
        }
    }

    @Test
    void syncAfterPartialSync_resumesCorrectly() {
        // A creates data in batches
        storeA().appendFact("e1", "name", "First", "user");

        // Partial sync: only e1 transfers
        sync(fixtureA, fixtureB);
        assertEquals("First", storeB().getCurrent("e1", "name"));

        // A creates more data
        storeA().appendFact("e2", "name", "Second", "user");
        storeA().appendFact("e3", "name", "Third", "user");

        // Another sync: only e2 and e3 should transfer (incremental)
        sync(fixtureA, fixtureB);
        assertEquals("First", storeB().getCurrent("e1", "name"));
        assertEquals("Second", storeB().getCurrent("e2", "name"));
        assertEquals("Third", storeB().getCurrent("e3", "name"));

        // A creates even more
        storeA().appendFact("e4", "name", "Fourth", "user");

        // Third sync
        sync(fixtureA, fixtureB);
        assertEquals("Fourth", storeB().getCurrent("e4", "name"));
    }
}
