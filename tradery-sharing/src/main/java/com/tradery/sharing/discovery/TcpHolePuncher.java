package com.tradery.sharing.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;

/**
 * TCP hole puncher using simultaneous open.
 *
 * Binds an outbound TCP socket to the same local port as PeerServer
 * (using SO_REUSEADDR + SO_REUSEPORT) and attempts to connect to the
 * remote peer's public endpoint. This creates a NAT mapping so that
 * the remote peer's reciprocal connection attempt can get through.
 *
 * For this to work:
 * - PeerServer's ServerSocket must have SO_REUSEADDR enabled
 * - Both peers must attempt to connect roughly simultaneously
 * - The NAT must support Endpoint Independent Mapping (most residential NATs do)
 */
public class TcpHolePuncher {

    private static final Logger log = LoggerFactory.getLogger(TcpHolePuncher.class);

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int ATTEMPTS = 10;
    private static final int RETRY_DELAY_MS = 2000;

    /**
     * Attempt TCP hole punch to a remote peer. Tries multiple times over ~20 seconds
     * to overlap with the remote peer's reciprocal punch attempts.
     *
     * @param localPort   PeerServer's local port (we bind to the same port)
     * @param remoteHost  remote peer's public IP
     * @param remotePort  remote peer's announced port
     * @return connected Socket on success, null on failure
     */
    public Socket punch(int localPort, String remoteHost, int remotePort) {
        log.info("Hole punch: attempting {} → {}:{} ({} attempts)", localPort, remoteHost, remotePort, ATTEMPTS);

        for (int i = 0; i < ATTEMPTS; i++) {
            try {
                SocketChannel ch = SocketChannel.open();
                ch.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                ch.setOption(StandardSocketOptions.SO_REUSEPORT, true);
                ch.bind(new InetSocketAddress(localPort));

                Socket socket = ch.socket();
                socket.connect(new InetSocketAddress(remoteHost, remotePort), CONNECT_TIMEOUT_MS);

                log.info("Hole punch: connected to {}:{} on attempt {}", remoteHost, remotePort, i + 1);
                return socket;

            } catch (IOException e) {
                log.debug("Hole punch: attempt {} failed: {}", i + 1, e.getMessage());
                if (i < ATTEMPTS - 1) {
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.info("Hole punch: failed to reach {}:{} after {} attempts", remoteHost, remotePort, ATTEMPTS);
        return null;
    }
}
