package com.tradery.sharing;

import com.tradery.documents.Document;
import com.tradery.documents.DocumentManager;
import com.tradery.documents.DocumentMember;
import com.tradery.documents.DocumentWorkspace;
import com.tradery.sharing.identity.UserSession;
import com.tradery.sharing.upgrade.SharingUpgrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharingUpgradeTest {

    @TempDir Path tempDir;

    private DocumentManager documentManager;
    private SharingUpgrade upgrade;
    private UserSession session;

    @BeforeEach
    void setUp() throws Exception {
        documentManager = new DocumentManager(tempDir);
        upgrade = new SharingUpgrade(documentManager);
        session = new UserSession("user-123", "Alice", "token", "refresh",
                System.currentTimeMillis() + 3600_000, null);
    }

    /**
     * Open a workspace using the Document object that already has its id set
     * (Document YAML may not round-trip the id field due to Jackson getter naming).
     */
    private DocumentWorkspace openWorkspace(Document doc) {
        Path docDir = tempDir.resolve(doc.id());
        return new DocumentWorkspace(doc, docDir);
    }

    @Test
    void upgrade_setsVisibilityAndOwner() throws Exception {
        Document doc = documentManager.createDocument("Test Doc");
        assertEquals(Document.Visibility.LOCAL, doc.visibility());

        try (DocumentWorkspace ws = openWorkspace(doc)) {
            upgrade.upgrade(doc.id(), Document.Visibility.PUBLIC, session, ws);
        }

        assertEquals(Document.Visibility.PUBLIC, doc.visibility());
        assertEquals("user-123", doc.ownerId());
    }

    @Test
    void upgrade_createsMembers() throws Exception {
        Document doc = documentManager.createDocument("Test Doc");

        try (DocumentWorkspace ws = openWorkspace(doc)) {
            upgrade.upgrade(doc.id(), Document.Visibility.PRIVATE, session, ws);
        }

        List<DocumentMember> members = documentManager.readMembers(doc.id());
        assertEquals(1, members.size());
        assertEquals("user-123", members.getFirst().userId());
    }

    @Test
    void upgrade_setsDefaultOpenGovernance() throws Exception {
        Document doc = documentManager.createDocument("Test Doc");
        assertNull(doc.governance());

        try (DocumentWorkspace ws = openWorkspace(doc)) {
            upgrade.upgrade(doc.id(), Document.Visibility.PUBLIC, session, ws);
        }

        assertNotNull(doc.governance());
        assertEquals(Document.Governance.Type.OPEN, doc.governance().type());
    }

    @Test
    void upgrade_preservesExistingGovernance() throws Exception {
        Document doc = documentManager.createDocument("Test Doc");
        doc.setGovernance(new Document.Governance(Document.Governance.Type.VOTING));

        try (DocumentWorkspace ws = openWorkspace(doc)) {
            upgrade.upgrade(doc.id(), Document.Visibility.PUBLIC, session, ws);
        }

        assertEquals(Document.Governance.Type.VOTING, doc.governance().type());
    }

    @Test
    void upgrade_storesUserIdInLocalConfig() throws Exception {
        Document doc = documentManager.createDocument("Test Doc");

        try (DocumentWorkspace ws = openWorkspace(doc)) {
            upgrade.upgrade(doc.id(), Document.Visibility.PUBLIC, session, ws);
            assertEquals("user-123", ws.entityStore().factStore().getLocalConfig("user_id"));
        }
    }

    @Test
    void upgrade_toLocal_throws() throws Exception {
        Document doc = documentManager.createDocument("Test Doc");

        try (DocumentWorkspace ws = openWorkspace(doc)) {
            assertThrows(IllegalArgumentException.class, () ->
                    upgrade.upgrade(doc.id(), Document.Visibility.LOCAL, session, ws));
        }
    }
}
