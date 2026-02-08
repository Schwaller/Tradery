package com.tradery.sharing.tests;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

/**
 * Docker container running a HeadlessPeer fat JAR.
 * Exposes the control port (8080) to the host; P2P port is internal to the Docker network.
 */
public class PeerContainer extends GenericContainer<PeerContainer> {

    private static final int CONTROL_PORT = 8080;

    private static final Path FAT_JAR_PATH = Path.of(
            System.getProperty("user.dir"), "build", "libs",
            "tradery-sharing-tests-" + System.getProperty("project.version", "1.0.1") + "-all.jar"
    );

    public PeerContainer(String peerId, Network network) {
        super("eclipse-temurin:21-jre");

        withNetwork(network);
        withNetworkAliases(peerId);
        withCopyFileToContainer(MountableFile.forHostPath(findFatJar()), "/app/peer.jar");
        withExposedPorts(CONTROL_PORT);
        withCommand("java", "-jar", "/app/peer.jar",
                "--control-port", String.valueOf(CONTROL_PORT),
                "--peer-id", peerId,
                "--data-dir", "/data");
        waitingFor(Wait.forHttp("/health").forPort(CONTROL_PORT));
    }

    public String controlUrl() {
        return "http://" + getHost() + ":" + getMappedPort(CONTROL_PORT);
    }

    private static String findFatJar() {
        // Try standard path first
        if (FAT_JAR_PATH.toFile().exists()) {
            return FAT_JAR_PATH.toString();
        }
        // Fallback: search for any -all.jar
        java.io.File libsDir = Path.of(System.getProperty("user.dir"), "build", "libs").toFile();
        if (libsDir.exists()) {
            java.io.File[] jars = libsDir.listFiles((dir, name) -> name.endsWith("-all.jar"));
            if (jars != null && jars.length > 0) {
                return jars[0].getAbsolutePath();
            }
        }
        return FAT_JAR_PATH.toString(); // will fail with clear message
    }
}
