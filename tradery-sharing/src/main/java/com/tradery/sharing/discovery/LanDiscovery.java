package com.tradery.sharing.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mDNS-style LAN discovery using UDP multicast.
 * Peers on the same local network can discover each other without a rendezvous server.
 *
 * Protocol: peers broadcast "{peerId}|{deviceId}:{port}" to a multicast group every 30 seconds.
 * Listeners collect announcements and maintain a list of known LAN peers.
 * Self-filter uses deviceId (not peerId) so the same user on different devices can discover each other.
 */
public class LanDiscovery implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LanDiscovery.class);

    private static final String MULTICAST_GROUP = "239.77.84.82"; // "MTRD" in decimal
    private static final int MULTICAST_PORT = 7482;
    private static final int ANNOUNCE_INTERVAL_MS = 30_000;
    private static final int PEER_TIMEOUT_MS = 90_000;

    private final String peerId;
    private final String deviceId;
    private final int serverPort;
    private final Map<String, LanPeer> lanPeers = new ConcurrentHashMap<>();

    private MulticastSocket socket;
    private InetSocketAddress group;
    private NetworkInterface networkInterface;
    private volatile boolean running;

    public LanDiscovery(String peerId, String deviceId, int serverPort) {
        this.peerId = peerId;
        this.deviceId = deviceId;
        this.serverPort = serverPort;
    }

    /**
     * Start broadcasting and listening for LAN peers.
     */
    public void start() throws IOException {
        socket = new MulticastSocket(MULTICAST_PORT);
        group = new InetSocketAddress(InetAddress.getByName(MULTICAST_GROUP), MULTICAST_PORT);
        networkInterface = findNetworkInterface();
        socket.joinGroup(group, networkInterface);
        running = true;

        // Listener thread
        Thread.ofVirtual().name("lan-discovery-listen").start(this::listenLoop);
        // Announcer thread
        Thread.ofVirtual().name("lan-discovery-announce").start(this::announceLoop);

        log.info("LAN discovery started on {}:{}", MULTICAST_GROUP, MULTICAST_PORT);
    }

    /**
     * Get currently visible LAN peers (excluding expired ones).
     */
    public List<LanPeer> activePeers() {
        long now = System.currentTimeMillis();
        lanPeers.values().removeIf(p -> now - p.lastSeen > PEER_TIMEOUT_MS);
        return List.copyOf(lanPeers.values());
    }

    private void listenLoop() {
        byte[] buf = new byte[512];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                // Wire format: "{peerId}|{deviceId}:{port}"
                int colonIdx = data.lastIndexOf(':');
                if (colonIdx < 0) continue;
                String identity = data.substring(0, colonIdx);
                int remotePort = Integer.parseInt(data.substring(colonIdx + 1));

                int pipeIdx = identity.indexOf('|');
                String remotePeerId;
                String remoteDeviceId;
                if (pipeIdx >= 0) {
                    remotePeerId = identity.substring(0, pipeIdx);
                    remoteDeviceId = identity.substring(pipeIdx + 1);
                } else {
                    // Backward compat: old format "{peerId}:{port}"
                    remotePeerId = identity;
                    remoteDeviceId = identity;
                }

                // Filter by deviceId so same user on different devices can see each other
                if (!remoteDeviceId.equals(deviceId)) {
                    String host = packet.getAddress().getHostAddress();
                    lanPeers.put(remoteDeviceId, new LanPeer(remotePeerId, remoteDeviceId, host, remotePort, System.currentTimeMillis()));
                }
            } catch (IOException e) {
                if (running) {
                    log.debug("LAN listen error: {}", e.getMessage());
                }
            }
        }
    }

    private void announceLoop() {
        byte[] data = (peerId + "|" + deviceId + ":" + serverPort).getBytes(StandardCharsets.UTF_8);
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(data, data.length,
                        InetAddress.getByName(MULTICAST_GROUP), MULTICAST_PORT);
                socket.send(packet);
                Thread.sleep(ANNOUNCE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                if (running) {
                    log.debug("LAN announce error: {}", e.getMessage());
                }
            }
        }
    }

    private NetworkInterface findNetworkInterface() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) {
                return ni;
            }
        }
        throw new SocketException("No suitable network interface found for multicast");
    }

    @Override
    public void close() {
        running = false;
        if (socket != null) {
            try { socket.leaveGroup(group, networkInterface); } catch (IOException ignored) {}
            socket.close();
        }
    }

    public record LanPeer(String peerId, String deviceId, String host, int port, long lastSeen) {}
}
