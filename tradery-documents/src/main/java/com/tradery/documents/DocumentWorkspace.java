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

    public DocumentWorkspace(Document document, Path documentDir) {
        this.document = document;
        this.documentDir = documentDir;

        Path dbPath = documentDir.resolve("facts.db");
        this.entityStore = new EntityStore(dbPath);
        this.schemaRegistry = new SchemaRegistry(entityStore);
        this.dataSourceRegistry = new DataSourceRegistry(entityStore, schemaRegistry);
    }

    public Document document() { return document; }
    public Path documentDir() { return documentDir; }
    public EntityStore entityStore() { return entityStore; }
    public SchemaRegistry schemaRegistry() { return schemaRegistry; }
    public DataSourceRegistry dataSourceRegistry() { return dataSourceRegistry; }

    @Override
    public void close() {
        entityStore.close();
    }
}
