package com.tradery.forge.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tradery.forge.TraderyApp;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists window positions and sizes to disk.
 * Stores which project windows were open for restoration on restart.
 */
public class WindowStateStore {

    private static final File STATE_FILE = new File(TraderyApp.USER_DIR, "window-state.json");
    private static final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static WindowStateStore instance;

    private WindowState state;

    private WindowStateStore() {
        load();
    }

    public static synchronized WindowStateStore getInstance() {
        if (instance == null) {
            instance = new WindowStateStore();
        }
        return instance;
    }

    /**
     * Load state from disk
     */
    private void load() {
        if (STATE_FILE.exists()) {
            try {
                state = mapper.readValue(STATE_FILE, WindowState.class);
            } catch (IOException e) {
                System.err.println("Failed to load window state: " + e.getMessage());
                state = new WindowState();
            }
        } else {
            state = new WindowState();
        }
    }

    /**
     * Save state to disk
     */
    public void save() {
        try {
            mapper.writeValue(STATE_FILE, state);
        } catch (IOException e) {
            System.err.println("Failed to save window state: " + e.getMessage());
        }
    }

    /**
     * Save launcher window position
     */
    public void saveLauncherBounds(Rectangle bounds) {
        state.launcherX = bounds.x;
        state.launcherY = bounds.y;
        state.launcherWidth = bounds.width;
        state.launcherHeight = bounds.height;
        save();
    }

    /**
     * Get launcher window bounds (or null if not saved)
     */
    public Rectangle getLauncherBounds() {
        if (state.launcherWidth > 0 && state.launcherHeight > 0) {
            return new Rectangle(state.launcherX, state.launcherY,
                                 state.launcherWidth, state.launcherHeight);
        }
        return null;
    }

    /**
     * Save project window position
     */
    public void saveProjectBounds(String strategyId, Rectangle bounds) {
        ProjectWindowState pws = state.projectWindows.computeIfAbsent(
            strategyId, k -> new ProjectWindowState());
        pws.x = bounds.x;
        pws.y = bounds.y;
        pws.width = bounds.width;
        pws.height = bounds.height;
        save();
    }

    /**
     * Get project window bounds (or null if not saved)
     */
    public Rectangle getProjectBounds(String strategyId) {
        ProjectWindowState pws = state.projectWindows.get(strategyId);
        if (pws != null && pws.width > 0 && pws.height > 0) {
            return new Rectangle(pws.x, pws.y, pws.width, pws.height);
        }
        return null;
    }

    /**
     * Mark a project window as open
     */
    public void setProjectOpen(String strategyId, boolean open) {
        ProjectWindowState pws = state.projectWindows.computeIfAbsent(
            strategyId, k -> new ProjectWindowState());
        pws.wasOpen = open;
        save();
    }

    /**
     * Get list of strategy IDs that were open when app last closed
     */
    public List<String> getOpenProjectIds() {
        List<String> openIds = new ArrayList<>();
        for (Map.Entry<String, ProjectWindowState> entry : state.projectWindows.entrySet()) {
            if (entry.getValue().wasOpen) {
                openIds.add(entry.getKey());
            }
        }
        return openIds;
    }

    /**
     * Get AI terminal mode preference.
     * @return "integrated" or "external"
     */
    public String getAiTerminalMode() {
        return state.aiTerminalMode != null ? state.aiTerminalMode : "integrated";
    }

    /**
     * Set AI terminal mode preference.
     * @param mode "integrated" or "external"
     */
    public void setAiTerminalMode(String mode) {
        state.aiTerminalMode = mode;
        save();
    }

    /**
     * Root state object
     */
    public static class WindowState {
        public int launcherX;
        public int launcherY;
        public int launcherWidth;
        public int launcherHeight;
        public String aiTerminalMode;  // "integrated" or "external"
        public Map<String, ProjectWindowState> projectWindows = new HashMap<>();
    }

    /**
     * Per-project window state
     */
    public static class ProjectWindowState {
        public int x;
        public int y;
        public int width;
        public int height;
        public boolean wasOpen;
    }
}
