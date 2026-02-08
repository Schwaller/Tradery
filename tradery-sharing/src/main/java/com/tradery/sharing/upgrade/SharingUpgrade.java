package com.tradery.sharing.upgrade;

import com.tradery.documents.Document;
import com.tradery.documents.DocumentManager;
import com.tradery.documents.DocumentMember;
import com.tradery.documents.DocumentWorkspace;
import com.tradery.sharing.identity.UserSession;

import java.io.IOException;
import java.util.List;

/**
 * Upgrades a LOCAL document to shared visibility.
 * Requires an active UserSession (Keycloak account).
 *
 * Steps:
 * 1. Store user_id in the document's FactStore local_config
 * 2. Create members.yaml with the user as owner
 * 3. Update document.yaml visibility
 */
public class SharingUpgrade {

    private final DocumentManager documentManager;

    public SharingUpgrade(DocumentManager documentManager) {
        this.documentManager = documentManager;
    }

    /**
     * Upgrade a document from LOCAL to a shared visibility level.
     *
     * @param docId      the document ID to upgrade
     * @param visibility the target visibility (PRIVATE, FRIENDS, or PUBLIC)
     * @param session    the authenticated user session
     * @param workspace  the open workspace for this document
     */
    public void upgrade(String docId, Document.Visibility visibility, UserSession session,
                        DocumentWorkspace workspace) throws IOException {
        if (visibility == Document.Visibility.LOCAL) {
            throw new IllegalArgumentException("Cannot upgrade to LOCAL — already local");
        }

        // 1. Store user_id in the document's FactStore local_config
        workspace.entityStore().factStore().setLocalConfig("user_id", session.userId());

        // 2. Create members.yaml with user as owner
        DocumentMember ownerMember = new DocumentMember(session.userId(), DocumentMember.Role.OWNER);
        documentManager.writeMembers(docId, List.of(ownerMember));

        // 3. Update document.yaml visibility + owner
        Document doc = workspace.document();
        doc.setOwnerId(session.userId());
        doc.setVisibility(visibility);
        if (doc.governance() == null) {
            doc.setGovernance(new Document.Governance(Document.Governance.Type.OPEN));
        }
        documentManager.updateDocument(doc);
    }
}
