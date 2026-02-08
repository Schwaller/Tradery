package com.tradery.sharing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.documents.Document;
import com.tradery.documents.DocumentManager;
import com.tradery.documents.DocumentMember;
import com.tradery.documents.DocumentWorkspace;
import com.tradery.news.ui.SharingService;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.sharing.discovery.LanDiscovery;
import com.tradery.sharing.identity.UserSession;
import com.tradery.sharing.sync.PeerManager;
import com.tradery.sharing.upgrade.SharingUpgrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradery.news.ui.FriendConfig;
import com.tradery.news.ui.IntelConfig;
import com.tradery.sharing.sync.NetworkMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Concrete implementation of SharingService that bridges tradery-news UI
 * with tradery-sharing infrastructure. Loaded via reflection at runtime.
 */
public class SharingServiceImpl implements SharingService {

    private static final Logger log = LoggerFactory.getLogger(SharingServiceImpl.class);

    private final DocumentManager documentManager;
    private final SharingUpgrade sharingUpgrade;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Lazy-initialized on first share. */
    private PeerManager peerManager;
    private LanDiscovery lanDiscovery;
    private UserSession localSession;

    /** Tracks open workspaces for sync. */
    private final ConcurrentHashMap<String, DocumentWorkspace> workspaces = new ConcurrentHashMap<>();

    /** Chat listeners registered by the UI. */
    private final List<Consumer<ChatMessage>> chatListeners = new CopyOnWriteArrayList<>();

    public SharingServiceImpl(Path documentsDir) {
        this.documentManager = new DocumentManager(documentsDir);
        this.sharingUpgrade = new SharingUpgrade(documentManager);
    }

    @Override
    public SharingState getState(String docId) {
        try {
            DocumentWorkspace ws = workspaces.get(docId);
            Document doc;
            if (ws != null) {
                doc = ws.document();
            } else {
                ws = documentManager.openDocument(docId);
                doc = ws.document();
                ws.close();
            }

            String visibility = doc.visibility() != null ? doc.visibility().name() : "LOCAL";
            String govType = doc.governance() != null ? doc.governance().type().name() : null;
            double quorum = doc.governance() != null ? doc.governance().votingQuorum() : 0.51;

            List<DocumentMember> members = documentManager.readMembers(docId);
            int peerCount = peerManager != null ? peerManager.connectedPeerIds().size() : 0;

            return new SharingState(visibility, govType, quorum, members.size(), peerCount);
        } catch (IOException e) {
            log.warn("Failed to read sharing state for {}: {}", docId, e.getMessage());
            return new SharingState("LOCAL", null, 0.51, 0, 0);
        }
    }

