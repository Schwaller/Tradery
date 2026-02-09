package com.tradery.sharing.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.documents.Document;
import com.tradery.documents.DocumentManager;
import com.tradery.documents.DocumentMember;
import com.tradery.documents.DocumentWorkspace;
import com.tradery.news.ui.FriendConfig;
import com.tradery.news.ui.IntelConfig;
import com.tradery.news.ui.coin.FactStore;
import com.tradery.sharing.governance.GovernanceEngine;
import com.tradery.sharing.sync.NetworkMessage;
import com.tradery.sharing.sync.PeerManager;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HTTP control API that bridges Javalin requests to DocumentManager/PeerManager/FactStore.
 * Used by integration tests to drive a headless peer inside a Docker container.
 */
public class PeerController {

    private static final Logger log = LoggerFactory.getLogger(PeerController.class);

    private final String peerId;
    private final DocumentManager documentManager;
    private final PeerManager peerManager;
    private final ObjectMapper mapper;
    private final Map<String, DocumentWorkspace> workspaces = new ConcurrentHashMap<>();
    private final List<NetworkMessage.ChatMessage> receivedChats = new CopyOnWriteArrayList<>();

    public PeerController(String peerId, Path dataDir, ObjectMapper mapper) throws IOException {
        this.peerId = peerId;
        this.mapper = mapper;
        this.documentManager = new DocumentManager(dataDir.resolve("documents"));
        this.peerManager = new PeerManager(peerId, peerId, documentManager, mapper);

        // Initialize IntelConfig for this peer
        IntelConfig config = IntelConfig.get();
        config.setUserEmail(peerId);
        config.setDeviceId(peerId);
        config.save();

        // Listen for incoming chat messages
        peerManager.addChatListener(receivedChats::add);
    }

    public void registerRoutes(Javalin app) {
        app.get("/health", ctx -> ctx.status(200).result("OK"));
        app.get("/status", this::handleStatus);
        app.post("/documents", this::handleCreateDocument);
        app.post("/documents/{id}/facts", this::handleAppendFacts);
        app.get("/documents/{id}/current", this::handleGetCurrent);
        app.get("/documents/{id}/facts", this::handleGetFacts);
        app.get("/documents/{id}/pending", this::handleGetPending);
        app.get("/documents/{id}/pending-peer-ids", this::handleGetPendingPeerIds);
        app.post("/documents/{id}/approve", this::handleApproveSubmission);
        app.post("/documents/{id}/reject", this::handleRejectSubmission);
        app.post("/documents/{id}/members", this::handleSetMembers);
        app.post("/connect", this::handleConnect);
        app.post("/sync", this::handleSync);
        app.get("/peers", this::handleGetPeers);

        // Friendship management
        app.post("/friends", this::handleAddFriend);
        app.delete("/friends/{email}", this::handleRemoveFriend);
        app.get("/friends", this::handleGetFriends);
        app.get("/mutual/{email}", this::handleIsMutualFriend);

        // Chat
        app.post("/chat", this::handleSendChat);
        app.get("/chat", this::handleGetChatMessages);
    }

