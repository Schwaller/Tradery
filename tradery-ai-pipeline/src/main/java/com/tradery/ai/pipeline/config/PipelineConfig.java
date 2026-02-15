package com.tradery.ai.pipeline.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a named pipeline — an ordered list of steps.
 */
public class PipelineConfig {
    private List<StepConfig> steps = new ArrayList<>();

    public PipelineConfig() {}

    public PipelineConfig(List<StepConfig> steps) {
        this.steps = steps;
    }

    public List<StepConfig> getSteps() { return steps; }
    public void setSteps(List<StepConfig> steps) { this.steps = steps; }
}
