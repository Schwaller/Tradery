package com.tradery.sharing.tests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Governance test with ADMIN_APPROVED document.
 * Admin peer (A) creates an admin-approved doc.
 * Member peer (B) adds facts — they should land in pending on A.
 * Admin approves, facts move to committed.
 */
@Testcontainers
class GovernanceIT {

    static Network network = Network.newNetwork();
    static PeerContainer peerA = new PeerContainer("gov-admin", network);
    static PeerContainer peerB = new PeerContainer("gov-member", network);

    static PeerClient clientA;
    static PeerClient clientB;

    @BeforeAll
    static void start() throws Exception {
        peerA.start();
        peerB.start();
        clientA = new PeerClient(peerA.controlUrl());
        clientB = new PeerClient(peerB.controlUrl());

        // Establish mutual friendship (required for sync)
        clientA.addFriend("gov-member", "Member");
        clientB.addFriend("gov-admin", "Admin");
        PeerClient.exchangeFriendshipCerts(clientA, "gov-admin", clientB, "gov-member");
    }

    @AfterAll
    static void stop() {
        peerA.stop();
        peerB.stop();
        network.close();
    }

    @Test
    void adminApprovedWorkflow() throws Exception {
        String docId = "gov-doc";

        // Admin creates the doc with ADMIN_APPROVED governance
        clientA.createDocument(docId, "Governed Doc", "ADMIN_APPROVED", 0.51);
        clientB.createDocument(docId, "Governed Doc", "ADMIN_APPROVED", 0.51);

        // Set up members on both peers using actual peer IDs
        var members = List.of(
                Map.of("user_id", "gov-admin", "role", "OWNER"),
                Map.of("user_id", "gov-member", "role", "MEMBER"));
        clientA.setMembers(docId, members);
        clientB.setMembers(docId, members);

        // Member creates data on their side
        clientB.appendFacts(docId, List.of(
                Map.of("entityId", "member-entity", "attribute", "name", "value", "MemberData", "source", "test")
        ));

        // Verify member has it locally
        assertEquals("MemberData", clientB.getCurrent(docId, "member-entity", "name"));

        // Connect A → B to trigger sync
        int bP2pPort = clientB.getP2pPort();
        clientA.connect("gov-member", bP2pPort);

        // With ADMIN_APPROVED governance, member facts should land in pending on A
        // (includes schema seed facts + the test fact)
        int pending = clientA.waitForPending(docId, 1, 15);
        assertTrue(pending > 0,
                "Admin peer should have pending facts from member (ADMIN_APPROVED governance), got: " + pending);

        // Get the actual peer IDs from the pending table (FactStore ULIDs, not user emails)
        JsonNode pendingPeerIds = clientA.getPendingPeerIds(docId);
        assertTrue(pendingPeerIds.size() > 0, "Should have at least one pending peer ID");
        String memberPeerId = pendingPeerIds.get(0).asText();

        // Admin approves the submission using the FactStore peer ID
        clientA.approveSubmission(docId, memberPeerId);

        // Now the data should be committed (getCurrent already shows pending values,
        // but after approval they are in the facts table)
        String value = clientA.getCurrent(docId, "member-entity", "name");
        assertEquals("MemberData", value,
                "Member data should be committed after admin approval");

        // Pending should be empty after approval
        int pendingAfter = clientA.getPendingCount(docId);
        assertEquals(0, pendingAfter, "Pending should be empty after approval");
    }
}
