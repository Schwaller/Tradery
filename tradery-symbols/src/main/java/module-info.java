module com.tradery.symbols {
    // Dependencies
    requires java.sql;
    requires java.desktop;
    requires org.xerial.sqlitejdbc;
    requires org.slf4j;
    requires com.tradery.ui.common;

    // Public API
    exports com.tradery.symbols.model;
    exports com.tradery.symbols.service;
    exports com.tradery.symbols.ui;
}
