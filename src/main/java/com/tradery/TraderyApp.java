package com.tradery;

import com.tradery.ui.MainFrame;

import javax.swing.*;
import java.awt.*;
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
        // Set native look and feel (macOS Aqua)
        try {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", APP_NAME);
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set native look and feel: " + e.getMessage());
        }

        // Ensure user data directory exists
        ensureUserDirectories();

        // Launch UI on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    /**
     * Create the ~/.tradery/ directory structure
     */
    private static void ensureUserDirectories() {
        File[] dirs = {
            USER_DIR,
            new File(USER_DIR, "strategies"),
            new File(USER_DIR, "results"),
            new File(USER_DIR, "results/history"),
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
