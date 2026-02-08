package com.tradery.news.ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages the ~/.tradery/documents/ directory for intel documents.
 * Each document is a subdirectory with document.yaml, services.yaml, facts.db, and news.db.
 */
public class IntelDocumentManager {

    private static final Path DOCUMENTS_DIR = Path.of(
        System.getProperty("user.home"), ".tradery", "documents"
    );

    private final ObjectMapper yaml;
    private final Path documentsDir;

    public IntelDocumentManager() {
        this(DOCUMENTS_DIR);
    }

    public IntelDocumentManager(Path documentsDir) {
        this.documentsDir = documentsDir;
        this.yaml = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
    }

    /**
     * Initialize the documents directory.
     * If no documents exist, creates a default one from the "news-analysis" template.
     */
    public void initialize() throws IOException {
        Files.createDirectories(documentsDir);

        // Clean up old standalone DBs
        Path oldEntityDb = documentsDir.getParent().resolve("entity-network.db");
        Files.deleteIfExists(oldEntityDb);
    }

    /** Create a new document from a template. */
    public DocMeta createDocument(String name, DocumentTemplate template) throws IOException {
        String id = UUID.randomUUID().toString();
        Path docDir = documentsDir.resolve(id);
        Files.createDirectories(docDir);

        DocMeta meta = new DocMeta();
        meta.id = id;
        meta.name = name;
        meta.templateId = template.getId();
        meta.createdAt = System.currentTimeMillis();

        // Use email identity if logged in, otherwise generate a local ID
        String email = IntelConfig.get().getUserEmail();
        meta.ownerId = (email != null && !email.isBlank()) ? email : "local-" + id.substring(0, 8);

        writeDocMeta(docDir, meta);

        // Write services.yaml from template
        DocumentServices services = DocumentServices.fromTemplate(template);
        services.save(docDir);

        return meta;
    }

    /** List all documents sorted by creation time. */
    public List<DocMeta> listDocuments() {
        List<DocMeta> docs = new ArrayList<>();
        if (!Files.isDirectory(documentsDir)) return docs;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(documentsDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                Path yamlFile = entry.resolve("document.yaml");
                if (!Files.exists(yamlFile)) continue;
                try {
                    DocMeta meta = yaml.readValue(yamlFile.toFile(), DocMeta.class);
                    docs.add(meta);
                } catch (IOException e) {
                    System.err.println("Failed to read document: " + yamlFile + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to scan documents dir: " + e.getMessage());
        }
        docs.sort(Comparator.comparingLong(d -> d.createdAt));
        return docs;
    }

    /** Get the directory path for a document. */
    public Path getDocumentDir(String docId) {
        return documentsDir.resolve(docId);
    }

    /** Rename a document. */
    public void renameDocument(String docId, String newName) throws IOException {
        Path docDir = documentsDir.resolve(docId);
        Path yamlFile = docDir.resolve("document.yaml");
        if (!Files.exists(yamlFile)) throw new IOException("Document not found: " + docId);

        DocMeta meta = yaml.readValue(yamlFile.toFile(), DocMeta.class);
        meta.name = newName;
        writeDocMeta(docDir, meta);
    }

    /** Delete a document and all its data. */
    public void deleteDocument(String docId) throws IOException {
        Path docDir = documentsDir.resolve(docId);
        if (!Files.isDirectory(docDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(docDir)) {
            for (Path file : stream) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(docDir);
    }

    private void writeDocMeta(Path docDir, DocMeta meta) throws IOException {
        yaml.writeValue(docDir.resolve("document.yaml").toFile(), meta);
    }

    /** Document metadata — serialized as document.yaml. Compatible with tradery-documents format. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocMeta {
        public String id;
        public String name;
        @JsonProperty("owner_id")
        public String ownerId;
        @JsonProperty("template_id")
        public String templateId;
        @JsonProperty("created_at")
        public long createdAt;
        public String visibility;

        public DocMeta() {}

        @Override
        public String toString() { return name; }
    }
}
