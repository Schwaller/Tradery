package com.tradery.news.ai;

import com.tradery.news.ui.IntelConfig;
import com.tradery.news.ui.IntelLogPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Centralized AI client that handles all CLI interactions for Claude and Codex.
 * Uses IntelConfig for provider selection, paths, and timeout settings.
 */
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private static AiClient instance;

    private Consumer<String> logCallback;

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
        return getConfig().getAiProvider().name();
    }

    /**
     * Check if the configured AI CLI is available.
     */
    public boolean isAvailable() {
        try {
            String cliPath = getCliPath();
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
     * Get the version string from the CLI.
     */
    public String getVersion() throws AiException {
        try {
            String cliPath = getCliPath();
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
     * Execute a query and return the raw output.
     *
     * @param prompt The prompt to send to the AI
     * @return The raw response text
     * @throws AiException If the query fails
     */
    public String query(String prompt) throws AiException {
        IntelConfig config = getConfig();
        String cliPath = getCliPath();
        int timeoutSeconds = config.getAiTimeoutSeconds();
        String provider = getProviderName();

        log.debug("AI query to {}: {}", provider, truncate(prompt, 100));

        try {
            ProcessBuilder pb = buildProcess(config, cliPath, prompt);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // For Claude, write prompt to stdin (others pass as argument)
            if (usesStdin()) {
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
     * Execute a query asynchronously.
     *
     * @param prompt     The prompt to send
     * @param onOutput   Called with incremental output (currently entire response on completion)
     * @param onComplete Called when query completes successfully with full response
     * @param onError    Called if the query fails
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
     * Test the AI connection with a simple prompt.
     *
     * @return Test result with success status, version, and message
     */
    public TestResult testConnection() {
        String provider = getProviderName();

        // Step 1: Check version
        String version;
        try {
            version = getVersion();
        } catch (AiException e) {
            return new TestResult(false, null, e.getMessage());
        }

        // Step 2: Test with a simple prompt
        try {
            String response = query("Say OK");
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

    private IntelConfig getConfig() {
        return IntelConfig.get();
    }

    private String getCliPath() {
        IntelConfig config = getConfig();
        return switch (config.getAiProvider()) {
            case CODEX -> config.getCodexPath();
            case CUSTOM -> {
                // For custom, extract the command (first word)
                String cmd = config.getCustomCommand();
                if (cmd == null || cmd.isBlank()) yield "";
                String[] parts = cmd.trim().split("\\s+", 2);
                yield parts[0];
            }
            default -> config.getClaudePath();
        };
    }

    private ProcessBuilder buildProcess(IntelConfig config, String cliPath, String prompt) {
        return switch (config.getAiProvider()) {
            case CODEX -> new ProcessBuilder(
                cliPath,
                "--quiet",
                "--approval-mode", "full-auto",
                prompt
            );
            case CUSTOM -> {
                // Parse custom command and append prompt as argument
                String cmd = config.getCustomCommand();
                if (cmd == null || cmd.isBlank()) {
                    yield new ProcessBuilder(cliPath, prompt);
                }
                String[] parts = cmd.trim().split("\\s+");
                java.util.List<String> args = new java.util.ArrayList<>(java.util.Arrays.asList(parts));
                args.add(prompt);
                yield new ProcessBuilder(args);
            }
            default -> new ProcessBuilder(
                cliPath,
                "--print",
                "--output-format", "text",
                "--model", "haiku"
            );
        };
    }

    /**
     * Check if prompt is sent via stdin (Claude) or as argument (Codex, Custom).
     */
    private boolean usesStdin() {
        return getConfig().getAiProvider() == IntelConfig.AiProvider.CLAUDE;
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

    private void logActivity(String message) {
        // Log to callback if set
        if (logCallback != null) {
            logCallback.accept(message);
        }
        // Also log to IntelLogPanel static method
        IntelLogPanel.logAI(message);
    }

    private void logActivityWithDetail(String summary, String prompt, String response) {
        // Log to callback if set (summary only)
        if (logCallback != null) {
            logCallback.accept(summary);
        }
        // Log to IntelLogPanel with full details
        IntelLogPanel.logAI(summary, prompt, response);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
