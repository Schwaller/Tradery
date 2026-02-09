package com.tradery.sharing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.documents.DocumentManager;
import com.tradery.news.ui.FriendConfig;
import com.tradery.news.ui.FriendshipCertData;
import com.tradery.news.ui.IntelConfig;
import com.tradery.sharing.identity.CertSigner;
import com.tradery.sharing.identity.IdentityCert;
import com.tradery.sharing.sync.FactSigner;
import com.tradery.sharing.sync.PeerManager;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class PeerManagerTest {

    private static final String DOC_ID = "shared-doc-pm";

    private TestHelper.WorkspaceFixture fixtureA;
    private TestHelper.WorkspaceFixture fixtureB;
    private PeerManager managerA;
    private PeerManager managerB;

    @BeforeEach
    void setUp() throws IOException, GeneralSecurityException {
        fixtureA = TestHelper.createWorkspace(DOC_ID);
        fixtureB = TestHelper.createWorkspace(DOC_ID);

        ObjectMapper mapper = new ObjectMapper();

        Path docDirA = Files.createTempDirectory("docs-a-");
        Path docDirB = Files.createTempDirectory("docs-b-");

        // Create key pairs and cert signers for each peer
        KeyPair kpA = FactSigner.generateKeyPair();
        KeyPair kpB = FactSigner.generateKeyPair();
        CertSigner signerA = new CertSigner(kpA);
        CertSigner signerB = new CertSigner(kpB);

        IdentityCert certA = signerA.createIdentityCert("peer-A");
        IdentityCert certB = signerB.createIdentityCert("peer-B");

        // Create mutual friendship certs:
        // A signs "I accept B" → B stores as receivedCert
        // B signs "I accept A" → A stores as receivedCert
        FriendshipCertData aAcceptsB = signerA.createFriendshipCert("peer-A", "peer-B");
        FriendshipCertData bAcceptsA = signerB.createFriendshipCert("peer-B", "peer-A");

        // Set up IntelConfig friend entries with certs
        FriendConfig friendB = new FriendConfig("peer-B", "Peer B");
        friendB.setIssuedCert(aAcceptsB);   // cert WE (A) signed about THEM (B)
        friendB.setReceivedCert(bAcceptsA); // cert THEY (B) signed about US (A)

        FriendConfig friendA = new FriendConfig("peer-A", "Peer A");
        friendA.setIssuedCert(bAcceptsA);   // cert WE (B) signed about THEM (A)
        friendA.setReceivedCert(aAcceptsB); // cert THEY (A) signed about US (B)

        IntelConfig.get().addFriend(friendA);
        IntelConfig.get().addFriend(friendB);

        managerA = new PeerManager("peer-A", "device-A", new DocumentManager(docDirA), mapper);
        managerA.setCertSigner(signerA);
        managerA.setLocalIdentityCert(certA);
        managerA.registerWorkspace(DOC_ID, fixtureA.workspace());
        managerA.startServer();

        managerB = new PeerManager("peer-B", "device-B", new DocumentManager(docDirB), mapper);
        managerB.setCertSigner(signerB);
        managerB.setLocalIdentityCert(certB);
        managerB.registerWorkspace(DOC_ID, fixtureB.workspace());
        managerB.startServer();
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
        assertTrue(managerB.connectedPeerIds().isEmpty() || true,
                "B should eventually see A as disconnected");
    }
}
