package com.tradery.ui.theme;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages theme selection and persistence.
 */
public class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();

    private final Map<String, Theme> availableThemes = new LinkedHashMap<>();
    private Theme currentTheme;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Path configPath;

    private ThemeManager() {
        // Register available themes
        availableThemes.put("Dark", new DarkTheme());
        availableThemes.put("Light", new LightTheme());

        // Config file path
        String home = System.getProperty("user.home");
        configPath = Paths.get(home, ".tradery", "theme.txt");

        // Load saved theme or default to Dark
        String savedTheme = loadSavedTheme();
        currentTheme = availableThemes.getOrDefault(savedTheme, availableThemes.get("Dark"));
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public Collection<Theme> getAvailableThemes() {
        return availableThemes.values();
    }

    public void setTheme(String themeName) {
        Theme theme = availableThemes.get(themeName);
        if (theme != null && theme != currentTheme) {
            currentTheme = theme;
            saveTheme(themeName);
            notifyListeners();
        }
    }

    public void setTheme(Theme theme) {
        if (theme != null && theme != currentTheme) {
            currentTheme = theme;
            saveTheme(theme.getName());
            notifyListeners();
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
        return "Dark";
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
        return INSTANCE.currentTheme;
    }
}
