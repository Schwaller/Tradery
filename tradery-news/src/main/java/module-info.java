module com.tradery.news {
    // Exports
    exports com.tradery.news.model;
    exports com.tradery.news.topic;
    exports com.tradery.news.fetch;
    exports com.tradery.news.store;
    exports com.tradery.news.ai;
    exports com.tradery.news.ui;

    // Java modules
    requires java.desktop;

    // UI
    requires com.formdev.flatlaf;

    // Jackson
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.dataformat.yaml;

    // HTTP & parsing
    requires okhttp3;
    requires static com.rometools.rome;  // RSS fetching
    requires static org.jsoup;           // HTML parsing

    // SQLite
    requires static org.xerial.sqlitejdbc;  // Optional - only needed for SQLite store

    // Logging
    requires org.slf4j;

    // Jackson reflection access
    opens com.tradery.news.model to com.fasterxml.jackson.databind;
    opens com.tradery.news.topic to com.fasterxml.jackson.databind;
}
