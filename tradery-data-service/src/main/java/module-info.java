module com.tradery.dataservice {
    // Exports - public API
    exports com.tradery.dataservice;
    exports com.tradery.dataservice.api;
    exports com.tradery.dataservice.data;
    exports com.tradery.dataservice.log;

    // Allow Log4j2 to discover the BufferAppender plugin
    opens com.tradery.dataservice.log to org.apache.logging.log4j.core;

    // License
    requires com.tradery.license;

    // Dependencies
    requires transitive com.tradery.core;
    requires com.tradery.data;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires okhttp3;
    requires io.javalin;
    requires msgpack.core;
    requires org.java_websocket;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires org.slf4j;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
}
