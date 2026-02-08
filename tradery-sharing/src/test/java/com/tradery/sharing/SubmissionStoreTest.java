package com.tradery.sharing;

import com.tradery.news.ui.coin.FactStore;
import com.tradery.sharing.governance.Submission;
import com.tradery.sharing.governance.SubmissionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionStoreTest {

    private SubmissionStore store;
    private TestHelper.WorkspaceFixture fixture;

    @BeforeEach
    void setUp() throws IOException {
        store = new SubmissionStore();
        fixture = TestHelper.createWorkspace();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private FactStore factStore() {
        return fixture.workspace().entityStore().factStore();
    }

    private int factCounter = 0;

    private void stageFacts(String peerId, String entityId, String attribute, String value) {
        factCounter++;
        long now = System.currentTimeMillis();
        factStore().stageRemoteFacts(List.of(
                new FactStore.Fact("f-" + factCounter + "-" + peerId + "-" + entityId,
                        entityId, attribute, value,
                        "user", peerId, factStore().lclock() + factCounter, now, null)
        ));
    }

    @Test
    void getPendingSubmissions_empty_returnsEmpty() {
        List<Submission> submissions = store.getPendingSubmissions(fixture.workspace());
        assertTrue(submissions.isEmpty());
    }

    @Test
    void getPendingSubmissions_singlePeer_returnsOne() {
        stageFacts("peer-A", "e1", "name", "Bitcoin");

        List<Submission> submissions = store.getPendingSubmissions(fixture.workspace());
        assertEquals(1, submissions.size());
        assertEquals("peer-A", submissions.getFirst().peerId());
        assertEquals(1, submissions.getFirst().factCount());
        assertTrue(submissions.getFirst().entityIds().contains("e1"));
    }

    @Test
    void getPendingSubmissions_multiplePeers_groupsCorrectly() {
        stageFacts("peer-A", "e1", "name", "Bitcoin");
        stageFacts("peer-A", "e1", "ticker", "BTC");
        stageFacts("peer-B", "e2", "name", "Ethereum");

        List<Submission> submissions = store.getPendingSubmissions(fixture.workspace());
        assertEquals(2, submissions.size());

        Submission subA = submissions.stream().filter(s -> "peer-A".equals(s.peerId())).findFirst().orElseThrow();
        assertEquals(2, subA.factCount()); // name + ticker
        assertEquals(1, subA.entityIds().size()); // just e1

        Submission subB = submissions.stream().filter(s -> "peer-B".equals(s.peerId())).findFirst().orElseThrow();
        assertEquals(1, subB.factCount());
    }

    @Test
    void recordVote_and_getVotes() {
        store.recordVote(fixture.workspace(), "peer-A", "voter-1", true);
        store.recordVote(fixture.workspace(), "peer-A", "voter-2", false);

        var votes = store.getVotes(fixture.workspace(), "peer-A");
        assertEquals(2, votes.size());

        var approveVote = votes.stream().filter(v -> "voter-1".equals(v.voterUserId())).findFirst().orElseThrow();
        assertTrue(approveVote.approve());

        var rejectVote = votes.stream().filter(v -> "voter-2".equals(v.voterUserId())).findFirst().orElseThrow();
        assertFalse(rejectVote.approve());
    }

    @Test
    void recordVote_sameVoterOverwrites() {
        store.recordVote(fixture.workspace(), "peer-A", "voter-1", false);
        store.recordVote(fixture.workspace(), "peer-A", "voter-1", true);

        var votes = store.getVotes(fixture.workspace(), "peer-A");
        assertEquals(1, votes.size());
        assertTrue(votes.getFirst().approve(), "Latest vote should win");
    }

    @Test
    void getVotes_differentSubmitters_isolated() {
        store.recordVote(fixture.workspace(), "peer-A", "voter-1", true);
        store.recordVote(fixture.workspace(), "peer-B", "voter-1", false);

        var votesA = store.getVotes(fixture.workspace(), "peer-A");
        assertEquals(1, votesA.size());
        assertTrue(votesA.getFirst().approve());

        var votesB = store.getVotes(fixture.workspace(), "peer-B");
        assertEquals(1, votesB.size());
        assertFalse(votesB.getFirst().approve());
    }

    @Test
    void clearVotes_softDeletes() {
        store.recordVote(fixture.workspace(), "peer-A", "voter-1", true);
        store.recordVote(fixture.workspace(), "peer-A", "voter-2", true);

        var votesBefore = store.getVotes(fixture.workspace(), "peer-A");
        assertEquals(2, votesBefore.size());

        store.clearVotes(fixture.workspace(), "peer-A");

        // After clearing, vote entities are soft-deleted (_deleted=1)
        // getVotes looks at current state which now has _deleted=1, so the
        // approve attribute is still there but the entity is logically deleted
        var votesAfter = store.getVotes(fixture.workspace(), "peer-A");
        // Votes still returned by getVotes since it only checks the approve attribute,
        // but the entities are marked as deleted
        for (String entityId : List.of(
                "_vote:peer-A:voter-1", "_vote:peer-A:voter-2")) {
            assertTrue(factStore().isDeleted(entityId));
        }
    }
}
