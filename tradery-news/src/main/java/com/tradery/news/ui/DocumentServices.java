package com.tradery.news.ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Per-document service configuration stored in {docDir}/services.yaml.
 * Controls which data sources are enabled and panel layout.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentServices {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    private String templateId;
    private Set<String> enabledSourceIds = new LinkedHashSet<>();
    private boolean aiExtractionEnabled;
    private int fetchIntervalMinutes;   // 0 = manual
    private List<PanelConfig> panels = new ArrayList<>();
    private Set<String> disabledFeedIds = new HashSet<>();

    public DocumentServices() {}

    /** Create services config from a template. */
    public static DocumentServices fromTemplate(DocumentTemplate template) {
        DocumentServices svc = new DocumentServices();
        svc.templateId = template.getId();
        svc.enabledSourceIds = template.getEnabledServices() != null
            ? new LinkedHashSet<>(template.getEnabledServices()) : new LinkedHashSet<>();
        svc.aiExtractionEnabled = svc.enabledSourceIds.contains("ai-extraction");
        svc.panels = template.getDefaultPanels() != null
            ? new ArrayList<>(template.getDefaultPanels()) : PanelConfig.defaults();
        return svc;
    }

    /** Load from a document directory. Returns null if not found. */
    public static DocumentServices load(Path docDir) {
        Path file = docDir.resolve("services.yaml");
        if (!Files.exists(file)) return null;
        try {
            return YAML.readValue(file.toFile(), DocumentServices.class);
        } catch (IOException e) {
            System.err.println("Failed to load services.yaml: " + e.getMessage());
            return null;
        }
    }

    /** Save to a document directory. */
    public void save(Path docDir) {
        try {
            YAML.writeValue(docDir.resolve("services.yaml").toFile(), this);
        } catch (IOException e) {
            System.err.println("Failed to save services.yaml: " + e.getMessage());
        }
    }

    // Getters and setters

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public Set<String> getEnabledSourceIds() { return enabledSourceIds; }
    public void setEnabledSourceIds(Set<String> enabledSourceIds) { this.enabledSourceIds = enabledSourceIds; }

    public boolean isSourceEnabled(String sourceId) { return enabledSourceIds.contains(sourceId); }

    public boolean isAiExtractionEnabled() { return aiExtractionEnabled; }
    public void setAiExtractionEnabled(boolean aiExtractionEnabled) { this.aiExtractionEnabled = aiExtractionEnabled; }

    public int getFetchIntervalMinutes() { return fetchIntervalMinutes; }
    public void setFetchIntervalMinutes(int fetchIntervalMinutes) { this.fetchIntervalMinutes = fetchIntervalMinutes; }

    public List<PanelConfig> getPanels() { return panels; }
    public void setPanels(List<PanelConfig> panels) { this.panels = panels; }

    public Set<String> getDisabledFeedIds() { return disabledFeedIds; }
    public void setDisabledFeedIds(Set<String> disabledFeedIds) { this.disabledFeedIds = disabledFeedIds; }
}
