package com.tradery.ai.pipeline.step;

/**
 * Metadata recorded for each step execution.
 */
public record StepMetadata(
    String stepName,
    StepResult.Status status,
    String message,
    long durationMs
) {}
