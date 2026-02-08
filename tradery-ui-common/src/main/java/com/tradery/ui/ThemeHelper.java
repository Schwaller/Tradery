package com.tradery.ui;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Shared theme management across all Plaiiin apps.
 * Persists theme choice in ~/.tradery/theme.txt (shared file).
 * Uses string-based class loading so intellij-themes jar is only needed at runtime.
 */
public class ThemeHelper {

    private static final Path THEME_PATH = Path.of(
        System.getProperty("user.home"), ".tradery", "theme.txt"
    );

    private static final List<Runnable> listeners = new ArrayList<>();

    private static final Map<String, String> THEMES = new LinkedHashMap<>();
    static {
        THEMES.put("Hiberbee Dark", "com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme");
        THEMES.put("Flat Dark", "com.formdev.flatlaf.FlatDarkLaf");
        THEMES.put("Flat Light", "com.formdev.flatlaf.FlatLightLaf");
        THEMES.put("Flat Darcula", "com.formdev.flatlaf.FlatDarculaLaf");
        THEMES.put("macOS Dark", "com.formdev.flatlaf.themes.FlatMacDarkLaf");
        THEMES.put("macOS Light", "com.formdev.flatlaf.themes.FlatMacLightLaf");
        THEMES.put("Arc Dark", "com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme");
        THEMES.put("Dracula", "com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme");
        THEMES.put("Nord", "com.formdev.flatlaf.intellijthemes.FlatNordIJTheme");
        THEMES.put("One Dark", "com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme");
        THEMES.put("Monokai Pro", "com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme");
        THEMES.put("Solarized Dark", "com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme");
        THEMES.put("Solarized Light", "com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme");
        THEMES.put("Gruvbox Dark", "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme");
        THEMES.put("Material Oceanic", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialOceanicIJTheme");
        THEMES.put("Carbon", "com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme");
    }

    public static List<String> getAvailableThemes() {
        return new ArrayList<>(THEMES.keySet());
    }

    public static String getCurrentTheme() {
        try {
            if (Files.exists(THEME_PATH)) {
                String theme = Files.readString(THEME_PATH).trim();
                if (THEMES.containsKey(theme)) {
                    return theme;
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return "Hiberbee Dark";
    }

    /**
     * Set and apply a theme. Persists to shared theme.txt.
     */
    public static void setTheme(String themeName) {
        if (!THEMES.containsKey(themeName)) return;

        try {
            Files.createDirectories(THEME_PATH.getParent());
            Files.writeString(THEME_PATH, themeName);
        } catch (IOException e) {
            System.err.println("Failed to save theme: " + e.getMessage());
        }

        applyTheme(themeName);
    }

    /**
     * Apply a theme to the current UI without persisting.
     */
    public static void applyTheme(String themeName) {
        String className = THEMES.get(themeName);
        if (className == null) return;

        try {
            UIManager.setLookAndFeel(className);
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
            notifyListeners();
        } catch (Exception e) {
            System.err.println("Failed to apply theme: " + e.getMessage());
        }
    }

    public static void addThemeChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void removeThemeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    /**
     * Apply the saved theme (call on startup).
     */
    public static void applyCurrentTheme() {
        applyTheme(getCurrentTheme());
    }
}
