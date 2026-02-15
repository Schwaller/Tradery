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

    private static final String MULTICAST_GROUP_V4 = "239.77.84.82"; // "MTRD" in decimal
    private static final String MULTICAST_GROUP_V6 = "ff02::1:504c"; // link-local scope
    private static final int MULTICAST_PORT = 7482;
    private static final int ANNOUNCE_INTERVAL_MS = 30_000;
    private static final int PEER_TIMEOUT_MS = 90_000;

    private final String peerId;
    private final String deviceId;
    private final int serverPort;
    private final Map<String, LanPeer> lanPeers = new ConcurrentHashMap<>();

    private MulticastSocket socketV4;
    private MulticastSocket socketV6;
    private InetSocketAddress groupV4;
    private InetSocketAddress groupV6;
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
        networkInterface = findNetworkInterface();
        running = true;

        // IPv4 multicast
        groupV4 = new InetSocketAddress(InetAddress.getByName(MULTICAST_GROUP_V4), MULTICAST_PORT);
        socketV4 = new MulticastSocket(null);
        socketV4.setReuseAddress(true);
        socketV4.bind(new InetSocketAddress(MULTICAST_PORT));
        socketV4.setNetworkInterface(networkInterface);
        socketV4.joinGroup(groupV4, networkInterface);

        Thread.ofVirtual().name("lan-discovery-listen-v4").start(() -> listenLoop(socketV4));

        // IPv6 multicast (best-effort — skip if not available)
        try {
            groupV6 = new InetSocketAddress(InetAddress.getByName(MULTICAST_GROUP_V6), MULTICAST_PORT);
            socketV6 = new MulticastSocket(null);
            socketV6.setReuseAddress(true);
            socketV6.bind(new InetSocketAddress(MULTICAST_PORT + 1)); // separate port to avoid conflict with v4
            socketV6.setNetworkInterface(networkInterface);
            socketV6.joinGroup(groupV6, networkInterface);
            Thread.ofVirtual().name("lan-discovery-listen-v6").start(() -> listenLoop(socketV6));
            log.info("LAN discovery started on {} + {} (dual-stack)", MULTICAST_GROUP_V4, MULTICAST_GROUP_V6);
        } catch (IOException e) {
            log.debug("IPv6 multicast not available: {}", e.getMessage());
            socketV6 = null;
            groupV6 = null;
            log.info("LAN discovery started on {} (IPv4 only)", MULTICAST_GROUP_V4);
        }

        // Announcer thread (sends on both groups)
        Thread.ofVirtual().name("lan-discovery-announce").start(this::announceLoop);
    }

    /**
     * Get currently visible LAN peers (excluding expired ones).
     */
    public List<LanPeer> activePeers() {
        long now = System.currentTimeMillis();
        lanPeers.values().removeIf(p -> now - p.lastSeen > PEER_TIMEOUT_MS);
        return List.copyOf(lanPeers.values());
    }

    private void listenLoop(MulticastSocket sock) {
        byte[] buf = new byte[512];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                sock.receive(packet);
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
                // Announce on IPv4
                DatagramPacket packetV4 = new DatagramPacket(data, data.length,
                        InetAddress.getByName(MULTICAST_GROUP_V4), MULTICAST_PORT);
                socketV4.send(packetV4);

                // Announce on IPv6 (if available)
                if (socketV6 != null && groupV6 != null) {
                    try {
                        DatagramPacket packetV6 = new DatagramPacket(data, data.length,
                                InetAddress.getByName(MULTICAST_GROUP_V6), MULTICAST_PORT + 1);
                        socketV6.send(packetV6);
                    } catch (IOException e) {
                        log.debug("LAN announce IPv6 error: {}", e.getMessage());
                    }
                }

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
                // Accept interface with IPv4 or IPv6 address
                boolean hasIp = false;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address || addr instanceof java.net.Inet6Address) {
                        hasIp = true;
                        break;
                    }
                }
                if (hasIp) return ni;
            }
        }
        throw new SocketException("No suitable network interface found for multicast");
    }

    @Override
    public void close() {
        running = false;
        if (socketV4 != null) {
            try { socketV4.leaveGroup(groupV4, networkInterface); } catch (IOException ignored) {}
            socketV4.close();
        }
        if (socketV6 != null) {
            try { socketV6.leaveGroup(groupV6, networkInterface); } catch (IOException ignored) {}
            socketV6.close();
        }
    }

    public record LanPeer(String peerId, String deviceId, String host, int port, long lastSeen) {}
}
