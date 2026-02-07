package com.tradery.forge.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

/**
 * Manages FlatLaf theme selection and persistence.
 * Chart colors are handled by DarkChartTheme in tradery-charts which auto-adapts
 * to light/dark based on UIManager colors set by the active FlatLaf theme.
 */
public class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();

    // FlatLaf L&F themes
    private final Map<String, String> flatLafThemes = new LinkedHashMap<>();

    private String currentFlatLafTheme;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Path configPath;

    private ThemeManager() {
        // Register FlatLaf L&F themes
        // Core FlatLaf themes
        flatLafThemes.put("Hiberbee Dark", "com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme");
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

        // Config file path
        String home = System.getProperty("user.home");
        configPath = Paths.get(home, ".tradery", "theme.txt");

        // Load saved theme or default to Hiberbee Dark
        String savedTheme = loadSavedTheme();
        if (!flatLafThemes.containsKey(savedTheme)) {
            savedTheme = "Hiberbee Dark";
        }
        currentFlatLafTheme = savedTheme;
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
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
     * Set theme by name - switches FlatLaf L&F and notifies listeners.
     * Chart colors auto-adapt via DarkChartTheme reading UIManager.
     */
    public void setTheme(String themeName) {
        if (!flatLafThemes.containsKey(themeName)) {
            return;
        }
        if (themeName.equals(currentFlatLafTheme)) {
            return;
        }

        currentFlatLafTheme = themeName;
        saveTheme(themeName);

        // Apply FlatLaf L&F (this updates UIManager colors)
        applyFlatLafTheme(themeName);

        // Notify listeners (charts re-read colors from DarkChartTheme which reads UIManager)
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
     * Apply the saved theme at startup.
     */
    public void applyCurrentTheme() {
        applyFlatLafTheme(currentFlatLafTheme);
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
}
