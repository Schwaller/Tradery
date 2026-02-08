package com.tradery.execution.journal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Base event type for execution journal entries.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderEvent.class, name = "order"),
    @JsonSubTypes.Type(value = PositionEvent.class, name = "position")
})
public abstract class ExecutionEvent {
    private final Instant timestamp;

    protected ExecutionEvent() {
        this.timestamp = Instant.now();
    }

    protected ExecutionEvent(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public abstract String getEventType();
    public abstract String getSummary();
}
