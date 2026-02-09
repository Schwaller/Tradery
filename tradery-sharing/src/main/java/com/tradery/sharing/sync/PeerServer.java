package com.tradery.sharing.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.net.ServerSocket;
import java.util.function.Consumer;

/**
 * TCP server that accepts incoming peer connections.
 * Runs on a random available port; the port is announced via rendezvous/mDNS.
 */
public class PeerServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PeerServer.class);

    private final ServerSocket serverSocket;
    private final ObjectMapper mapper;
    private final Consumer<PeerConnection> connectionHandler;
    private final Thread acceptThread;
    private volatile boolean running;

    public PeerServer(ObjectMapper mapper, Consumer<PeerConnection> connectionHandler) throws IOException {
        this.mapper = mapper;
        this.connectionHandler = connectionHandler;
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        ssc.setOption(StandardSocketOptions.SO_REUSEPORT, true); // allow hole punch sockets to share this port
        ssc.bind(new InetSocketAddress(0)); // random available port
        this.serverSocket = ssc.socket();
        this.running = true;

        this.acceptThread = Thread.ofVirtual().name("peer-server-accept").start(this::acceptLoop);
        log.info("Peer server listening on port {}", serverSocket.getLocalPort());
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                PeerConnection conn = new PeerConnection(socket, mapper);
                log.info("Accepted connection from {}", conn.remoteAddress());
                Thread.ofVirtual().name("peer-handler-" + conn.remoteAddress()).start(() -> {
                    try {
                        connectionHandler.accept(conn);
                    } catch (Exception e) {
                        log.warn("Error handling peer {}: {}", conn.remoteAddress(), e.getMessage());
                    } finally {
                        conn.close();
                    }
                });
            } catch (IOException e) {
                if (running) {
                    log.warn("Accept failed: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void close() {
        running = false;
        try { serverSocket.close(); } catch (IOException ignored) {}
    }
}
