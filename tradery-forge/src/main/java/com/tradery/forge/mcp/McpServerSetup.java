package com.tradery.forge.mcp;

import java.io.*;
import java.nio.file.*;

/**
 * Handles automatic setup of the Tradery MCP server for Claude Code integration.
 * On startup, ensures the MCP server files are installed and up to date.
 */
public class McpServerSetup {

    private static final String MCP_VERSION = "1.3.0";
    private static final Path MCP_DIR = Paths.get(System.getProperty("user.home"), ".tradery", "mcp-server");
    private static final Path VERSION_FILE = MCP_DIR.resolve(".version");

    /**
     * Ensure MCP server is installed and up to date.
     * Called on app startup.
     */
    public static void ensureInstalled() {
        try {
            if (needsInstall()) {
                install();
            } else {
                // Even if MCP server is already installed, ensure config file exists
                ensureMcpConfig();
            }
        } catch (Exception e) {
            System.err.println("Failed to setup MCP server: " + e.getMessage());
            // Non-fatal - app continues without MCP
        }
    }

    private static void ensureMcpConfig() throws IOException {
        Path traderyDir = Paths.get(System.getProperty("user.home"), ".tradery");
        Path mcpConfigPath = traderyDir.resolve(".mcp.json");
        if (!Files.exists(mcpConfigPath)) {
            writeMcpConfig();
        }
    }

    private static boolean needsInstall() {
        if (!Files.exists(MCP_DIR)) {
            return true;
        }
        if (!Files.exists(VERSION_FILE)) {
            return true;
        }
        try {
            String installed = Files.readString(VERSION_FILE).trim();
            return !MCP_VERSION.equals(installed);
        } catch (IOException e) {
            return true;
        }
    }

    private static void install() throws IOException, InterruptedException {
        System.out.println("Installing MCP server v" + MCP_VERSION + "...");

        // Create directory
        Files.createDirectories(MCP_DIR);

        // Copy files from resources
        copyResource("mcp-server/package.json", MCP_DIR.resolve("package.json"));
        copyResource("mcp-server/index.js", MCP_DIR.resolve("index.js"));

        // Run npm install
        System.out.println("Running npm install...");
        ProcessBuilder pb = new ProcessBuilder("npm", "install")
                .directory(MCP_DIR.toFile())
                .redirectErrorStream(true);

        Process process = pb.start();

        // Consume output to prevent blocking
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Optionally log: System.out.println("npm: " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("npm install failed with exit code " + exitCode);
        }

        // Write version file
        Files.writeString(VERSION_FILE, MCP_VERSION);

        // Write .mcp.json config in ~/.tradery (where Claude is launched from)
        writeMcpConfig();

        System.out.println("MCP server installed successfully");
    }

    private static void writeMcpConfig() throws IOException {
        Path traderyDir = Paths.get(System.getProperty("user.home"), ".tradery");
        Path mcpConfigPath = traderyDir.resolve(".mcp.json");

        String mcpConfig = """
            {
              "mcpServers": {
                "tradery": {
                  "command": "node",
                  "args": ["%s"]
                }
              }
            }
            """.formatted(getServerPath());

        Files.writeString(mcpConfigPath, mcpConfig);
        System.out.println("Created MCP config: " + mcpConfigPath);
    }

    private static void copyResource(String resourceName, Path target) throws IOException {
        try (InputStream is = McpServerSetup.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourceName);
            }
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Get the path to the MCP server index.js for use in .mcp.json configuration.
     */
    public static String getServerPath() {
        return MCP_DIR.resolve("index.js").toString();
    }

    /**
     * Get example .mcp.json configuration for users.
     */
    public static String getMcpJsonExample() {
        return """
            {
              "mcpServers": {
                "tradery": {
                  "command": "node",
                  "args": ["%s"]
                }
              }
            }
            """.formatted(getServerPath());
    }
}
