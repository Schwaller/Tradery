package com.tradery.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages the ~/.tradery/documents/ directory.
 * Each document is a self-contained directory with its own facts.db and document.yaml.
 */
public class DocumentManager {

    private static final Path DOCUMENTS_DIR = Path.of(System.getProperty("user.home"), ".tradery", "documents");
    private static final String DEFAULT_DOC_NAME = "Default";

    private final ObjectMapper yaml;
    private final Path documentsDir;

    public DocumentManager() {
        this(DOCUMENTS_DIR);
    }

    public DocumentManager(Path documentsDir) {
        this.documentsDir = documentsDir;
        this.yaml = new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
    }

    /**
     * Initialize documents directory. If old entity-network.db exists, delete it.
     * Ensures a default document exists.
     * Returns the default document ID.
     */
    public String initialize() throws IOException {
        Files.createDirectories(documentsDir);

        // Alpha/beta: delete old DB if present
        Path oldDb = documentsDir.getParent().resolve("entity-network.db");
        Files.deleteIfExists(oldDb);

        // Ensure at least one document exists
        List<Document> docs = listDocuments();
        if (docs.isEmpty()) {
            Document defaultDoc = createDocument(DEFAULT_DOC_NAME);
            return defaultDoc.id();
        }
        return docs.getFirst().id();
    }

    /** Create a new LOCAL document. Returns the Document metadata. */
    public Document createDocument(String name) throws IOException {
        String id = UUID.randomUUID().toString();
        Path docDir = documentsDir.resolve(id);
        Files.createDirectories(docDir);

        Document doc = new Document(id, name);
        writeDocumentYaml(docDir, doc);
        return doc;
    }

    /** List all known documents by scanning the documents directory. */
    public List<Document> listDocuments() throws IOException {
        List<Document> docs = new ArrayList<>();
        if (!Files.isDirectory(documentsDir)) return docs;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(documentsDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                Path yamlFile = entry.resolve("document.yaml");
                if (!Files.exists(yamlFile)) continue;
                try {
                    Document doc = readDocumentYaml(yamlFile);
                    docs.add(doc);
                } catch (IOException e) {
                    System.err.println("Failed to read document: " + yamlFile + ": " + e.getMessage());
                }
            }
        }
        docs.sort(Comparator.comparingLong(Document::createdAt));
        return docs;
    }

    /** Open a document by ID, returning a fully wired workspace. */
    public DocumentWorkspace openDocument(String docId) throws IOException {
        Path docDir = documentsDir.resolve(docId);
        Path yamlFile = docDir.resolve("document.yaml");
        if (!Files.exists(yamlFile)) {
            throw new IOException("Document not found: " + docId);
        }
        Document doc = readDocumentYaml(yamlFile);
        return new DocumentWorkspace(doc, docDir);
    }

    /** Delete a document and all its data. */
    public void deleteDocument(String docId) throws IOException {
        Path docDir = documentsDir.resolve(docId);
        if (!Files.isDirectory(docDir)) return;

        // Delete all files in the directory
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(docDir)) {
            for (Path file : stream) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(docDir);
    }

    /** Update document metadata (name, visibility, governance). */
    public void updateDocument(Document doc) throws IOException {
        Path docDir = documentsDir.resolve(doc.id());
        if (!Files.isDirectory(docDir)) {
            throw new IOException("Document directory not found: " + doc.id());
        }
        writeDocumentYaml(docDir, doc);
    }

    /** Read members for a shared document. Returns empty list for local docs. */
    public List<DocumentMember> readMembers(String docId) throws IOException {
        Path membersFile = documentsDir.resolve(docId).resolve("members.yaml");
        if (!Files.exists(membersFile)) return new ArrayList<>();
        MembersWrapper wrapper = yaml.readValue(membersFile.toFile(), MembersWrapper.class);
        return wrapper.members != null ? wrapper.members : new ArrayList<>();
    }

    /** Write members for a shared document. */
    public void writeMembers(String docId, List<DocumentMember> members) throws IOException {
        Path membersFile = documentsDir.resolve(docId).resolve("members.yaml");
        MembersWrapper wrapper = new MembersWrapper();
        wrapper.members = members;
        yaml.writeValue(membersFile.toFile(), wrapper);
    }

    public Path documentsDir() { return documentsDir; }

    private Document readDocumentYaml(Path yamlFile) throws IOException {
        return yaml.readValue(yamlFile.toFile(), Document.class);
    }

    private void writeDocumentYaml(Path docDir, Document doc) throws IOException {
        yaml.writeValue(docDir.resolve("document.yaml").toFile(), doc);
    }

    /** Wrapper for members.yaml which has a top-level 'members' key. */
    private static class MembersWrapper {
        public List<DocumentMember> members;
    }
}
