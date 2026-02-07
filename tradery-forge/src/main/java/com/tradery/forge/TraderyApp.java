package com.tradery.forge;

import com.tradery.dataclient.DataServiceLauncher;
import com.tradery.forge.io.AppLock;
import com.tradery.forge.mcp.McpServerSetup;
import com.tradery.forge.ui.LauncherFrame;
import com.tradery.forge.ui.theme.ThemeManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Tradery - Java Desktop Trading Strategy Backtester
 *
 * File-based architecture for seamless Claude Code integration.
 * All data stored in ~/.tradery/ as plain JSON/CSV files.
 */
public class TraderyApp {

    public static final String APP_NAME = "Tradery";
    public static final String VERSION = "1.0.0";
    public static final File USER_DIR = new File(System.getProperty("user.home"), ".tradery");

    // Exit codes
    public static final int EXIT_CODE_ALREADY_RUNNING = 42;

    // Data service launcher for shared data access
    private static DataServiceLauncher dataServiceLauncher;

    public static void main(String[] args) {
        // Set FlatLaf theme from saved preference
        try {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", APP_NAME);
            ThemeManager.getInstance().applyCurrentTheme();
        } catch (Exception e) {
            System.err.println("Could not set FlatLaf look and feel: " + e.getMessage());
        }

        // Set application dock icon (macOS)
//        setApplicationIcon();

        // Ensure user data directory exists
        ensureUserDirectories();

        // Ensure Claude integration files exist
        ensureClaudeFiles();

        // Ensure MCP server is installed for Claude Code integration
        McpServerSetup.ensureInstalled();

        // Check for existing instance and acquire lock
        AppLock appLock = AppLock.getInstance();
        if (appLock.isAnotherInstanceRunning()) {
            JOptionPane.showMessageDialog(null,
                "Tradery is already running.\n\nCheck your running applications.",
                "Already Running",
                JOptionPane.WARNING_MESSAGE);
            System.exit(EXIT_CODE_ALREADY_RUNNING);
        }
        appLock.acquire();

        // Start data service (or connect to existing) and register as consumer
        startDataService();

        // Register shutdown hook to cleanly unregister from data service
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (dataServiceLauncher != null) {
                System.out.println("Shutting down data service connection...");
                dataServiceLauncher.shutdown();
            }
        }, "DataService-Shutdown"));

        // Initialize shared application context
        ApplicationContext.getInstance();

        // Launch UI on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            LauncherFrame launcher = new LauncherFrame();
            launcher.setVisible(true);
        });
    }

    /**
     * Set the application dock icon for macOS
     */
    private static void setApplicationIcon() {
        try {
            InputStream iconStream = TraderyApp.class.getResourceAsStream("/icons/application-icon.png");
            if (iconStream != null) {
                Image icon = ImageIO.read(iconStream);
                iconStream.close();

                // Use Taskbar API for macOS dock icon (Java 9+)
                if (Taskbar.isTaskbarSupported()) {
                    Taskbar taskbar = Taskbar.getTaskbar();
                    if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                        taskbar.setIconImage(icon);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not set application icon: " + e.getMessage());
        }
    }

    /**
     * Create the ~/.tradery/ directory structure
     */
    private static void ensureUserDirectories() {
        File[] dirs = {
            USER_DIR,
            new File(USER_DIR, "strategies"),
            new File(USER_DIR, "phases"),
            new File(USER_DIR, "data")
        };

        for (File dir : dirs) {
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (created) {
                    System.out.println("Created directory: " + dir.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Create/update AI assistant integration files (CLAUDE.md, CODEX.md, AGENTS.md, STRATEGY_GUIDE.md).
     * Always overwrites existing files to ensure they stay up-to-date with the latest version.
     */
    private static void ensureClaudeFiles() {
        // Always copy instruction files from resources (overwrite existing to keep up-to-date)
        String[] files = {"CLAUDE.md", "CODEX.md", "AGENTS.md", "STRATEGY_GUIDE.md", "DSL_REFERENCE.md"};
        for (String filename : files) {
            File target = new File(USER_DIR, filename);
            try (InputStream is = TraderyApp.class.getResourceAsStream("/" + filename)) {
                if (is != null) {
                    Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                System.err.println("Could not create " + filename + ": " + e.getMessage());
            }
        }
    }

    /**
     * Start the data service (or connect to existing instance).
     * The data service handles data fetching/caching and is shared between apps.
     * It will auto-shutdown when no apps are using it.
     */
    private static void startDataService() {
        try {
            dataServiceLauncher = new DataServiceLauncher("StrategyDesigner");
            int port = dataServiceLauncher.ensureRunning();
            System.out.println("Connected to data service on port " + port);
        } catch (IOException e) {
            System.err.println("Warning: Could not start data service: " + e.getMessage());
            System.err.println("The app will continue but some features may be limited.");
            // Don't fail startup - the app can still work with direct data access
        }
    }

    /**
     * Get the data service launcher instance.
     * @return the launcher, or null if data service is not available
     */
    public static DataServiceLauncher getDataServiceLauncher() {
        return dataServiceLauncher;
    }
}