    @Override
    public List<Member> getMembers(String docId) {
        try {
            List<DocumentMember> members = documentManager.readMembers(docId);
            List<Member> result = new ArrayList<>();
            for (DocumentMember m : members) {
                String role = m.role() != null ? m.role().name() : "OWNER";
                result.add(new Member(m.userId(), role));
            }
            return result;
        } catch (IOException e) {
            log.warn("Failed to read members for {}: {}", docId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void addMember(String docId, String email, String role) throws Exception {
        List<DocumentMember> members = documentManager.readMembers(docId);

        // Check if already a member
        for (DocumentMember m : members) {
            if (email.equals(m.userId())) {
                throw new IllegalArgumentException("Already a member: " + email);
            }
        }

        members.add(new DocumentMember(email, DocumentMember.Role.valueOf(role)));
        documentManager.writeMembers(docId, members);
        log.info("Added member {} as {} to doc {}", email, role, docId);
    }

    @Override
    public void removeMember(String docId, String email) throws Exception {
        List<DocumentMember> members = documentManager.readMembers(docId);
        boolean removed = members.removeIf(m -> email.equals(m.userId()));
        if (!removed) {
            throw new IllegalArgumentException("Not a member: " + email);
        }
        documentManager.writeMembers(docId, members);
        log.info("Removed member {} from doc {}", email, docId);
    }

    @Override
    public void enableSharing(String docId, String visibility, String governanceType,
                              double votingQuorum, String ownerEmail, Path docDir,
                              EntityStore entityStore) throws Exception {
        ensureSession(ownerEmail);
        ensurePeerManager();

        // Open workspace via DocumentManager
        DocumentWorkspace ws = documentManager.openDocument(docId);

        // Migrate any local-* owner to the real email identity
        migrateOwner(docId, ownerEmail, ws);

        // Run upgrade (sets owner, members, visibility)
        Document.Visibility vis = Document.Visibility.valueOf(visibility);
        sharingUpgrade.upgrade(docId, vis, localSession, ws);

        // Update governance if specified
        Document doc = ws.document();
        if (governanceType != null) {
            Document.Governance gov = doc.governance() != null ? doc.governance() : new Document.Governance();
            gov.setType(Document.Governance.Type.valueOf(governanceType));
            gov.setVotingQuorum(votingQuorum);
            doc.setGovernance(gov);
            documentManager.updateDocument(doc);
        }

        // Register for sync
        workspaces.put(docId, ws);
        peerManager.registerWorkspace(docId, ws);

        log.info("Enabled sharing for doc {} with visibility={}, owner={}", docId, visibility, ownerEmail);
    }

    @Override
    public void updateSharing(String docId, String visibility, String governanceType,
                              double votingQuorum, String ownerEmail) throws Exception {
        DocumentWorkspace ws = workspaces.get(docId);
        if (ws == null) {
            ws = documentManager.openDocument(docId);
            workspaces.put(docId, ws);
        }

        // Migrate owner identity if needed
        migrateOwner(docId, ownerEmail, ws);

        Document doc = ws.document();
        doc.setVisibility(Document.Visibility.valueOf(visibility));

        Document.Governance gov = doc.governance() != null ? doc.governance() : new Document.Governance();
        gov.setType(Document.Governance.Type.valueOf(governanceType));
        gov.setVotingQuorum(votingQuorum);
        doc.setGovernance(gov);

        documentManager.updateDocument(doc);
        log.info("Updated sharing for doc {} to visibility={}, governance={}", docId, visibility, governanceType);
    }

    /**
     * Migrate document ownership from a local-* or mismatched ID to the real email identity.
     * Updates document.yaml owner_id, members.yaml, and facts.db local_config.
     */
    private void migrateOwner(String docId, String newEmail, DocumentWorkspace ws) throws IOException {
        Document doc = ws.document();
        String currentOwner = doc.ownerId();

        // Nothing to migrate if owner already matches
        if (newEmail.equals(currentOwner)) return;

        log.info("Migrating owner for doc {} from '{}' to '{}'", docId, currentOwner, newEmail);

        // 1. Update document.yaml owner_id
        doc.setOwnerId(newEmail);
        documentManager.updateDocument(doc);

        // 2. Update members.yaml — replace old owner entry with new email
        List<DocumentMember> members = documentManager.readMembers(docId);
        List<DocumentMember> updated = new ArrayList<>();
        boolean ownerFound = false;
        for (DocumentMember m : members) {
            if (m.userId().equals(currentOwner) || (currentOwner != null && currentOwner.startsWith("local-") && "OWNER".equals(m.role() != null ? m.role().name() : null))) {
                updated.add(new DocumentMember(newEmail, DocumentMember.Role.OWNER));
                ownerFound = true;
            } else if (m.userId().equals(newEmail)) {
                // New email already exists as non-owner — promote to owner
                updated.add(new DocumentMember(newEmail, DocumentMember.Role.OWNER));
                ownerFound = true;
            } else {
                updated.add(m);
            }
        }
        if (!ownerFound) {
            updated.add(0, new DocumentMember(newEmail, DocumentMember.Role.OWNER));
        }
        documentManager.writeMembers(docId, updated);

        // 3. Update facts.db local_config user_id
        ws.entityStore().factStore().setLocalConfig("user_id", newEmail);
    }

    @Override
    public void disableSharing(String docId) throws Exception {
        DocumentWorkspace ws = workspaces.remove(docId);
        if (peerManager != null) {
            peerManager.unregisterWorkspace(docId);
        }

        if (ws == null) {
            ws = documentManager.openDocument(docId);
        }

        Document doc = ws.document();
        doc.setVisibility(Document.Visibility.LOCAL);
        doc.setGovernance(null);
        documentManager.updateDocument(doc);
        ws.close();

        log.info("Disabled sharing for doc {}", docId);
    }

    @Override
    public void syncNow(String docId) {
        if (peerManager != null) {
            peerManager.requestSync();
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void bootstrap() {
        String email = IntelConfig.get().getUserEmail();
        if (email == null || email.isBlank()) return;
        try {
            ensureSession(email);
            ensurePeerManager();
            log.info("Sharing bootstrapped for {}", email);
        } catch (Exception e) {
            log.warn("Failed to bootstrap sharing: {}", e.getMessage());
        }
    }

    @Override
    public void registerDocument(String docId, Path docDir, EntityStore entityStore) {
        if (peerManager == null) return;
        try {
            Document doc = documentManager.openDocument(docId).document();
            DocumentWorkspace ws = new DocumentWorkspace(doc, docDir, entityStore);
            workspaces.put(docId, ws);
            peerManager.registerWorkspace(docId, ws);
            log.info("Registered document {} for sync", docId);
        } catch (IOException e) {
            log.warn("Failed to register document {} for sync: {}", docId, e.getMessage());
        }
    }

    @Override
    public void unregisterDocument(String docId) {
        workspaces.remove(docId);
        if (peerManager != null) {
            peerManager.unregisterWorkspace(docId);
        }
        log.info("Unregistered document {} from sync", docId);
    }

    private synchronized void ensureSession(String email) throws NoSuchAlgorithmException {
        if (localSession != null && email.equals(localSession.userId())) return;

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();

        localSession = new UserSession(email, email, null, null, Long.MAX_VALUE, keyPair);
        log.info("Created sharing identity for: {}", email);
    }

    private synchronized void ensurePeerManager() throws IOException {
        if (peerManager != null) return;

        String peerId = localSession.userId();
        String deviceId = IntelConfig.get().getDeviceId();
        peerManager = new PeerManager(peerId, deviceId, documentManager, mapper);
        peerManager.addChatListener(this::onNetworkChat);
        lanDiscovery = new LanDiscovery(peerId, deviceId, peerManager.serverPort());

        try {
            lanDiscovery.start();
        } catch (IOException e) {
            log.warn("LAN discovery failed to start: {}", e.getMessage());
        }

        // Auto-connect to peers who are members of our shared documents (or our own devices)
        Thread.ofVirtual().name("peer-auto-connect").start(() -> {
            while (true) {
                try {
                    Thread.sleep(35_000);
                    var knownMembers = collectAllMemberEmails();
                    var connectedDevices = peerManager.connectedDeviceIds();
                    for (var peer : lanDiscovery.activePeers()) {
                        if (knownMembers.contains(peer.peerId())
                                && !connectedDevices.contains(peer.deviceId())) {
                            peerManager.connectAndSync(peer.host(), peer.port());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        log.info("PeerManager started on port {}", peerManager.serverPort());
    }

    // ==================== Friends ====================

    @Override
    public List<FriendStatus> getFriendsWithStatus() {
        var activePeers = lanDiscovery != null ? lanDiscovery.activePeers() : List.<LanDiscovery.LanPeer>of();
        var peerMap = new java.util.HashMap<String, LanDiscovery.LanPeer>();
        for (var peer : activePeers) peerMap.put(peer.peerId(), peer);

        List<FriendStatus> result = new ArrayList<>();
        for (FriendConfig f : IntelConfig.get().getFriends()) {
            var peer = peerMap.get(f.getEmail());
            boolean online = peer != null;
            long lastSeen = online ? peer.lastSeen() : 0;
            result.add(new FriendStatus(f.getEmail(), f.label(), online, lastSeen));
        }
        return result;
    }

    @Override
    public boolean isFriendOnline(String email) {
        if (lanDiscovery == null) return false;
        for (var peer : lanDiscovery.activePeers()) {
            if (email.equals(peer.peerId())) return true;
        }
        return false;
    }

    // ==================== Chat ====================

    @Override
    public void sendChat(String text) {
        if (peerManager == null || localSession == null) return;
        peerManager.broadcastChat(localSession.userId(), text);
        // Also echo locally so the sender sees their own message
        var local = new ChatMessage(localSession.userId(), text, System.currentTimeMillis());
        for (var listener : chatListeners) {
            try { listener.accept(local); } catch (Exception ex) { log.warn("Chat listener error", ex); }
        }
    }

    @Override
    public void addChatListener(Consumer<ChatMessage> listener) {
        chatListeners.add(listener);
    }

    @Override
    public void removeChatListener(Consumer<ChatMessage> listener) {
        chatListeners.remove(listener);
    }

    /** Bridge: forward network chat messages to SharingService.ChatMessage listeners. */
    private void onNetworkChat(NetworkMessage.ChatMessage netMsg) {
        var msg = new ChatMessage(netMsg.senderId(), netMsg.text(), netMsg.timestamp());
        for (var listener : chatListeners) {
            try {
                listener.accept(msg);
            } catch (Exception ex) {
                log.warn("Chat listener error", ex);
            }
        }
    }

    /** Collect all member emails across all shared documents + friends + own email (for peer filtering). */
    private java.util.Set<String> collectAllMemberEmails() {
        var emails = new java.util.HashSet<String>();
        // Include own email so we auto-connect to our own devices
        String ownEmail = IntelConfig.get().getUserEmail();
        if (ownEmail != null && !ownEmail.isBlank()) {
            emails.add(ownEmail);
        }
        for (var entry : workspaces.entrySet()) {
            try {
                var members = documentManager.readMembers(entry.getKey());
                for (var m : members) {
                    emails.add(m.userId());
                }
            } catch (IOException e) {
                // skip
            }
        }
        // Also include friends so they auto-connect on LAN
        for (FriendConfig f : IntelConfig.get().getFriends()) {
            emails.add(f.getEmail());
        }
        return emails;
    }
}
