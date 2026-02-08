module com.tradery.news {
    // Exports
    exports com.tradery.news.model;
    exports com.tradery.news.topic;
    exports com.tradery.news.fetch;
    exports com.tradery.news.store;
    exports com.tradery.news.ai;
    exports com.tradery.news.ui;
    exports com.tradery.news.ui.coin;
    exports com.tradery.news.api;
    exports com.tradery.news.source;

    // Java modules
    requires java.desktop;
    requires java.prefs;
    requires jdk.httpserver;

    // UI
    requires com.tradery.ui.common;
    requires com.tradery.help;
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
    opens com.tradery.news.ai to com.fasterxml.jackson.databind;
}
