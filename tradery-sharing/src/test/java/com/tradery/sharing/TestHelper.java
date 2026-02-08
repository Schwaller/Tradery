package com.tradery.sharing;

import com.tradery.documents.Document;
import com.tradery.documents.DocumentMember;
import com.tradery.documents.DocumentWorkspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Shared test fixtures for tradery-sharing integration tests.
 */
public class TestHelper {

    public static WorkspaceFixture createWorkspace() throws IOException {
        return createWorkspace(UUID.randomUUID().toString());
    }

    public static WorkspaceFixture createWorkspace(String docId) throws IOException {
        Path tempDir = Files.createTempDirectory("tradery-test-");
        Document doc = new Document(docId, "Test Document");
        doc.setVisibility(Document.Visibility.PUBLIC);
        DocumentWorkspace workspace = new DocumentWorkspace(doc, tempDir);
        return new WorkspaceFixture(tempDir, workspace);
    }

    public static Document createGovernedDocument(String id, String name,
                                                   Document.Governance.Type govType,
                                                   double quorum) {
        Document doc = new Document(id, name);
        doc.setVisibility(Document.Visibility.PUBLIC);
        Document.Governance gov = new Document.Governance(govType);
        gov.setVotingQuorum(quorum);
        doc.setGovernance(gov);
        return doc;
    }

    public static List<DocumentMember> createMembers(String ownerId, String adminId,
                                                      String memberId, String viewerId) {
        return List.of(
                new DocumentMember(ownerId, DocumentMember.Role.OWNER),
                new DocumentMember(adminId, DocumentMember.Role.ADMIN),
                new DocumentMember(memberId, DocumentMember.Role.MEMBER),
                new DocumentMember(viewerId, DocumentMember.Role.VIEWER)
        );
    }

    public record WorkspaceFixture(Path tempDir, DocumentWorkspace workspace) implements AutoCloseable {
        @Override
        public void close() {
            workspace.close();
            deleteRecursive(tempDir);
        }
    }

    private static void deleteRecursive(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.forEach(TestHelper::deleteRecursive);
                }
            }
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {}
    }
}
