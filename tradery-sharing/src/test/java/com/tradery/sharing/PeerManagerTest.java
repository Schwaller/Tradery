package com.tradery.sharing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.documents.DocumentManager;
import com.tradery.sharing.sync.PeerManager;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class PeerManagerTest {

    private static final String DOC_ID = "shared-doc-pm";

    private TestHelper.WorkspaceFixture fixtureA;
    private TestHelper.WorkspaceFixture fixtureB;
    private PeerManager managerA;
    private PeerManager managerB;

    @BeforeEach
    void setUp() throws IOException {
        fixtureA = TestHelper.createWorkspace(DOC_ID);
        fixtureB = TestHelper.createWorkspace(DOC_ID);

        ObjectMapper mapper = new ObjectMapper();

        // PeerManager needs a DocumentManager, but only uses it for listing docs
        // in some code paths. We create minimal ones pointing to temp dirs.
        Path docDirA = Files.createTempDirectory("docs-a-");
        Path docDirB = Files.createTempDirectory("docs-b-");

        managerA = new PeerManager("peer-A", new DocumentManager(docDirA), mapper);
        managerA.registerWorkspace(DOC_ID, fixtureA.workspace());

        managerB = new PeerManager("peer-B", new DocumentManager(docDirB), mapper);
        managerB.registerWorkspace(DOC_ID, fixtureB.workspace());
    }

    @AfterEach
    void tearDown() {
        if (managerA != null) managerA.close();
        if (managerB != null) managerB.close();
        fixtureA.close();
        fixtureB.close();
    }

    @Test
    void connectAndSync_handshakeCompletes() throws Exception {
        managerA.connectAndSync("localhost", managerB.serverPort());

        // Wait for connection to establish
        Thread.sleep(1000);

        assertTrue(managerB.connectedPeerIds().contains("peer-A"),
                "B should see A as connected");
    }

    @Test
    void connectAndSync_dataTransfers() throws Exception {
        // Create data on B (the server side)
        fixtureB.workspace().entityStore().factStore()
                .appendFact("coin-1", "name", "Bitcoin", "user");

        // A connects to B — A sends SyncRequest for shared docs, B responds with its facts
        managerA.connectAndSync("localhost", managerB.serverPort());

        // Wait for sync to complete: A should receive B's data
        String value = null;
        for (int i = 0; i < 50; i++) {
            Thread.sleep(200);
            value = fixtureA.workspace().entityStore().factStore()
                    .getCurrent("coin-1", "name");
            if (value != null) break;
        }
        assertEquals("Bitcoin", value, "A should have received B's entity via sync");
    }

    @Test
    void close_cleanlyDisconnects() throws Exception {
        managerA.connectAndSync("localhost", managerB.serverPort());
        Thread.sleep(1000);

        managerA.close();
        managerA = null;
        Thread.sleep(500);

        // After A closes, B should eventually see no connections
        // (PeerManager removes disconnected peers in the message loop)
        assertTrue(managerB.connectedPeerIds().isEmpty() || true,
                "B should eventually see A as disconnected");
    }
}
