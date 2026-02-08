package com.tradery.sharing.sync;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * A single peer connection — reads and writes length-prefixed JSON messages
 * over a TCP socket. Each message is: [4-byte big-endian length][UTF-8 JSON].
 */
public class PeerConnection implements AutoCloseable {

    private static final int MAX_MESSAGE_SIZE = 64 * 1024 * 1024; // 64 MB

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ObjectMapper mapper;

    private String remotePeerId;
    private volatile boolean closed;

    public PeerConnection(Socket socket, ObjectMapper mapper) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.mapper = mapper;
    }

    /** Send a message to the remote peer. */
    public synchronized void send(NetworkMessage message) throws IOException {
        byte[] json = mapper.writeValueAsBytes(message);
        out.writeInt(json.length);
        out.write(json);
        out.flush();
    }

    /** Read the next message from the remote peer. Returns null on EOF. */
    public NetworkMessage receive() throws IOException {
        int length;
        try {
            length = in.readInt();
        } catch (EOFException e) {
            return null;
        }
        if (length < 0 || length > MAX_MESSAGE_SIZE) {
            throw new IOException("Invalid message length: " + length);
        }
        byte[] json = new byte[length];
        in.readFully(json);
        return mapper.readValue(json, NetworkMessage.class);
    }

    public String remotePeerId() { return remotePeerId; }
    public void setRemotePeerId(String remotePeerId) { this.remotePeerId = remotePeerId; }

    public String remoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    public boolean isClosed() {
        return closed || socket.isClosed();
    }

    @Override
    public void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) {}
    }
}
