package com.tradery.sharing.tests;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

/**
 * Docker container running the rendezvous server fat JAR.
 */
public class RendezvousContainer extends GenericContainer<RendezvousContainer> {

    private static final int SERVER_PORT = 7480;

    public RendezvousContainer(Network network) {
        super("eclipse-temurin:21-jre");

        withNetwork(network);
        withNetworkAliases("rendezvous");
        withCopyFileToContainer(MountableFile.forHostPath(findFatJar()), "/app/rendezvous.jar");
        withExposedPorts(SERVER_PORT);
        withCommand("java", "-jar", "/app/rendezvous.jar", String.valueOf(SERVER_PORT));
        waitingFor(Wait.forHttp("/health").forPort(SERVER_PORT));
    }

    /** URL reachable from the host (mapped port). */
    public String hostUrl() {
        return "http://" + getHost() + ":" + getMappedPort(SERVER_PORT);
    }

    /** URL reachable from within the Docker network. */
    public String internalUrl() {
        return "http://rendezvous:" + SERVER_PORT;
    }

    private static String findFatJar() {
        Path base = Path.of(System.getProperty("user.dir")).getParent().resolve("tradery-rendezvous/build/libs");
        // Search for -all.jar
        java.io.File dir = base.toFile();
        if (dir.exists()) {
            java.io.File[] jars = dir.listFiles((d, name) -> name.endsWith("-all.jar"));
            if (jars != null && jars.length > 0) {
                return jars[0].getAbsolutePath();
            }
        }
        // Fallback with expected name
        return base.resolve("tradery-rendezvous-" + System.getProperty("project.version", "1.0.1") + "-all.jar").toString();
    }
}
