package com.tradery.rendezvous;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Javalin HTTP server implementing the rendezvous protocol.
 * Endpoints match the contract expected by RendezvousClient in tradery-sharing.
 */
public class RendezvousServer {

    private static final Logger log = LoggerFactory.getLogger(RendezvousServer.class);

    private final Javalin app;
    private final PeerRegistry registry;

    public RendezvousServer(int port) {
        this.registry = new PeerRegistry();
        this.app = createApp();
        app.start(port);
        log.info("Rendezvous server started on port {}", port());
    }

    public int port() {
        return app.port();
    }

    public void stop() {
        app.stop();
    }

    private Javalin createApp() {
        Javalin javalin = Javalin.create();

        // Bearer auth filter — skip for /health
        javalin.before(ctx -> {
            if ("/health".equals(ctx.path())) return;
            String auth = ctx.header("Authorization");
            if (auth == null || !auth.startsWith("Bearer ") || auth.substring(7).isBlank()) {
                ctx.status(401).result("Unauthorized");
                ctx.skipRemainingHandlers();
            }
        });

        javalin.get("/health", ctx -> ctx.status(200).result("OK"));
        javalin.post("/announce", this::handleAnnounce);
        javalin.get("/peers", this::handlePeers);
        javalin.delete("/depart", this::handleDepart);

        return javalin;
    }

    private void handleAnnounce(Context ctx) {
        AnnounceRequest req = ctx.bodyAsClass(AnnounceRequest.class);
        if (req.peerId() == null || req.peerId().isBlank()) {
            ctx.status(400).result("peerId is required");
            return;
        }
        String host = ctx.ip();
        registry.announce(req.peerId(), host, req.port(), req.documentIds() != null ? req.documentIds() : List.of());
        log.info("Announce: peer={} host={} port={} docs={}", req.peerId(), host, req.port(),
                req.documentIds() != null ? req.documentIds().size() : 0);
        ctx.status(200).result("OK");
    }

    private void handlePeers(Context ctx) {
        String documentId = ctx.queryParam("documentId");
        if (documentId == null || documentId.isBlank()) {
            ctx.status(400).result("documentId query parameter is required");
            return;
        }
        List<PeerResponse> peers = registry.findByDocument(documentId);
        ctx.json(peers);
    }

    private void handleDepart(Context ctx) {
        String peerId = ctx.queryParam("peerId");
        if (peerId == null || peerId.isBlank()) {
            ctx.status(400).result("peerId query parameter is required");
            return;
        }
        registry.depart(peerId);
        log.info("Depart: peer={}", peerId);
        ctx.status(200).result("OK");
    }
}
