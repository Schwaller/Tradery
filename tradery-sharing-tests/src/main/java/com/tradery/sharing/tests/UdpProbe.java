package com.tradery.sharing.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.sharing.sync.NetworkMessage;
import com.tradery.sharing.sync.UdpPeerConnection;
import com.tradery.sharing.sync.UdpPeerServer;

import java.util.List;
import java.util.concurrent.*;

/**
 * Standalone UDP connectivity probe for testing NAT traversal.
 *
 * <p>Usage:
 * <pre>
 *   # On plaiiin.com (public IP, known port):
 *   java -cp probe.jar com.tradery.sharing.tests.UdpProbe listen 19876
 *
 *   # On local machine (behind NAT):
 *   java -cp probe.jar com.tradery.sharing.tests.UdpProbe connect plaiiin.com 19876
 * </pre>
 *
 * <p>The "connect" side sends CONNECT packets which punch the NAT hole.
 * The "listen" side accepts and sends test messages back through the hole.
 * Both sides exchange HELLO + a test SyncRequest to prove bidirectional data flow.
 */
public class UdpProbe {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage:");
            System.err.println("  listen  <port>            — listen on a fixed UDP port (run on public server)");
            System.err.println("  connect <host> <port>     — connect to remote (run behind NAT)");
            System.err.println("  dual    <host> <port>     — simultaneous: listen + connect at same time");
            System.exit(1);
        }

        switch (args[0]) {
            case "listen" -> listen(Integer.parseInt(args[1]));
            case "connect" -> connect(args[1], Integer.parseInt(args[2]));
            case "dual" -> dual(args[1], Integer.parseInt(args[2]));
            default -> {
                System.err.println("Unknown mode: " + args[0]);
                System.exit(1);
            }
        }
    }

    /**
     * Listen mode — run on plaiiin.com with a fixed port.
     * Accepts incoming connections, exchanges test messages, reports results.
     */
    static void listen(int port) throws Exception {
        System.out.println("=== UDP Probe: LISTEN mode on port " + port + " ===");

        var incoming = new LinkedBlockingQueue<UdpPeerConnection>();

        var server = new UdpPeerServer(mapper, conn -> {
            incoming.offer(conn);
            // Keep alive until probe closes
            while (!conn.isClosed()) {
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
        }, port);
        server.start();

        System.out.println("Listening on UDP port " + server.port());
        System.out.println("Waiting for connections... (Ctrl+C to stop)");

        while (true) {
            UdpPeerConnection conn = incoming.poll(60, TimeUnit.SECONDS);
            if (conn == null) {
                System.out.println("  (still waiting...)");
                continue;
            }

            System.out.println();
            System.out.println(">>> CONNECT from " + conn.remoteAddress());

            try {
                // Wait for HELLO from connector
                NetworkMessage msg = conn.receive();
                if (msg instanceof NetworkMessage.Hello hello) {
                    System.out.println("<<< HELLO from " + hello.peerId() + " (device: " + hello.deviceId() + ")");

                    // Send HELLO back
                    conn.send(new NetworkMessage.Hello("probe-server", "server-" + port, null, List.of()));
                    System.out.println(">>> Sent HELLO back");

                    // Wait for test message
                    NetworkMessage test = conn.receive();
                    if (test instanceof NetworkMessage.SyncRequest req) {
                        System.out.println("<<< SyncRequest (docId=" + req.documentId() + ", since=" + req.sinceLclock() + ")");

                        // Send test response back
                        conn.send(new NetworkMessage.SyncDone(req.documentId()));
                        System.out.println(">>> Sent SyncDone back");

                        System.out.println();
                        System.out.println("SUCCESS — bidirectional UDP communication verified!");
                        System.out.println("  Remote: " + conn.remoteAddress());
                    } else {
                        System.out.println("<<< Unexpected: " + (test != null ? test.getClass().getSimpleName() : "null"));
                    }
                } else {
                    System.out.println("<<< Unexpected first message: " + (msg != null ? msg.getClass().getSimpleName() : "null"));
                }
            } catch (Exception e) {
                System.out.println("!!! Error: " + e.getMessage());
            }

            System.out.println();
            System.out.println("Waiting for more connections...");
        }
    }

    /**
     * Connect mode — run behind NAT.
     * Connects to remote server, sends test messages, verifies response.
     */
    static void connect(String host, int port) throws Exception {
        System.out.println("=== UDP Probe: CONNECT mode to " + host + ":" + port + " ===");

        var server = new UdpPeerServer(mapper, conn -> {
            // Accept incoming connections (for simultaneous open testing)
            System.out.println(">>> Incoming connection from " + conn.remoteAddress());
            while (!conn.isClosed()) {
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
        });
        server.start();

        System.out.println("Local UDP port: " + server.port());
        System.out.println("Connecting to " + host + ":" + port + "...");

        long start = System.currentTimeMillis();
        UdpPeerConnection conn = server.connect(host, port, 10_000);
        long elapsed = System.currentTimeMillis() - start;

        if (conn == null) {
            System.out.println();
            System.out.println("FAILED — could not establish UDP connection after 10s");
            System.out.println("  This means CONNECT packets are not reaching the remote,");
            System.out.println("  or CONNECT_ACK packets are not making it back through NAT.");
            server.close();
            System.exit(1);
        }

        System.out.println("Connected in " + elapsed + "ms (remote: " + conn.remoteAddress() + ")");

        // Send HELLO
        conn.send(new NetworkMessage.Hello("probe-client", "client-local", null, List.of("test-doc")));
        System.out.println(">>> Sent HELLO");

        // Wait for HELLO back
        NetworkMessage reply = conn.receive();
        if (reply instanceof NetworkMessage.Hello hello) {
            System.out.println("<<< HELLO from " + hello.peerId());
        } else {
            System.out.println("<<< Unexpected: " + (reply != null ? reply.getClass().getSimpleName() : "null"));
            conn.close();
            server.close();
            System.exit(1);
        }

        // Send test message
        conn.send(new NetworkMessage.SyncRequest("test-doc", 42));
        System.out.println(">>> Sent SyncRequest");

        // Wait for response
        NetworkMessage response = conn.receive();
        if (response instanceof NetworkMessage.SyncDone done) {
            System.out.println("<<< SyncDone (docId=" + done.documentId() + ")");
        } else {
            System.out.println("<<< Unexpected: " + (response != null ? response.getClass().getSimpleName() : "null"));
        }

        System.out.println();
        System.out.println("SUCCESS — bidirectional UDP communication verified!");
        System.out.println("  Connect time: " + elapsed + "ms");
        System.out.println("  Local port:   " + server.port());
        System.out.println("  Remote:       " + conn.remoteAddress());

        conn.close();
        server.close();
    }

    /**
     * Dual mode — simultaneous connect test.
     * Starts a local server AND connects to remote at the same time.
     * This simulates the real P2P scenario where both peers connect to each other.
     */
    static void dual(String host, int port) throws Exception {
        System.out.println("=== UDP Probe: DUAL mode (simultaneous connect) ===");
        System.out.println("  Remote: " + host + ":" + port);

        var incoming = new LinkedBlockingQueue<UdpPeerConnection>();

        var server = new UdpPeerServer(mapper, conn -> {
            System.out.println(">>> Incoming connection from " + conn.remoteAddress());
            incoming.offer(conn);
            while (!conn.isClosed()) {
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
        });
        server.start();

        System.out.println("Local UDP port: " + server.port());
        System.out.println("Attempting simultaneous connect...");

        // Try outgoing connect (this also accepts incoming via dispatch loop)
        long start = System.currentTimeMillis();
        UdpPeerConnection outgoing = server.connect(host, port, 10_000);
        long elapsed = System.currentTimeMillis() - start;

        // Check if we got an incoming connection too
        UdpPeerConnection inc = incoming.poll(1, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("Results after " + elapsed + "ms:");
        System.out.println("  Outgoing connect: " + (outgoing != null ? "SUCCESS" : "FAILED"));
        System.out.println("  Incoming connect: " + (inc != null ? "SUCCESS from " + inc.remoteAddress() : "none"));

        if (outgoing != null) {
            outgoing.close();
        }
        if (inc != null) {
            inc.close();
        }
        server.close();
    }
}
