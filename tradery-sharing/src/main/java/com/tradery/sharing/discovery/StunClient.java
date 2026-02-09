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

    private static final int MAGIC_COOKIE = 0x2112A442;
    private static final int TIMEOUT_MS = 3000;

    public record PublicEndpoint(String ip, int port) {}

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

    private PublicEndpoint queryStun(String server, int localPort) throws Exception {
        String[] parts = server.split(":");
        InetAddress addr = InetAddress.getByName(parts[0]);
        int port = Integer.parseInt(parts[1]);

        // Build Binding Request (20 bytes header, no attributes)
        byte[] txnId = new byte[12];
        new SecureRandom().nextBytes(txnId);

        ByteBuffer request = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN);
        request.putShort((short) 0x0001);  // Binding Request
        request.putShort((short) 0x0000);  // Length (no attributes)
        request.putInt(MAGIC_COOKIE);
        request.put(txnId);

        try (DatagramSocket socket = new DatagramSocket(localPort)) {
            socket.setSoTimeout(TIMEOUT_MS);
            socket.send(new DatagramPacket(request.array(), 20, addr, port));

            byte[] buf = new byte[512];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);

            return parseResponse(ByteBuffer.wrap(response.getData(), 0, response.getLength()));
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
        if (family != 0x01) return null;  // IPv4 only

        int xorPort = Short.toUnsignedInt(buf.getShort()) ^ (cookie >>> 16);
        int xorIp = buf.getInt() ^ cookie;

        String ip = ((xorIp >> 24) & 0xFF) + "." + ((xorIp >> 16) & 0xFF) + "."
                + ((xorIp >> 8) & 0xFF) + "." + (xorIp & 0xFF);
        return new PublicEndpoint(ip, xorPort);
    }

    private PublicEndpoint parseMappedAddress(ByteBuffer buf) {
        buf.get();  // reserved
        byte family = buf.get();
        if (family != 0x01) return null;

        int port = Short.toUnsignedInt(buf.getShort());
        int ip = buf.getInt();

        String ipStr = ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "."
                + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
        return new PublicEndpoint(ipStr, port);
    }
}
