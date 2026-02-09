package com.tradery.sharing.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.documents.DocumentManager;
import com.tradery.documents.DocumentWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradery.news.ui.FriendConfig;
import com.tradery.news.ui.IntelConfig;

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

    /** Tracks whether each connected peer (by device ID) has mutual friendship with us. */
    private final Map<String, Boolean> mutualFriends = new ConcurrentHashMap<>();

    /** Tracks whether each connected peer has us in their friend list (their last FriendshipAck). */
    private final Map<String, Boolean> peerFriendsUs = new ConcurrentHashMap<>();

    /** Callback when friendship status changes (e.g. FriendshipAck received). */
    private volatile Runnable friendshipChangeCallback;

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
     * Accept a pre-connected socket (e.g. from TCP hole punching) and sync.
     */
    public void connectWithSocket(Socket socket) {
        Thread.ofVirtual().name("peer-punch-" + socket.getRemoteSocketAddress()).start(() -> {
            try {
                PeerConnection conn = new PeerConnection(socket, mapper);
                handleOutgoingConnection(conn);
            } catch (IOException e) {
                log.warn("Failed to use punched connection to {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
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

            // Exchange friendship status
            boolean weFriendThem = isFriendLocally(hello.peerId());
            conn.send(new NetworkMessage.FriendshipAck(weFriendThem));

            // Wait for their FriendshipAck
            NetworkMessage ackMsg = conn.receive();
            boolean theyFriendUs = false;
            if (ackMsg instanceof NetworkMessage.FriendshipAck ack) {
                theyFriendUs = ack.isFriend();
            } else if (ackMsg != null) {
                // They may be running an older version without FriendshipAck — push back for processing
                log.debug("Peer {} did not send FriendshipAck, got {}", remoteKey, ackMsg.getClass().getSimpleName());
            }

            boolean mutual = weFriendThem && theyFriendUs;
            peerFriendsUs.put(remoteKey, theyFriendUs);
            mutualFriends.put(remoteKey, mutual);
            log.info("Friendship with {} ({}): we={}, they={}, mutual={}", hello.peerId(), remoteKey, weFriendThem, theyFriendUs, mutual);
            if (friendshipChangeCallback != null) friendshipChangeCallback.run();

            // Only sync documents if mutual friends
            if (mutual) {
                Set<String> sharedDocs = new HashSet<>(workspaces.keySet());
                sharedDocs.retainAll(new HashSet<>(hello.documentIds()));
                for (String docId : sharedDocs) {
                    DocumentWorkspace ws = workspaces.get(docId);
                    if (ws != null) {
                        NetworkMessage.SyncRequest req = syncEngine.createSyncRequest(ws, docId, hello.peerId());
                        conn.send(req);
                    }
                }
            }

            // If ackMsg was not a FriendshipAck, process it as a regular message first
            if (ackMsg != null && !(ackMsg instanceof NetworkMessage.FriendshipAck)) {
                handleMessage(conn, hello, ackMsg);
            }

            // Process messages
            processMessages(conn, hello);

        } catch (IOException e) {
            log.warn("Error with incoming peer {}: {}", conn.remoteAddress(), e.getMessage());
        } finally {
            if (conn.remotePeerId() != null) {
                connections.remove(conn.remotePeerId());
                deviceToPeerEmail.remove(conn.remotePeerId());
                mutualFriends.remove(conn.remotePeerId());
                peerFriendsUs.remove(conn.remotePeerId());
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

            // Exchange friendship status
            boolean weFriendThem = isFriendLocally(hello.peerId());
            conn.send(new NetworkMessage.FriendshipAck(weFriendThem));

            // Wait for their FriendshipAck
            NetworkMessage ackMsg = conn.receive();
            boolean theyFriendUs = false;
            if (ackMsg instanceof NetworkMessage.FriendshipAck ack) {
                theyFriendUs = ack.isFriend();
            } else if (ackMsg != null) {
                log.debug("Peer {} did not send FriendshipAck, got {}", remoteKey, ackMsg.getClass().getSimpleName());
            }

            boolean mutual = weFriendThem && theyFriendUs;
            peerFriendsUs.put(remoteKey, theyFriendUs);
            mutualFriends.put(remoteKey, mutual);
            log.info("Friendship with {} ({}): we={}, they={}, mutual={}", hello.peerId(), remoteKey, weFriendThem, theyFriendUs, mutual);
            if (friendshipChangeCallback != null) friendshipChangeCallback.run();

            // Only sync documents if mutual friends
            if (mutual) {
                Set<String> sharedDocs = new HashSet<>(workspaces.keySet());
                sharedDocs.retainAll(new HashSet<>(hello.documentIds()));
                for (String docId : sharedDocs) {
                    DocumentWorkspace ws = workspaces.get(docId);
                    if (ws != null) {
                        NetworkMessage.SyncRequest req = syncEngine.createSyncRequest(ws, docId, hello.peerId());
                        conn.send(req);
                    }
                }
            }

            // If ackMsg was not a FriendshipAck, process it as a regular message first
            if (ackMsg != null && !(ackMsg instanceof NetworkMessage.FriendshipAck)) {
                handleMessage(conn, hello, ackMsg);
            }

            // Process messages
            processMessages(conn, hello);

        } catch (IOException e) {
            log.warn("Error with outgoing peer {}: {}", conn.remoteAddress(), e.getMessage());
        } finally {
            if (conn.remotePeerId() != null) {
                connections.remove(conn.remotePeerId());
                deviceToPeerEmail.remove(conn.remotePeerId());
                mutualFriends.remove(conn.remotePeerId());
                peerFriendsUs.remove(conn.remotePeerId());
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
            handleMessage(conn, hello, msg);
        }
    }

    private void handleMessage(PeerConnection conn, NetworkMessage.Hello hello, NetworkMessage msg) throws IOException {
        String remoteKey = conn.remotePeerId();
        boolean mutual = isMutualFriend(remoteKey);

        switch (msg) {
            case NetworkMessage.SyncRequest req -> {
                if (!mutual) {
                    log.debug("Ignoring SyncRequest from non-mutual peer {}", remoteKey);
                    return;
                }
                DocumentWorkspace ws = workspaces.get(req.documentId());
                if (ws != null) {
                    NetworkMessage.SyncResponse resp = syncEngine.handleSyncRequest(ws, req);
                    conn.send(resp);
                    conn.send(new NetworkMessage.SyncDone(req.documentId()));
                }
            }
            case NetworkMessage.SyncResponse resp -> {
                if (!mutual) {
                    log.debug("Ignoring SyncResponse from non-mutual peer {}", remoteKey);
                    return;
                }
                DocumentWorkspace ws = workspaces.get(resp.documentId());
                if (ws != null) {
                    syncEngine.handleSyncResponse(ws, remoteKey, resp);
                }
            }
            case NetworkMessage.SyncDone done ->
                log.debug("Sync complete for doc {} from {}", done.documentId(), remoteKey);
            case NetworkMessage.MemberUpdate update ->
                log.info("Member update for doc {} from {}", update.documentId(), remoteKey);
            case NetworkMessage.ChatMessage chat -> {
                log.debug("Chat from {}: {}", chat.senderId(), chat.text());
                for (var listener : chatListeners) {
                    try { listener.accept(chat); } catch (Exception ex) { log.warn("Chat listener error", ex); }
                }
            }
            case NetworkMessage.FriendshipAck ack -> {
                // Re-evaluate mutual status (e.g. they added/removed us)
                peerFriendsUs.put(remoteKey, ack.isFriend());
                boolean weFriendThem = isFriendLocally(hello.peerId());
                boolean newMutual = weFriendThem && ack.isFriend();
                mutualFriends.put(remoteKey, newMutual);
                log.info("Friendship update from {}: they={}, mutual={}", hello.peerId(), ack.isFriend(), newMutual);
                if (friendshipChangeCallback != null) friendshipChangeCallback.run();
            }
            case NetworkMessage.Hello ignored ->
                log.warn("Unexpected HELLO from {}", remoteKey);
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
            if (!isMutualFriend(remotePeerId)) continue;

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

    /** Send a chat message to a specific peer (all their devices). */
    public void sendChat(String senderId, String recipientId, String text) {
        var msg = new NetworkMessage.ChatMessage(senderId, recipientId, text, System.currentTimeMillis());
        for (var entry : deviceToPeerEmail.entrySet()) {
            if (recipientId.equals(entry.getValue())) {
                PeerConnection conn = connections.get(entry.getKey());
                if (conn != null && !conn.isClosed()) {
                    try {
                        conn.send(msg);
                    } catch (IOException e) {
                        log.warn("Failed to send chat to {}: {}", entry.getKey(), e.getMessage());
                    }
                }
            }
        }
    }

    public void addChatListener(Consumer<NetworkMessage.ChatMessage> listener) {
        chatListeners.add(listener);
    }

    public void removeChatListener(Consumer<NetworkMessage.ChatMessage> listener) {
        chatListeners.remove(listener);
    }

    /** Check if a connected peer has mutual friendship with us. */
    public boolean isMutualFriend(String deviceId) {
        return Boolean.TRUE.equals(mutualFriends.get(deviceId));
    }

    /** Check if a peer email is a mutual friend (checks all their devices). */
    public boolean isMutualFriendByEmail(String email) {
        for (var entry : deviceToPeerEmail.entrySet()) {
            if (email.equals(entry.getValue()) && isMutualFriend(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    /** Check if we have this peer in our local friend list. */
    private boolean isFriendLocally(String peerEmail) {
        return IntelConfig.get().getFriendByEmail(peerEmail) != null;
    }

    /** Re-announce friendship status to all connected peers after friend list changes. */
    public void reannounceFriendship() {
        for (var entry : connections.entrySet()) {
            String deviceId = entry.getKey();
            PeerConnection conn = entry.getValue();
            if (conn.isClosed()) continue;

            String peerEmail = deviceToPeerEmail.get(deviceId);
            boolean weFriendThem = peerEmail != null && isFriendLocally(peerEmail);
            try {
                conn.send(new NetworkMessage.FriendshipAck(weFriendThem));
            } catch (IOException e) {
                log.warn("Failed to re-announce friendship to {}: {}", deviceId, e.getMessage());
            }

            // Re-evaluate mutual status locally using the peer's last-known FriendshipAck
            boolean theyFriendUs = Boolean.TRUE.equals(peerFriendsUs.get(deviceId));
            boolean newMutual = weFriendThem && theyFriendUs;
            boolean oldMutual = Boolean.TRUE.equals(mutualFriends.get(deviceId));
            if (newMutual != oldMutual) {
                mutualFriends.put(deviceId, newMutual);
                log.info("Friendship re-evaluated with {} ({}): mutual={}", peerEmail, deviceId, newMutual);
                if (friendshipChangeCallback != null) friendshipChangeCallback.run();
            }
        }
    }

    public void setFriendshipChangeCallback(Runnable callback) {
        this.friendshipChangeCallback = callback;
    }

    @Override
    public void close() {
        server.close();
        connections.values().forEach(PeerConnection::close);
        connections.clear();
    }
}
