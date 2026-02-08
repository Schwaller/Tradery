package com.tradery.dataclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Locates and connects to a running Plaiiin Data Service.
 */
public class DataServiceLocator {
    private static final Logger LOG = LoggerFactory.getLogger(DataServiceLocator.class);

    private static final Path PORT_FILE = Paths.get(
        System.getProperty("user.home"), ".tradery", "dataservice.port"
    );

    private static final int DEFAULT_PORT = 9810;
    private static final int HEALTH_CHECK_TIMEOUT_MS = 2000;

    /**
     * Find a running data service.
     * @return Optional containing the port if service is found and healthy
     */
    public static Optional<Integer> findRunningService() {
        // First check the port file
        if (Files.exists(PORT_FILE)) {
            try {
                String content = Files.readString(PORT_FILE).trim();
                int port = Integer.parseInt(content);
                if (isResponding(port)) {
                    LOG.debug("Found data service on port {} (from port file)", port);
                    return Optional.of(port);
                }
            } catch (IOException | NumberFormatException e) {
                LOG.debug("Could not read port file: {}", e.getMessage());
            }
        }

        // Fall back to default port
        if (isResponding(DEFAULT_PORT)) {
            LOG.debug("Found data service on default port {}", DEFAULT_PORT);
            return Optional.of(DEFAULT_PORT);
        }

        LOG.debug("No running data service found");
        return Optional.empty();
    }

    /**
     * Check if a data service is responding on the given port.
     */
    public static boolean isResponding(int port) {
        try {
            URL url = new URL("http://localhost:" + port + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(HEALTH_CHECK_TIMEOUT_MS);
            conn.setReadTimeout(HEALTH_CHECK_TIMEOUT_MS);
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            return responseCode == 200;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Wait for the data service to become available.
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return Optional containing the port if service becomes available
     */
    public static Optional<Integer> waitForService(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int checkInterval = 500;

        while (System.currentTimeMillis() < deadline) {
            Optional<Integer> port = findRunningService();
            if (port.isPresent()) {
                return port;
            }

            try {
                Thread.sleep(checkInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * Locate a running data service and return its connection info.
     * @return Optional containing the service info if found
     */
    public static Optional<ServiceInfo> locate() {
        return findRunningService().map(port -> new ServiceInfo("localhost", port));
    }

    /**
     * Create a client connected to a running data service.
     * @return Optional containing the client if service is found
     */
    public static Optional<DataServiceClient> createClient() {
        return findRunningService().map(port -> new DataServiceClient("localhost", port));
    }

    /**
     * Service connection info.
     */
    public record ServiceInfo(String host, int port) {}

    /**
     * Create a client, waiting for the service if needed.
     * @param timeoutMs Maximum time to wait for service
     * @return Optional containing the client if service becomes available
     */
    public static Optional<DataServiceClient> createClient(long timeoutMs) {
        return waitForService(timeoutMs).map(port -> new DataServiceClient("localhost", port));
    }

    /**
     * Get the path to the port file.
     */
    public static Path getPortFilePath() {
        return PORT_FILE;
    }
}
