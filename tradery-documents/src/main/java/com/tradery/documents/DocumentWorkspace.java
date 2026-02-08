package com.tradery.documents;

import com.tradery.news.source.DataSourceRegistry;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaRegistry;

import java.nio.file.Path;

/**
 * A fully bound workspace for one open document.
 * Holds the EntityStore, SchemaRegistry, and DataSourceRegistry wired to
 * the document's own facts.db.
 */
public class DocumentWorkspace implements AutoCloseable {

    private final Document document;
    private final Path documentDir;
    private final EntityStore entityStore;
    private final SchemaRegistry schemaRegistry;
    private final DataSourceRegistry dataSourceRegistry;

    private final boolean ownsEntityStore;

    public DocumentWorkspace(Document document, Path documentDir) {
        this.document = document;
        this.documentDir = documentDir;

        Path dbPath = documentDir.resolve("facts.db");
        this.entityStore = new EntityStore(dbPath);
        this.schemaRegistry = new SchemaRegistry(entityStore);
        this.dataSourceRegistry = new DataSourceRegistry(entityStore, schemaRegistry);
        this.ownsEntityStore = true;
    }

    /** Constructor that reuses an existing EntityStore (avoids duplicate SQLite connections). */
    public DocumentWorkspace(Document document, Path documentDir, EntityStore entityStore) {
        this.document = document;
        this.documentDir = documentDir;
        this.entityStore = entityStore;
        this.schemaRegistry = new SchemaRegistry(entityStore);
        this.dataSourceRegistry = new DataSourceRegistry(entityStore, schemaRegistry);
        this.ownsEntityStore = false;
    }

    public Document document() { return document; }
    public Path documentDir() { return documentDir; }
    public EntityStore entityStore() { return entityStore; }
    public SchemaRegistry schemaRegistry() { return schemaRegistry; }
    public DataSourceRegistry dataSourceRegistry() { return dataSourceRegistry; }

    @Override
    public void close() {
        if (ownsEntityStore) {
            entityStore.close();
        }
    }
}
