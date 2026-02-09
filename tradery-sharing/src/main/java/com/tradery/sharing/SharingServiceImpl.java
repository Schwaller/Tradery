package com.tradery.sharing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.documents.Document;
import com.tradery.documents.DocumentManager;
import com.tradery.documents.DocumentMember;
import com.tradery.documents.DocumentWorkspace;
import com.tradery.news.ui.SharingService;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.sharing.discovery.LanDiscovery;
import com.tradery.sharing.discovery.NatPmpMapper;
import com.tradery.sharing.discovery.RendezvousClient;
import com.tradery.sharing.discovery.StunClient;
import com.tradery.sharing.discovery.TcpHolePuncher;
import com.tradery.sharing.discovery.UpnpPortMapper;
import com.tradery.sharing.identity.AuthConfig;
import com.tradery.sharing.identity.KeyPairStore;
import com.tradery.sharing.identity.OAuthLogin;
import com.tradery.sharing.identity.UserSession;
import com.tradery.sharing.sync.PeerManager;
import com.tradery.sharing.upgrade.SharingUpgrade;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradery.news.ui.FriendConfig;
import com.tradery.news.ui.IntelConfig;
import com.tradery.sharing.sync.NetworkMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
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

    private static final String RENDEZVOUS_URL = "https://plaiiin.com/rendezvous";

    /** Lazy-initialized on first share. */
    private PeerManager peerManager;
    private LanDiscovery lanDiscovery;
    private RendezvousClient rendezvousClient;
    private NatPmpMapper natPmpMapper;
    private UpnpPortMapper upnpMapper;
    private TcpHolePuncher holePuncher;
    private volatile String portMappingMethod;  // "NAT-PMP", "UPnP", or null
    private volatile String publicIp;           // from STUN or port mapper
    private volatile boolean lanActive;
    private UserSession localSession;

    /** Device credential for rendezvous auth (persisted in AuthConfig). */
    private volatile String deviceCredential;

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
        ensureSession();
        ensurePeerManager();

        // Use authenticated email if available, fall back to parameter
        String email = localSession != null ? localSession.userId() : ownerEmail;

        // Open workspace via DocumentManager
        DocumentWorkspace ws = documentManager.openDocument(docId);

        // Migrate any local-* owner to the real email identity
        migrateOwner(docId, email, ws);

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
        ensureSession();
        ensurePeerManager();

        DocumentWorkspace ws = workspaces.get(docId);
        if (ws == null) {
            ws = documentManager.openDocument(docId);
            workspaces.put(docId, ws);
            peerManager.registerWorkspace(docId, ws);
        }

        // Migrate owner identity if needed
        String email = localSession != null ? localSession.userId() : ownerEmail;
        migrateOwner(docId, email, ws);

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
        // Try silent token refresh — no browser popup on app start
        AuthConfig auth = AuthConfig.load();
        if (!auth.isLoggedIn()) return;

        try {
            OAuthLogin oauth = new OAuthLogin();
            var tokens = oauth.refresh(auth.getRefreshToken());
            if (tokens == null) {
                log.info("Silent token refresh failed at bootstrap — starting LAN-only, user will need to sign in for rendezvous");
                ensurePeerManager();
                return;
            }

            var info = oauth.parseIdToken(tokens.idToken());
            KeyPairStore keyStore = new KeyPairStore();
            KeyPair keyPair = keyStore.loadOrGenerate();

            localSession = new UserSession(info.email(), info.displayName(),
                    tokens.accessToken(), tokens.refreshToken(),
                    System.currentTimeMillis() + tokens.expiresIn() * 1000L, keyPair);

            auth.setRefreshToken(tokens.refreshToken());
            auth.setEmail(info.email());
            auth.setDisplayName(info.displayName());
            auth.setUserId(info.userId());
            auth.save();
            updateConfigEmail(info.email());

            ensurePeerManager();
            log.info("Sharing bootstrapped for {} (silent refresh)", info.email());
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

    private synchronized void ensureSession() throws Exception {
        if (localSession != null && !localSession.isTokenExpired()) return;

        AuthConfig auth = AuthConfig.load();
        KeyPairStore keyStore = new KeyPairStore();
        KeyPair keyPair = keyStore.loadOrGenerate();
        OAuthLogin oauth = new OAuthLogin();

        // Try silent refresh first
        if (auth.isLoggedIn()) {
            var tokens = oauth.refresh(auth.getRefreshToken());
            if (tokens != null) {
                var info = oauth.parseIdToken(tokens.idToken());
                localSession = new UserSession(info.email(), info.displayName(),
                        tokens.accessToken(), tokens.refreshToken(),
                        System.currentTimeMillis() + tokens.expiresIn() * 1000L, keyPair);
                auth.setRefreshToken(tokens.refreshToken());
                auth.setEmail(info.email());
                auth.setDisplayName(info.displayName());
                auth.setUserId(info.userId());
                auth.save();
                updateConfigEmail(info.email());
                return;
            }
        }

        // Refresh failed or no stored token — interactive browser login
        var tokens = oauth.login();
        if (tokens == null) throw new IllegalStateException("Login cancelled or timed out");
        var info = oauth.parseIdToken(tokens.idToken());
        localSession = new UserSession(info.email(), info.displayName(),
                tokens.accessToken(), tokens.refreshToken(),
                System.currentTimeMillis() + tokens.expiresIn() * 1000L, keyPair);
        auth.setRefreshToken(tokens.refreshToken());
        auth.setEmail(info.email());
        auth.setDisplayName(info.displayName());
        auth.setUserId(info.userId());
        auth.save();
        updateConfigEmail(info.email());
    }

    /**
     * Ensure we have a device credential for rendezvous auth.
     * Enrolls with the rendezvous server on first login, reuses stored credential thereafter.
     */
    private void ensureDeviceEnrolled() {
        AuthConfig auth = AuthConfig.load();
        if (auth.isDeviceEnrolled()) {
            deviceCredential = auth.getDeviceCredential();
            return;
        }

        if (localSession == null || rendezvousClient == null) return;

        try {
            KeyPairStore keyStore = new KeyPairStore();
            java.security.KeyPair keyPair = keyStore.loadOrGenerate();
            String devicePubKey = java.util.Base64.getEncoder().encodeToString(
                    keyPair.getPublic().getEncoded());
            String deviceName = System.getProperty("os.name", "unknown") + " " +
                    System.getProperty("user.name", "");

            var result = rendezvousClient.enrollDevice(
                    localSession.accessToken(), devicePubKey, deviceName.trim());

            auth.setDeviceId(result.deviceId());
            auth.setDeviceCredential(result.deviceCredential());
            auth.setBackendPublicKey(result.backendPublicKey());
            auth.save();

            deviceCredential = result.deviceCredential();
            log.info("Device enrolled with rendezvous: deviceId={}", result.deviceId());
        } catch (Exception e) {
            log.warn("Device enrollment failed: {} — rendezvous will be unavailable", e.getMessage());
        }
    }

    /** Sync IntelConfig.userEmail from Keycloak identity. */
    private void updateConfigEmail(String email) {
        IntelConfig config = IntelConfig.get();
        if (!email.equals(config.getUserEmail())) {
            config.setUserEmail(email);
            config.save();
        }
    }

    @Override
    public boolean login() {
        try {
            ensureSession();
            if (localSession != null) {
                ensurePeerManager();
            }
            return localSession != null;
        } catch (Exception e) {
            log.warn("Login failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void logout() {
        AuthConfig auth = AuthConfig.load();
        auth.clear();
        localSession = null;
        log.info("Logged out — auth tokens cleared");
    }

    @Override
    public String getAuthenticatedEmail() {
        if (localSession != null) return localSession.userId();
        AuthConfig auth = AuthConfig.load();
        return auth.getEmail();
    }

    @Override
    public NetworkStatus getNetworkStatus() {
        String email = localSession != null ? localSession.userId() : AuthConfig.load().getEmail();
        return new NetworkStatus(
            email,
            deviceCredential != null,
            peerManager != null ? peerManager.serverPort() : 0,
            portMappingMethod,
            publicIp,
            lanActive,
            lanDiscovery != null ? lanDiscovery.activePeers().size() : 0,
            deviceCredential != null,
            peerManager != null ? peerManager.connectedPeerIds().size() : 0,
            peerManager != null ? peerManager.connectedDeviceIds().size() : 0
        );
    }

    private synchronized void ensurePeerManager() throws IOException {
        if (peerManager != null) return;

        String peerId = localSession != null ? localSession.userId() : AuthConfig.load().getEmail();
        if (peerId == null || peerId.isBlank()) throw new IOException("No identity available for peer manager");
        String deviceId = IntelConfig.get().getDeviceId();
        peerManager = new PeerManager(peerId, deviceId, documentManager, mapper);
        peerManager.addChatListener(this::onNetworkChat);
        lanDiscovery = new LanDiscovery(peerId, deviceId, peerManager.serverPort());

        try {
            lanDiscovery.start();
            lanActive = true;
        } catch (IOException e) {
            log.warn("LAN discovery failed to start: {}", e.getMessage());
        }

        // Port mapping: try NAT-PMP → UPnP → STUN (for hole punching)
        natPmpMapper = new NatPmpMapper();
        upnpMapper = new UpnpPortMapper();
        holePuncher = new TcpHolePuncher();
        int announcePort = peerManager.serverPort();
        Thread.ofVirtual().name("port-mapper").start(() -> {
            // Try NAT-PMP
            var mapping = natPmpMapper.mapPort(peerManager.serverPort());
            if (mapping != null) {
                portMappingMethod = "NAT-PMP";
                publicIp = mapping.externalIp();
                return;
            }

            // Try UPnP
            var upnpMapping = upnpMapper.mapPort(peerManager.serverPort());
            if (upnpMapping != null) {
                log.info("Port mapped via UPnP: {} → {}:{}", peerManager.serverPort(), upnpMapping.externalIp(), upnpMapping.externalPort());
                portMappingMethod = "UPnP";
                publicIp = upnpMapping.externalIp();
                return;
            }

            // No port mapping — discover public IP via STUN for hole punching
            var stun = new StunClient();
            var endpoint = stun.discover();
            if (endpoint != null) {
                publicIp = endpoint.ip();
                log.info("STUN: public IP is {} (no port mapping, will use hole punching)", endpoint.ip());
            } else {
                log.info("No port mapping or STUN available — LAN/rendezvous only");
            }
        });

        // Rendezvous client + device enrollment
        rendezvousClient = new RendezvousClient(RENDEZVOUS_URL, new OkHttpClient(), mapper);
        ensureDeviceEnrolled();

        // Initial announce (only if we have a device credential)
        if (deviceCredential != null) {
            try {
                var docIds = new ArrayList<>(workspaces.keySet());
                rendezvousClient.announce(deviceCredential, peerId, announcePort, docIds);
                log.info("Rendezvous: announced as {} with {} documents", peerId, docIds.size());
            } catch (Exception e) {
                log.warn("Rendezvous: initial announce failed: {}", e.getMessage());
            }
        }

        // Combined discovery loop: LAN auto-connect + rendezvous announce/discover
        Thread.ofVirtual().name("peer-discovery-loop").start(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000);
                    var knownMembers = collectAllMemberEmails();
                    var connectedDevices = peerManager.connectedDeviceIds();

                    // LAN auto-connect
                    for (var peer : lanDiscovery.activePeers()) {
                        if (knownMembers.contains(peer.peerId())
                                && !connectedDevices.contains(peer.deviceId())) {
                            peerManager.connectAndSync(peer.host(), peer.port());
                        }
                    }

                    // Rendezvous re-announce + discover (uses device credential, not access token)
                    if (deviceCredential != null) {
                        var docIds = new ArrayList<>(workspaces.keySet());

                        try {
                            rendezvousClient.announce(deviceCredential, peerId, announcePort, docIds);
                        } catch (Exception e) {
                            log.debug("Rendezvous: re-announce failed: {}", e.getMessage());
                        }

                        for (String docId : docIds) {
                            try {
                                var peers = rendezvousClient.discoverPeers(deviceCredential, docId);
                                for (var rp : peers) {
                                    if (peerId.equals(rp.peerId())) continue;
                                    if (portMappingMethod != null) {
                                        peerManager.connectAndSync(rp.host(), rp.port());
                                    } else {
                                        tryConnectWithHolePunch(rp.host(), rp.port());
                                    }
                                }
                                if (!peers.isEmpty()) {
                                    log.debug("Rendezvous: discovered {} peers for doc {}", peers.size(), docId);
                                }
                            } catch (Exception e) {
                                log.debug("Rendezvous: discover failed for doc {}: {}", docId, e.getMessage());
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // Shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (natPmpMapper != null) natPmpMapper.unmap();
            if (upnpMapper != null) upnpMapper.unmap();
            if (rendezvousClient != null && deviceCredential != null) {
                try {
                    rendezvousClient.depart(deviceCredential, peerId);
                } catch (Exception e) {
                    // shutting down, ignore
                }
            }
        }, "sharing-shutdown"));

        log.info("PeerManager started on port {}", peerManager.serverPort());
    }

    /**
     * Try direct TCP connect first. If that fails quickly, attempt TCP hole punching
     * which binds to the PeerServer port and retries to create a NAT mapping.
     */
    private void tryConnectWithHolePunch(String host, int port) {
        Thread.ofVirtual().name("hole-punch-" + host).start(() -> {
            // Quick direct connect attempt (might work for full-cone NAT)
            try {
                var socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), 3000);
                peerManager.connectWithSocket(socket);
                return;
            } catch (IOException e) {
                log.debug("Direct connect to {}:{} failed, trying hole punch", host, port);
            }

            // TCP hole punch: bind to PeerServer port and retry
            var socket = holePuncher.punch(peerManager.serverPort(), host, port);
            if (socket != null) {
                peerManager.connectWithSocket(socket);
            }
        });
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
    public void sendChat(String recipientEmail, String text) {
        if (peerManager == null || localSession == null) return;
        peerManager.sendChat(localSession.userId(), recipientEmail, text);
        // Also echo locally so the sender sees their own message
        var local = new ChatMessage(localSession.userId(), recipientEmail, text, System.currentTimeMillis());
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
        var msg = new ChatMessage(netMsg.senderId(), netMsg.recipientId(), netMsg.text(), netMsg.timestamp());
        for (var listener : chatListeners) {
            try {
                listener.accept(msg);
            } catch (Exception ex) {
                log.warn("Chat listener error", ex);
            }
        }
    }

    @Override
    public void onFriendListChanged() {
        if (peerManager != null) {
            peerManager.reannounceFriendship();
        }
    }

    @Override
    public boolean isMutualFriend(String email) {
        if (peerManager == null) return false;
        return peerManager.isMutualFriendByEmail(email);
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
