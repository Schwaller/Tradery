package com.tradery.ai;

/**
 * Environment utilities for AI CLI execution.
 * macOS .app bundles get a minimal PATH (/usr/bin:/bin:/usr/sbin:/sbin),
 * so we expand it to include common CLI install locations.
 */
public class AiEnvironment {

    private static String expandedPath;

    /**
     * Get a PATH that includes common CLI install locations.
     * Call {@link #applyToProcess(ProcessBuilder)} to set it on a ProcessBuilder.
     */
    public static String getExpandedPath() {
        if (expandedPath == null) {
            expandedPath = buildExpandedPath();
        }
        return expandedPath;
    }

    /**
     * Apply the expanded PATH to a ProcessBuilder so CLI tools can be found.
     */
    public static void applyToProcess(ProcessBuilder pb) {
        pb.environment().put("PATH", getExpandedPath());
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
