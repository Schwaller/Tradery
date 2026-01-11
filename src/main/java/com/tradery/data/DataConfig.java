package com.tradery.data;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages configurable data directory location.
 * Strategies, phases, hoops stay in ~/.tradery/ for AI access.
 * Large data files (candles, aggTrades, funding) can be moved elsewhere.
 */
public class DataConfig {

    private static final DataConfig INSTANCE = new DataConfig();

    private Path configFile; // Lazy initialized
    private File dataDir;
    private final List<Runnable> listeners = new ArrayList<>();

    private DataConfig() {
        dataDir = loadDataDir();
    }

    private Path getConfigFile() {
        if (configFile == null) {
            // Don't use TraderyApp.USER_DIR to avoid circular initialization
            File userDir = new File(System.getProperty("user.home"), ".tradery");
            configFile = Paths.get(userDir.getPath(), "data-location.txt");
        }
        return configFile;
    }

    public static DataConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Get the current data directory for candles, aggTrades, funding.
     */
    public File getDataDir() {
        return dataDir;
    }

    /**
     * Set a new data directory location.
     * Does NOT move existing data - caller should handle migration.
     */
    public void setDataDir(File newDir) {
        if (newDir != null && !newDir.equals(dataDir)) {
            dataDir = newDir;
            saveDataDir();
            notifyListeners();
        }
    }

    /**
     * Get the default data directory (~/.tradery/data)
     */
    public File getDefaultDataDir() {
        // Don't use TraderyApp.USER_DIR to avoid circular initialization
        File userDir = new File(System.getProperty("user.home"), ".tradery");
        return new File(userDir, "data");
    }

    /**
     * Check if using the default location.
     */
    public boolean isDefaultLocation() {
        return dataDir.equals(getDefaultDataDir());
    }

    /**
     * Calculate total size of data directory.
     */
    public long getTotalDataSize() {
        return calculateDirectorySize(dataDir);
    }

    /**
     * Get human-readable size string.
     */
    public String getTotalDataSizeFormatted() {
        return formatSize(getTotalDataSize());
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private File loadDataDir() {
        try {
            Path configPath = getConfigFile();
            if (Files.exists(configPath)) {
                String path = Files.readString(configPath).trim();
                if (!path.isEmpty()) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        return dir;
                    }
                }
            }
        } catch (IOException e) {
            // Ignore, use default
        }
        return getDefaultDataDir();
    }

    private void saveDataDir() {
        try {
            Path configPath = getConfigFile();
            Files.createDirectories(configPath.getParent());
            if (isDefaultLocation()) {
                // Delete config file if using default (cleaner)
                Files.deleteIfExists(configPath);
            } else {
                Files.writeString(configPath, dataDir.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Failed to save data location: " + e.getMessage());
        }
    }

    private long calculateDirectorySize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                }
            }
        }
        return size;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
