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
import com.tradery.sharing.discovery.UpnpPortMapper;
import com.tradery.news.ui.FriendshipCertData;
import com.tradery.sharing.identity.*;
import com.tradery.sharing.sync.FriendshipDocId;
import com.tradery.sharing.sync.PeerManager;
import com.tradery.sharing.upgrade.SharingUpgrade;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradery.news.ui.FriendConfig;
import com.tradery.news.ui.IntelConfig;
import com.tradery.sharing.sync.NetworkMessage;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
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
    private volatile String portMappingMethod;  // "NAT-PMP", "UPnP", or null
    private volatile String publicIp;           // from STUN or port mapper
    private volatile String publicIpv6;         // from IPv6 STUN, null if unavailable
    private volatile int announcePort;          // external port to announce to rendezvous
    private volatile boolean lanActive;
    private UserSession localSession;

    /** CertSigner for creating identity + friendship certs. */
    private CertSigner certSigner;

    /** Our identity cert (self-signed, sent in Hello). */
    private IdentityCert localIdentityCert;

    /** Device credential for rendezvous auth (persisted in AuthConfig). */
    private volatile String deviceCredential;

    /** Tracks open workspaces for sync. */
    private final ConcurrentHashMap<String, DocumentWorkspace> workspaces = new ConcurrentHashMap<>();

    /** Chat listeners registered by the UI. */
    private final List<Consumer<ChatMessage>> chatListeners = new CopyOnWriteArrayList<>();

    /** Performance test request listeners registered by the UI. */
    private final List<Consumer<PerfTestRequest>> perfRequestListeners = new CopyOnWriteArrayList<>();

    /** Tracks active perf test state: testId -> callback. */
    private final ConcurrentHashMap<String, Consumer<PerfTestResult>> activePerfTests = new ConcurrentHashMap<>();

    /** Tracks which device each perf test is with. */
    private final ConcurrentHashMap<String, String> perfTestDevices = new ConcurrentHashMap<>();

    /** Tracks which device initiated each incoming perf request. */
    private final ConcurrentHashMap<String, String> incomingPerfRequests = new ConcurrentHashMap<>();

    /** Presence: track last user activity for ONLINE vs IDLE detection. */
    private final AtomicLong lastActivityMs = new AtomicLong(System.currentTimeMillis());
    private static final long IDLE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes
    private volatile boolean presenceEnabled = true;

    /** Cached list of other devices belonging to this user, updated by discovery loop. */
    private volatile List<DeviceInfo> myDevices = List.of();

    /** Friends visible on rendezvous (their email), updated by discovery loop. */
    private volatile java.util.Set<String> rendezvousVisibleFriends = java.util.Set.of();

    /** Cached friend presence states, updated by presence heartbeat. */
    private final ConcurrentHashMap<String, String> friendPresenceCache = new ConcurrentHashMap<>();

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

    /**
     * Clear stale device credential and re-enroll.
     * Called when the rendezvous server rejects the current credential (e.g., backend key rotated).
     */
    private void reEnrollDevice() {
        deviceCredential = null;
        AuthConfig auth = AuthConfig.load();
        auth.setDeviceId(null);
        auth.setDeviceCredential(null);
        auth.setBackendPublicKey(null);
        auth.save();
        try {
            ensureSession(); // refresh Keycloak token (enrollment requires it)
        } catch (Exception e) {
            log.warn("Rendezvous: session refresh failed during re-enrollment: {}", e.getMessage());
            return;
        }
        ensureDeviceEnrolled();
        if (deviceCredential != null) {
            log.info("Rendezvous: successfully re-enrolled after credential rejection");
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
        deviceCredential = null;
        log.info("Logged out — auth tokens and device credential cleared");
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
            publicIpv6,
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
        peerManager.addPerfListener(this::onPerfMessage);
        peerManager.setFriendshipDocIds(computeFriendshipDocIds());

        // Wire up cert signer + identity cert
        ensureCertSigner();
        if (certSigner != null) {
            peerManager.setCertSigner(certSigner);
            peerManager.setLocalIdentityCert(localIdentityCert);
        }
        peerManager.setFriendImportCallback(this::handleFriendImport);

        // Auto-generate certs for existing friends that don't have them yet (migration)
        migrateFriendCerts();

        announcePort = peerManager.serverPort();

        // STUN discovery from server socket BEFORE dispatch starts.
        // This discovers the NAT-mapped external port for the actual UDP socket,
        // so we announce the correct port to rendezvous for hole punching.
        var stun = new StunClient();
        var stunEndpoint = stun.discover(peerManager.serverSocket());
        if (stunEndpoint != null) {
            publicIp = stunEndpoint.ip();
            announcePort = stunEndpoint.port();
            log.info("STUN: public endpoint {}:{} (local port {})", stunEndpoint.ip(), stunEndpoint.port(), peerManager.serverPort());
        }

        // IPv6 STUN discovery (dual-stack socket can send/receive IPv6)
        var stunV6 = stun.discoverIpv6(peerManager.serverSocket());
        if (stunV6 != null) {
            publicIpv6 = stunV6.ip();
            log.info("IPv6: globally routable at [{}]:{}", stunV6.ip(), stunV6.port());
        }

        // Now start the dispatch loop (after STUN is done using the socket)
        peerManager.startServer();

        lanDiscovery = new LanDiscovery(peerId, deviceId, peerManager.serverPort());
        try {
            lanDiscovery.start();
            lanActive = true;
        } catch (IOException e) {
            log.warn("LAN discovery failed to start: {}", e.getMessage());
        }

        // Port mapping: try NAT-PMP → UPnP (may override STUN-discovered port)
        natPmpMapper = new NatPmpMapper();
        upnpMapper = new UpnpPortMapper();
        Thread.ofVirtual().name("port-mapper").start(() -> {
            // Try NAT-PMP
            var mapping = natPmpMapper.mapPort(peerManager.serverPort());
            if (mapping != null) {
                portMappingMethod = "NAT-PMP";
                publicIp = mapping.externalIp();
                announcePort = mapping.externalPort();
                log.info("NAT-PMP: mapped port {} → {}", peerManager.serverPort(), mapping.externalPort());
                return;
            }

            // Try UPnP
            var upnpMapping = upnpMapper.mapPort(peerManager.serverPort());
            if (upnpMapping != null) {
                portMappingMethod = "UPnP";
                publicIp = upnpMapping.externalIp();
                announcePort = upnpMapping.externalPort();
                log.info("UPnP: mapped port {} → {}:{}", peerManager.serverPort(), upnpMapping.externalIp(), upnpMapping.externalPort());
                return;
            }

            if (publicIp == null) {
                log.info("No port mapping or STUN available — LAN/rendezvous only");
            } else {
                log.info("STUN: public IP is {} (no port mapping, will use hole punching)", publicIp);
            }
        });

        // Rendezvous client + device enrollment
        rendezvousClient = new RendezvousClient(RENDEZVOUS_URL, new OkHttpClient(), mapper);
        ensureDeviceEnrolled();

        // Initial announce (only if we have a device credential)
        if (deviceCredential != null) {
            try {
                var docIds = new ArrayList<>(workspaces.keySet());
                docIds.addAll(computeFriendshipDocIds());
                rendezvousClient.announce(deviceCredential, peerId, announcePort, docIds, publicIpv6);
                log.info("Rendezvous: announced as {} with {} docs ({}+{} friendship){}", peerId, docIds.size(), workspaces.size(), docIds.size() - workspaces.size(),
                        publicIpv6 != null ? " [IPv6: " + publicIpv6 + "]" : "");
            } catch (RendezvousClient.CredentialRejectedException e) {
                log.warn("Rendezvous: credential rejected on initial announce, re-enrolling...");
                reEnrollDevice();
                // Re-announce with fresh credential
                if (deviceCredential != null) {
                    try {
                        var docIds2 = new ArrayList<>(workspaces.keySet());
                        docIds2.addAll(computeFriendshipDocIds());
                        rendezvousClient.announce(deviceCredential, peerId, announcePort, docIds2, publicIpv6);
                        log.info("Rendezvous: re-announced after re-enrollment with {} docs", docIds2.size());
                    } catch (Exception e2) {
                        log.warn("Rendezvous: re-announce after re-enrollment failed: {}", e2.getMessage());
                    }
                }
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
                        docIds.addAll(computeFriendshipDocIds());

                        try {
                            rendezvousClient.announce(deviceCredential, peerId, announcePort, docIds, publicIpv6);
                        } catch (RendezvousClient.CredentialRejectedException e) {
                            log.warn("Rendezvous: credential rejected, re-enrolling...");
                            reEnrollDevice();
                            continue; // retry on next loop iteration with fresh credential
                        } catch (Exception e) {
                            log.debug("Rendezvous: re-announce failed: {}", e.getMessage());
                        }

                        var visibleFriends = new java.util.HashSet<String>();
                        for (String docId : docIds) {
                            try {
                                var peers = rendezvousClient.discoverPeers(deviceCredential, docId);
                                for (var rp : peers) {
                                    if (peerId.equals(rp.peerId())) continue;
                                    visibleFriends.add(rp.peerId());
                                    // UDP connect IS the hole punch — try IPv6 first if available
                                    peerManager.connectAndSync(rp.host(), rp.ipv6Host(), rp.port());
                                }
                                if (!peers.isEmpty()) {
                                    log.debug("Rendezvous: discovered {} peers for doc {}", peers.size(), docId);
                                }
                            } catch (RendezvousClient.CredentialRejectedException e) {
                                log.warn("Rendezvous: credential rejected during discover, re-enrolling...");
                                reEnrollDevice();
                                break; // retry on next loop iteration
                            } catch (Exception e) {
                                log.debug("Rendezvous: discover failed for doc {}: {}", docId, e.getMessage());
                            }
                        }

                        rendezvousVisibleFriends = java.util.Set.copyOf(visibleFriends);

                        // Self-discovery: find our own devices on other networks
                        try {
                            var discoveredDevices = rendezvousClient.discoverMyDevices(deviceCredential);
                            myDevices = discoveredDevices.stream()
                                    .map(d -> new DeviceInfo(d.host(), d.port(), d.ipv6Host()))
                                    .toList();
                            for (var dev : discoveredDevices) {
                                peerManager.connectAndSync(dev.host(), dev.ipv6Host(), dev.port());
                            }
                            if (!discoveredDevices.isEmpty()) {
                                log.debug("Rendezvous: discovered {} of my own devices", discoveredDevices.size());
                            }
                        } catch (RendezvousClient.CredentialRejectedException e) {
                            log.warn("Rendezvous: credential rejected during my-devices, re-enrolling...");
                            reEnrollDevice();
                        } catch (Exception e) {
                            log.debug("Rendezvous: my-devices discovery failed: {}", e.getMessage());
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
                    rendezvousClient.depart(deviceCredential);
                } catch (Exception e) {
                    // shutting down, ignore
                }
            }
        }, "sharing-shutdown"));

        // Start presence heartbeat (30-second interval)
        startPresenceHeartbeat();

        log.info("PeerManager started on port {}", peerManager.serverPort());
    }

    // ==================== Presence ====================

    @Override
    public void setPresenceEnabled(boolean enabled) {
        this.presenceEnabled = enabled;
    }

    @Override
    public boolean isPresenceEnabled() {
        return presenceEnabled;
    }

    @Override
    public String queryFriendPresence(String friendEmail) {
        if (rendezvousClient == null || deviceCredential == null) return "OFFLINE";

        FriendConfig fc = IntelConfig.get().getFriendByEmail(friendEmail);
        if (fc == null || fc.getReceivedCert() == null) return "OFFLINE";

        try {
            String certJson = mapper.writeValueAsString(fc.getReceivedCert());
            var info = rendezvousClient.queryPresence(deviceCredential, friendEmail, certJson);
            return info != null ? info.state() : "OFFLINE";
        } catch (RendezvousClient.CredentialRejectedException e) {
            log.warn("Presence query credential rejected, re-enrolling...");
            reEnrollDevice();
            return "OFFLINE";
        } catch (Exception e) {
            log.debug("Presence query failed for {}: {}", friendEmail, e.getMessage());
            return "OFFLINE";
        }
    }

    @Override
    public List<DeviceInfo> getMyDevices() {
        return myDevices;
    }

    @Override
    public List<FriendNetworkStatus> getFriendNetworkStatuses() {
        List<FriendNetworkStatus> result = new ArrayList<>();
        for (FriendConfig f : IntelConfig.get().getFriends()) {
            String email = f.getEmail();
            String connectionState;
            if (peerManager != null && peerManager.isMutualFriendByEmail(email)) {
                connectionState = "connected";
            } else if (rendezvousVisibleFriends.contains(email)) {
                connectionState = "discovered";
            } else {
                connectionState = "offline";
            }
            String presence = friendPresenceCache.getOrDefault(email, "OFFLINE");
            result.add(new FriendNetworkStatus(email, f.label(), connectionState, presence));
        }
        return result;
    }

    private boolean isUserActive() {
        return System.currentTimeMillis() - lastActivityMs.get() < IDLE_THRESHOLD_MS;
    }

    /** Install AWT event listener to track user activity. */
    private void installActivityListener() {
        try {
            AWTEventListener listener = e -> lastActivityMs.set(System.currentTimeMillis());
            Toolkit.getDefaultToolkit().addAWTEventListener(listener,
                    AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        } catch (Exception e) {
            log.debug("Could not install AWT activity listener: {}", e.getMessage());
        }
    }

    /** Start the heartbeat thread that publishes presence every 30 seconds. */
    private void startPresenceHeartbeat() {
        installActivityListener();
        Thread.ofVirtual().name("presence-heartbeat").start(() -> {
            while (true) {
                try {
                    Thread.sleep(30_000);
                    if (rendezvousClient != null && deviceCredential != null) {
                        // Publish our own presence
                        if (presenceEnabled) {
                            String state = isUserActive() ? "ONLINE" : "IDLE";
                            try {
                                rendezvousClient.publishPresence(deviceCredential, state);
                            } catch (RendezvousClient.CredentialRejectedException e) {
                                log.warn("Presence heartbeat credential rejected, re-enrolling...");
                                reEnrollDevice();
                            } catch (Exception e) {
                                log.debug("Presence heartbeat failed: {}", e.getMessage());
                            }
                        }

                        // Query friend presence (cached for UI)
                        for (FriendConfig f : IntelConfig.get().getFriends()) {
                            String p = queryFriendPresence(f.getEmail());
                            friendPresenceCache.put(f.getEmail(), p);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
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
            boolean online = peer != null
                    || (peerManager != null && peerManager.isMutualFriendByEmail(f.getEmail()));
            long lastSeen = (peer != null) ? peer.lastSeen() : 0;
            result.add(new FriendStatus(f.getEmail(), f.label(), online, lastSeen));
        }
        return result;
    }

    @Override
    public boolean isFriendOnline(String email) {
        if (peerManager != null && peerManager.isMutualFriendByEmail(email)) return true;
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

    // ==================== Performance Test ====================

    @Override
    public void startPerfTest(String friendEmail, Consumer<PerfTestResult> onComplete) {
        if (peerManager == null) {
            onComplete.accept(null);
            return;
        }

        // Find a connected device for this friend
        String targetDeviceId = null;
        for (var info : peerManager.getConnectionInfos()) {
            if (friendEmail.equals(info.email()) && info.mutualFriend()) {
                targetDeviceId = info.deviceId();
                break;
            }
        }
        if (targetDeviceId == null) {
            onComplete.accept(null);
            return;
        }

        String testId = java.util.UUID.randomUUID().toString();
        activePerfTests.put(testId, onComplete);
        perfTestDevices.put(testId, targetDeviceId);

        try {
            peerManager.sendToDevice(targetDeviceId, new NetworkMessage.PerfRequest(testId));
        } catch (IOException e) {
            log.warn("Failed to send PerfRequest: {}", e.getMessage());
            activePerfTests.remove(testId);
            perfTestDevices.remove(testId);
            onComplete.accept(null);
        }
    }

    @Override
    public void respondToPerfTest(String testId, boolean accept) {
        if (peerManager == null) return;
        String deviceId = incomingPerfRequests.remove(testId);
        if (deviceId == null) return;

        try {
            if (accept) {
                peerManager.sendToDevice(deviceId, new NetworkMessage.PerfAccept(testId));
            } else {
                peerManager.sendToDevice(deviceId, new NetworkMessage.PerfReject(testId));
            }
        } catch (IOException e) {
            log.warn("Failed to send perf response: {}", e.getMessage());
        }
    }

    @Override
    public void addPerfRequestListener(Consumer<PerfTestRequest> l) {
        perfRequestListeners.add(l);
    }

    @Override
    public void removePerfRequestListener(Consumer<PerfTestRequest> l) {
        perfRequestListeners.remove(l);
    }

    /** Handle perf-related messages from PeerManager. */
    private void onPerfMessage(String deviceId, NetworkMessage msg) {
        switch (msg) {
            case NetworkMessage.PerfRequest req -> {
                incomingPerfRequests.put(req.testId(), deviceId);
                String email = peerManager != null ? findEmailForDevice(deviceId) : "unknown";
                FriendConfig fc = IntelConfig.get().getFriendByEmail(email);
                String displayName = fc != null && fc.label() != null ? fc.label() : email;
                var request = new PerfTestRequest(req.testId(), email, displayName);
                for (var l : perfRequestListeners) {
                    try { l.accept(request); } catch (Exception ex) { log.warn("Perf request listener error", ex); }
                }
            }
            case NetworkMessage.PerfAccept accept -> {
                if (activePerfTests.containsKey(accept.testId())) {
                    String targetDeviceId = perfTestDevices.get(accept.testId());
                    if (targetDeviceId != null) {
                        Thread.ofVirtual().name("perf-test-" + accept.testId()).start(() ->
                                runPerfTestSequence(accept.testId(), targetDeviceId));
                    }
                }
            }
            case NetworkMessage.PerfReject reject -> {
                var callback = activePerfTests.remove(reject.testId());
                perfTestDevices.remove(reject.testId());
                if (callback != null) {
                    javax.swing.SwingUtilities.invokeLater(() -> callback.accept(null));
                }
            }
            case NetworkMessage.PerfPong pong -> {
                // Collected by the test sequence thread via perfPongQueue
                var queue = perfPongQueues.get(pong.testId());
                if (queue != null) {
                    queue.offer(pong);
                }
            }
            default -> {} // PerfPing handled by PeerManager auto-reply
        }
    }

    /** Per-test pong collection queues. */
    private final ConcurrentHashMap<String, java.util.concurrent.BlockingQueue<NetworkMessage.PerfPong>> perfPongQueues = new ConcurrentHashMap<>();

    /** Run the actual perf test sequence (latency + throughput). Called on virtual thread after PerfAccept. */
    private void runPerfTestSequence(String testId, String targetDeviceId) {
        var pongQueue = new java.util.concurrent.LinkedBlockingQueue<NetworkMessage.PerfPong>();
        perfPongQueues.put(testId, pongQueue);

        try {
            // Phase 1: Latency — 20 pings with 32-byte payload, 100ms apart
            int latencyPings = 20;
            byte[] smallPayload = new byte[32];
            List<Double> rtts = new ArrayList<>();

            for (int i = 0; i < latencyPings; i++) {
                long sendTs = System.nanoTime();
                try {
                    peerManager.sendToDevice(targetDeviceId, new NetworkMessage.PerfPing(testId, i, sendTs, smallPayload));
                } catch (IOException e) {
                    break;
                }
                // Wait for pong with 500ms timeout
                try {
                    var pong = pongQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (pong != null && pong.seq() == i) {
                        double rttMs = (System.nanoTime() - pong.sendTs()) / 1_000_000.0;
                        rtts.add(rttMs);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }

            // Phase 2: Throughput — 50 pings with 1200-byte payload, sent as fast as possible
            int throughputPings = 50;
            byte[] largePayload = new byte[1200];
            int throughputReceived = 0;
            long throughputStart = System.nanoTime();

            for (int i = 0; i < throughputPings; i++) {
                long sendTs = System.nanoTime();
                try {
                    peerManager.sendToDevice(targetDeviceId, new NetworkMessage.PerfPing(testId, 100 + i, sendTs, largePayload));
                } catch (IOException e) {
                    break;
                }
            }

            // Collect pongs for up to 5 seconds
            long deadline = System.currentTimeMillis() + 5000;
            while (throughputReceived < throughputPings && System.currentTimeMillis() < deadline) {
                try {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) break;
                    var pong = pongQueue.poll(remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (pong != null && pong.seq() >= 100) {
                        throughputReceived++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            long throughputElapsedNs = System.nanoTime() - throughputStart;

            // Build result
            double avgLatency = rtts.isEmpty() ? 0 : rtts.stream().mapToDouble(d -> d).average().orElse(0);
            double minLatency = rtts.isEmpty() ? 0 : rtts.stream().mapToDouble(d -> d).min().orElse(0);
            double maxLatency = rtts.isEmpty() ? 0 : rtts.stream().mapToDouble(d -> d).max().orElse(0);
            double throughputKBps = throughputElapsedNs > 0
                    ? (throughputReceived * 1200.0 / 1024.0) / (throughputElapsedNs / 1_000_000_000.0)
                    : 0;
            double packetLoss = latencyPings > 0 ? (1.0 - (double) rtts.size() / latencyPings) * 100.0 : 100.0;

            var result = new PerfTestResult(avgLatency, minLatency, maxLatency,
                    throughputKBps, packetLoss, rtts.size(), latencyPings);

            var callback = activePerfTests.remove(testId);
            perfTestDevices.remove(testId);
            if (callback != null) {
                javax.swing.SwingUtilities.invokeLater(() -> callback.accept(result));
            }
        } finally {
            perfPongQueues.remove(testId);
        }
    }

    private String findEmailForDevice(String deviceId) {
        if (peerManager == null) return null;
        for (var info : peerManager.getConnectionInfos()) {
            if (deviceId.equals(info.deviceId())) return info.email();
        }
        return null;
    }

    // ==================== Connected Devices ====================

    @Override
    public List<ConnectedDevice> getConnectedDevices() {
        if (peerManager == null) return List.of();
        List<ConnectedDevice> result = new ArrayList<>();
        for (var info : peerManager.getConnectionInfos()) {
            String email = info.email();
            FriendConfig fc = email != null ? IntelConfig.get().getFriendByEmail(email) : null;
            String displayName = fc != null && fc.label() != null ? fc.label() : email;
            String connType = classifyConnectionType(info.remoteAddress());
            result.add(new ConnectedDevice(email, displayName, info.deviceId(),
                    info.remoteAddress(), connType, info.mutualFriend()));
        }
        return result;
    }

    /** Classify connection type from remote address string. */
    private static String classifyConnectionType(String address) {
        if (address == null) return "Unknown";
        // Remove leading / from InetSocketAddress.toString()
        String addr = address.startsWith("/") ? address.substring(1) : address;

        // IPv6
        if (addr.contains(":") && addr.contains("[")) return "IPv6";
        // Count colons — if more than one, it's IPv6
        long colonCount = addr.chars().filter(c -> c == ':').count();
        if (colonCount > 1) return "IPv6";

        // Extract host part (before last :port)
        String host = addr;
        int lastColon = addr.lastIndexOf(':');
        if (lastColon > 0) host = addr.substring(0, lastColon);

        // Private IP ranges
        if (host.startsWith("10.") || host.startsWith("192.168.")
                || host.startsWith("172.16.") || host.startsWith("172.17.")
                || host.startsWith("172.18.") || host.startsWith("172.19.")
                || host.startsWith("172.20.") || host.startsWith("172.21.")
                || host.startsWith("172.22.") || host.startsWith("172.23.")
                || host.startsWith("172.24.") || host.startsWith("172.25.")
                || host.startsWith("172.26.") || host.startsWith("172.27.")
                || host.startsWith("172.28.") || host.startsWith("172.29.")
                || host.startsWith("172.30.") || host.startsWith("172.31.")
                || host.startsWith("127.")) {
            return "LAN";
        }
        return "Hole Punch";
    }

    @Override
    public void onFriendListChanged() {
        if (peerManager != null) {
            peerManager.setFriendshipDocIds(computeFriendshipDocIds());
        }
    }

    @Override
    public void onFriendAdded(String friendEmail) {
        if (certSigner == null) {
            ensureCertSigner();
        }
        if (certSigner == null) return;

        String localEmail = localSession != null ? localSession.userId() : AuthConfig.load().getEmail();
        if (localEmail == null) return;

        try {
            // Create a friendship cert: "I ({localEmail}) accept {friendEmail}"
            FriendshipCertData cert = certSigner.createFriendshipCert(localEmail, friendEmail);
            FriendConfig fc = IntelConfig.get().getFriendByEmail(friendEmail);
            if (fc != null) {
                fc.setIssuedCert(cert);
                IntelConfig.get().save();
                log.info("Created friendship cert for {}", friendEmail);

                // Deliver to peer if connected
                if (peerManager != null) {
                    peerManager.sendCertToFriend(friendEmail, cert);
                }

                // Update UER
                updateLocalUer();
            }
        } catch (GeneralSecurityException e) {
            log.warn("Failed to create friendship cert for {}: {}", friendEmail, e.getMessage());
        }
    }

    @Override
    public boolean isMutualFriend(String email) {
        if (peerManager == null) return false;
        return peerManager.isMutualFriendByEmail(email);
    }

    /** Ensure CertSigner and IdentityCert are initialized. */
    private void ensureCertSigner() {
        if (certSigner != null) return;
        try {
            KeyPairStore keyStore = new KeyPairStore();
            KeyPair keyPair = keyStore.loadOrGenerate();
            certSigner = new CertSigner(keyPair);

            // Load or create identity cert
            AuthConfig auth = AuthConfig.load();
            String email = localSession != null ? localSession.userId() : auth.getEmail();
            if (email != null) {
                if (auth.getIdentityCertJson() != null) {
                    try {
                        localIdentityCert = mapper.readValue(auth.getIdentityCertJson(), IdentityCert.class);
                        // Re-create if email changed
                        if (!email.equals(localIdentityCert.email())) {
                            localIdentityCert = certSigner.createIdentityCert(email);
                            auth.setIdentityCertJson(mapper.writeValueAsString(localIdentityCert));
                            auth.save();
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse stored identity cert, creating new one");
                        localIdentityCert = certSigner.createIdentityCert(email);
                        auth.setIdentityCertJson(mapper.writeValueAsString(localIdentityCert));
                        auth.save();
                    }
                } else {
                    localIdentityCert = certSigner.createIdentityCert(email);
                    auth.setIdentityCertJson(mapper.writeValueAsString(localIdentityCert));
                    auth.save();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to initialize cert signer: {}", e.getMessage());
        }
    }

    /** Migrate existing friends: auto-generate certs for friends without them. */
    private void migrateFriendCerts() {
        if (certSigner == null) return;
        String localEmail = localSession != null ? localSession.userId() : AuthConfig.load().getEmail();
        if (localEmail == null) return;

        boolean changed = false;
        for (FriendConfig f : IntelConfig.get().getFriends()) {
            if (f.getIssuedCert() == null) {
                try {
                    FriendshipCertData cert = certSigner.createFriendshipCert(localEmail, f.getEmail());
                    f.setIssuedCert(cert);
                    changed = true;
                    log.info("Migrated: created friendship cert for {}", f.getEmail());
                } catch (GeneralSecurityException e) {
                    log.warn("Failed to migrate cert for {}: {}", f.getEmail(), e.getMessage());
                }
            }
        }
        if (changed) {
            IntelConfig.get().save();
        }
    }

    /** Encrypt and distribute UER to mutual friends. */
    private void updateLocalUer() {
        // UER update is a best-effort background operation
        // Full implementation will prompt user for password and encrypt
        // For now, just log that it should happen
        log.debug("UER update requested (password-based encryption pending UI integration)");
    }

    /** Handle a FriendImportOffer from a peer with a key mismatch. */
    private void handleFriendImport(String peerEmail, NetworkMessage.FriendImportOffer offer) {
        log.info("FriendImportOffer from {} — old certs available for re-import", peerEmail);
        // UI integration will show FriendImportDialog
        // For now, auto-accept if we have the friend in our list
        FriendConfig fc = IntelConfig.get().getFriendByEmail(peerEmail);
        if (fc != null && certSigner != null) {
            String localEmail = localSession != null ? localSession.userId() : AuthConfig.load().getEmail();
            if (localEmail != null) {
                try {
                    // Generate new certs with current key
                    FriendshipCertData newCert = certSigner.createFriendshipCert(localEmail, peerEmail);
                    fc.setIssuedCert(newCert);
                    // receivedCert will come from the peer when they re-add us
                    IntelConfig.get().save();
                    log.info("Re-established friendship with {} after key change", peerEmail);

                    if (peerManager != null) {
                        peerManager.sendCertToFriend(peerEmail, newCert);
                    }
                } catch (GeneralSecurityException e) {
                    log.warn("Failed to re-create cert for {}: {}", peerEmail, e.getMessage());
                }
            }
        }
    }

    /** Compute friendship doc IDs from the local friend list. */
    private List<String> computeFriendshipDocIds() {
        String localEmail = localSession != null ? localSession.userId() : AuthConfig.load().getEmail();
        if (localEmail == null || localEmail.isBlank()) return List.of();
        var ids = new ArrayList<String>();
        for (FriendConfig f : IntelConfig.get().getFriends()) {
            ids.add(FriendshipDocId.compute(localEmail, f.getEmail()));
        }
        return ids;
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
