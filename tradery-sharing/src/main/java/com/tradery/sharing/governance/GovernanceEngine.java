package com.tradery.sharing.governance;

import com.tradery.documents.Document;
import com.tradery.documents.DocumentMember;
import com.tradery.documents.DocumentWorkspace;
import com.tradery.news.ui.coin.FactStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Per-document governance engine. Decides how incoming remote facts are handled
 * based on the document's governance type and the author's member role.
 *
 * <ul>
 *   <li><b>open</b> — all member facts are committed directly</li>
 *   <li><b>user_curated</b> — same as open (full sync); per-user acceptance is display-layer only</li>
 *   <li><b>admin_approved</b> — admin/owner facts are committed directly;
 *       member facts go to pending for admin review</li>
 *   <li><b>voting</b> — all non-admin facts go to pending;
 *       members vote, committed when quorum is reached</li>
 * </ul>
 */
public class GovernanceEngine {

    private static final Logger log = LoggerFactory.getLogger(GovernanceEngine.class);

    private final SubmissionStore submissionStore;

    public GovernanceEngine() {
        this.submissionStore = new SubmissionStore();
    }

    /**
     * Route incoming remote facts based on governance rules.
     *
     * @param workspace   the document workspace
     * @param document    the document metadata (governance type)
     * @param members     current member list
     * @param remotePeerId the peer that sent these facts
     * @param remoteUserId the Keycloak user ID of the remote peer (from HELLO)
     * @param facts       the incoming facts
     */
    public void routeIncomingFacts(
            DocumentWorkspace workspace,
            Document document,
            List<DocumentMember> members,
            String remotePeerId,
            String remoteUserId,
            List<FactStore.Fact> facts) {

        if (facts.isEmpty()) return;

        DocumentMember.Role role = findRole(members, remoteUserId);
        if (role == null) {
            log.warn("Rejected facts from unknown user {} (peer {}) for doc {}",
                    remoteUserId, remotePeerId, document.id());
            return;
        }

        if (role == DocumentMember.Role.VIEWER) {
            log.warn("Rejected facts from viewer {} for doc {}", remoteUserId, document.id());
            return;
        }

        Document.Governance governance = document.governance();
        Document.Governance.Type govType = governance != null ? governance.type() : Document.Governance.Type.OPEN;

        FactStore store = workspace.entityStore().factStore();

        switch (govType) {
            case OPEN, USER_CURATED -> {
                // All members can commit directly (USER_CURATED filtering is display-layer only)
                store.receiveFacts(facts);
                log.info("{}: committed {} facts from {} for doc {}", govType, facts.size(), remoteUserId, document.id());
            }
            case ADMIN_APPROVED -> {
                if (isPrivileged(role)) {
                    // Admin/owner facts go directly to committed
                    store.receiveFacts(facts);
                    log.info("ADMIN_APPROVED: committed {} facts from admin {} for doc {}",
                            facts.size(), remoteUserId, document.id());
                } else {
                    // Non-admin facts go to pending for review
                    store.stageRemoteFacts(facts);
                    log.info("ADMIN_APPROVED: staged {} facts from member {} for review in doc {}",
                            facts.size(), remoteUserId, document.id());
                }
            }
            case VOTING -> {
                if (isPrivileged(role)) {
                    // Admin/owner facts go directly
                    store.receiveFacts(facts);
                    log.info("VOTING: committed {} facts from admin {} for doc {}",
                            facts.size(), remoteUserId, document.id());
                } else {
                    // Non-admin facts go to pending, await votes
                    store.stageRemoteFacts(facts);
                    log.info("VOTING: staged {} facts from {} for voting in doc {}",
                            facts.size(), remoteUserId, document.id());
                }
            }
        }
    }

    /**
     * Approve a peer's pending submission (admin action for admin_approved docs).
     * Moves all pending facts from the given peer into committed facts.
     *
     * @return the commit_id, or null if nothing to commit
     */
    public String approveSubmission(DocumentWorkspace workspace, String submitterPeerId) {
        String commitId = workspace.entityStore().factStore().commitPendingByPeerId(submitterPeerId);
        if (commitId != null) {
            log.info("Approved submission from peer {}, commit_id={}", submitterPeerId, commitId);
        }
        return commitId;
    }

    /**
     * Reject a peer's pending submission (admin action for admin_approved docs).
     * Discards all pending facts from the given peer.
     */
    public void rejectSubmission(DocumentWorkspace workspace, String submitterPeerId) {
        workspace.entityStore().factStore().rollbackPendingByPeerId(submitterPeerId);
        log.info("Rejected submission from peer {}", submitterPeerId);
    }

    /**
     * Cast a vote on a peer's pending submission (for voting-type documents).
     * The vote is stored as a committed fact (visible to all peers via sync).
     */
    public void castVote(DocumentWorkspace workspace, String submitterPeerId, String voterUserId, boolean approve) {
        submissionStore.recordVote(workspace, submitterPeerId, voterUserId, approve);
        log.info("Vote cast by {} on submission from {}: {}", voterUserId, submitterPeerId, approve ? "approve" : "reject");
    }

    /**
     * Check if a pending submission has reached quorum and should be auto-committed.
     *
     * @return the commit_id if quorum was reached and submission was committed, null otherwise
     */
    public String checkAndApplyQuorum(
            DocumentWorkspace workspace,
            Document document,
            List<DocumentMember> members,
            String submitterPeerId) {

        Document.Governance governance = document.governance();
        if (governance == null || governance.type() != Document.Governance.Type.VOTING) {
            return null;
        }

        // Count eligible voters (OWNER, ADMIN, MEMBER — not VIEWER)
        long eligibleVoters = members.stream()
                .filter(m -> m.role() != DocumentMember.Role.VIEWER)
                .count();

        if (eligibleVoters == 0) return null;

        // Count approve votes
        var votes = submissionStore.getVotes(workspace, submitterPeerId);
        long approveCount = votes.stream().filter(SubmissionStore.Vote::approve).count();

        double ratio = (double) approveCount / eligibleVoters;
        double quorum = governance.votingQuorum();

        if (ratio >= quorum) {
            String commitId = approveSubmission(workspace, submitterPeerId);
            log.info("Quorum reached for submission from {}: {}/{} votes ({}% >= {}%)",
                    submitterPeerId, approveCount, eligibleVoters,
                    Math.round(ratio * 100), Math.round(quorum * 100));
            return commitId;
        }

        return null;
    }

    /**
     * Get all pending submissions grouped by peer for governance review.
     */
    public List<Submission> getPendingSubmissions(DocumentWorkspace workspace) {
        return submissionStore.getPendingSubmissions(workspace);
    }

    private boolean isPrivileged(DocumentMember.Role role) {
        return role == DocumentMember.Role.OWNER || role == DocumentMember.Role.ADMIN;
    }

    private DocumentMember.Role findRole(List<DocumentMember> members, String userId) {
        if (userId == null) return null;
        return members.stream()
                .filter(m -> userId.equals(m.userId()))
                .map(DocumentMember::role)
                .findFirst()
                .orElse(null);
    }
}
