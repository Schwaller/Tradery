package com.tradery.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Auto-detects available AI providers on the system.
 * Each CLI check runs with a 3-second timeout.
 */
public class AiDetector {

    private static final int TIMEOUT_SECONDS = 3;

    public record DetectedProvider(
        AiProvider provider,
        String name,
        String description,
        String version,
        String command,
        String path,
        String args,
        boolean detected,
        boolean requiresSetup,
        String installUrl
    ) {}

    /**
     * Run all detection checks. Returns all known providers (detected or not).
     */
    public static List<DetectedProvider> detectAll() {
        List<DetectedProvider> results = new ArrayList<>();

        // Claude
        results.add(detectClaude());

        // Codex
        results.add(detectCodex());

        // Ollama (may return multiple entries, one per model)
        results.addAll(detectOllama());

        // Gemini CLI
        results.add(detectGemini());

        return results;
    }

    private static DetectedProvider detectClaude() {
        String version = runCommand("claude", "--version");
        boolean detected = version != null;
        return new DetectedProvider(
            AiProvider.CLAUDE,
            "Claude Code" + (detected ? "  v" + version.trim() : ""),
            "Anthropic's AI assistant CLI",
            detected ? version.trim() : null,
            null,
            "claude",
            "--print --output-format text --model haiku",
            detected,
            false,
            "https://docs.anthropic.com/en/docs/claude-code/overview"
        );
    }

    private static DetectedProvider detectCodex() {
        String version = runCommand("codex", "--version");
        boolean detected = version != null;
        return new DetectedProvider(
            AiProvider.CODEX,
            "Codex CLI" + (detected ? "  v" + version.trim() : ""),
            "OpenAI's coding CLI",
            detected ? version.trim() : null,
            null,
            "codex",
            "exec",
            detected,
            false,
            "https://github.com/openai/codex"
        );
    }

    private static DetectedProvider detectGemini() {
        String version = runCommand("gemini", "--version");
        boolean detected = version != null;
        String ver = null;
        if (detected) {
            ver = version.trim();
            if (ver.contains(" ")) {
                ver = ver.substring(ver.lastIndexOf(' ') + 1);
            }
        }
        return new DetectedProvider(
            AiProvider.GEMINI,
            "Gemini CLI" + (detected ? "  v" + ver : ""),
            detected
                ? "Google's AI CLI \u2014 free tier (1,000 req/day with Google account)"
                : "Google's AI CLI \u2014 free tier (1,000 req/day). Install: npm i -g @google/gemini-cli",
            ver,
            null,
            "gemini",
            "-p",
            detected,
            false,
            "https://github.com/google-gemini/gemini-cli"
        );
    }

    private static List<DetectedProvider> detectOllama() {
        List<DetectedProvider> results = new ArrayList<>();
        String version = runCommand("ollama", "--version");
        boolean installed = version != null;

        if (!installed) {
            results.add(new DetectedProvider(
                AiProvider.CUSTOM,
                "Ollama",
                "Local AI \u2014 free, private, no internet needed. Install from ollama.com",
                null,
                null,
                null,
                null,
                false,
                true,
                "https://ollama.com"
            ));
            return results;
        }

        // Parse version string (e.g., "ollama version is 0.1.17")
        String ver = version.trim();
        if (ver.contains(" ")) {
            ver = ver.substring(ver.lastIndexOf(' ') + 1);
        }

        // Get available models
        String modelOutput = runCommand("ollama", "list");
        List<String> models = parseOllamaModels(modelOutput);

        if (models.isEmpty()) {
            results.add(new DetectedProvider(
                AiProvider.CUSTOM,
                "Ollama  v" + ver,
                "Local AI \u2014 installed but no models. Run `ollama pull llama3` to download a model",
                ver,
                null,
                null,
                null,
                true,
                true,
                "https://ollama.com"
            ));
        } else {
            // Create one entry per model (up to 3)
            int limit = Math.min(models.size(), 3);
            for (int i = 0; i < limit; i++) {
                String model = models.get(i);
                String displayName = model.contains(":") ? model.substring(0, model.indexOf(':')) : model;
                results.add(new DetectedProvider(
                    AiProvider.CUSTOM,
                    "Ollama \u2014 " + displayName,
                    "Local AI \u2014 free, private, no internet needed",
                    ver,
                    "ollama run " + model,
                    null,
                    null,
                    true,
                    false,
                    "https://ollama.com"
                ));
            }
        }

        return results;
    }

    private static List<String> parseOllamaModels(String output) {
        List<String> models = new ArrayList<>();
        if (output == null) return models;

        String[] lines = output.split("\n");
        for (int i = 1; i < lines.length; i++) { // Skip header line
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            // First column is model name (e.g., "llama3:latest")
            String[] parts = line.split("\\s+");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                models.add(parts[0]);
            }
        }
        return models;
    }

    private static String runCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            // Inherit PATH from environment
            pb.environment().putIfAbsent("PATH", System.getenv("PATH"));
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) output.append("\n");
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            return process.exitValue() == 0 ? output.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
