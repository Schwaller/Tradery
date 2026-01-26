module com.tradery.runner {
    // Internal modules
    requires transitive com.tradery.core;
    requires com.tradery.engine;
    requires com.tradery.dataclient;

    // Data/IO
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires okhttp3;

    // Logging
    requires org.slf4j;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;

    // Exports
    exports com.tradery.runner;

    // Jackson reflection access
    opens com.tradery.runner to com.fasterxml.jackson.databind;
}
