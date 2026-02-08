package com.tradery.dataclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Manages the data service lifecycle from client apps.
 * - Starts the data service if not running
 * - Provides consumerId for WebSocket connection (WS handles registration)
 */
public class DataServiceLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(DataServiceLauncher.class);

    private static final long STARTUP_TIMEOUT_MS = 15_000;

    private final String consumerId;
    private final String consumerName;
    private final int pid;

    private Process dataServiceProcess;
    private int servicePort;

    public DataServiceLauncher(String consumerName) {
        this.consumerId = UUID.randomUUID().toString();
        this.consumerName = consumerName;
        this.pid = (int) ProcessHandle.current().pid();
    }

    /**
     * Ensure the data service is running.
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

        return servicePort;
    }

    /**
     * Get the port the service is running on.
     */
    public int getPort() {
        return servicePort;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    /**
     * Shutdown the launcher.
     */
    public void shutdown() {
        // Nothing to clean up — WS connection handles consumer lifecycle
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
            "/Applications/Strategy Forge.app/Contents/app/tradery-data-service.jar",
            "/Applications/Plaiiin.app/Contents/app/tradery-data-service.jar",
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

        // Check if running from macOS app bundle via java.home (jlink runtime)
        String javaHome = System.getProperty("java.home");
        LOG.debug("java.home: {}", javaHome);
        if (javaHome != null && javaHome.contains(".app/Contents/")) {
            // Extract app bundle path from java.home (e.g., /path/to/App.app/Contents/runtime/Contents/Home)
            int appIdx = javaHome.indexOf(".app/Contents/");
            if (appIdx > 0) {
                Path appContents = Paths.get(javaHome.substring(0, appIdx + ".app/Contents/".length()));
                Path serviceJar = appContents.resolve("app/tradery-data-service.jar");
                LOG.debug("Checking jlink app bundle path: {}", serviceJar);
                if (Files.exists(serviceJar)) {
                    LOG.info("Found data service JAR in app bundle at: {}", serviceJar);
                    return serviceJar;
                }
            }
        }

        // Try relative to the classes location (handles Gradle run where classes are in build/classes)
        try {
            Path classesDir = Paths.get(getClass().getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            LOG.debug("Classes location: {}", classesDir);

            // Check if we're inside a macOS app bundle (path contains .app/Contents/)
            String classesPath = classesDir.toString();
            int appContentsIdx = classesPath.indexOf(".app/Contents/");
            if (appContentsIdx > 0) {
                // We're in an app bundle - look in the app directory
                Path appContents = Paths.get(classesPath.substring(0, appContentsIdx + ".app/Contents/".length()));
                Path serviceJar = appContents.resolve("app/tradery-data-service.jar");
                LOG.debug("Checking app bundle path: {}", serviceJar);
                if (Files.exists(serviceJar)) {
                    LOG.info("Found data service JAR in app bundle at: {}", serviceJar);
                    return serviceJar;
                }
            }

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
}
