package com.tradery.sharing.sync;

import com.tradery.documents.Document;
import com.tradery.documents.DocumentMember;
import com.tradery.documents.DocumentWorkspace;
import com.tradery.news.ui.coin.FactStore;
import com.tradery.sharing.governance.GovernanceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Per-document fact exchange using FactStore's built-in sync primitives.
 * Trivially thin: getFactsSince() outbound, receiveFacts() inbound,
 * with sync state tracking in local_config.
 *
 * When a document has governance (admin_approved or voting), incoming facts
 * are routed through the GovernanceEngine instead of being committed directly.
 */
public class SyncEngine {

    private static final Logger log = LoggerFactory.getLogger(SyncEngine.class);
    private static final String SYNC_KEY_PREFIX = "sync:";

    private final GovernanceEngine governanceEngine;

    public SyncEngine() {
        this.governanceEngine = new GovernanceEngine();
    }

    public SyncEngine(GovernanceEngine governanceEngine) {
        this.governanceEngine = governanceEngine;
    }

    public GovernanceEngine governanceEngine() { return governanceEngine; }

    /**
     * Handle an incoming sync request: return facts that the remote peer hasn't seen.
     */
    public NetworkMessage.SyncResponse handleSyncRequest(
            DocumentWorkspace workspace,
            NetworkMessage.SyncRequest request) {

        FactStore store = workspace.entityStore().factStore();
        List<FactStore.Fact> facts = store.getFactsSince(request.sinceLclock());
        log.info("Sync request for doc {} since lclock {}: sending {} facts",
                request.documentId(), request.sinceLclock(), facts.size());
        return new NetworkMessage.SyncResponse(request.documentId(), facts);
    }

    /**
     * Handle an incoming sync response: receive remote facts into our FactStore.
     * For documents with governance, routes through GovernanceEngine.
     */
    public void handleSyncResponse(
            DocumentWorkspace workspace,
            Document document,
            List<DocumentMember> members,
            String remotePeerId,
            String remoteUserId,
            NetworkMessage.SyncResponse response) {

        if (response.facts().isEmpty()) {
            log.debug("Sync response for doc {} from {}: no new facts",
                    response.documentId(), remotePeerId);
            return;
        }

        FactStore store = workspace.entityStore().factStore();

        // Route through governance if the document has non-open governance
        Document.Governance governance = document.governance();
        boolean hasGovernance = governance != null && governance.type() != Document.Governance.Type.OPEN;

        if (hasGovernance && remoteUserId != null) {
            governanceEngine.routeIncomingFacts(workspace, document, members,
                    remotePeerId, remoteUserId, response.facts());
        } else {
            // Open governance or local document — commit directly
            store.receiveFacts(response.facts());
        }

        // Track the highest lclock received from this peer
        long maxClock = response.facts().stream()
                .mapToLong(FactStore.Fact::lclock)
                .max()
                .orElse(0);
        store.setLocalConfig(syncKey(remotePeerId), String.valueOf(maxClock));

        log.info("Received {} facts for doc {} from {}, max lclock={}",
                response.facts().size(), response.documentId(), remotePeerId, maxClock);
    }

    /**
     * Simplified handler for local/open documents (no governance routing needed).
     */
    public void handleSyncResponse(
            DocumentWorkspace workspace,
            String remotePeerId,
            NetworkMessage.SyncResponse response) {

        handleSyncResponse(workspace, workspace.document(), List.of(),
                remotePeerId, null, response);
    }

    /**
     * Create a sync request for a document — asks the remote peer for facts
     * newer than the last lclock we received from them.
     */
    public NetworkMessage.SyncRequest createSyncRequest(
            DocumentWorkspace workspace,
            String documentId,
            String remotePeerId) {

        FactStore store = workspace.entityStore().factStore();
        String lastClock = store.getLocalConfig(syncKey(remotePeerId));
        long sinceLclock = lastClock != null ? Long.parseLong(lastClock) : 0;
        return new NetworkMessage.SyncRequest(documentId, sinceLclock);
    }

    private String syncKey(String remotePeerId) {
        return SYNC_KEY_PREFIX + remotePeerId + ":last_lclock";
    }
}
