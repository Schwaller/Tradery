package com.tradery.rendezvous;

/**
 * Entry point for the rendezvous server.
 * Usage: java -jar rendezvous.jar [port]
 */
public class RendezvousApp {

    private static final int DEFAULT_PORT = 7480;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0] + ", using default " + DEFAULT_PORT);
            }
        }

        RendezvousServer server = new RendezvousServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
