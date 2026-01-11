package com.tradery.ui.theme;

import com.formdev.flatlaf.*;

import javax.swing.*;
import java.awt.Window;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages theme selection and persistence.
 * Supports both FlatLaf L&F themes and chart color themes.
 */
public class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();

    // Chart color themes (keyed by FlatLaf theme name)
    private final Map<String, Theme> chartThemes = new LinkedHashMap<>();

    // FlatLaf L&F themes
    private final Map<String, String> flatLafThemes = new LinkedHashMap<>();

    private Theme currentChartTheme;
    private String currentFlatLafTheme;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Path configPath;

    private ThemeManager() {
        // Register FlatLaf L&F themes
        // Core FlatLaf themes
        flatLafThemes.put("Hiberbee Dark", "com.formdev.flatlaf.FlatDarkLaf");
        flatLafThemes.put("Flat Light", "com.formdev.flatlaf.FlatLightLaf");
        flatLafThemes.put("Flat IntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf");
        flatLafThemes.put("Flat Darcula", "com.formdev.flatlaf.FlatDarculaLaf");
        flatLafThemes.put("macOS Dark", "com.formdev.flatlaf.themes.FlatMacDarkLaf");
        flatLafThemes.put("macOS Light", "com.formdev.flatlaf.themes.FlatMacLightLaf");

        // IntelliJ themes - Dark
        flatLafThemes.put("Arc Dark", "com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme");
        flatLafThemes.put("Arc Dark Orange", "com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme");
        flatLafThemes.put("Carbon", "com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme");
        flatLafThemes.put("Cobalt 2", "com.formdev.flatlaf.intellijthemes.FlatCobalt2IJTheme");
        flatLafThemes.put("Cyan Light", "com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme");
        flatLafThemes.put("Dark Flat", "com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme");
        flatLafThemes.put("Dracula", "com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme");
        flatLafThemes.put("Gradianto Dark Fuchsia", "com.formdev.flatlaf.intellijthemes.FlatGradiantoDarkFuchsiaIJTheme");
        flatLafThemes.put("Gradianto Deep Ocean", "com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme");
        flatLafThemes.put("Gradianto Midnight Blue", "com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme");
        flatLafThemes.put("Gruvbox Dark Hard", "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme");
        flatLafThemes.put("Hiberbee Dark", "com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme");
        flatLafThemes.put("High Contrast", "com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme");
        flatLafThemes.put("Material Darker", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialDarkerIJTheme");
        flatLafThemes.put("Material Deep Ocean", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialDeepOceanIJTheme");
        flatLafThemes.put("Material Oceanic", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialOceanicIJTheme");
        flatLafThemes.put("Monokai Pro", "com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme");
        flatLafThemes.put("Nord", "com.formdev.flatlaf.intellijthemes.FlatNordIJTheme");
        flatLafThemes.put("One Dark", "com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme");
        flatLafThemes.put("Solarized Dark", "com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme");
        flatLafThemes.put("Spacegray", "com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme");
        flatLafThemes.put("Vuesion", "com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme");

        // IntelliJ themes - Light
        flatLafThemes.put("Arc", "com.formdev.flatlaf.intellijthemes.FlatArcIJTheme");
        flatLafThemes.put("Arc Orange", "com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme");
        flatLafThemes.put("Gruvbox Light Hard", "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkSoftIJTheme");
        flatLafThemes.put("Material Lighter", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialLighterIJTheme");
        flatLafThemes.put("Solarized Light", "com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme");

        // Register chart color themes (map FlatLaf theme to chart theme)
        Theme darkChartTheme = new DarkTheme();
        Theme lightChartTheme = new LightTheme();

        // Core themes
        chartThemes.put("Hiberbee Dark", darkChartTheme);
        chartThemes.put("Flat Light", lightChartTheme);
        chartThemes.put("Flat IntelliJ", lightChartTheme);
        chartThemes.put("Flat Darcula", darkChartTheme);
        chartThemes.put("macOS Dark", darkChartTheme);
        chartThemes.put("macOS Light", lightChartTheme);

        // Map all dark IntelliJ themes to dark chart theme
        for (String name : List.of("Arc Dark", "Arc Dark Orange", "Carbon", "Cobalt 2", "Dark Flat",
                "Dracula", "Gradianto Dark Fuchsia", "Gradianto Deep Ocean", "Gradianto Midnight Blue",
                "Gruvbox Dark Hard", "Hiberbee Dark", "High Contrast", "Material Darker",
                "Material Deep Ocean", "Material Oceanic", "Monokai Pro", "Nord", "One Dark",
                "Solarized Dark", "Spacegray", "Vuesion")) {
            chartThemes.put(name, darkChartTheme);
        }

        // Map all light IntelliJ themes to light chart theme
        for (String name : List.of("Arc", "Arc Orange", "Cyan Light", "Gruvbox Light Hard",
                "Material Lighter", "Solarized Light")) {
            chartThemes.put(name, lightChartTheme);
        }

        // Config file path
        String home = System.getProperty("user.home");
        configPath = Paths.get(home, ".tradery", "theme.txt");

        // Load saved theme or default to Hiberbee Dark
        String savedTheme = loadSavedTheme();
        if (!flatLafThemes.containsKey(savedTheme)) {
            savedTheme = "Hiberbee Dark";
        }
        currentFlatLafTheme = savedTheme;
        currentChartTheme = chartThemes.getOrDefault(savedTheme, chartThemes.get("Hiberbee Dark"));
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    /**
     * Get the current chart color theme.
     */
    public Theme getCurrentTheme() {
        return currentChartTheme;
    }

    /**
     * Get the current FlatLaf theme name.
     */
    public String getCurrentThemeName() {
        return currentFlatLafTheme;
    }

    /**
     * Get all available theme names.
     */
    public Collection<String> getAvailableThemeNames() {
        return flatLafThemes.keySet();
    }

    /**
     * @deprecated Use getAvailableThemeNames() instead
     */
    @Deprecated
    public Collection<Theme> getAvailableThemes() {
        return chartThemes.values();
    }

    /**
     * Set theme by name - switches both FlatLaf L&F and chart colors.
     */
    public void setTheme(String themeName) {
        if (!flatLafThemes.containsKey(themeName)) {
            return;
        }
        if (themeName.equals(currentFlatLafTheme)) {
            return;
        }

        currentFlatLafTheme = themeName;
        currentChartTheme = chartThemes.getOrDefault(themeName, chartThemes.get("Hiberbee Dark"));
        saveTheme(themeName);

        // Apply FlatLaf L&F
        applyFlatLafTheme(themeName);

        // Notify listeners (for chart refreshes)
        notifyListeners();
    }

    /**
     * Apply the FlatLaf Look and Feel and update all windows.
     */
    private void applyFlatLafTheme(String themeName) {
        String className = flatLafThemes.get(themeName);
        if (className == null) {
            return;
        }

        try {
            UIManager.setLookAndFeel(className);

            // Update all open windows
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
        } catch (Exception e) {
            System.err.println("Failed to apply theme " + themeName + ": " + e.getMessage());
        }
    }

    /**
     * Apply the saved theme at startup (call from TraderyApp).
     */
    public void applyCurrentTheme() {
        applyFlatLafTheme(currentFlatLafTheme);
    }

    /**
     * Check if current theme is a dark theme.
     */
    public boolean isDarkTheme() {
        return currentFlatLafTheme.toLowerCase().contains("dark") ||
               currentFlatLafTheme.toLowerCase().contains("darcula");
    }

    public void setTheme(Theme theme) {
        // Legacy support - find matching FlatLaf theme
        String name = theme.getName();
        if ("Dark".equals(name)) {
            setTheme("Hiberbee Dark");
        } else if ("Light".equals(name)) {
            setTheme("Flat Light");
        }
    }

    public void addThemeChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeThemeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private String loadSavedTheme() {
        try {
            if (Files.exists(configPath)) {
                return Files.readString(configPath).trim();
            }
        } catch (IOException e) {
            // Ignore, use default
        }
        return "Hiberbee Dark";
    }

    private void saveTheme(String themeName) {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, themeName);
        } catch (IOException e) {
            // Ignore save errors
        }
    }

    // ===== Convenience accessors for current theme colors =====

    public static Theme theme() {
        return INSTANCE.currentChartTheme;
    }
}
