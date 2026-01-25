package com.tradery.dataclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Manages the data service lifecycle from client apps.
 * - Starts the data service if not running
 * - Registers as a consumer
 * - Sends periodic heartbeats
 * - Unregisters on shutdown
 */
public class DataServiceLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(DataServiceLauncher.class);

    private static final int DEFAULT_PORT = 9810;
    private static final long STARTUP_TIMEOUT_MS = 15_000;
    private static final long HEARTBEAT_INTERVAL_MS = 10_000;

    private final String consumerId;
    private final String consumerName;
    private final int pid;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService heartbeatExecutor;

    private Process dataServiceProcess;
    private int servicePort;
    private volatile boolean registered = false;

    public DataServiceLauncher(String consumerName) {
        this.consumerId = UUID.randomUUID().toString();
        this.consumerName = consumerName;
        this.pid = (int) ProcessHandle.current().pid();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DataService-Heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Ensure the data service is running and register as a consumer.
     * Starts the service if not already running.
     *
     * @return the port the service is running on
     * @throws IOException if unable to start or connect to the service
     */
    public int ensureRunning() throws IOException {
        // Check if already running
        var existingPort = DataServiceLocator.findRunningService();
        if (existingPort.isPresent()) {
            servicePort = existingPort.get();
            LOG.info("Data service already running on port {}", servicePort);
        } else {
            // Start the service
            servicePort = startDataService();
            LOG.info("Started data service on port {}", servicePort);
        }

        // Register as consumer
        register();

        // Start heartbeat
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat,
            HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        return servicePort;
    }

    /**
     * Get the port the service is running on.
     */
    public int getPort() {
        return servicePort;
    }

    /**
     * Check if we're registered with the data service.
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Shutdown - unregister from the data service.
     */
    public void shutdown() {
        heartbeatExecutor.shutdownNow();

        if (registered) {
            try {
                unregister();
            } catch (Exception e) {
                LOG.warn("Failed to unregister from data service", e);
            }
        }

        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    /**
     * Start the data service as a subprocess.
     */
    private int startDataService() throws IOException {
        Path jarPath = findDataServiceJar();
        if (jarPath == null) {
            throw new IOException("Could not find tradery-data-service JAR");
        }

        LOG.info("Starting data service from: {}", jarPath);

        ProcessBuilder pb = new ProcessBuilder(
            "java",
            "-Xmx2g",
            "-jar",
            jarPath.toString()
        );

        // Redirect output to log files
        Path logDir = Paths.get(System.getProperty("user.home"), ".tradery", "logs");
        Files.createDirectories(logDir);
        pb.redirectOutput(logDir.resolve("dataservice.log").toFile());
        pb.redirectError(logDir.resolve("dataservice-error.log").toFile());

        dataServiceProcess = pb.start();

        // Wait for the service to start
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            var port = DataServiceLocator.findRunningService();
            if (port.isPresent()) {
                return port.get();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for data service to start");
            }
        }

        throw new IOException("Data service failed to start within " + STARTUP_TIMEOUT_MS + "ms");
    }

    /**
     * Find the data service JAR file.
     */
    private Path findDataServiceJar() {
        // Check common locations
        String[] searchPaths = {
            // Development: in the build directory (relative to project root)
            "tradery-data-service/build/libs/tradery-data-service-1.0.0-all.jar",
            "tradery-data-service/build/libs/tradery-data-service-1.0.0.jar",
            // Installed: in the same directory as the app
            "../tradery-data-service.jar",
            "tradery-data-service.jar",
            // Installed: in Applications
            "/Applications/Tradery/tradery-data-service.jar",
        };

        // First, try relative to current working directory
        Path cwd = Paths.get("").toAbsolutePath();
        LOG.debug("Looking for data service JAR. CWD: {}", cwd);

        for (String path : searchPaths) {
            Path p = Paths.get(path);
            LOG.debug("Checking path: {} (absolute: {})", path, p.toAbsolutePath());
            if (Files.exists(p)) {
                LOG.info("Found data service JAR at: {}", p.toAbsolutePath());
                return p.toAbsolutePath();
            }
        }

        // Try relative to the classes location (handles Gradle run where classes are in build/classes)
        try {
            Path classesDir = Paths.get(getClass().getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            LOG.debug("Classes location: {}", classesDir);

            // Navigate up to find project root: build/classes/java/main -> project root
            Path projectRoot = classesDir;
            for (int i = 0; i < 6; i++) {  // Go up enough levels
                projectRoot = projectRoot.getParent();
                if (projectRoot == null) break;

                // Check for tradery-data-service sibling
                Path serviceJar = projectRoot.resolve("tradery-data-service/build/libs/tradery-data-service-1.0.0-all.jar");
                if (Files.exists(serviceJar)) {
                    LOG.info("Found data service JAR at: {}", serviceJar);
                    return serviceJar;
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not determine JAR location from classes", e);
        }

        LOG.warn("Could not find data service JAR in any location");
        return null;
    }

    /**
     * Register with the data service.
     */
    private void register() throws IOException {
        String url = String.format("http://localhost:%d/consumers/register", servicePort);
        String json = objectMapper.writeValueAsString(new RegisterRequest(consumerId, consumerName, pid));

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(json, MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                registered = true;
                LOG.info("Registered with data service as {} ({})", consumerName, consumerId);
            } else {
                throw new IOException("Failed to register: " + response.code());
            }
        }
    }

    /**
     * Unregister from the data service.
     */
    private void unregister() throws IOException {
        String url = String.format("http://localhost:%d/consumers/unregister", servicePort);
        String json = objectMapper.writeValueAsString(new UnregisterRequest(consumerId));

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(json, MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                registered = false;
                LOG.info("Unregistered from data service");
            }
        }
    }

    /**
     * Send heartbeat to the data service.
     */
    private void sendHeartbeat() {
        if (!registered) return;

        try {
            String url = String.format("http://localhost:%d/consumers/heartbeat", servicePort);
            String json = objectMapper.writeValueAsString(new HeartbeatRequest(consumerId));

            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    LOG.warn("Heartbeat failed: {}", response.code());
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to send heartbeat", e);
        }
    }

    // Request records
    private record RegisterRequest(String consumerId, String consumerName, int pid) {}
    private record UnregisterRequest(String consumerId) {}
    private record HeartbeatRequest(String consumerId) {}
}
