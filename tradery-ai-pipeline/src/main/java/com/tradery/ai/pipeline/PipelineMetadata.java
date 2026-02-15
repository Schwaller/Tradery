package com.tradery.ai.pipeline;

import com.tradery.ai.pipeline.step.StepMetadata;

import java.util.List;

/**
 * Metadata about a pipeline execution.
 */
public record PipelineMetadata(
    long durationMs,
    List<StepMetadata> steps,
    boolean wasEscalated,
    boolean wasSalvaged,
    int challengeFilteredCount
) {}
