package com.tradery;

import com.formdev.flatlaf.FlatDarkLaf;
import com.tradery.ui.LauncherFrame;

import javax.swing.*;
import java.io.File;

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

        // Ensure user data directory exists
        ensureUserDirectories();

        // Initialize shared application context
        ApplicationContext.getInstance();

        // Launch UI on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            LauncherFrame launcher = new LauncherFrame();
            launcher.setVisible(true);
        });
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
}
