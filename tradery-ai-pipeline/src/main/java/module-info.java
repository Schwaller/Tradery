module com.tradery.ai.pipeline {
    requires com.tradery.ai;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires org.slf4j;

    exports com.tradery.ai.pipeline;
    exports com.tradery.ai.pipeline.schema;
    exports com.tradery.ai.pipeline.config;
    exports com.tradery.ai.pipeline.matching;

    opens com.tradery.ai.pipeline.config to com.fasterxml.jackson.databind;
}
