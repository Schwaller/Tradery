package com.tradery.ai.pipeline.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and saves pipeline configuration from ~/.tradery/ai-pipeline.yaml.
 * Seeds with defaults on first run.
 */
public class PipelineStore {

    private static final Logger log = LoggerFactory.getLogger(PipelineStore.class);
    private static final Path CONFIG_PATH = Path.of(
        System.getProperty("user.home"), ".tradery", "ai-pipeline.yaml"
    );
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static PipelineStore instance;

    private PipelineYamlRoot root;

    private PipelineStore() {
        this.root = load();
    }

    public static synchronized PipelineStore get() {
        if (instance == null) {
            instance = new PipelineStore();
        }
        return instance;
    }

    public Map<String, List<String>> getTiers() {
        return root.tiers;
    }

    public Map<String, PipelineConfig> getPipelines() {
        return root.pipelines;
    }

    public String getDefaultPipelineName() {
        return root.defaultPipeline;
    }

    public PipelineConfig getPipeline(String name) {
        return root.pipelines.get(name);
    }

    public PipelineConfig getDefaultPipeline() {
        return root.pipelines.get(root.defaultPipeline);
    }

    public TierResolver createTierResolver() {
        return new TierResolver(root.tiers);
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            YAML.writeValue(CONFIG_PATH.toFile(), root);
        } catch (IOException e) {
            log.error("Failed to save pipeline config: {}", e.getMessage());
        }
    }

    private static PipelineYamlRoot load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                PipelineYamlRoot loaded = YAML.readValue(CONFIG_PATH.toFile(), PipelineYamlRoot.class);
                if (loaded != null && loaded.pipelines != null && !loaded.pipelines.isEmpty()) {
                    return loaded;
                }
            } catch (IOException e) {
                log.error("Failed to load pipeline config: {}", e.getMessage());
            }
        }

        // Seed defaults
        PipelineYamlRoot defaults = createDefaults();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            YAML.writeValue(CONFIG_PATH.toFile(), defaults);
            log.info("Created default pipeline config at {}", CONFIG_PATH);
        } catch (IOException e) {
            log.error("Failed to save default pipeline config: {}", e.getMessage());
        }
        return defaults;
    }

    private static PipelineYamlRoot createDefaults() {
        PipelineYamlRoot root = new PipelineYamlRoot();

        // Tier mappings
        root.tiers = new LinkedHashMap<>();
        root.tiers.put("fast", List.of("gemini-flash", "claude-haiku"));
        root.tiers.put("standard", List.of("claude-haiku", "gemini-flash"));
        root.tiers.put("premium", List.of("claude-sonnet", "claude-haiku"));

        // Pipeline recipes
        root.pipelines = new LinkedHashMap<>();

        // Quick: just query + parse + validate
        root.pipelines.put("quick", new PipelineConfig(List.of(
            new StepConfig("query", "fast"),
            new StepConfig("json-parse"),
            new StepConfig("schema-validate")
        )));

        // Thorough: query + parse + salvage + validate + quality gate + escalate + challenge + dedup
        StepConfig qualityGate = new StepConfig("quality-gate");
        qualityGate.setParams(Map.of("minResults", 5));
        root.pipelines.put("thorough", new PipelineConfig(List.of(
            new StepConfig("query", "fast"),
            new StepConfig("json-parse"),
            new StepConfig("json-salvage", "fast"),
            new StepConfig("schema-validate"),
            qualityGate,
            new StepConfig("escalate", "standard"),
            new StepConfig("challenge", "fast"),
            new StepConfig("dedup")
        )));

        // Deep: web research + thorough pipeline with premium escalation
        StepConfig deepQualityGate = new StepConfig("quality-gate");
        deepQualityGate.setParams(Map.of("minResults", 5));
        root.pipelines.put("deep", new PipelineConfig(List.of(
            new StepConfig("web-research"),
            new StepConfig("query", "standard"),
            new StepConfig("json-parse"),
            new StepConfig("json-salvage", "fast"),
            new StepConfig("schema-validate"),
            deepQualityGate,
            new StepConfig("escalate", "premium"),
            new StepConfig("challenge", "fast"),
            new StepConfig("dedup")
        )));

        root.defaultPipeline = "thorough";
        return root;
    }
}
