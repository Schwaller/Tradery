package com.tradery.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the rendezvous server.
 * Usage: java -jar rendezvous.jar [port]
 *
 * Environment variables:
 *   PORT           — Server port (default: 7480, use 0 for random). Overridden by CLI arg.
 *   KEYCLOAK_URL   — Keycloak issuer URL (e.g. https://plaiiin.com/auth). Omit for test mode.
 *   KEYCLOAK_REALM — Keycloak realm name (default: plaiiin)
 *   KEY_STORE_DIR  — Directory for backend signing keys (default: ~/.tradery/rendezvous-keys)
 */
public class RendezvousApp {

    private static final Logger log = LoggerFactory.getLogger(RendezvousApp.class);
    private static final int DEFAULT_PORT = 7480;

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0] + ", using default " + port);
            }
        }

        String keycloakUrl = System.getenv("KEYCLOAK_URL");
        String keycloakRealm = System.getenv().getOrDefault("KEYCLOAK_REALM", "plaiiin");

        RendezvousServer server;
        if (keycloakUrl != null && !keycloakUrl.isBlank()) {
            log.info("Starting with Keycloak validation: {} realm={}", keycloakUrl, keycloakRealm);
            server = new RendezvousServer(port, RendezvousServer.createDefaultKeyStore(),
                    KeycloakValidator.keycloak(keycloakUrl, keycloakRealm));
        } else {
            log.info("Starting in test mode (no Keycloak validation)");
            server = new RendezvousServer(port);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
