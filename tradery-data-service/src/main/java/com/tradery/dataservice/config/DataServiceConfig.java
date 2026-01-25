package com.tradery.dataservice.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for the Tradery Data Service.
 */
public class DataServiceConfig {
    private static final String DEFAULT_DATA_DIR = System.getProperty("user.home") + "/.tradery";
    private static final int DEFAULT_PORT = 9810;
    private static final String PORT_FILE_NAME = "dataservice.port";

    private final int port;
    private final Path dataDir;
    private final int maxConcurrentDownloads;
    private final long maxMemoryMb;

    public DataServiceConfig(int port, Path dataDir, int maxConcurrentDownloads, long maxMemoryMb) {
        this.port = port;
        this.dataDir = dataDir;
        this.maxConcurrentDownloads = maxConcurrentDownloads;
        this.maxMemoryMb = maxMemoryMb;
    }

    public static DataServiceConfig load() {
        // Load from environment or system properties, with sensible defaults
        int port = Integer.parseInt(System.getProperty("tradery.data.port",
            System.getenv().getOrDefault("TRADERY_DATA_PORT", String.valueOf(DEFAULT_PORT))));

        String dataDirStr = System.getProperty("tradery.data.dir",
            System.getenv().getOrDefault("TRADERY_DATA_DIR", DEFAULT_DATA_DIR));
        Path dataDir = Paths.get(dataDirStr);

        int maxDownloads = Integer.parseInt(System.getProperty("tradery.data.max_downloads",
            System.getenv().getOrDefault("TRADERY_MAX_DOWNLOADS", "3")));

        long maxMemory = Long.parseLong(System.getProperty("tradery.data.max_memory_mb",
            System.getenv().getOrDefault("TRADERY_MAX_MEMORY_MB", "2048")));

        return new DataServiceConfig(port, dataDir, maxDownloads, maxMemory);
    }

    public int getPort() {
        return port;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getDatabasePath() {
        return dataDir.resolve("data").resolve("tradery.db");
    }

    public Path getAggTradesDir() {
        return dataDir.resolve("aggtrades");
    }

    public int getMaxConcurrentDownloads() {
        return maxConcurrentDownloads;
    }

    public long getMaxMemoryMb() {
        return maxMemoryMb;
    }

    public static Path getPortFilePath() {
        return Paths.get(DEFAULT_DATA_DIR).resolve(PORT_FILE_NAME);
    }

    public static Path getTraderyDir() {
        return Paths.get(DEFAULT_DATA_DIR);
    }
}
