package com.tradery.sharing.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.tradery.sharing.sync.UdpPeerConnection.*;

/**
 * UDP server that owns a single DatagramSocket and dispatches incoming packets
 * to the appropriate UdpPeerConnection by source address.
 *
 * <p>Handles connection establishment (CONNECT/CONNECT_ACK) and routes DATA/ACK/
 * HEARTBEAT/DISCONNECT packets to existing connections.
 */
public class UdpPeerServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UdpPeerServer.class);

    private static final int MAX_PACKET_SIZE = 1500;
    private static final int CONNECT_RETRY_MS = 500;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;

    private final DatagramSocket socket;
    private final ObjectMapper mapper;
    private final Consumer<UdpPeerConnection> connectionHandler;
    private volatile Thread dispatchThread;
    private volatile boolean running;

    /** Active connections by remote address. */
    private final Map<InetSocketAddress, UdpPeerConnection> connections = new ConcurrentHashMap<>();

    /** Pending outgoing connects — address → future that completes when CONNECT_ACK received. */
    private final Map<InetSocketAddress, CompletableFuture<Void>> pendingConnects = new ConcurrentHashMap<>();

    /** Addresses we've already seen CONNECT from while we have a pending connect to them (simultaneous open). */
    private final Set<InetSocketAddress> simultaneousConnects = ConcurrentHashMap.newKeySet();

    public UdpPeerServer(ObjectMapper mapper, Consumer<UdpPeerConnection> connectionHandler) throws SocketException {
        this(mapper, connectionHandler, 0);
    }

    public UdpPeerServer(ObjectMapper mapper, Consumer<UdpPeerConnection> connectionHandler, int port) throws SocketException {
        this.mapper = mapper;
        this.connectionHandler = connectionHandler;
        this.socket = new DatagramSocket(port);
        this.running = true;
    }

    /** Start the dispatch loop. Must be called before connect() or accepting connections. */
    public void start() {
        if (dispatchThread != null) return;
        this.dispatchThread = Thread.ofVirtual().name("udp-server-dispatch").start(this::dispatchLoop);
        log.info("UDP peer server listening on port {}", socket.getLocalPort());
    }

    /** Access the underlying socket (e.g. for STUN discovery before dispatch starts). */
    public DatagramSocket socket() {
        return socket;
    }

    public int port() {
        return socket.getLocalPort();
    }

    /**
     * Initiate an outgoing connection to a remote peer. Sends CONNECT packets
     * every 500ms until CONNECT_ACK is received or timeout expires.
     *
     * @return the established connection, or null on timeout
     */
    public UdpPeerConnection connect(String host, int port, int timeoutMs) {
        InetSocketAddress addr = new InetSocketAddress(host, port);

        // Already connected?
        UdpPeerConnection existing = connections.get(addr);
        if (existing != null && !existing.isClosed()) return existing;

        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingConnects.put(addr, future);

        try {
            long deadline = System.currentTimeMillis() + timeoutMs;
            byte[] connectPacket = UdpPeerConnection.buildConnectPacket();

            while (System.currentTimeMillis() < deadline && !future.isDone()) {
                socket.send(new DatagramPacket(connectPacket, connectPacket.length, addr));

                try {
                    future.get(CONNECT_RETRY_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    // retry
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                } catch (ExecutionException e) {
                    return null;
                }
            }

            if (!future.isDone()) {
                log.info("UDP connect to {} timed out after {}ms", addr, timeoutMs);
                return null;
            }

            // Connection established — it was created by the dispatch loop
            UdpPeerConnection conn = connections.get(addr);
            if (conn != null) {
                log.info("UDP connected to {}", addr);
            }
            return conn;

        } catch (IOException e) {
            log.warn("UDP connect to {} failed: {}", addr, e.getMessage());
            return null;
        } finally {
            pendingConnects.remove(addr);
            simultaneousConnects.remove(addr);
        }
    }

    public UdpPeerConnection connect(String host, int port) {
        return connect(host, port, DEFAULT_CONNECT_TIMEOUT_MS);
    }

    private void dispatchLoop() {
        byte[] buf = new byte[MAX_PACKET_SIZE];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                InetSocketAddress from = new InetSocketAddress(packet.getAddress(), packet.getPort());
                int len = packet.getLength();
                if (len < 1) continue;

                byte type = buf[0];

                switch (type) {
                    case TYPE_CONNECT -> handleConnect(from, buf, len);
                    case TYPE_CONNECT_ACK -> handleConnectAck(from, buf, len);
                    case TYPE_DATA -> handleData(from, buf, len);
                    case TYPE_ACK -> handleAckPacket(from, buf, len);
                    case TYPE_HEARTBEAT -> {
                        UdpPeerConnection conn = connections.get(from);
                        if (conn != null) conn.handleHeartbeat();
                    }
                    case TYPE_DISCONNECT -> {
                        UdpPeerConnection conn = connections.remove(from);
                        if (conn != null) conn.handleDisconnect();
                    }
                    default -> log.debug("Unknown packet type {} from {}", type, from);
                }
            } catch (SocketException e) {
                if (running) log.debug("Socket closed: {}", e.getMessage());
                break;
            } catch (IOException e) {
                if (running) log.warn("Dispatch error: {}", e.getMessage());
            }
        }
    }

    private void handleConnect(InetSocketAddress from, byte[] buf, int len) throws IOException {
        if (len < CONNECT_SIZE) return;
        ByteBuffer bb = ByteBuffer.wrap(buf, 1, len - 1).order(ByteOrder.BIG_ENDIAN);
        int magic = bb.getInt();
        if (magic != MAGIC) return;

        // Already connected to this address?
        if (connections.containsKey(from)) {
            // Re-send CONNECT_ACK in case ours was lost
            socket.send(new DatagramPacket(buildConnectAckPacket(), CONNECT_SIZE, from));
            return;
        }

        // If we have a pending outgoing connect to this address, this is simultaneous open.
        // We still accept it — both sides will end up with a connection.
        CompletableFuture<Void> pendingFuture = pendingConnects.get(from);
        if (pendingFuture != null) {
            simultaneousConnects.add(from);
        }

        // Send CONNECT_ACK
        socket.send(new DatagramPacket(buildConnectAckPacket(), CONNECT_SIZE, from));

        // Create connection
        UdpPeerConnection conn = new UdpPeerConnection(socket, from, mapper);
        connections.put(from, conn);

        // If this was a simultaneous connect, also complete the pending future
        if (pendingFuture != null) {
            pendingFuture.complete(null);
        }

        // Invoke handler on virtual thread
        Thread.ofVirtual().name("udp-handler-" + from).start(() -> {
            try {
                connectionHandler.accept(conn);
            } catch (Exception e) {
                log.warn("Error handling peer {}: {}", from, e.getMessage());
            } finally {
                conn.close();
                connections.remove(from, conn);
            }
        });
    }

    private void handleConnectAck(InetSocketAddress from, byte[] buf, int len) {
        if (len < CONNECT_SIZE) return;
        ByteBuffer bb = ByteBuffer.wrap(buf, 1, len - 1).order(ByteOrder.BIG_ENDIAN);
        int magic = bb.getInt();
        if (magic != MAGIC) return;

        CompletableFuture<Void> future = pendingConnects.get(from);
        if (future == null) return;

        // Create connection if not already created by simultaneous open
        if (!connections.containsKey(from)) {
            UdpPeerConnection conn = new UdpPeerConnection(socket, from, mapper);
            connections.put(from, conn);
        }
        future.complete(null);
    }

    private void handleData(InetSocketAddress from, byte[] buf, int len) {
        if (len < DATA_HEADER_SIZE) return;

        UdpPeerConnection conn = connections.get(from);
        if (conn == null) return;

        ByteBuffer bb = ByteBuffer.wrap(buf, 1, len - 1).order(ByteOrder.BIG_ENDIAN);
        int seqNum = bb.getInt();
        int fragIdx = Short.toUnsignedInt(bb.getShort());
        int fragCount = Short.toUnsignedInt(bb.getShort());
        int payloadLen = Short.toUnsignedInt(bb.getShort());

        if (payloadLen > MAX_FRAGMENT_PAYLOAD || DATA_HEADER_SIZE + payloadLen > len) return;

        byte[] payload = new byte[payloadLen];
        System.arraycopy(buf, DATA_HEADER_SIZE, payload, 0, payloadLen);

        conn.handleFragment(seqNum, fragIdx, fragCount, payload);
    }

    private void handleAckPacket(InetSocketAddress from, byte[] buf, int len) {
        if (len < ACK_SIZE) return;

        UdpPeerConnection conn = connections.get(from);
        if (conn == null) return;

        ByteBuffer bb = ByteBuffer.wrap(buf, 1, len - 1).order(ByteOrder.BIG_ENDIAN);
        int seqNum = bb.getInt();
        conn.handleAck(seqNum);
    }

    /** Remove a closed connection from the map. */
    void removeConnection(InetSocketAddress addr) {
        connections.remove(addr);
    }

    @Override
    public void close() {
        running = false;
        connections.values().forEach(UdpPeerConnection::close);
        connections.clear();
        socket.close();
    }
}
