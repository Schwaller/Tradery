package com.tradery.dataservice;

import com.tradery.dataservice.api.DataServiceServer;
import com.tradery.dataservice.coingecko.CoinGeckoClient;
import com.tradery.dataservice.config.DataServiceConfig;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.SymbolsConnection;
import com.tradery.dataservice.symbols.SymbolSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private static SymbolsConnection symbolsConnection;
    private static SymbolSyncService symbolSyncService;
    private static CoinGeckoClient coingeckoClient;
    private static ScheduledExecutorService scheduler;
    private static java.time.Instant startTime;

    public static void main(String[] args) {
        LOG.info("Starting Tradery Data Service...");
        startTime = java.time.Instant.now();

        try {
            DataServiceConfig config = DataServiceConfig.load();

            // Create data store for SQLite access
            dataStore = new SqliteDataStore();
            LOG.info("SqliteDataStore initialized");

            // Initialize symbol resolution components
            symbolsConnection = SymbolsConnection.getInstance();
            symbolsConnection.initializeSchema();
            LOG.info("SymbolsConnection initialized");

            coingeckoClient = new CoinGeckoClient();
            symbolSyncService = new SymbolSyncService(coingeckoClient, symbolsConnection);
            LOG.info("SymbolSyncService initialized");

            // Create consumer registry with shutdown callback
            consumerRegistry = new ConsumerRegistry(() -> {
                LOG.info("Initiating idle shutdown...");
                shutdownLatch.countDown();
            });

            server = new DataServiceServer(config, consumerRegistry, dataStore,
                symbolSyncService, symbolsConnection, coingeckoClient);

            // Schedule daily symbol sync at 3 AM
            scheduleSymbolSync();

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
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (server != null) {
            server.stop();
        }
        if (consumerRegistry != null) {
            consumerRegistry.shutdown();
        }
        if (symbolsConnection != null) {
            symbolsConnection.close();
        }
        deletePortFile();
    }

    /**
     * Schedule symbol sync every 6 hours.
     * On startup, resumes any incomplete sync (only syncs exchanges not synced within 6h).
     */
    private static void scheduleSymbolSync() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "symbol-sync-scheduler");
            t.setDaemon(true);
            return t;
        });

        final long SYNC_INTERVAL_HOURS = 6;

        // Schedule sync every 6 hours
        scheduler.scheduleAtFixedRate(() -> {
            try {
                LOG.info("Running scheduled symbol sync...");
                symbolSyncService.syncAll(true); // Skip exchanges synced within 6h
            } catch (Exception e) {
                LOG.error("Scheduled symbol sync failed", e);
            }
        }, SYNC_INTERVAL_HOURS, SYNC_INTERVAL_HOURS, TimeUnit.HOURS);

        LOG.info("Symbol sync scheduled every {}h", SYNC_INTERVAL_HOURS);

        // 5-minute status heartbeat
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int consumers = consumerRegistry.getConsumerCount();
                int activePages = server.getActivePageCount();
                int liveCandles = server.getLiveCandleCount();
                int liveAggTrades = server.getLiveAggTradeCount();
                long uptimeMin = Duration.between(startTime, java.time.Instant.now()).toMinutes();
                boolean syncing = symbolSyncService.isSyncing();

                LOG.info("STATUS | uptime={}m | consumers={} | pages={} | liveCandles={} | liveAggTrades={} | syncing={}",
                    uptimeMin, consumers, activePages, liveCandles, liveAggTrades, syncing);
            } catch (Exception e) {
                LOG.debug("Status heartbeat error: {}", e.getMessage());
            }
        }, 1, 5, TimeUnit.MINUTES);

        // Trigger sync on startup - resumes incomplete sync
        scheduler.schedule(() -> {
            try {
                LOG.info("Running startup symbol sync (resuming incomplete)...");
                symbolSyncService.syncAll(true); // Skip exchanges synced within 6h
            } catch (Exception e) {
                LOG.error("Startup symbol sync failed", e);
            }
        }, 5, TimeUnit.SECONDS);
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
