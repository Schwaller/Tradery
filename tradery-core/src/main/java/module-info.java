module com.tradery.core {
    // Exports - all public packages
    exports com.tradery.core.model;
    exports com.tradery.core.dsl;
    exports com.tradery.core.indicators;
    exports com.tradery.core.indicators.registry;
    exports com.tradery.core.indicators.registry.specs;

    // Java modules
    requires java.desktop;  // For SwingUtilities in IndicatorCache

    // Jackson (for model serialization)
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.dataformat.yaml;

    // HTTP client (for calendar sync)
    requires okhttp3;

    // Logging
    requires org.slf4j;

    // Jackson needs reflection access to models
    opens com.tradery.core.model to com.fasterxml.jackson.databind;
}
