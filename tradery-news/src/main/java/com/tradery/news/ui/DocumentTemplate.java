package com.tradery.news.ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Template for creating new documents. Built-in templates are hardcoded;
 * user templates are saved to ~/.tradery/templates/{id}/template.yaml
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentTemplate {

    private static final Path TEMPLATES_DIR = Path.of(
        System.getProperty("user.home"), ".tradery", "templates"
    );
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    private String id;
    private String name;
    private String description;
    private List<PanelConfig> defaultPanels;
    private Set<String> enabledServices;
    private boolean builtIn;

    public DocumentTemplate() {}

    public DocumentTemplate(String id, String name, String description,
                            List<PanelConfig> defaultPanels, Set<String> enabledServices) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.defaultPanels = defaultPanels;
        this.enabledServices = enabledServices;
        this.builtIn = false;
    }

    /** The 3 built-in templates. */
    public static List<DocumentTemplate> builtIn() {
        // News Analysis: full featured — RSS + CoinGecko + AI extraction
        var newsMap = new PanelConfig("news-default", "News", PanelConfig.PanelType.NEWS_MAP);
        newsMap.setBands(BandConfig.defaultNewsBands());
        var coinGraph = new PanelConfig("coin-default", "Coin Relations", PanelConfig.PanelType.COIN_GRAPH);

        DocumentTemplate newsAnalysis = new DocumentTemplate(
            "news-analysis", "News Analysis",
            "RSS feeds + AI extraction + News Map + Coin Graph",
            List.of(newsMap, coinGraph),
            Set.of("rss", "coingecko", "ai-extraction")
        );
        newsAnalysis.builtIn = true;

        // Mind Map: empty canvas for manual entity creation
        var mindMapGraph = new PanelConfig("coin-default", "Graph", PanelConfig.PanelType.COIN_GRAPH);
        DocumentTemplate mindMap = new DocumentTemplate(
            "mind-map", "Mind Map",
            "Empty canvas for manual entity & relationship creation",
            List.of(mindMapGraph),
            Set.of()
        );
        mindMap.builtIn = true;

        // Crypto Research Board: CoinGecko market data only, no news feeds
        var researchGraph = new PanelConfig("coin-default", "Coin Relations", PanelConfig.PanelType.COIN_GRAPH);
        DocumentTemplate cryptoResearch = new DocumentTemplate(
            "crypto-research", "Crypto Research Board",
            "Entity management with CoinGecko market data, no news feeds",
            List.of(researchGraph),
            Set.of("coingecko")
        );
        cryptoResearch.builtIn = true;

        return List.of(newsAnalysis, mindMap, cryptoResearch);
    }

    /** Load user-created templates from ~/.tradery/templates/ */
    public static List<DocumentTemplate> loadUserTemplates() {
        List<DocumentTemplate> templates = new ArrayList<>();
        if (!Files.isDirectory(TEMPLATES_DIR)) return templates;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(TEMPLATES_DIR)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                Path yamlFile = entry.resolve("template.yaml");
                if (!Files.exists(yamlFile)) continue;
                try {
                    DocumentTemplate t = YAML.readValue(yamlFile.toFile(), DocumentTemplate.class);
                    t.builtIn = false;
                    templates.add(t);
                } catch (IOException e) {
                    System.err.println("Failed to read template: " + yamlFile + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to scan templates dir: " + e.getMessage());
        }
        templates.sort(Comparator.comparing(DocumentTemplate::getName));
        return templates;
    }

    /** All templates: built-in first, then user-created. */
    public static List<DocumentTemplate> all() {
        List<DocumentTemplate> all = new ArrayList<>(builtIn());
        all.addAll(loadUserTemplates());
        return all;
    }

    /** Save this template as a user template. */
    public void saveAsUserTemplate() throws IOException {
        Files.createDirectories(TEMPLATES_DIR.resolve(id));
        builtIn = false;
        YAML.writeValue(TEMPLATES_DIR.resolve(id).resolve("template.yaml").toFile(), this);
    }

    /** Delete a user template. */
    public static void deleteUserTemplate(String templateId) throws IOException {
        Path dir = TEMPLATES_DIR.resolve(templateId);
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path file : stream) Files.deleteIfExists(file);
            }
            Files.deleteIfExists(dir);
        }
    }

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<PanelConfig> getDefaultPanels() { return defaultPanels; }
    public void setDefaultPanels(List<PanelConfig> defaultPanels) { this.defaultPanels = defaultPanels; }

    public Set<String> getEnabledServices() { return enabledServices; }
    public void setEnabledServices(Set<String> enabledServices) { this.enabledServices = enabledServices; }

    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }

    @Override
    public String toString() {
        return name;
    }
}
