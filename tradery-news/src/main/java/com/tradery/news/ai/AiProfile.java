package com.tradery.news.ai;

import com.tradery.news.ui.IntelConfig;

/**
 * Named AI profile containing all provider settings.
 * Users can configure multiple profiles and choose which one to use for each operation.
 */
public class AiProfile {

    private String id;
    private String name;
    private String description;
    private IntelConfig.AiProvider provider = IntelConfig.AiProvider.CLAUDE;
    private int timeoutSeconds = 60;

    // CLI providers (CLAUDE, CODEX)
    private String path = "claude";
    private String args = "--print --output-format text --model haiku";

    // Custom provider
    private String command = "";

    // Gemini provider
    private String apiKey = "";
    private String model = "gemini-2.5-flash-lite";

    public AiProfile() {
    }

    public AiProfile(String id, String name, IntelConfig.AiProvider provider) {
        this.id = id;
        this.name = name;
        this.provider = provider;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public IntelConfig.AiProvider getProvider() {
        return provider;
    }

    public void setProvider(IntelConfig.AiProvider provider) {
        this.provider = provider;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    @Override
    public String toString() {
        return name != null ? name : id;
    }
}
