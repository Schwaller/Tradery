package com.tradery.ai.pipeline.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root POJO for ai-pipeline.yaml deserialization.
 */
public class PipelineYamlRoot {
    public Map<String, List<String>> tiers = new LinkedHashMap<>();
    public Map<String, PipelineConfig> pipelines = new LinkedHashMap<>();
    public String defaultPipeline = "thorough";
}
