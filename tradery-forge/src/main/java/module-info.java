module com.tradery.forge {
    // Internal modules
    requires transitive com.tradery.core;
    requires com.tradery.engine;
    requires com.tradery.dataclient;
    requires com.tradery.charts;

    // UI
    requires java.desktop;
    requires org.jfree.jfreechart;
    requires com.formdev.flatlaf;
    requires com.formdev.flatlaf.intellijthemes;

    // Terminal module for embedded AI terminal
    requires com.tradery.terminal;

    // HTTP server for API
    requires jdk.httpserver;

    // Data/IO
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires okhttp3;
    requires directory.watcher;

    // Markdown - flexmark (use individual modules)
    requires com.vladsch.flexmark;
    requires com.vladsch.flexmark.util;
    requires com.vladsch.flexmark.util.ast;
    requires com.vladsch.flexmark.util.data;
    requires com.vladsch.flexmark.util.misc;
    requires com.vladsch.flexmark.util.sequence;
    requires com.vladsch.flexmark.util.html;
    requires com.vladsch.flexmark.ext.tables;

    // Logging
    requires org.slf4j;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;

    // Exports - public API (minimal, most is internal)
    exports com.tradery.forge;

    // Jackson reflection access
    opens com.tradery.forge to com.fasterxml.jackson.databind;
    opens com.tradery.forge.io to com.fasterxml.jackson.databind;
    opens com.tradery.forge.data to com.fasterxml.jackson.databind;
    opens com.tradery.forge.analysis to com.fasterxml.jackson.databind;
    opens com.tradery.forge.api to com.fasterxml.jackson.databind;
}
