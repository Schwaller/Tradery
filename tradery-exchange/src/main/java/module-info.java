module com.tradery.exchange {
    // Exports
    exports com.tradery.exchange;
    exports com.tradery.exchange.model;
    exports com.tradery.exchange.exception;
    exports com.tradery.exchange.hyperliquid;

    // Dependencies
    requires transitive com.tradery.core;
    requires okhttp3;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires org.java.websocket;
    requires org.bouncycastle.provider;
    requires org.slf4j;

    // Jackson needs reflection access to models
    opens com.tradery.exchange.model to com.fasterxml.jackson.databind;
}
