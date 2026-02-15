package com.tradery.sharing.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;

/**
 * Minimal STUN client (RFC 5389) for discovering public IP:port mapping.
 * Sends a Binding Request to a public STUN server and parses the
 * XOR-MAPPED-ADDRESS from the response.
 */
public class StunClient {

    private static final Logger log = LoggerFactory.getLogger(StunClient.class);

    private static final String[] STUN_SERVERS = {
            "stun.l.google.com:19302",
            "stun1.l.google.com:19302",
            "stun.cloudflare.com:3478",
    };

    /** STUN servers known to have AAAA records for IPv6 discovery. */
    private static final String[] STUN_SERVERS_V6 = {
            "stun.l.google.com:19302",
            "stun.cloudflare.com:3478",
    };

    private static final int MAGIC_COOKIE = 0x2112A442;
    private static final int TIMEOUT_MS = 3000;

    public record PublicEndpoint(String ip, int port, boolean ipv6) {
        /** Convenience constructor for IPv4 endpoints (backward compat). */
        public PublicEndpoint(String ip, int port) {
            this(ip, port, false);
        }
    }

    /**
     * Discover public IP:port by sending a STUN Binding Request from the given local port.
     * Binds a UDP socket to localPort so the NAT mapping corresponds to that port.
     * Returns null if all STUN servers fail.
     */
    public PublicEndpoint discover(int localPort) {
        for (String server : STUN_SERVERS) {
            try {
                var result = queryStun(server, localPort);
                if (result != null) return result;
            } catch (Exception e) {
                log.debug("STUN: {} failed: {}", server, e.getMessage());
            }
        }
        log.info("STUN: all servers failed");
        return null;
    }

    /**
     * Discover public IP only (binds to any local port).
     */
    public PublicEndpoint discover() {
        return discover(0);
    }

    /**
     * Discover public IP:port using an existing DatagramSocket.
     * Use this to discover the NAT mapping for the actual server socket
     * (must be called before the dispatch loop starts consuming packets).
     */
    public PublicEndpoint discover(DatagramSocket existingSocket) {
        for (String server : STUN_SERVERS) {
            try {
                var result = queryStun(server, existingSocket);
                if (result != null) return result;
            } catch (Exception e) {
                log.debug("STUN: {} failed: {}", server, e.getMessage());
            }
        }
        log.info("STUN: all servers failed");
        return null;
    }

