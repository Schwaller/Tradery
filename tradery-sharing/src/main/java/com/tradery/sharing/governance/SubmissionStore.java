package com.tradery.sharing.governance;

import com.tradery.documents.DocumentWorkspace;
import com.tradery.news.ui.coin.FactStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Queries pending submissions and manages votes for governance.
 *
 * Votes are stored as committed facts in the FactStore using the convention:
 * <pre>
 *   entity_id: _vote:{submitterPeerId}:{voterUserId}
 *   attribute: approve
 *   value:     "1" (approve) or "0" (reject)
 * </pre>
 *
 * This means votes sync naturally via P2P (they're just regular facts).
 */
public class SubmissionStore {

    private static final String VOTE_PREFIX = "_vote:";

    /**
     * Get all pending submissions grouped by peer.
     */
    public List<Submission> getPendingSubmissions(DocumentWorkspace workspace) {
        FactStore store = workspace.entityStore().factStore();
        List<FactStore.Fact> allPending = store.getPendingFacts();

        // Group by peer_id
        Map<String, List<FactStore.Fact>> byPeer = new LinkedHashMap<>();
        for (FactStore.Fact f : allPending) {
            byPeer.computeIfAbsent(f.peerId(), k -> new ArrayList<>()).add(f);
        }

        List<Submission> submissions = new ArrayList<>();
        for (var entry : byPeer.entrySet()) {
            List<FactStore.Fact> facts = entry.getValue();
            List<String> entityIds = facts.stream()
                    .map(FactStore.Fact::entityId)
                    .distinct()
                    .collect(Collectors.toList());

            // Count distinct (entity_id, attribute) pairs
            long distinctChanges = facts.stream()
                    .map(f -> f.entityId() + ":" + f.attribute())
                    .distinct()
                    .count();

            submissions.add(new Submission(
                    entry.getKey(),
                    facts,
                    (int) distinctChanges,
                    entityIds));
        }
        return submissions;
    }

    /**
     * Record a vote on a submission. The vote is stored as a committed fact.
     */
    public void recordVote(DocumentWorkspace workspace, String submitterPeerId, String voterUserId, boolean approve) {
        FactStore store = workspace.entityStore().factStore();
        String entityId = VOTE_PREFIX + submitterPeerId + ":" + voterUserId;
        store.appendFact(entityId, "approve", approve ? "1" : "0", "governance");
    }

    /**
     * Get all votes for a submission.
     */
    public List<Vote> getVotes(DocumentWorkspace workspace, String submitterPeerId) {
        FactStore store = workspace.entityStore().factStore();
        String prefix = VOTE_PREFIX + submitterPeerId + ":";

        // Find all vote entities for this submitter
        List<String> voteEntityIds = store.findByEntityIdPattern(prefix + "%");
        List<Vote> votes = new ArrayList<>();

        for (String voteEntityId : voteEntityIds) {
            // Extract voter ID from entity_id: _vote:{submitterPeerId}:{voterUserId}
            String voterUserId = voteEntityId.substring(prefix.length());
            String approveVal = store.getCurrent(voteEntityId, "approve");
            if (approveVal != null) {
                votes.add(new Vote(voterUserId, "1".equals(approveVal)));
            }
        }
        return votes;
    }

    /**
     * Clear votes for a submission (after it's been committed or rejected).
     */
    public void clearVotes(DocumentWorkspace workspace, String submitterPeerId) {
        FactStore store = workspace.entityStore().factStore();
        String prefix = VOTE_PREFIX + submitterPeerId + ":";

        List<String> voteEntityIds = store.findByEntityIdPattern(prefix + "%");
        for (String voteEntityId : voteEntityIds) {
            // Soft-delete the vote entity
            store.appendFact(voteEntityId, "_deleted", "1", "governance");
        }
    }

    /**
     * A single vote record.
     */
    public record Vote(String voterUserId, boolean approve) {}
}
