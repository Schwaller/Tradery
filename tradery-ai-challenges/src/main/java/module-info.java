module com.tradery.ai.challenges {
    requires com.tradery.ai;
    requires com.tradery.ai.pipeline;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;

    exports com.tradery.ai.challenges.model;
    exports com.tradery.ai.challenges.execution;
    exports com.tradery.ai.challenges.schedule;
    exports com.tradery.ai.challenges.store;
    exports com.tradery.ai.challenges.subject;

    opens com.tradery.ai.challenges.model to com.fasterxml.jackson.databind;
    opens com.tradery.ai.challenges.schedule to com.fasterxml.jackson.databind;
}