    /**
     * Discover public IPv6 address using an existing (dual-stack) DatagramSocket.
     * Resolves STUN server hostnames to AAAA records and sends the request via IPv6.
     * Returns null if no IPv6 connectivity or all servers fail.
     */
    public PublicEndpoint discoverIpv6(DatagramSocket existingSocket) {
        for (String server : STUN_SERVERS_V6) {
            try {
                String[] parts = server.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);

                // Resolve to IPv6 (AAAA) specifically
                InetAddress[] allAddrs = InetAddress.getAllByName(host);
                InetAddress v6Addr = null;
                for (InetAddress a : allAddrs) {
                    if (a instanceof Inet6Address) {
                        v6Addr = a;
                        break;
                    }
                }
                if (v6Addr == null) continue;

                var result = queryStunAddr(v6Addr, port, existingSocket);
                if (result != null && result.ipv6()) return result;
            } catch (Exception e) {
                log.debug("STUN IPv6: {} failed: {}", server, e.getMessage());
            }
        }
        log.debug("STUN IPv6: all servers failed or no IPv6 connectivity");
        return null;
    }

    private PublicEndpoint queryStun(String server, int localPort) throws Exception {
        try (DatagramSocket socket = new DatagramSocket(localPort)) {
            return queryStun(server, socket);
        }
    }

    private PublicEndpoint queryStun(String server, DatagramSocket socket) throws Exception {
        String[] parts = server.split(":");
        InetAddress addr = InetAddress.getByName(parts[0]);
        int port = Integer.parseInt(parts[1]);
        return queryStunAddr(addr, port, socket);
    }

    private PublicEndpoint queryStunAddr(InetAddress addr, int port, DatagramSocket socket) throws Exception {
        // Build Binding Request (20 bytes header, no attributes)
        byte[] txnId = new byte[12];
        new SecureRandom().nextBytes(txnId);

        ByteBuffer request = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN);
        request.putShort((short) 0x0001);  // Binding Request
        request.putShort((short) 0x0000);  // Length (no attributes)
        request.putInt(MAGIC_COOKIE);
        request.put(txnId);

        int oldTimeout = socket.getSoTimeout();
        socket.setSoTimeout(TIMEOUT_MS);
        try {
            socket.send(new DatagramPacket(request.array(), 20, addr, port));

            byte[] buf = new byte[512];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);

            return parseResponse(ByteBuffer.wrap(response.getData(), 0, response.getLength()));
        } finally {
            socket.setSoTimeout(oldTimeout);
        }
    }

    private PublicEndpoint parseResponse(ByteBuffer buf) {
        buf.order(ByteOrder.BIG_ENDIAN);

        short type = buf.getShort();
        if (type != 0x0101) return null;  // Not a Binding Response

        short length = buf.getShort();
        int cookie = buf.getInt();
        buf.position(buf.position() + 12);  // skip transaction ID

        // Parse attributes
        int end = 20 + length;
        while (buf.position() < end) {
            short attrType = buf.getShort();
            short attrLength = buf.getShort();
            int attrStart = buf.position();

            // XOR-MAPPED-ADDRESS (0x0020) or MAPPED-ADDRESS (0x0001)
            if (attrType == 0x0020) {
                return parseXorMappedAddress(buf, cookie);
            } else if (attrType == 0x0001) {
                return parseMappedAddress(buf);
            }

            // Skip to next attribute (padded to 4 bytes)
            buf.position(attrStart + ((attrLength + 3) & ~3));
        }

        return null;
    }

    private PublicEndpoint parseXorMappedAddress(ByteBuffer buf, int cookie) {
        buf.get();  // reserved
        byte family = buf.get();

        int xorPort = Short.toUnsignedInt(buf.getShort()) ^ (cookie >>> 16);

        if (family == 0x01) {
            // IPv4: 4-byte address XOR'd with magic cookie
            int xorIp = buf.getInt() ^ cookie;
            String ip = ((xorIp >> 24) & 0xFF) + "." + ((xorIp >> 16) & 0xFF) + "."
                    + ((xorIp >> 8) & 0xFF) + "." + (xorIp & 0xFF);
            return new PublicEndpoint(ip, xorPort, false);
        } else if (family == 0x02) {
            // IPv6: 16-byte address XOR'd with magic cookie (4B) + transaction ID (12B)
            // We need the transaction ID from the response — it's at bytes 8-19 of the response.
            // Since we don't have it here, we use the XOR mask from position in the buffer.
            // The full 16-byte XOR mask = cookie (4 bytes big-endian) + txnId (12 bytes).
            // However, we already consumed the header. Reconstruct from the ByteBuffer's backing array.
            byte[] xorMask = new byte[16];
            xorMask[0] = (byte) (cookie >> 24);
            xorMask[1] = (byte) (cookie >> 16);
            xorMask[2] = (byte) (cookie >> 8);
            xorMask[3] = (byte) cookie;
            // Transaction ID is at offset 8 in the original response
            byte[] backing = buf.array();
            System.arraycopy(backing, 8, xorMask, 4, 12);

            byte[] addrBytes = new byte[16];
            buf.get(addrBytes);
            for (int i = 0; i < 16; i++) {
                addrBytes[i] ^= xorMask[i];
            }

            try {
                InetAddress addr = InetAddress.getByAddress(addrBytes);
                return new PublicEndpoint(addr.getHostAddress(), xorPort, true);
            } catch (java.net.UnknownHostException e) {
                return null;
            }
        }
        return null;
    }

    private PublicEndpoint parseMappedAddress(ByteBuffer buf) {
        buf.get();  // reserved
        byte family = buf.get();

        int port = Short.toUnsignedInt(buf.getShort());

        if (family == 0x01) {
            int ip = buf.getInt();
            String ipStr = ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "."
                    + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
            return new PublicEndpoint(ipStr, port, false);
        } else if (family == 0x02) {
            byte[] addrBytes = new byte[16];
            buf.get(addrBytes);
            try {
                InetAddress addr = InetAddress.getByAddress(addrBytes);
                return new PublicEndpoint(addr.getHostAddress(), port, true);
            } catch (java.net.UnknownHostException e) {
                return null;
            }
        }
        return null;
    }
}
