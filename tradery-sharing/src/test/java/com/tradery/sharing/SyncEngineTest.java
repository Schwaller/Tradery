package com.tradery.sharing;

import com.tradery.news.ui.coin.FactStore;
import com.tradery.sharing.sync.NetworkMessage;
import com.tradery.sharing.sync.SyncEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyncEngineTest {

    private static final String DOC_ID = "shared-doc-1";

    private SyncEngine engine;
    private TestHelper.WorkspaceFixture fixtureA;
    private TestHelper.WorkspaceFixture fixtureB;
    private long baseClockA;
    private long baseClockB;

    @BeforeEach
    void setUp() throws IOException {
        engine = new SyncEngine();
        fixtureA = TestHelper.createWorkspace(DOC_ID);
        fixtureB = TestHelper.createWorkspace(DOC_ID);
        // DocumentWorkspace seeds schema facts, so capture baseline clocks
        baseClockA = storeA().lclock();
        baseClockB = storeB().lclock();
    }

    @AfterEach
    void tearDown() {
        fixtureA.close();
        fixtureB.close();
    }

    private FactStore storeA() { return fixtureA.workspace().entityStore().factStore(); }
    private FactStore storeB() { return fixtureB.workspace().entityStore().factStore(); }

    @Test
    void handleSyncRequest_returnsFactsSinceGivenClock() {
        storeA().appendFact("e1", "name", "Bitcoin", "user");
        long clockAfterFirst = storeA().lclock();
        storeA().appendFact("e2", "name", "Ethereum", "user");

        var request = new NetworkMessage.SyncRequest(DOC_ID, clockAfterFirst);
        NetworkMessage.SyncResponse response = engine.handleSyncRequest(fixtureA.workspace(), request);

        assertEquals(DOC_ID, response.documentId());
        assertEquals(1, response.facts().size());
        assertEquals("Ethereum", response.facts().getFirst().value());
    }

    @Test
    void handleSyncRequest_withHighClock_returnsEmpty() {
        storeA().appendFact("e1", "name", "Bitcoin", "user");

        var request = new NetworkMessage.SyncRequest(DOC_ID, 999999);
        NetworkMessage.SyncResponse response = engine.handleSyncRequest(fixtureA.workspace(), request);

        assertTrue(response.facts().isEmpty());
    }

    @Test
    void handleSyncResponse_commitsFacts() {
        // Get only user-created facts (not schema seed facts)
        storeA().appendFact("e1", "name", "Bitcoin", "user");
        List<FactStore.Fact> allFacts = storeA().getFactsSince(baseClockA);
        // Filter to just our fact
        List<FactStore.Fact> ourFacts = allFacts.stream()
                .filter(f -> "e1".equals(f.entityId()))
                .toList();

        var response = new NetworkMessage.SyncResponse(DOC_ID, ourFacts);
        engine.handleSyncResponse(fixtureB.workspace(), storeA().peerId(), response);

        assertEquals("Bitcoin", storeB().getCurrent("e1", "name"));
    }

    @Test
    void fullRoundTrip_createOnA_syncToB() {
        // Create entity on A
        storeA().appendFact("coin-btc", "name", "Bitcoin", "user");
        storeA().appendFact("coin-btc", "symbol", "BTC", "user");

        // A creates sync request for B (B has never synced with A)
        var request = engine.createSyncRequest(fixtureB.workspace(), DOC_ID, storeA().peerId());
        assertEquals(0, request.sinceLclock());

        // A responds with all facts (including schema seed facts)
        var response = engine.handleSyncRequest(fixtureA.workspace(), request);
        // Should include at least our 2 facts plus schema seed facts
        assertTrue(response.facts().size() >= 2);

        // B receives the facts
        engine.handleSyncResponse(fixtureB.workspace(), storeA().peerId(), response);

        // Verify B has the user data
        assertEquals("Bitcoin", storeB().getCurrent("coin-btc", "name"));
        assertEquals("BTC", storeB().getCurrent("coin-btc", "symbol"));
    }

    @Test
    void incrementalSync_onlyTransfersNewFacts() {
        // First sync
        storeA().appendFact("e1", "name", "Bitcoin", "user");
        var req1 = engine.createSyncRequest(fixtureB.workspace(), DOC_ID, storeA().peerId());
        var resp1 = engine.handleSyncRequest(fixtureA.workspace(), req1);
        engine.handleSyncResponse(fixtureB.workspace(), storeA().peerId(), resp1);

        // Add more data
        storeA().appendFact("e2", "name", "Ethereum", "user");

        // Second sync should only get the new fact
        var req2 = engine.createSyncRequest(fixtureB.workspace(), DOC_ID, storeA().peerId());
        assertTrue(req2.sinceLclock() > 0, "Should remember last sync clock");

        var resp2 = engine.handleSyncRequest(fixtureA.workspace(), req2);
        assertEquals(1, resp2.facts().size());
        assertEquals("Ethereum", resp2.facts().getFirst().value());

        engine.handleSyncResponse(fixtureB.workspace(), storeA().peerId(), resp2);
        assertEquals("Ethereum", storeB().getCurrent("e2", "name"));
    }

    @Test
    void bidirectionalSync_bothPeersHaveData() {
        // A has one entity
        storeA().appendFact("e1", "name", "Bitcoin", "user");
        // B has another
        storeB().appendFact("e2", "name", "Ethereum", "user");

        // Sync A → B
        var reqAtoB = engine.createSyncRequest(fixtureB.workspace(), DOC_ID, storeA().peerId());
        var respAtoB = engine.handleSyncRequest(fixtureA.workspace(), reqAtoB);
        engine.handleSyncResponse(fixtureB.workspace(), storeA().peerId(), respAtoB);

        // Sync B → A
        var reqBtoA = engine.createSyncRequest(fixtureA.workspace(), DOC_ID, storeB().peerId());
        var respBtoA = engine.handleSyncRequest(fixtureB.workspace(), reqBtoA);
        engine.handleSyncResponse(fixtureA.workspace(), storeB().peerId(), respBtoA);

        // Both should have both entities
        assertEquals("Bitcoin", storeA().getCurrent("e1", "name"));
        assertEquals("Ethereum", storeA().getCurrent("e2", "name"));
        assertEquals("Bitcoin", storeB().getCurrent("e1", "name"));
        assertEquals("Ethereum", storeB().getCurrent("e2", "name"));
    }

    @Test
    void emptyResponse_doesNothing() {
        var response = new NetworkMessage.SyncResponse(DOC_ID, List.of());
        // Should not throw
        engine.handleSyncResponse(fixtureB.workspace(), "some-peer", response);

        // No user entity should exist
        assertNull(storeB().getCurrent("anything", "name"));
    }
}