    private void handleStatus(Context ctx) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("peerId", peerId);
        status.put("p2pPort", peerManager.serverPort());
        status.put("documents", List.copyOf(workspaces.keySet()));
        status.put("connectedPeers", List.copyOf(peerManager.connectedPeerIds()));
        ctx.json(status);
    }

    private void handleCreateDocument(Context ctx) throws IOException {
        CreateDocRequest req = ctx.bodyAsClass(CreateDocRequest.class);
        String docId = req.docId();

        // Create directory and document.yaml
        Path docDir = documentManager.documentsDir().resolve(docId);
        docDir.toFile().mkdirs();

        Document doc = new Document(docId, req.name());
        doc.setId(docId);
        doc.setVisibility(Document.Visibility.PUBLIC);

        if (req.governanceType() != null && !req.governanceType().equals("OPEN")) {
            Document.Governance gov = new Document.Governance();
            gov.setType(Document.Governance.Type.valueOf(req.governanceType()));
            gov.setVotingQuorum(req.votingQuorum());
            doc.setGovernance(gov);
        }

        documentManager.updateDocument(doc);

        // Open workspace
        DocumentWorkspace ws = documentManager.openDocument(docId);
        workspaces.put(docId, ws);
        peerManager.registerWorkspace(docId, ws);

        log.info("Created document {} ({})", docId, req.name());
        ctx.status(201).json(Map.of("docId", docId, "peerId", ws.entityStore().factStore().peerId()));
    }

    private void handleAppendFacts(Context ctx) {
        String docId = ctx.pathParam("id");
        DocumentWorkspace ws = workspaces.get(docId);
        if (ws == null) {
            ctx.status(404).result("Document not found: " + docId);
            return;
        }

        AppendFactsRequest req = ctx.bodyAsClass(AppendFactsRequest.class);
        FactStore store = ws.entityStore().factStore();

        List<FactStore.PendingFact> pending = req.facts().stream()
                .map(f -> new FactStore.PendingFact(f.entityId(), f.attribute(), f.value(),
                        f.source() != null ? f.source() : "test"))
                .toList();
        store.appendFacts(pending);

        log.info("Appended {} facts to doc {}", pending.size(), docId);
        ctx.status(200).json(Map.of("appended", pending.size()));
    }

    private void handleGetCurrent(Context ctx) {
        String docId = ctx.pathParam("id");
        DocumentWorkspace ws = workspaces.get(docId);
        if (ws == null) {
            ctx.status(404).result("Document not found: " + docId);
            return;
        }

        String entityId = ctx.queryParam("entityId");
        String attribute = ctx.queryParam("attribute");
        FactStore store = ws.entityStore().factStore();

        if (entityId != null && attribute != null) {
            String value = store.getCurrent(entityId, attribute);
            ctx.json(Map.of("value", value != null ? value : ""));
        } else if (entityId != null) {
            Map<String, String> map = store.getCurrentMap(entityId);
            ctx.json(map);
        } else {
            ctx.status(400).result("entityId parameter required");
        }
    }

    private void handleGetFacts(Context ctx) {
        String docId = ctx.pathParam("id");
        DocumentWorkspace ws = workspaces.get(docId);
        if (ws == null) {
            ctx.status(404).result("Document not found: " + docId);
            return;
        }

        long since = 0;
        String sinceParam = ctx.queryParam("since");
        if (sinceParam != null) {
            since = Long.parseLong(sinceParam);
        }

        List<FactStore.Fact> facts = ws.entityStore().factStore().getFactsSince(since);
        ctx.json(facts);
    }

    private void handleGetPending(Context ctx) {
        String docId = ctx.pathParam("id");
        DocumentWorkspace ws = workspaces.get(docId);
        if (ws == null) {
            ctx.status(404).result("Document not found: " + docId);
            return;
        }

        int count = ws.entityStore().factStore().getPendingCount();
        ctx.json(Map.of("pendingCount", count));
    }

    private void handleGetPendingPeerIds(Context ctx) {
        String docId = ctx.pathParam("id");
        DocumentWorkspace ws = workspaces.get(docId);
        if (ws == null) {
            ctx.status(404).result("Document not found: " + docId);
            return;
        }

        List<String> peerIds = ws.entityStore().factStore().getPendingPeerIds();
        ctx.json(peerIds);
    }

    private void handleApproveSubmission(Context ctx) {
        String docId = ctx.pathParam("id");
        DocumentWorkspace ws = workspaces.get(docId);
        if (ws == null) {
            ctx.status(404).result("Document not found: " + docId);
            return;
        }

        String submitterPeerId = ctx.queryParam("submitterPeerId");
        if (submitterPeerId == null) {
            ctx.status(400).result("submitterPeerId required");
            return;
        }

        GovernanceEngine governance = new GovernanceEngine();
        String commitId = governance.approveSubmission(ws, submitterPeerId);
        ctx.json(Map.of("commitId", commitId != null ? commitId : "", "approved", commitId != null));
    }

    private void handleRejectSubmission(Context ctx) {
        String docId = ctx.pathParam("id");
        DocumentWorkspace ws = workspaces.get(docId);
        if (ws == null) {
            ctx.status(404).result("Document not found: " + docId);
            return;
        }

        String submitterPeerId = ctx.queryParam("submitterPeerId");
        if (submitterPeerId == null) {
            ctx.status(400).result("submitterPeerId required");
            return;
        }

        GovernanceEngine governance = new GovernanceEngine();
        governance.rejectSubmission(ws, submitterPeerId);
        ctx.status(200).result("OK");
    }

    private void handleSetMembers(Context ctx) throws IOException {
        String docId = ctx.pathParam("id");
        DocumentWorkspace ws = workspaces.get(docId);
        if (ws == null) {
            ctx.status(404).result("Document not found: " + docId);
            return;
        }

        List<DocumentMember> members = ctx.bodyAsClass(MemberListWrapper.class).members();
        documentManager.writeMembers(docId, members);
        ctx.status(200).result("OK");
    }

    private void handleConnect(Context ctx) {
        ConnectRequest req = ctx.bodyAsClass(ConnectRequest.class);
        peerManager.connectAndSync(req.host(), req.port());
        log.info("Connecting to {}:{}", req.host(), req.port());
        ctx.status(200).result("OK");
    }

    private void handleSync(Context ctx) {
        peerManager.requestSync();
        log.info("Re-sync requested with all connected peers");
        ctx.status(200).result("OK");
    }

    private void handleGetPeers(Context ctx) {
        ctx.json(List.copyOf(peerManager.connectedPeerIds()));
    }

    // ==================== Friendship ====================

    private void handleAddFriend(Context ctx) {
        AddFriendRequest req = ctx.bodyAsClass(AddFriendRequest.class);
        IntelConfig.get().addFriend(new FriendConfig(req.email(), req.displayName()));
        IntelConfig.get().save();
        peerManager.reannounceFriendship();
        log.info("Added friend: {}", req.email());
        ctx.status(200).result("OK");
    }

    private void handleRemoveFriend(Context ctx) {
        String email = ctx.pathParam("email");
        IntelConfig.get().removeFriend(email);
        IntelConfig.get().save();
        peerManager.reannounceFriendship();
        log.info("Removed friend: {}", email);
        ctx.status(200).result("OK");
    }

    private void handleGetFriends(Context ctx) {
        List<Map<String, String>> friends = IntelConfig.get().getFriends().stream()
                .map(f -> Map.of("email", f.getEmail(), "displayName", f.label()))
                .toList();
        ctx.json(friends);
    }

    private void handleIsMutualFriend(Context ctx) {
        String email = ctx.pathParam("email");
        boolean mutual = peerManager.isMutualFriendByEmail(email);
        ctx.json(Map.of("mutual", mutual));
    }

    // ==================== Chat ====================

    private void handleSendChat(Context ctx) {
        SendChatRequest req = ctx.bodyAsClass(SendChatRequest.class);
        peerManager.sendChat(peerId, req.recipientId(), req.text());
        log.info("Sent chat to {}: {}", req.recipientId(), req.text());
        ctx.status(200).result("OK");
    }

    private void handleGetChatMessages(Context ctx) {
        ctx.json(receivedChats.stream().map(msg -> Map.of(
                "senderId", msg.senderId(),
                "recipientId", msg.recipientId() != null ? msg.recipientId() : "",
                "text", msg.text(),
                "timestamp", String.valueOf(msg.timestamp())
        )).toList());
    }

    // ==================================================

    public void close() {
        peerManager.close();
        workspaces.values().forEach(DocumentWorkspace::close);
    }

    record MemberListWrapper(List<DocumentMember> members) {}
}
