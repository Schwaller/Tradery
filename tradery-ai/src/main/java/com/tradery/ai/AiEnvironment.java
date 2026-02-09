package com.tradery.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Environment utilities for AI CLI execution.
 * macOS .app bundles get a minimal PATH (/usr/bin:/bin:/usr/sbin:/sbin),
 * so we resolve CLI paths via a login shell to pick up the user's full environment.
 */
public class AiEnvironment {

    private static final Logger log = LoggerFactory.getLogger(AiEnvironment.class);
    private static final Map<String, String> resolvedPaths = new ConcurrentHashMap<>();
    private static String expandedPath;

    /**
     * Resolve the absolute path of a CLI tool using login shell.
     * Caches results so each tool is resolved only once.
     *
     * @return absolute path (e.g., "/opt/homebrew/bin/claude") or the original name if unresolvable
     */
    public static String resolve(String command) {
        return resolvedPaths.computeIfAbsent(command, cmd -> {
            // Try login shell which reads user's .zprofile/.zshrc
            String path = shellWhich(cmd);
            if (path != null) {
                log.info("Resolved CLI '{}' -> {}", cmd, path);
                return path;
            }
            // Fallback: check common locations directly
            path = probeKnownPaths(cmd);
            if (path != null) {
                log.info("Found CLI '{}' at {}", cmd, path);
                return path;
            }
            log.debug("CLI '{}' not found", cmd);
            return cmd; // Return original name as fallback
        });
    }

    /**
     * Get expanded PATH for ProcessBuilder environment.
     */
    public static String getExpandedPath() {
        if (expandedPath == null) {
            expandedPath = buildExpandedPath();
        }
        return expandedPath;
    }

    /**
     * Apply expanded PATH to a ProcessBuilder.
     */
    public static void applyToProcess(ProcessBuilder pb) {
        pb.environment().put("PATH", getExpandedPath());
    }

    /**
     * Use login shell to resolve a command's absolute path.
     * This picks up the user's full environment (homebrew, nvm, etc.)
     */
    private static String shellWhich(String command) {
        try {
            // Use login shell (-l) to get full user PATH
            String shell = System.getenv("SHELL");
            if (shell == null || shell.isBlank()) shell = "/bin/zsh";

            ProcessBuilder pb = new ProcessBuilder(shell, "-lc", "which " + command);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                output = reader.readLine();
            }

            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }

            if (p.exitValue() == 0 && output != null && output.startsWith("/")) {
                return output.trim();
            }
        } catch (Exception e) {
            log.debug("shellWhich('{}') failed: {}", command, e.getMessage());
        }
        return null;
    }

    /**
     * Check common installation paths directly (no shell needed).
     */
    private static String probeKnownPaths(String command) {
        String home = System.getProperty("user.home");
        String[] dirs = {
            "/opt/homebrew/bin",
            "/usr/local/bin",
            home + "/.local/bin",
            home + "/.npm-global/bin",
            home + "/.nvm/current/bin",
            home + "/.cargo/bin",
        };
        for (String dir : dirs) {
            java.io.File f = new java.io.File(dir, command);
            if (f.isFile() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    private static String buildExpandedPath() {
        String home = System.getProperty("user.home");
        String basePath = System.getenv("PATH");
        if (basePath == null) basePath = "/usr/bin:/bin:/usr/sbin:/sbin";

        String[] extraDirs = {
            "/usr/local/bin",
            "/opt/homebrew/bin",
            "/opt/homebrew/sbin",
            home + "/.local/bin",
            home + "/.npm-global/bin",
            home + "/.nvm/current/bin",
            "/usr/local/lib/node_modules/.bin",
            home + "/.cargo/bin",
        };

        StringBuilder path = new StringBuilder(basePath);
        for (String dir : extraDirs) {
            if (!basePath.contains(dir)) {
                path.append(":").append(dir);
            }
        }
        return path.toString();
    }
}
