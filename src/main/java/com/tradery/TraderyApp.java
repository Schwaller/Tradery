package com.tradery;

import com.formdev.flatlaf.FlatDarkLaf;
import com.tradery.io.AppLock;
import com.tradery.ui.LauncherFrame;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

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

    public static void main(String[] args) {
        // Set FlatLaf macOS theme
        try {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", APP_NAME);
            FlatDarkLaf.setup();
        } catch (Exception e) {
            System.err.println("Could not set FlatLaf look and feel: " + e.getMessage());
        }

        // Set application dock icon (macOS)
//        setApplicationIcon();

        // Ensure user data directory exists
        ensureUserDirectories();

        // Ensure Claude integration files exist
        ensureClaudeFiles();

        // Check for existing instance and acquire lock
        AppLock appLock = AppLock.getInstance();
        if (appLock.isAnotherInstanceRunning()) {
            JOptionPane.showMessageDialog(null,
                "Tradery is already running.\n\nCheck your running applications.",
                "Already Running",
                JOptionPane.WARNING_MESSAGE);
            System.exit(0);
        }
        appLock.acquire();

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
     * Create AI assistant integration files (CLAUDE.md, CODEX.md, AGENTS.md)
     */
    private static void ensureClaudeFiles() {
        // Copy instruction files from resources if they don't exist
        String[] files = {"CLAUDE.md", "CODEX.md", "AGENTS.md"};
        for (String filename : files) {
            File target = new File(USER_DIR, filename);
            if (!target.exists()) {
                try (InputStream is = TraderyApp.class.getResourceAsStream("/" + filename)) {
                    if (is != null) {
                        Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("Created: " + target.getAbsolutePath());
                    }
                } catch (IOException e) {
                    System.err.println("Could not create " + filename + ": " + e.getMessage());
                }
            }
        }
    }
}
