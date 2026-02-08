package com.tradery.sharing;

import com.tradery.documents.Document;
import com.tradery.documents.DocumentMember;
import com.tradery.news.ui.coin.FactStore;
import com.tradery.sharing.governance.GovernanceEngine;
import com.tradery.sharing.governance.Submission;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceEngineTest {

    private static final String OWNER_ID = "owner-user";
    private static final String ADMIN_ID = "admin-user";
    private static final String MEMBER_ID = "member-user";
    private static final String VIEWER_ID = "viewer-user";

    private GovernanceEngine engine;
    private List<DocumentMember> members;

    @BeforeEach
    void setUp() {
        engine = new GovernanceEngine();
        members = TestHelper.createMembers(OWNER_ID, ADMIN_ID, MEMBER_ID, VIEWER_ID);
    }

    private List<FactStore.Fact> makeFacts(String peerId) {
        return List.of(new FactStore.Fact(
                "f-" + peerId, "entity-1", "name", "Test",
                "user", peerId, 1, System.currentTimeMillis(), "c1"
        ));
    }

    @Nested
    class OpenGovernance {

        private Document document;

        @BeforeEach
        void setUp() {
            document = TestHelper.createGovernedDocument("doc-open", "Open Doc",
                    Document.Governance.Type.OPEN, 0.51);
        }

        @Test
        void memberFacts_committedDirectly() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-open")) {
                var facts = makeFacts("peer-member");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-member", MEMBER_ID, facts);

                assertEquals("Test", fixture.workspace().entityStore().factStore()
                        .getCurrent("entity-1", "name"));
                assertEquals(0, fixture.workspace().entityStore().factStore().getPendingCount());
            }
        }

        @Test
        void adminFacts_committedDirectly() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-open")) {
                var facts = makeFacts("peer-admin");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-admin", ADMIN_ID, facts);

                assertEquals("Test", fixture.workspace().entityStore().factStore()
                        .getCurrent("entity-1", "name"));
            }
        }
    }

    @Nested
    class AdminApprovedGovernance {

        private Document document;

        @BeforeEach
        void setUp() {
            document = TestHelper.createGovernedDocument("doc-admin", "Admin Doc",
                    Document.Governance.Type.ADMIN_APPROVED, 0.51);
        }

        @Test
        void adminFacts_committedDirectly() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-admin")) {
                var facts = makeFacts("peer-admin");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-admin", ADMIN_ID, facts);

                // Should be committed (in facts table, not pending)
                FactStore store = fixture.workspace().entityStore().factStore();
                assertEquals("Test", store.getCurrent("entity-1", "name"));
                assertEquals(0, store.getPendingCount());
            }
        }

        @Test
        void memberFacts_goToPending() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-admin")) {
                var facts = makeFacts("peer-member");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-member", MEMBER_ID, facts);

                FactStore store = fixture.workspace().entityStore().factStore();
                assertTrue(store.getPendingCount() > 0, "Facts should be in pending");
            }
        }

        @Test
        void approveSubmission_movesPendingToCommitted() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-admin")) {
                var facts = makeFacts("peer-member");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-member", MEMBER_ID, facts);

                // Pending should have something
                FactStore store = fixture.workspace().entityStore().factStore();
                assertTrue(store.getPendingCount() > 0);

                // Approve
                String commitId = engine.approveSubmission(fixture.workspace(), "peer-member");
                assertNotNull(commitId);

                // Now pending should be empty and data accessible via committed facts
                assertEquals(0, store.getPendingCount());
                assertEquals("Test", store.getCurrent("entity-1", "name"));
            }
        }

        @Test
        void rejectSubmission_discardsPending() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-admin")) {
                var facts = makeFacts("peer-member");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-member", MEMBER_ID, facts);

                assertTrue(fixture.workspace().entityStore().factStore().getPendingCount() > 0);

                engine.rejectSubmission(fixture.workspace(), "peer-member");

                FactStore store = fixture.workspace().entityStore().factStore();
                assertEquals(0, store.getPendingCount());
                // After rejection + rebuild, the entity should not exist in current
                // (it was only in pending, never committed)
                assertNull(store.getCurrent("entity-1", "name"));
            }
        }
    }

    @Nested
    class VotingGovernance {

        private Document document;

        @BeforeEach
        void setUp() {
            // 51% quorum: 2/3 eligible voters (owner, admin, member) = 66% >= 51%
            document = TestHelper.createGovernedDocument("doc-voting", "Voting Doc",
                    Document.Governance.Type.VOTING, 0.51);
        }

        @Test
        void memberFacts_goToPending() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-voting")) {
                var facts = makeFacts("peer-member");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-member", MEMBER_ID, facts);

                assertTrue(fixture.workspace().entityStore().factStore().getPendingCount() > 0);
            }
        }

        @Test
        void quorumReached_autoCommits() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-voting")) {
                // Member submits facts
                var facts = makeFacts("peer-member");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-member", MEMBER_ID, facts);

                // Two approvals (owner + admin) = 2/3 eligible = 66% >= 51% quorum
                engine.castVote(fixture.workspace(), "peer-member", OWNER_ID, true);
                engine.castVote(fixture.workspace(), "peer-member", ADMIN_ID, true);

                String commitId = engine.checkAndApplyQuorum(fixture.workspace(), document,
                        members, "peer-member");

                assertNotNull(commitId, "Should auto-commit when quorum reached");
                assertEquals(0, fixture.workspace().entityStore().factStore().getPendingCount());
            }
        }

        @Test
        void belowQuorum_doesNotCommit() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-voting")) {
                var facts = makeFacts("peer-member");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-member", MEMBER_ID, facts);

                // Only 1 approval = 1/3 = 33% < 51%
                engine.castVote(fixture.workspace(), "peer-member", OWNER_ID, true);

                String commitId = engine.checkAndApplyQuorum(fixture.workspace(), document,
                        members, "peer-member");

                assertNull(commitId, "Should not commit below quorum");
                assertTrue(fixture.workspace().entityStore().factStore().getPendingCount() > 0);
            }
        }

        @Test
        void rejectVotes_doNotCountTowardsQuorum() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-voting")) {
                var facts = makeFacts("peer-member");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-member", MEMBER_ID, facts);

                // 1 approve + 1 reject = 1/3 approve = 33% < 51%
                engine.castVote(fixture.workspace(), "peer-member", OWNER_ID, true);
                engine.castVote(fixture.workspace(), "peer-member", ADMIN_ID, false);

                String commitId = engine.checkAndApplyQuorum(fixture.workspace(), document,
                        members, "peer-member");

                assertNull(commitId, "Reject votes should not count towards quorum");
            }
        }

        @Test
        void getPendingSubmissions_groupsByPeer() throws IOException {
            try (var fixture = TestHelper.createWorkspace("doc-voting")) {
                // Two different peers submit
                var facts1 = List.of(new FactStore.Fact(
                        "f1", "e1", "name", "Bitcoin", "user", "peer-A", 1,
                        System.currentTimeMillis(), "c1"));
                var facts2 = List.of(new FactStore.Fact(
                        "f2", "e2", "name", "Ethereum", "user", "peer-B", 2,
                        System.currentTimeMillis(), "c2"));

                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-A", MEMBER_ID, facts1);
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-B", MEMBER_ID, facts2);

                List<Submission> submissions = engine.getPendingSubmissions(fixture.workspace());
                assertEquals(2, submissions.size());

                // Each submission should have the correct peer ID
                assertTrue(submissions.stream().anyMatch(s -> "peer-A".equals(s.peerId())));
                assertTrue(submissions.stream().anyMatch(s -> "peer-B".equals(s.peerId())));
            }
        }
    }

    @Nested
    class CrossCutting {

        @Test
        void viewerFacts_rejected() throws IOException {
            Document document = TestHelper.createGovernedDocument("doc-1", "Test",
                    Document.Governance.Type.ADMIN_APPROVED, 0.51);

            try (var fixture = TestHelper.createWorkspace("doc-1")) {
                var facts = makeFacts("peer-viewer");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-viewer", VIEWER_ID, facts);

                // Viewer facts should be silently rejected
                FactStore store = fixture.workspace().entityStore().factStore();
                assertNull(store.getCurrent("entity-1", "name"));
                assertEquals(0, store.getPendingCount());
            }
        }

        @Test
        void unknownUser_rejected() throws IOException {
            Document document = TestHelper.createGovernedDocument("doc-1", "Test",
                    Document.Governance.Type.OPEN, 0.51);

            try (var fixture = TestHelper.createWorkspace("doc-1")) {
                var facts = makeFacts("peer-unknown");
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-unknown", "unknown-user-id", facts);

                FactStore store = fixture.workspace().entityStore().factStore();
                assertNull(store.getCurrent("entity-1", "name"));
            }
        }

        @Test
        void emptyFacts_noError() throws IOException {
            Document document = TestHelper.createGovernedDocument("doc-1", "Test",
                    Document.Governance.Type.VOTING, 0.51);

            try (var fixture = TestHelper.createWorkspace("doc-1")) {
                // Should not throw
                engine.routeIncomingFacts(fixture.workspace(), document, members,
                        "peer-1", MEMBER_ID, List.of());
            }
        }
    }
}
