package com.tradery.sharing.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Headless peer application for Docker-based integration testing.
 * Exposes an HTTP control API that tests use to drive P2P sync scenarios.
 *
 * Usage: java -jar peer.jar --control-port 8080 --peer-id peer-1 --data-dir /data
 */
public class HeadlessPeerApp {

    private static final Logger log = LoggerFactory.getLogger(HeadlessPeerApp.class);

    public static void main(String[] args) throws Exception {
        int controlPort = 8080;
        String peerId = "peer-" + ProcessHandle.current().pid();
        Path dataDir = Path.of("/data");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--control-port" -> controlPort = Integer.parseInt(args[++i]);
                case "--peer-id" -> peerId = args[++i];
                case "--data-dir" -> dataDir = Path.of(args[++i]);
            }
        }

        dataDir.toFile().mkdirs();
        ObjectMapper mapper = new ObjectMapper();

        PeerController controller = new PeerController(peerId, dataDir, mapper);

        Javalin app = Javalin.create();
        controller.registerRoutes(app);
        app.start(controlPort);

        log.info("HeadlessPeer started: peerId={} controlPort={} dataDir={}", peerId, controlPort, dataDir);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            controller.close();
            app.stop();
        }));
    }
}
