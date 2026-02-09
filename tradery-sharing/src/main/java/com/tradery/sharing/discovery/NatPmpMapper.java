package com.tradery.sharing.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * NAT-PMP (RFC 6886) port mapper. Simpler and more reliable than UPnP on many routers,
 * especially Apple AirPort and Fritz!Box. Binary UDP protocol on gateway:5351.
 */
public class NatPmpMapper {

    private static final Logger log = LoggerFactory.getLogger(NatPmpMapper.class);

    private static final int NAT_PMP_PORT = 5351;
    private static final int TIMEOUT_MS = 3000;
    private static final int LEASE_SECONDS = 3600;

    private InetAddress gateway;
    private int mappedInternalPort;

    public record Mapping(String externalIp, int externalPort) {}

    /**
     * Try to map localPort via NAT-PMP. Returns Mapping on success, null if unavailable.
     */
    public Mapping mapPort(int localPort) {
        try {
            gateway = discoverGateway();
            if (gateway == null) {
                log.info("NAT-PMP: could not determine gateway");
                return null;
            }
            log.debug("NAT-PMP: trying gateway {}", gateway.getHostAddress());

            // 1. Get external IP
            String externalIp = getExternalIp();
            if (externalIp == null) {
                log.info("NAT-PMP: gateway did not respond");
                return null;
            }

            // 2. Request TCP port mapping
            int externalPort = requestMapping(localPort);
            if (externalPort <= 0) {
                log.info("NAT-PMP: port mapping request failed");
                return null;
            }

            mappedInternalPort = localPort;
            log.info("NAT-PMP: mapped port {} → {}:{}", localPort, externalIp, externalPort);
            return new Mapping(externalIp, externalPort);

        } catch (Exception e) {
            log.info("NAT-PMP: port mapping failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Remove the port mapping (call on shutdown). Sends a mapping request with lifetime=0.
     */
    public void unmap() {
        if (gateway == null || mappedInternalPort == 0) return;
        try {
            // Opcode 2 = TCP, lifetime 0 = delete
            ByteBuffer req = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
            req.put((byte) 0);  // version
            req.put((byte) 2);  // opcode: TCP
            req.putShort((short) 0);  // reserved
            req.putShort((short) mappedInternalPort);
            req.putShort((short) 0);  // suggested external port
            req.putInt(0);  // lifetime = 0 (delete)

            sendAndReceive(req.array());
            log.info("NAT-PMP: unmapped port {}", mappedInternalPort);
        } catch (Exception e) {
            log.debug("NAT-PMP: unmap failed: {}", e.getMessage());
        }
    }

    private String getExternalIp() throws IOException {
        // Opcode 0 = get external address
        byte[] request = {0x00, 0x00};  // version=0, opcode=0
        byte[] response = sendAndReceive(request);
        if (response == null || response.length < 12) return null;

        ByteBuffer buf = ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN);
        buf.get();  // version
        buf.get();  // opcode (128)
        short resultCode = buf.getShort();
        if (resultCode != 0) {
            log.debug("NAT-PMP: external IP request failed with code {}", resultCode);
            return null;
        }
        buf.getInt();  // epoch

        // 4 bytes of IP address
        byte[] ip = new byte[4];
        buf.get(ip);
        return InetAddress.getByAddress(ip).getHostAddress();
    }

    private int requestMapping(int internalPort) throws IOException {
        // Opcode 2 = TCP mapping
        ByteBuffer req = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
        req.put((byte) 0);  // version
        req.put((byte) 2);  // opcode: TCP
        req.putShort((short) 0);  // reserved
        req.putShort((short) internalPort);
        req.putShort((short) internalPort);  // suggested external port = same
        req.putInt(LEASE_SECONDS);

        byte[] response = sendAndReceive(req.array());
        if (response == null || response.length < 16) return -1;

        ByteBuffer buf = ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN);
        buf.get();  // version
        buf.get();  // opcode (130)
        short resultCode = buf.getShort();
        if (resultCode != 0) {
            log.debug("NAT-PMP: mapping request failed with code {}", resultCode);
            return -1;
        }
        buf.getInt();  // epoch
        buf.getShort();  // internal port echo
        int mappedPort = Short.toUnsignedInt(buf.getShort());  // mapped external port
        // buf.getInt() = lifetime

        return mappedPort;
    }

    private byte[] sendAndReceive(byte[] request) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            socket.send(new DatagramPacket(request, request.length, gateway, NAT_PMP_PORT));

            byte[] buf = new byte[128];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);
            byte[] result = new byte[response.getLength()];
            System.arraycopy(response.getData(), 0, result, 0, response.getLength());
            return result;
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    /**
     * Discover the default gateway by deriving from local IP (x.x.x.1).
     * Works for the vast majority of home networks.
     */
    private InetAddress discoverGateway() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            String localIp = socket.getLocalAddress().getHostAddress();
            String gatewayIp = localIp.substring(0, localIp.lastIndexOf('.') + 1) + "1";
            return InetAddress.getByName(gatewayIp);
        } catch (Exception e) {
            return null;
        }
    }
}
