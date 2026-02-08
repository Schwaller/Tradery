package com.tradery.sharing.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.documents.DocumentManager;
import com.tradery.documents.DocumentWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages the lifecycle of peer connections and orchestrates sync.
 * Tracks active connections, handles HELLO handshake, and routes
 * messages to the SyncEngine for each shared document.
 */
public class PeerManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PeerManager.class);

    private final String localPeerId;
    private final String localDeviceId;
    private final DocumentManager documentManager;
    private final SyncEngine syncEngine;
    private final ObjectMapper mapper;
    private final PeerServer server;

    /** Active connections indexed by remote device ID. */
    private final Map<String, PeerConnection> connections = new ConcurrentHashMap<>();

    /** Maps remote device ID to peer email (for member matching). */
    private final Map<String, String> deviceToPeerEmail = new ConcurrentHashMap<>();

    /** Open document workspaces by document ID. */
    private final Map<String, DocumentWorkspace> workspaces = new ConcurrentHashMap<>();

    /** Chat message listeners. */
    private final List<Consumer<NetworkMessage.ChatMessage>> chatListeners = new CopyOnWriteArrayList<>();

    public PeerManager(String localPeerId, String localDeviceId, DocumentManager documentManager, ObjectMapper mapper) throws IOException {
        this.localPeerId = localPeerId;
        this.localDeviceId = localDeviceId;
        this.documentManager = documentManager;
        this.syncEngine = new SyncEngine();
        this.mapper = mapper;
        this.server = new PeerServer(mapper, this::handleIncomingConnection);
    }

    public int serverPort() { return server.port(); }

    /**
     * Register an open document workspace for sync.
     */
    public void registerWorkspace(String documentId, DocumentWorkspace workspace) {
        workspaces.put(documentId, workspace);
    }

    public void unregisterWorkspace(String documentId) {
        workspaces.remove(documentId);
    }

    /**
     * Connect to a remote peer and sync all shared documents.
     */
    public void connectAndSync(String host, int port) {
        Thread.ofVirtual().name("peer-connect-" + host + ":" + port).start(() -> {
            try {
                Socket socket = new Socket(host, port);
                PeerConnection conn = new PeerConnection(socket, mapper);
                handleOutgoingConnection(conn);
            } catch (IOException e) {
                log.warn("Failed to connect to {}:{}: {}", host, port, e.getMessage());
            }
        });
    }

    /**
     * Handle an incoming connection (called by PeerServer).
     */
    private void handleIncomingConnection(PeerConnection conn) {
        try {
            // Wait for HELLO
            NetworkMessage msg = conn.receive();
            if (!(msg instanceof NetworkMessage.Hello hello)) {
                log.warn("Expected HELLO from {}, got {}", conn.remoteAddress(),
                        msg != null ? msg.getClass().getSimpleName() : "null");
                return;
            }

            String remoteKey = hello.deviceId() != null ? hello.deviceId() : hello.peerId();
            conn.setRemotePeerId(remoteKey);
            connections.put(remoteKey, conn);
            deviceToPeerEmail.put(remoteKey, hello.peerId());
            log.info("Peer {} (device {}) connected from {}", hello.peerId(), remoteKey, conn.remoteAddress());

            // Send our HELLO back
            conn.send(new NetworkMessage.Hello(
                    localPeerId, localDeviceId, null, null,
                    List.copyOf(workspaces.keySet())));

            // Also request sync for shared documents (bidirectional sync)
            Set<String> sharedDocs = new HashSet<>(workspaces.keySet());
            sharedDocs.retainAll(new HashSet<>(hello.documentIds()));

            for (String docId : sharedDocs) {
                DocumentWorkspace ws = workspaces.get(docId);
                if (ws != null) {
                    NetworkMessage.SyncRequest req = syncEngine.createSyncRequest(ws, docId, hello.peerId());
                    conn.send(req);
                }
            }

            // Process messages
            processMessages(conn, hello);

        } catch (IOException e) {
            log.warn("Error with incoming peer {}: {}", conn.remoteAddress(), e.getMessage());
        } finally {
            if (conn.remotePeerId() != null) {
                connections.remove(conn.remotePeerId());
                deviceToPeerEmail.remove(conn.remotePeerId());
            }
        }
    }

    /**
     * Handle an outgoing connection (we initiated).
     */
    private void handleOutgoingConnection(PeerConnection conn) {
        try {
            // Send HELLO
            conn.send(new NetworkMessage.Hello(
                    localPeerId, localDeviceId, null, null,
                    List.copyOf(workspaces.keySet())));

            // Wait for HELLO back
            NetworkMessage msg = conn.receive();
            if (!(msg instanceof NetworkMessage.Hello hello)) {
                log.warn("Expected HELLO from {}, got {}", conn.remoteAddress(),
                        msg != null ? msg.getClass().getSimpleName() : "null");
                return;
            }

            String remoteKey = hello.deviceId() != null ? hello.deviceId() : hello.peerId();
            conn.setRemotePeerId(remoteKey);
            connections.put(remoteKey, conn);
            deviceToPeerEmail.put(remoteKey, hello.peerId());
            log.info("Connected to peer {} (device {}) at {}", hello.peerId(), remoteKey, conn.remoteAddress());

            // Find shared documents and request sync
            Set<String> sharedDocs = new HashSet<>(workspaces.keySet());
            sharedDocs.retainAll(new HashSet<>(hello.documentIds()));

            for (String docId : sharedDocs) {
                DocumentWorkspace ws = workspaces.get(docId);
                if (ws != null) {
                    NetworkMessage.SyncRequest req = syncEngine.createSyncRequest(ws, docId, hello.peerId());
                    conn.send(req);
                }
            }

            // Process messages
            processMessages(conn, hello);

        } catch (IOException e) {
            log.warn("Error with outgoing peer {}: {}", conn.remoteAddress(), e.getMessage());
        } finally {
            if (conn.remotePeerId() != null) {
                connections.remove(conn.remotePeerId());
                deviceToPeerEmail.remove(conn.remotePeerId());
            }
        }
    }

    /**
     * Message processing loop — handles sync requests/responses until disconnect.
     */
    private void processMessages(PeerConnection conn, NetworkMessage.Hello hello) throws IOException {
        while (!conn.isClosed()) {
            NetworkMessage msg = conn.receive();
            if (msg == null) break; // EOF

            switch (msg) {
                case NetworkMessage.SyncRequest req -> {
                    DocumentWorkspace ws = workspaces.get(req.documentId());
                    if (ws != null) {
                        NetworkMessage.SyncResponse resp = syncEngine.handleSyncRequest(ws, req);
                        conn.send(resp);
                        conn.send(new NetworkMessage.SyncDone(req.documentId()));
                    }
                }
                case NetworkMessage.SyncResponse resp -> {
                    DocumentWorkspace ws = workspaces.get(resp.documentId());
                    if (ws != null) {
                        syncEngine.handleSyncResponse(ws, conn.remotePeerId(), resp);
                    }
                }
                case NetworkMessage.SyncDone done ->
                    log.debug("Sync complete for doc {} from {}", done.documentId(), conn.remotePeerId());
                case NetworkMessage.MemberUpdate update ->
                    log.info("Member update for doc {} from {}", update.documentId(), conn.remotePeerId());
                case NetworkMessage.ChatMessage chat -> {
                    log.debug("Chat from {}: {}", chat.senderId(), chat.text());
                    for (var listener : chatListeners) {
                        try { listener.accept(chat); } catch (Exception ex) { log.warn("Chat listener error", ex); }
                    }
                }
                case NetworkMessage.Hello ignored ->
                    log.warn("Unexpected HELLO from {}", conn.remotePeerId());
            }
        }
    }

    /**
     * Push local data to all connected peers and request their latest data.
     * Sends SyncResponses with our facts and SyncRequests to pull theirs.
     */
    public void requestSync() {
        for (Map.Entry<String, PeerConnection> entry : connections.entrySet()) {
            String remotePeerId = entry.getKey();
            PeerConnection conn = entry.getValue();
            if (conn.isClosed()) continue;

            for (Map.Entry<String, DocumentWorkspace> wsEntry : workspaces.entrySet()) {
                String docId = wsEntry.getKey();
                DocumentWorkspace ws = wsEntry.getValue();
                try {
                    // Push our facts to remote
                    NetworkMessage.SyncResponse push = syncEngine.handleSyncRequest(ws,
                            new NetworkMessage.SyncRequest(docId, 0));
                    if (!push.facts().isEmpty()) {
                        conn.send(push);
                    }

                    // Pull their facts
                    NetworkMessage.SyncRequest req = syncEngine.createSyncRequest(ws, docId, remotePeerId);
                    conn.send(req);
                } catch (IOException e) {
                    log.warn("Failed to sync with {}: {}", remotePeerId, e.getMessage());
                }
            }
        }
    }

    /** Returns connected peer emails (for member matching). */
    public Collection<String> connectedPeerIds() {
        return Collections.unmodifiableCollection(deviceToPeerEmail.values());
    }

    /** Returns connected device IDs (for dedup). */
    public Collection<String> connectedDeviceIds() {
        return Collections.unmodifiableCollection(connections.keySet());
    }

    /** Broadcast a chat message to all connected peers. */
    public void broadcastChat(String senderId, String text) {
        var msg = new NetworkMessage.ChatMessage(senderId, text, System.currentTimeMillis());
        for (PeerConnection conn : connections.values()) {
            if (conn.isClosed()) continue;
            try {
                conn.send(msg);
            } catch (IOException e) {
                log.warn("Failed to send chat to {}: {}", conn.remotePeerId(), e.getMessage());
            }
        }
    }

    public void addChatListener(Consumer<NetworkMessage.ChatMessage> listener) {
        chatListeners.add(listener);
    }

    public void removeChatListener(Consumer<NetworkMessage.ChatMessage> listener) {
        chatListeners.remove(listener);
    }

    @Override
    public void close() {
        server.close();
        connections.values().forEach(PeerConnection::close);
        connections.clear();
    }
}
