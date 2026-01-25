module com.tradery.dataclient {
    // Exports
    exports com.tradery.dataclient;
    exports com.tradery.dataclient.page;

    // Java modules
    requires java.desktop;  // For SwingUtilities

    // Dependencies
    requires transitive com.tradery.core;
    requires okhttp3;
    requires msgpack.core;
    requires org.java_websocket;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;

    // Jackson reflection access for internal DTOs
    opens com.tradery.dataclient to com.fasterxml.jackson.databind;
}
