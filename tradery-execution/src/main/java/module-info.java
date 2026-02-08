module com.tradery.execution {
    // Exports
    exports com.tradery.execution;
    exports com.tradery.execution.order;
    exports com.tradery.execution.position;
    exports com.tradery.execution.risk;
    exports com.tradery.execution.journal;
    exports com.tradery.execution.bridge;

    // Dependencies
    requires transitive com.tradery.core;
    requires transitive com.tradery.exchange;
    requires com.tradery.engine;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires org.slf4j;

    // Jackson needs reflection access
    opens com.tradery.execution.journal to com.fasterxml.jackson.databind;
    opens com.tradery.execution.order to com.fasterxml.jackson.databind;
    opens com.tradery.execution.position to com.fasterxml.jackson.databind;
}
