package com.tradery.dataservice;

import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.api.DataServiceServer;
import com.tradery.dataservice.config.DataServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Tradery Data Service - standalone daemon for data fetching and caching.
 * Provides HTTP/WebSocket API for Strategy Designer and Local Runner.
 *
 * Lifecycle: Started by apps on demand, shuts down automatically when no consumers remain.
 */
public class DataServiceApp {
    private static final Logger LOG = LoggerFactory.getLogger(DataServiceApp.class);

    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private static DataServiceServer server;
    private static ConsumerRegistry consumerRegistry;
    private static SqliteDataStore dataStore;

    public static void main(String[] args) {
        LOG.info("Starting Tradery Data Service...");

        try {
            DataServiceConfig config = DataServiceConfig.load();

            // Create data store for SQLite access
            dataStore = new SqliteDataStore();
            LOG.info("SqliteDataStore initialized");

            // Create consumer registry with shutdown callback
            consumerRegistry = new ConsumerRegistry(() -> {
                LOG.info("Initiating idle shutdown...");
                shutdownLatch.countDown();
            });

            server = new DataServiceServer(config, consumerRegistry, dataStore);

            // Write port file for service discovery
            writePortFile(config.getPort());

            // Register shutdown hook to clean up
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutting down Tradery Data Service...");
                cleanup();
            }));

            server.start();
            LOG.info("Tradery Data Service started on port {}", config.getPort());

            // Wait for shutdown signal (either from consumer registry or external)
            shutdownLatch.await();

            LOG.info("Shutdown signal received, stopping server...");
            cleanup();
            System.exit(0);

        } catch (Exception e) {
            LOG.error("Failed to start Data Service", e);
            System.exit(1);
        }
    }

    private static void cleanup() {
        if (server != null) {
            server.stop();
        }
        if (consumerRegistry != null) {
            consumerRegistry.shutdown();
        }
        deletePortFile();
    }

    private static void writePortFile(int port) throws IOException {
        Path portFile = DataServiceConfig.getPortFilePath();
        Files.createDirectories(portFile.getParent());
        Files.writeString(portFile, String.valueOf(port));
        LOG.info("Wrote port file: {}", portFile);
    }

    private static void deletePortFile() {
        try {
            Path portFile = DataServiceConfig.getPortFilePath();
            Files.deleteIfExists(portFile);
            LOG.info("Deleted port file");
        } catch (IOException e) {
            LOG.warn("Failed to delete port file", e);
        }
    }
}
