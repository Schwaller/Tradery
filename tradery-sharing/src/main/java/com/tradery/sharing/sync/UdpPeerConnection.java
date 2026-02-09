package com.tradery.sharing.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A single UDP peer connection — sends and receives JSON messages over a shared
 * DatagramSocket with fragmentation, ACKs, and heartbeats.
 *
 * <p>Packet types:
 * <ul>
 *   <li>DATA(0): 11-byte header + payload (up to 1389 bytes per fragment)</li>
 *   <li>ACK(1): 5 bytes — acknowledges a sequence number</li>
 *   <li>CONNECT(2): 11 bytes — connection request</li>
 *   <li>CONNECT_ACK(3): 11 bytes — connection accepted</li>
 *   <li>HEARTBEAT(4): 5 bytes — keepalive</li>
 *   <li>DISCONNECT(5): 5 bytes — graceful close</li>
 * </ul>
 */
public class UdpPeerConnection implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UdpPeerConnection.class);

    // Packet types
    static final byte TYPE_DATA = 0;
    static final byte TYPE_ACK = 1;
    static final byte TYPE_CONNECT = 2;
    static final byte TYPE_CONNECT_ACK = 3;
    static final byte TYPE_HEARTBEAT = 4;
    static final byte TYPE_DISCONNECT = 5;

    // Protocol constants
    static final int MAGIC = 0x504C4149; // "PLAI"
    static final short PROTOCOL_VERSION = 1;
    static final int MAX_FRAGMENT_PAYLOAD = 1389; // 1400 - 11 byte header
    static final int DATA_HEADER_SIZE = 11;
    static final int ACK_SIZE = 5;
    static final int CONNECT_SIZE = 11;
    static final int HEARTBEAT_SIZE = 5;

    // Timing
    private static final int ACK_TIMEOUT_MS = 500;
    private static final int MAX_RETRIES = 5;
    private static final int HEARTBEAT_INTERVAL_MS = 10_000;
    static final int RECEIVE_TIMEOUT_MS = 35_000;

    private final DatagramSocket socket; // shared, NOT owned by us
    private final InetSocketAddress remoteAddr;
    private final ObjectMapper mapper;

    private String remotePeerId;
    private volatile boolean closed;

    // Sending: monotonic sequence number, synchronized via sendLock
    private int nextSeqNum = 1;
    private final Object sendLock = new Object();

    // ACK signaling: sender blocks until ACK received
    private final ReentrantLock ackLock = new ReentrantLock();
    private final Condition ackReceived = ackLock.newCondition();
    private volatile int lastAckedSeqNum = 0;

    // Receiving: fragment reassembly and completed message queue
    private final Map<Integer, byte[][]> fragmentBuffers = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> fragmentCounts = new ConcurrentHashMap<>();
    private final BlockingQueue<byte[]> inboundQueue = new LinkedBlockingQueue<>();

    // Duplicate detection: last N acked seqNums
    private final Set<Integer> recentlyAcked = ConcurrentHashMap.newKeySet();

    // Heartbeat tracking
    private volatile long lastReceiveTime = System.currentTimeMillis();
    private volatile long lastSendTime = System.currentTimeMillis();
    private final Thread heartbeatThread;

    public UdpPeerConnection(DatagramSocket socket, InetSocketAddress remoteAddr, ObjectMapper mapper) {
        this.socket = socket;
        this.remoteAddr = remoteAddr;
        this.mapper = mapper;

        this.heartbeatThread = Thread.ofVirtual().name("udp-heartbeat-" + remoteAddr).start(this::heartbeatLoop);
    }

    /**
     * Send a NetworkMessage to the remote peer. Serializes to JSON, fragments if needed,
     * sends all fragments, and waits for ACK. Blocks until ACKed or connection fails.
     */
    public void send(NetworkMessage message) throws IOException {
        if (closed) throw new IOException("Connection closed");

        byte[] json = mapper.writeValueAsBytes(message);

        synchronized (sendLock) {
            int seqNum = nextSeqNum++;
            int fragCount = (json.length + MAX_FRAGMENT_PAYLOAD - 1) / MAX_FRAGMENT_PAYLOAD;

            for (int retry = 0; retry <= MAX_RETRIES; retry++) {
                if (closed) throw new IOException("Connection closed during send");

                // Send all fragments
                for (int i = 0; i < fragCount; i++) {
                    int offset = i * MAX_FRAGMENT_PAYLOAD;
                    int len = Math.min(MAX_FRAGMENT_PAYLOAD, json.length - offset);

                    ByteBuffer buf = ByteBuffer.allocate(DATA_HEADER_SIZE + len).order(ByteOrder.BIG_ENDIAN);
                    buf.put(TYPE_DATA);
                    buf.putInt(seqNum);
                    buf.putShort((short) i);
                    buf.putShort((short) fragCount);
                    buf.putShort((short) len);
                    buf.put(json, offset, len);

                    sendRaw(buf.array());
                }
                lastSendTime = System.currentTimeMillis();

                // Wait for ACK
                ackLock.lock();
                try {
                    long deadline = System.currentTimeMillis() + ACK_TIMEOUT_MS;
                    while (lastAckedSeqNum < seqNum) {
                        long remaining = deadline - System.currentTimeMillis();
                        if (remaining <= 0) break;
                        ackReceived.await(remaining, TimeUnit.MILLISECONDS);
                    }
                    if (lastAckedSeqNum >= seqNum) return; // success
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for ACK");
                } finally {
                    ackLock.unlock();
                }

                if (retry < MAX_RETRIES) {
                    log.debug("Retransmitting seqNum {} to {} (attempt {})", seqNum, remoteAddr, retry + 2);
                }
            }

            // All retries exhausted
            close();
            throw new IOException("No ACK after " + MAX_RETRIES + " retries to " + remoteAddr);
        }
    }

    /**
     * Receive the next message. Blocks until a complete message is available,
     * or returns null on timeout (35s) or disconnect.
     */
    public NetworkMessage receive() throws IOException {
        if (closed) return null;
        try {
            byte[] json = inboundQueue.poll(RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (json == null) {
                // Timeout — check if we should close
                if (System.currentTimeMillis() - lastReceiveTime > RECEIVE_TIMEOUT_MS) {
                    log.info("Receive timeout from {} — closing", remoteAddr);
                    close();
                }
                return null;
            }
            // Empty array is a poison pill (disconnect or close signal)
            if (json.length == 0) return null;
            return mapper.readValue(json, NetworkMessage.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // --- Methods called by UdpPeerServer's dispatch loop ---

    void handleFragment(int seqNum, int fragIdx, int fragCount, byte[] payload) {
        lastReceiveTime = System.currentTimeMillis();

        // Duplicate detection
        if (recentlyAcked.contains(seqNum)) {
            sendAck(seqNum); // Re-ACK in case our ACK was lost
            return;
        }

        byte[][] fragments = fragmentBuffers.computeIfAbsent(seqNum, k -> new byte[fragCount][]);
        fragmentCounts.putIfAbsent(seqNum, fragCount);
        fragments[fragIdx] = payload;

        // Check if all fragments received
        int expected = fragmentCounts.get(seqNum);
        boolean complete = true;
        int totalLen = 0;
        for (int i = 0; i < expected; i++) {
            if (fragments[i] == null) {
                complete = false;
                break;
            }
            totalLen += fragments[i].length;
        }

        if (complete) {
            // Reassemble
            byte[] full = new byte[totalLen];
            int pos = 0;
            for (int i = 0; i < expected; i++) {
                System.arraycopy(fragments[i], 0, full, pos, fragments[i].length);
                pos += fragments[i].length;
            }

            fragmentBuffers.remove(seqNum);
            fragmentCounts.remove(seqNum);
            inboundQueue.offer(full);

            // Track for dedup (keep last 100)
            recentlyAcked.add(seqNum);
            if (recentlyAcked.size() > 100) {
                // Remove oldest (approximate — ConcurrentHashMap.newKeySet doesn't preserve order)
                var iter = recentlyAcked.iterator();
                if (iter.hasNext()) { iter.next(); iter.remove(); }
            }

            sendAck(seqNum);
        }
    }

    void handleAck(int seqNum) {
        lastReceiveTime = System.currentTimeMillis();
        ackLock.lock();
        try {
            if (seqNum > lastAckedSeqNum) {
                lastAckedSeqNum = seqNum;
            }
            ackReceived.signalAll();
        } finally {
            ackLock.unlock();
        }
    }

    void handleHeartbeat() {
        lastReceiveTime = System.currentTimeMillis();
    }

    void handleDisconnect() {
        log.debug("Received DISCONNECT from {}", remoteAddr);
        closed = true;
        // Poison the queue so receive() returns null
        inboundQueue.offer(new byte[0]);
        heartbeatThread.interrupt();
    }

    // --- Packet construction ---

    private void sendAck(int seqNum) {
        ByteBuffer buf = ByteBuffer.allocate(ACK_SIZE).order(ByteOrder.BIG_ENDIAN);
        buf.put(TYPE_ACK);
        buf.putInt(seqNum);
        try {
            sendRaw(buf.array());
        } catch (IOException e) {
            log.debug("Failed to send ACK: {}", e.getMessage());
        }
    }

    static byte[] buildConnectPacket() {
        ByteBuffer buf = ByteBuffer.allocate(CONNECT_SIZE).order(ByteOrder.BIG_ENDIAN);
        buf.put(TYPE_CONNECT);
        buf.putInt(MAGIC);
        buf.putShort(PROTOCOL_VERSION);
        buf.putInt(ThreadLocalRandom.current().nextInt());
        return buf.array();
    }

    static byte[] buildConnectAckPacket() {
        ByteBuffer buf = ByteBuffer.allocate(CONNECT_SIZE).order(ByteOrder.BIG_ENDIAN);
        buf.put(TYPE_CONNECT_ACK);
        buf.putInt(MAGIC);
        buf.putShort(PROTOCOL_VERSION);
        buf.putInt(ThreadLocalRandom.current().nextInt());
        return buf.array();
    }

    private void sendRaw(byte[] data) throws IOException {
        socket.send(new DatagramPacket(data, data.length, remoteAddr));
    }

    private void heartbeatLoop() {
        while (!closed) {
            try {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
                if (closed) break;

                // Send heartbeat if we haven't sent data recently
                if (System.currentTimeMillis() - lastSendTime >= HEARTBEAT_INTERVAL_MS) {
                    ByteBuffer buf = ByteBuffer.allocate(HEARTBEAT_SIZE).order(ByteOrder.BIG_ENDIAN);
                    buf.put(TYPE_HEARTBEAT);
                    buf.putInt(MAGIC);
                    sendRaw(buf.array());
                    lastSendTime = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                break;
            } catch (IOException e) {
                if (!closed) log.debug("Heartbeat send failed: {}", e.getMessage());
            }
        }
    }

    // --- Accessors ---

    public String remotePeerId() { return remotePeerId; }
    public void setRemotePeerId(String remotePeerId) { this.remotePeerId = remotePeerId; }

    public String remoteAddress() { return remoteAddr.toString(); }

    public InetSocketAddress remoteSocketAddress() { return remoteAddr; }

    public boolean isClosed() { return closed; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        // Send DISCONNECT (best-effort)
        try {
            ByteBuffer buf = ByteBuffer.allocate(HEARTBEAT_SIZE).order(ByteOrder.BIG_ENDIAN);
            buf.put(TYPE_DISCONNECT);
            buf.putInt(MAGIC);
            sendRaw(buf.array());
        } catch (IOException ignored) {}

        heartbeatThread.interrupt();
        // Wake up any blocked receive()
        inboundQueue.offer(new byte[0]);
        // Wake up any blocked send()
        ackLock.lock();
        try {
            ackReceived.signalAll();
        } finally {
            ackLock.unlock();
        }
    }
}
