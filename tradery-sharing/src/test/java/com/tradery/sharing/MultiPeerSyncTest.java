package com.tradery.sharing;

import com.tradery.news.ui.coin.FactStore;
import com.tradery.sharing.sync.NetworkMessage;
import com.tradery.sharing.sync.SyncEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MultiPeerSyncTest {

    private static final String DOC_ID = "shared-doc-multi";

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

    /** Sync all facts from src → dst */
    private void sync(TestHelper.WorkspaceFixture src, TestHelper.WorkspaceFixture dst) {
        String srcPeerId = src.workspace().entityStore().factStore().peerId();
        var req = engine.createSyncRequest(dst.workspace(), DOC_ID, srcPeerId);
        var resp = engine.handleSyncRequest(src.workspace(), req);
        engine.handleSyncResponse(dst.workspace(), srcPeerId, resp);
    }

    @Test
    void threePeers_entityCreatedOnA_syncsToBC() {
        storeA().appendFact("e1", "name", "Bitcoin", "user");

        sync(fixtureA, fixtureB);
        sync(fixtureA, fixtureC);

        assertEquals("Bitcoin", storeB().getCurrent("e1", "name"));
        assertEquals("Bitcoin", storeC().getCurrent("e1", "name"));
    }

    @Test
    void differentEntitiesOnEach_convergeThroughSync() {
        storeA().appendFact("e1", "name", "Bitcoin", "user");
        storeB().appendFact("e2", "name", "Ethereum", "user");
        storeC().appendFact("e3", "name", "Solana", "user");

        // Full mesh sync: each pair syncs bidirectionally
        sync(fixtureA, fixtureB);
        sync(fixtureB, fixtureA);
        sync(fixtureA, fixtureC);
        sync(fixtureC, fixtureA);
        sync(fixtureB, fixtureC);
        sync(fixtureC, fixtureB);

        // All three should converge
        for (var store : new FactStore[]{storeA(), storeB(), storeC()}) {
            assertEquals("Bitcoin", store.getCurrent("e1", "name"));
            assertEquals("Ethereum", store.getCurrent("e2", "name"));
            assertEquals("Solana", store.getCurrent("e3", "name"));
        }
    }

    @Test
    void conflictResolution_LWW() {
        // A and B both write to the same entity/attribute
        storeA().appendFact("e1", "name", "BitcoinFromA", "user");
        storeB().appendFact("e1", "name", "BitcoinFromB", "user");

        // Sync bidirectionally
        sync(fixtureA, fixtureB);
        sync(fixtureB, fixtureA);

        // After sync, both should agree (LWW: highest lclock wins)
        String valueA = storeA().getCurrent("e1", "name");
        String valueB = storeB().getCurrent("e1", "name");
        assertEquals(valueA, valueB, "Both peers should agree after sync (LWW)");
    }

    @Test
    void transitiveSync_A_to_B_to_C() {
        storeA().appendFact("e1", "name", "Bitcoin", "user");

        // A → B
        sync(fixtureA, fixtureB);
        assertEquals("Bitcoin", storeB().getCurrent("e1", "name"));

        // B → C (A's data flows transitively through B)
        sync(fixtureB, fixtureC);
        assertEquals("Bitcoin", storeC().getCurrent("e1", "name"));
    }

    @Test
    void duplicateFacts_idempotent() {
        storeA().appendFact("e1", "name", "Bitcoin", "user");

        // Sync twice
        sync(fixtureA, fixtureB);
        sync(fixtureA, fixtureB);

        assertEquals("Bitcoin", storeB().getCurrent("e1", "name"));

        // Verify no duplicates: only 1 fact should exist for this entity/attribute
        var facts = storeB().getFactsSince(0);
        long matchingFacts = facts.stream()
                .filter(f -> "e1".equals(f.entityId()) && "name".equals(f.attribute()))
                .count();
        assertEquals(1, matchingFacts, "Duplicate facts should be deduplicated");
    }

    @Test
    void largeSync_100entities() {
        // Create 100 entities on A
        for (int i = 0; i < 100; i++) {
            storeA().appendFact("entity-" + i, "name", "Entity " + i, "user");
        }

        sync(fixtureA, fixtureB);

        // Verify all entities arrived
        for (int i = 0; i < 100; i++) {
            assertEquals("Entity " + i, storeB().getCurrent("entity-" + i, "name"),
                    "Entity " + i + " should be synced");
        }
    }
}
