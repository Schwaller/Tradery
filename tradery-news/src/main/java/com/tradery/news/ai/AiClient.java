package com.tradery.news.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.news.ui.IntelConfig;
import com.tradery.news.ui.IntelLogPanel;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Centralized AI client that handles all CLI interactions for Claude and Codex.
 * Supports profile-based queries where each AiProfile carries its own provider settings.
 */
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.get("application/json");

    private static AiClient instance;

    private Consumer<String> logCallback;
    private final Map<Integer, OkHttpClient> httpClients = new ConcurrentHashMap<>();

    private AiClient() {
    }

    /**
     * Get the singleton instance.
     */
    public static synchronized AiClient getInstance() {
        if (instance == null) {
            instance = new AiClient();
        }
        return instance;
    }

    /**
     * Set a callback for activity logging.
     * All AI activity will be logged through this callback.
     */
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    /**
     * Get the name of the currently configured AI provider.
     */
    public String getProviderName() {
        AiProfile profile = IntelConfig.get().getDefaultProfile();
        return profile != null ? profile.getProvider().name() : "NONE";
    }

    /**
     * Check if the default AI profile is available.
     */
    public boolean isAvailable() {
        AiProfile profile = IntelConfig.get().getDefaultProfile();
        return profile != null && isAvailable(profile);
    }

    /**
     * Check if a specific AI profile is available.
     */
    public boolean isAvailable(AiProfile profile) {
        if (profile.getProvider() == IntelConfig.AiProvider.GEMINI) {
            try {
                String apiKey = profile.getApiKey();
                if (apiKey == null || apiKey.isBlank()) return false;
                Request request = new Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey)
                    .get()
                    .build();
                try (Response response = getHttpClient(profile.getTimeoutSeconds()).newCall(request).execute()) {
                    return response.isSuccessful();
                }
            } catch (Exception e) {
                return false;
            }
        }
        try {
            String cliPath = getCliPath(profile);
            ProcessBuilder pb = new ProcessBuilder(cliPath, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the version string from the default profile's CLI.
     */
    public String getVersion() throws AiException {
        AiProfile profile = IntelConfig.get().getDefaultProfile();
        if (profile == null) throw new AiException(AiException.ErrorType.NOT_FOUND, "No AI profile configured");
        return getVersion(profile);
    }

    /**
     * Get the version string from a specific profile's CLI.
     */
    public String getVersion(AiProfile profile) throws AiException {
        if (profile.getProvider() == IntelConfig.AiProvider.GEMINI) {
            return "Gemini API - " + profile.getModel();
        }
        try {
            String cliPath = getCliPath(profile);
            ProcessBuilder pb = new ProcessBuilder(cliPath, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new AiException(AiException.ErrorType.TIMEOUT, "CLI not responding");
            }

            if (p.exitValue() != 0) {
                throw new AiException(AiException.ErrorType.NOT_FOUND, "CLI not found or error");
            }

            return output.toString().trim();
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(AiException.ErrorType.NOT_FOUND, "CLI not found: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a query using the default profile.
     *
     * @param prompt The prompt to send to the AI
     * @return The raw response text
     * @throws AiException If the query fails
     */
    public String query(String prompt) throws AiException {
        AiProfile defaultProfile = IntelConfig.get().getDefaultProfile();
        if (defaultProfile == null) throw new AiException(AiException.ErrorType.NOT_FOUND, "No AI profile configured");
        return query(prompt, defaultProfile);
    }

    /**
     * Execute a query using a specific AI profile.
     *
     * @param prompt  The prompt to send to the AI
     * @param profile The AI profile to use
     * @return The raw response text
     * @throws AiException If the query fails
     */
    public String query(String prompt, AiProfile profile) throws AiException {
        int timeoutSeconds = profile.getTimeoutSeconds();
        String provider = profile.getProvider().name();

        log.debug("AI query to {} ({}): {}", provider, profile.getName(), truncate(prompt, 100));

        if (profile.getProvider() == IntelConfig.AiProvider.GEMINI) {
            return queryGemini(prompt, profile);
        }

        String cliPath = getCliPath(profile);

        try {
            ProcessBuilder pb = buildProcess(profile, cliPath, prompt);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // For Claude, write prompt to stdin (others pass as argument)
            if (usesStdin(profile)) {
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(prompt.getBytes());
                    stdin.flush();
                }
            }

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logActivityWithDetail("[" + provider + "] Timeout after " + timeoutSeconds + "s", prompt, null);
                throw new AiException(AiException.ErrorType.TIMEOUT,
                    provider + " CLI timed out after " + timeoutSeconds + " seconds");
            }

            String result = output.toString();

            if (process.exitValue() != 0) {
                AiException error = parseError(result, provider, cliPath);
                logActivityWithDetail("[" + provider + "] Error: " + error.getMessage(), prompt, result);
                throw error;
            }

            // Log successful query with full details
            String summary = "[" + provider + "] Query completed (" + result.length() + " chars)";
            logActivityWithDetail(summary, prompt, result);
            log.debug("AI response from {}: {}", provider, truncate(result, 200));

            return result;
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            logActivityWithDetail("[" + provider + "] Error: " + e.getMessage(), prompt, null);
            throw new AiException(AiException.ErrorType.UNKNOWN, "Query failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a query asynchronously using the default profile.
     */
    public void queryAsync(String prompt, Consumer<String> onOutput,
                           Consumer<String> onComplete, Consumer<Exception> onError) {
        new Thread(() -> {
            try {
                String result = query(prompt);
                if (onOutput != null) onOutput.accept(result);
                if (onComplete != null) onComplete.accept(result);
            } catch (Exception e) {
                if (onError != null) onError.accept(e);
            }
        }, "AiClient-Query").start();
    }

    /**
     * Test the default profile's AI connection.
     */
    public TestResult testConnection() {
        AiProfile profile = IntelConfig.get().getDefaultProfile();
        if (profile == null) return new TestResult(false, null, "No AI profile configured");
        return testConnection(profile);
    }

    /**
     * Test a specific profile's AI connection.
     */
    public TestResult testConnection(AiProfile profile) {
        String provider = profile.getProvider().name();

        // Step 1: Check version
        String version;
        try {
            version = getVersion(profile);
        } catch (AiException e) {
            return new TestResult(false, null, e.getMessage());
        }

        // Step 2: Test with a simple prompt
        try {
            String response = query("Say OK", profile);
            return new TestResult(true, version, "Response: " + truncate(response.trim(), 50));
        } catch (AiException e) {
            return new TestResult(false, version, e.getMessage());
        }
    }

    /**
     * Result of a connection test.
     */
    public record TestResult(boolean success, String version, String message) {
    }

    // ==================== Private helpers ====================

    private String getCliPath(AiProfile profile) {
        return switch (profile.getProvider()) {
            case CODEX -> profile.getPath();
            case CUSTOM -> {
                String cmd = profile.getCommand();
                if (cmd == null || cmd.isBlank()) yield "";
                String[] parts = cmd.trim().split("\\s+", 2);
                yield parts[0];
            }
            case GEMINI -> profile.getModel();
            default -> profile.getPath();
        };
    }

    private OkHttpClient getHttpClient(int timeoutSeconds) {
        return httpClients.computeIfAbsent(timeoutSeconds, timeout ->
            new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(timeout))
                .writeTimeout(Duration.ofSeconds(timeout))
                .build()
        );
    }

    private String queryGemini(String prompt, AiProfile profile) throws AiException {
        String apiKey = profile.getApiKey();
        String model = profile.getModel();
        String provider = profile.getProvider().name();

        if (apiKey == null || apiKey.isBlank()) {
            throw new AiException(AiException.ErrorType.API_KEY_MISSING, "Gemini API key not configured");
        }

        try {
            String url = GEMINI_API_BASE + model + ":generateContent";

            String body = JSON.writeValueAsString(java.util.Map.of(
                "contents", java.util.List.of(java.util.Map.of(
                    "parts", java.util.List.of(java.util.Map.of("text", prompt))
                )),
                "generationConfig", java.util.Map.of(
                    "temperature", 0.1,
                    "maxOutputTokens", 4096
                )
            ));

            Request request = new Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON_MEDIA))
                .build();

            try (Response response = getHttpClient(profile.getTimeoutSeconds()).newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    AiException error = switch (response.code()) {
                        case 401, 403 -> new AiException(AiException.ErrorType.API_KEY_MISSING,
                            "Gemini API key invalid or unauthorized (HTTP " + response.code() + ")");
                        case 429 -> new AiException(AiException.ErrorType.RATE_LIMITED,
                            "Gemini rate limit exceeded - free tier allows 15 RPM / 1,000 RPD");
                        default -> new AiException(AiException.ErrorType.UNKNOWN,
                            "Gemini API error (HTTP " + response.code() + "): " + truncate(responseBody, 200));
                    };
                    logActivityWithDetail("[" + provider + "] Error: " + error.getMessage(), prompt, responseBody);
                    throw error;
                }

                JsonNode root = JSON.readTree(responseBody);
                JsonNode candidates = root.path("candidates");
                if (candidates.isMissingNode() || candidates.isEmpty()) {
                    throw new AiException(AiException.ErrorType.UNKNOWN,
                        "Gemini returned no candidates: " + truncate(responseBody, 200));
                }

                String result = candidates.get(0).path("content").path("parts").get(0).path("text").asText("");

                String summary = "[" + provider + "] Query completed (" + result.length() + " chars)";
                logActivityWithDetail(summary, prompt, result);
                log.debug("AI response from {}: {}", provider, truncate(result, 200));

                return result;
            }
        } catch (AiException e) {
            throw e;
        } catch (java.net.SocketTimeoutException e) {
            logActivityWithDetail("[" + provider + "] Timeout", prompt, null);
            throw new AiException(AiException.ErrorType.TIMEOUT,
                "Gemini API timed out after " + profile.getTimeoutSeconds() + " seconds", e);
        } catch (Exception e) {
            logActivityWithDetail("[" + provider + "] Error: " + e.getMessage(), prompt, null);
            throw new AiException(AiException.ErrorType.UNKNOWN, "Gemini query failed: " + e.getMessage(), e);
        }
    }

    private ProcessBuilder buildProcess(AiProfile profile, String cliPath, String prompt) {
        return switch (profile.getProvider()) {
            case CODEX -> {
                java.util.List<String> args = new java.util.ArrayList<>();
                args.add(cliPath);
                String codexArgs = profile.getArgs();
                if (codexArgs != null && !codexArgs.isBlank()) {
                    args.addAll(java.util.Arrays.asList(codexArgs.trim().split("\\s+")));
                }
                args.add(prompt);
                yield new ProcessBuilder(args);
            }
            case CUSTOM -> {
                String cmd = profile.getCommand();
                if (cmd == null || cmd.isBlank()) {
                    yield new ProcessBuilder(cliPath, prompt);
                }
                String[] parts = cmd.trim().split("\\s+");
                java.util.List<String> args = new java.util.ArrayList<>(java.util.Arrays.asList(parts));
                args.add(prompt);
                yield new ProcessBuilder(args);
            }
            default -> {
                // Claude - use configurable args
                java.util.List<String> args = new java.util.ArrayList<>();
                args.add(cliPath);
                String claudeArgs = profile.getArgs();
                if (claudeArgs != null && !claudeArgs.isBlank()) {
                    args.addAll(java.util.Arrays.asList(claudeArgs.trim().split("\\s+")));
                }
                yield new ProcessBuilder(args);
            }
        };
    }

    private boolean usesStdin(AiProfile profile) {
        return profile.getProvider() == IntelConfig.AiProvider.CLAUDE;
    }

    private AiException parseError(String output, String provider, String cliPath) {
        String lower = output.toLowerCase();

        if (lower.contains("log in") || lower.contains("login") || lower.contains("authenticate")) {
            return new AiException(AiException.ErrorType.NOT_LOGGED_IN,
                "Not logged in - run '" + cliPath + "' in terminal to authenticate");
        }

        if (lower.contains("api key") || lower.contains("apikey")) {
            return new AiException(AiException.ErrorType.API_KEY_MISSING,
                "API key missing or invalid");
        }

        return new AiException(AiException.ErrorType.UNKNOWN,
            provider + " CLI failed: " + truncate(output, 100));
    }

    private void logActivityWithDetail(String summary, String prompt, String response) {
        if (logCallback != null) {
            logCallback.accept(summary);
        }
        IntelLogPanel.logAI(summary, prompt, response);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
