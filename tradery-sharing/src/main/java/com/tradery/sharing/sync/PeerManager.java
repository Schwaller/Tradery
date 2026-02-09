package com.tradery.sharing.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.documents.DocumentManager;
import com.tradery.documents.DocumentWorkspace;
import com.tradery.news.ui.FriendConfig;
import com.tradery.news.ui.FriendshipCertData;
import com.tradery.news.ui.IntelConfig;
import com.tradery.sharing.identity.CertSigner;
import com.tradery.sharing.identity.FriendBackupStore;
import com.tradery.sharing.identity.IdentityCert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Manages the lifecycle of peer connections and orchestrates sync.
 * Tracks active connections, handles HELLO + CertExchange handshake,
 * and routes messages to the SyncEngine for each shared document.
 */
public class PeerManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PeerManager.class);

    private final String localPeerId;
    private final String localDeviceId;
    private final DocumentManager documentManager;
    private final SyncEngine syncEngine;
    private final ObjectMapper mapper;
    private final UdpPeerServer server;

    /** CertSigner for creating/verifying certs. */
    private volatile CertSigner certSigner;

    /** Our identity cert (self-signed, sent in Hello). */
    private volatile IdentityCert localIdentityCert;

    /** Backup store for encrypted UER blobs from friends. */
    private final FriendBackupStore backupStore = new FriendBackupStore();

    /** Active connections indexed by remote device ID. */
    private final Map<String, UdpPeerConnection> connections = new ConcurrentHashMap<>();

    /** Maps remote device ID to peer email (for member matching). */
    private final Map<String, String> deviceToPeerEmail = new ConcurrentHashMap<>();

    /** Open document workspaces by document ID. */
    private final Map<String, DocumentWorkspace> workspaces = new ConcurrentHashMap<>();

    /** Chat message listeners. */
    private final List<Consumer<NetworkMessage.ChatMessage>> chatListeners = new CopyOnWriteArrayList<>();

    /** Tracks whether each connected peer (by device ID) has mutual friendship with us (cert-verified). */
    private final Map<String, Boolean> mutualFriends = new ConcurrentHashMap<>();

    /** Friendship doc IDs to include in HELLO for rendezvous discovery. */
    private final Set<String> friendshipDocIds = ConcurrentHashMap.newKeySet();

    /** Callback when friendship status changes. */
    private volatile Runnable friendshipChangeCallback;

    /** Callback for friend import offers (email, offer). */
    private volatile BiConsumer<String, NetworkMessage.FriendImportOffer> friendImportCallback;

    public PeerManager(String localPeerId, String localDeviceId, DocumentManager documentManager, ObjectMapper mapper) throws IOException {
        this.localPeerId = localPeerId;
        this.localDeviceId = localDeviceId;
        this.documentManager = documentManager;
        this.syncEngine = new SyncEngine();
        this.mapper = mapper;
        this.server = new UdpPeerServer(mapper, this::handleIncomingConnection);
    }

    public void setCertSigner(CertSigner certSigner) { this.certSigner = certSigner; }
    public void setLocalIdentityCert(IdentityCert cert) { this.localIdentityCert = cert; }

    public int serverPort() { return server.port(); }

    /** Access the server's socket (e.g. for STUN discovery before dispatch starts). */
    public java.net.DatagramSocket serverSocket() { return server.socket(); }

    /** Start the server's dispatch loop. Call after any pre-start ops (e.g. STUN). */
    public void startServer() { server.start(); }

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
     * Connect to a remote peer via UDP and sync all shared documents.
     */
    public void connectAndSync(String host, int port) {
        Thread.ofVirtual().name("peer-connect-" + host + ":" + port).start(() -> {
            UdpPeerConnection conn = server.connect(host, port, 30_000);
            if (conn != null) {
                handleOutgoingConnection(conn);
            } else {
                log.warn("Failed to connect to {}:{}", host, port);
            }
        });
    }

    /**
     * Handle an incoming connection (called by PeerServer).
     */
    private void handleIncomingConnection(UdpPeerConnection conn) {
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
                    localPeerId, localDeviceId, localIdentityCert,
                    allDocIds()));

            // Cert-based friendship exchange
            boolean mutual = exchangeCerts(conn, hello);
            mutualFriends.put(remoteKey, mutual);
            log.info("Friendship with {} ({}): mutual={}", hello.peerId(), remoteKey, mutual);
            if (friendshipChangeCallback != null) friendshipChangeCallback.run();

            // Only sync documents if mutual friends
            if (mutual) {
                syncSharedDocs(conn, hello);
            }

            // Process messages
            processMessages(conn, hello);

        } catch (IOException e) {
            log.warn("Error with incoming peer {}: {}", conn.remoteAddress(), e.getMessage());
        } finally {
            cleanupConnection(conn);
        }
    }

    /**
     * Handle an outgoing connection (we initiated).
     */
    private void handleOutgoingConnection(UdpPeerConnection conn) {
        try {
            // Send HELLO
            conn.send(new NetworkMessage.Hello(
                    localPeerId, localDeviceId, localIdentityCert,
                    allDocIds()));

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

            // Cert-based friendship exchange
            boolean mutual = exchangeCerts(conn, hello);
            mutualFriends.put(remoteKey, mutual);
            log.info("Friendship with {} ({}): mutual={}", hello.peerId(), remoteKey, mutual);
            if (friendshipChangeCallback != null) friendshipChangeCallback.run();

            // Only sync documents if mutual friends
            if (mutual) {
                syncSharedDocs(conn, hello);
            }

            // Process messages
            processMessages(conn, hello);

        } catch (IOException e) {
            log.warn("Error with outgoing peer {}: {}", conn.remoteAddress(), e.getMessage());
        } finally {
            cleanupConnection(conn);
        }
    }

    /**
     * Exchange friendship certs with a peer.
     * Each side sends a CertExchange containing a cert that THE REMOTE PEER signed about us
     * (i.e. the receivedCert we have stored). The remote verifies it was their own signature.
     *
     * Returns true if mutual friendship is verified.
     */
    private boolean exchangeCerts(UdpPeerConnection conn, NetworkMessage.Hello hello) throws IOException {
        String peerEmail = hello.peerId();

        // Find the cert THEY signed about US (our receivedCert for this friend)
        FriendConfig friendConfig = IntelConfig.get().getFriendByEmail(peerEmail);
        FriendshipCertData proofCert = friendConfig != null ? friendConfig.getReceivedCert() : null;

        // Send our proof (may be null if we don't have their cert yet)
        conn.send(new NetworkMessage.CertExchange(proofCert));

        // Receive their proof
        NetworkMessage certMsg = conn.receive();
        if (!(certMsg instanceof NetworkMessage.CertExchange exchange)) {
            log.debug("Peer {} did not send CertExchange, got {}",
                    conn.remotePeerId(), certMsg != null ? certMsg.getClass().getSimpleName() : "null");
            return false;
        }

        // Verify their proof: it should be a cert WE signed about THEM
        boolean theyProvedUs = verifyCertExchange(exchange, hello);

        // We proved ourselves if we sent a valid cert (they'll verify on their end)
        boolean weHaveProof = proofCert != null;

        boolean mutual = weHaveProof && theyProvedUs;

        // Handle key mismatch (same email, different public key = password reset)
        if (!theyProvedUs && friendConfig != null && hello.identityCert() != null
                && friendConfig.getIssuedCert() != null) {
            String storedPubKey = friendConfig.getIssuedCert().issuerPublicKey();
            String remotePubKey = hello.identityCert().publicKey();
            if (storedPubKey != null && !storedPubKey.equals(remotePubKey)) {
                log.info("Key mismatch for {} — likely password reset, offering import", peerEmail);
                offerFriendImport(conn, friendConfig);
            }
        }

        return mutual;
    }

    /**
     * Verify a CertExchange: the proof cert should be one WE signed (issuerPublicKey matches ours).
     */
    private boolean verifyCertExchange(NetworkMessage.CertExchange exchange, NetworkMessage.Hello hello) {
        FriendshipCertData proof = exchange.proofCert();
        if (proof == null) return false;
        if (certSigner == null) return false;

        // The proof cert should have been signed by US about THEM
        String ourPubKey = certSigner.publicKeyBase64();
        if (!ourPubKey.equals(proof.issuerPublicKey())) {
            log.debug("CertExchange from {}: issuer key mismatch", hello.peerId());
            return false;
        }

        try {
            boolean valid = CertSigner.verifyFriendshipCert(proof);
            if (!valid) {
                log.debug("CertExchange from {}: signature verification failed", hello.peerId());
            }
            return valid;
        } catch (GeneralSecurityException e) {
            log.warn("CertExchange verification error for {}: {}", hello.peerId(), e.getMessage());
            return false;
        }
    }

    /**
     * Send FriendImportOffer when detecting key mismatch (password reset flow).
     */
    private void offerFriendImport(UdpPeerConnection conn, FriendConfig friendConfig) throws IOException {
        conn.send(new NetworkMessage.FriendImportOffer(
                friendConfig.getIssuedCert(),    // cert WE signed about THEM (old)
                friendConfig.getReceivedCert()   // cert THEY signed about US (old)
        ));
    }

    /**
     * Sync shared documents with a mutual friend.
     */
    private void syncSharedDocs(UdpPeerConnection conn, NetworkMessage.Hello hello) throws IOException {
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

    private void cleanupConnection(UdpPeerConnection conn) {
        if (conn.remotePeerId() != null) {
            connections.remove(conn.remotePeerId());
            deviceToPeerEmail.remove(conn.remotePeerId());
            mutualFriends.remove(conn.remotePeerId());
        }
    }

    /**
     * Message processing loop — handles sync requests/responses until disconnect.
     */
    private void processMessages(UdpPeerConnection conn, NetworkMessage.Hello hello) throws IOException {
        while (!conn.isClosed()) {
            NetworkMessage msg = conn.receive();
            if (msg == null) break; // EOF
            handleMessage(conn, hello, msg);
        }
    }

    private void handleMessage(UdpPeerConnection conn, NetworkMessage.Hello hello, NetworkMessage msg) throws IOException {
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
            case NetworkMessage.CertExchange ignored ->
                log.debug("Late CertExchange from {} (already handled in handshake)", remoteKey);
            case NetworkMessage.BackupStore backup -> {
                if (!mutual) {
                    log.debug("Ignoring BackupStore from non-mutual peer {}", remoteKey);
                    return;
                }
                try {
                    backupStore.store(backup.ownerEmail(), backup.encryptedRegistry(), backup.updatedAt());
                    log.info("Stored UER backup from {} ({} bytes)", backup.ownerEmail(),
                            backup.encryptedRegistry() != null ? backup.encryptedRegistry().length : 0);
                } catch (IOException e) {
                    log.warn("Failed to store backup from {}: {}", backup.ownerEmail(), e.getMessage());
                }
            }
            case NetworkMessage.BackupRequest req -> {
                try {
                    var entry = backupStore.load(req.email());
                    if (entry != null) {
                        conn.send(new NetworkMessage.BackupResponse(
                                entry.email(), entry.data(), entry.updatedAt()));
                        log.info("Sent backup for {} to {}", req.email(), remoteKey);
                    } else {
                        conn.send(new NetworkMessage.BackupResponse(req.email(), null, 0));
                        log.debug("No backup found for {} (requested by {})", req.email(), remoteKey);
                    }
                } catch (IOException e) {
                    log.warn("Failed to load backup for {}: {}", req.email(), e.getMessage());
                }
            }
            case NetworkMessage.BackupResponse ignored ->
                log.debug("BackupResponse from {} (handled by recovery flow)", remoteKey);
            case NetworkMessage.FriendImportOffer offer -> {
                log.info("FriendImportOffer from {}", hello.peerId());
                if (friendImportCallback != null) {
                    friendImportCallback.accept(hello.peerId(), offer);
                }
            }
            case NetworkMessage.Hello ignored ->
                log.warn("Unexpected HELLO from {}", remoteKey);
        }
    }

    /**
     * Push local data to all connected peers and request their latest data.
     */
    public void requestSync() {
        for (Map.Entry<String, UdpPeerConnection> entry : connections.entrySet()) {
            String remotePeerId = entry.getKey();
            UdpPeerConnection conn = entry.getValue();
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

    /** Send encrypted UER backup to all mutual friends. */
    public void pushBackupToFriends(String ownerEmail, byte[] encryptedRegistry, long updatedAt) {
        var msg = new NetworkMessage.BackupStore(ownerEmail, encryptedRegistry, updatedAt);
        for (var entry : connections.entrySet()) {
            if (!isMutualFriend(entry.getKey())) continue;
            UdpPeerConnection conn = entry.getValue();
            if (conn.isClosed()) continue;
            try {
                conn.send(msg);
            } catch (IOException e) {
                log.warn("Failed to push backup to {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /** Send a cert to a specific friend (by email) if they're connected. */
    public void sendCertToFriend(String friendEmail, FriendshipCertData cert) {
        for (var entry : deviceToPeerEmail.entrySet()) {
            if (friendEmail.equals(entry.getValue())) {
                UdpPeerConnection conn = connections.get(entry.getKey());
                if (conn != null && !conn.isClosed()) {
                    try {
                        conn.send(new NetworkMessage.CertExchange(cert));
                    } catch (IOException e) {
                        log.warn("Failed to send cert to {}: {}", entry.getKey(), e.getMessage());
                    }
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
                UdpPeerConnection conn = connections.get(entry.getKey());
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

    public void setFriendshipDocIds(Collection<String> ids) {
        friendshipDocIds.clear();
        friendshipDocIds.addAll(ids);
    }

    /** All doc IDs to announce: workspace docs + friendship docs. */
    private List<String> allDocIds() {
        var all = new ArrayList<>(workspaces.keySet());
        all.addAll(friendshipDocIds);
        return all;
    }

    public void setFriendshipChangeCallback(Runnable callback) {
        this.friendshipChangeCallback = callback;
    }

    public void setFriendImportCallback(BiConsumer<String, NetworkMessage.FriendImportOffer> callback) {
        this.friendImportCallback = callback;
    }

    @Override
    public void close() {
        server.close();
        connections.values().forEach(UdpPeerConnection::close);
        connections.clear();
    }
}
