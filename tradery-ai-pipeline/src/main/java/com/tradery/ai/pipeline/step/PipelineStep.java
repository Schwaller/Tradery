package com.tradery.ai.pipeline.step;

/**
 * A single step in the discovery pipeline.
 */
public interface PipelineStep {

    /**
     * Execute this step, reading from and writing to the context.
     */
    StepResult execute(StepContext context);

    /**
     * Human-readable name for this step.
     */
    String name();
}
