package com.tradery.rendezvous;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Javalin HTTP server implementing the rendezvous protocol.
 * Endpoints:
 *   POST /enroll-device         — exchange Keycloak token for device credential (Keycloak auth)
 *   POST /rotate-credential     — refresh device credential (device auth)
 *   GET  /backend-key           — public key for offline credential verification (no auth)
 *   POST /announce              — peer registration (device auth)
 *   GET  /peers                 — peer discovery (device auth)
 *   DELETE /depart              — peer departure (device auth)
 *   GET  /health                — health check (no auth)
 */
public class RendezvousServer {

    private static final Logger log = LoggerFactory.getLogger(RendezvousServer.class);

    /** Endpoints that don't require any auth. */
    private static final Set<String> NO_AUTH_PATHS = Set.of("/health", "/backend-key");

    /** Endpoints that use Keycloak Bearer token (enrollment only). */
    private static final Set<String> KEYCLOAK_AUTH_PATHS = Set.of("/enroll-device");

    private final Javalin app;
    private final PeerRegistry peerRegistry;
    private final DeviceRegistry deviceRegistry;
    private final BackendKeyStore keyStore;
    private final KeycloakValidator keycloakValidator;

    public RendezvousServer(int port, BackendKeyStore keyStore, KeycloakValidator keycloakValidator) {
        this.peerRegistry = new PeerRegistry();
        this.deviceRegistry = new DeviceRegistry();
        this.keyStore = keyStore;
        this.keycloakValidator = keycloakValidator;
        this.app = createApp();
        app.start(port);
        log.info("Rendezvous server started on port {}", port());
    }

    /** Constructor for tests — accepts Keycloak token as-is (no validation). */
    public RendezvousServer(int port) {
        this(port, createDefaultKeyStore(), token -> {
            // Test mode: extract email from token string or use token as userId
            return new KeycloakValidator.UserIdentity(token, token);
        });
    }

    public int port() { return app.port(); }
    public void stop() { app.stop(); }

    private Javalin createApp() {
        Javalin javalin = Javalin.create();

        javalin.before(ctx -> {
            String path = ctx.path();

            // No auth required
            if (NO_AUTH_PATHS.contains(path)) return;

            // Keycloak Bearer auth (enrollment)
            if (KEYCLOAK_AUTH_PATHS.contains(path)) {
                String auth = ctx.header("Authorization");
                if (auth == null || !auth.startsWith("Bearer ") || auth.substring(7).isBlank()) {
                    ctx.status(401).result("Unauthorized");
                    ctx.skipRemainingHandlers();
                }
                return;
            }

            // Device credential auth (all other endpoints)
            String credential = ctx.header("X-Device-Credential");
            if (credential == null || credential.isBlank()) {
                ctx.status(401).result("Missing device credential");
                ctx.skipRemainingHandlers();
                return;
            }

            var claims = DeviceCredential.verify(credential, keyStore.verifyKey());
            if (claims == null) {
                ctx.status(401).result("Invalid device credential");
                ctx.skipRemainingHandlers();
                return;
            }
            if (claims.isExpired()) {
                ctx.status(401).result("Device credential expired");
                ctx.skipRemainingHandlers();
                return;
            }
            if (deviceRegistry.isRevoked(claims.deviceId(), claims.userId())) {
                ctx.status(403).result("Device revoked");
                ctx.skipRemainingHandlers();
                return;
            }

            // Verify request signature if present
            String signature = ctx.header("X-Request-Signature");
            String timestamp = ctx.header("X-Request-TS");
            if (signature != null && timestamp != null) {
                try {
                    PublicKey deviceKey = DeviceCredential.extractDevicePublicKey(credential);
                    byte[] sigBytes = Base64.getDecoder().decode(signature);
                    byte[] body = ctx.bodyAsBytes();
                    if (!RequestSigner.verify(deviceKey, ctx.method().name(), ctx.path(),
                            timestamp, body, sigBytes)) {
                        ctx.status(401).result("Invalid request signature");
                        ctx.skipRemainingHandlers();
                        return;
                    }
                    // Check timestamp window (60 seconds)
                    long ts = Long.parseLong(timestamp);
                    if (Math.abs(System.currentTimeMillis() - ts) > 60_000) {
                        ctx.status(401).result("Request timestamp out of window");
                        ctx.skipRemainingHandlers();
                        return;
                    }
                } catch (Exception e) {
                    ctx.status(401).result("Signature verification failed");
                    ctx.skipRemainingHandlers();
                    return;
                }
            }

            // Store claims in context for handlers
            ctx.attribute("deviceId", claims.deviceId());
            ctx.attribute("userId", claims.userId());
            deviceRegistry.touch(claims.deviceId());
        });

        javalin.get("/health", ctx -> ctx.status(200).result("OK"));
        javalin.get("/backend-key", ctx -> ctx.json(new BackendKeyResponse(keyStore.publicKeyBase64())));
        javalin.post("/enroll-device", this::handleEnroll);
        javalin.post("/rotate-credential", this::handleRotate);
        javalin.post("/announce", this::handleAnnounce);
        javalin.get("/peers", this::handlePeers);
        javalin.delete("/depart", this::handleDepart);

        return javalin;
    }

    private void handleEnroll(Context ctx) {
        try {
            EnrollRequest req = ctx.bodyAsClass(EnrollRequest.class);
            if (req.keycloakToken() == null || req.devicePublicKey() == null) {
                ctx.status(400).result("keycloakToken and devicePublicKey are required");
                return;
            }

            // Validate Keycloak token
            KeycloakValidator.UserIdentity identity = keycloakValidator.validate(req.keycloakToken());
            if (identity == null) {
                ctx.status(401).result("Invalid Keycloak token");
                return;
            }

            // Generate device ID and credential
            String deviceId = UUID.randomUUID().toString();
            String credential = DeviceCredential.create(
                    deviceId, identity.userId(), req.devicePublicKey(),
                    keyStore.signingKey(), DeviceCredential.DEFAULT_TTL);

            // Register device
            deviceRegistry.register(deviceId, identity.userId(), req.devicePublicKey());

            log.info("Enrolled device {} for user {} ({})", deviceId, identity.userId(), identity.email());
            ctx.json(new EnrollResponse(deviceId, credential, keyStore.publicKeyBase64()));

        } catch (Exception e) {
            log.error("Enrollment failed", e);
            ctx.status(500).result("Enrollment failed: " + e.getMessage());
        }
    }

    private void handleRotate(Context ctx) {
        try {
            String deviceId = ctx.attribute("deviceId");
            String userId = ctx.attribute("userId");

            // Get the current credential to extract device public key
            String currentCredential = ctx.header("X-Device-Credential");
            var claims = DeviceCredential.verify(currentCredential, keyStore.verifyKey());
            if (claims == null) {
                ctx.status(401).result("Invalid credential");
                return;
            }

            // Issue new credential with same device info
            String newCredential = DeviceCredential.create(
                    deviceId, userId, claims.devicePublicKey(),
                    keyStore.signingKey(), DeviceCredential.DEFAULT_TTL);

            log.info("Rotated credential for device {} (user {})", deviceId, userId);
            ctx.json(new EnrollResponse(deviceId, newCredential, keyStore.publicKeyBase64()));

        } catch (Exception e) {
            log.error("Credential rotation failed", e);
            ctx.status(500).result("Rotation failed: " + e.getMessage());
        }
    }

    private void handleAnnounce(Context ctx) {
        AnnounceRequest req = ctx.bodyAsClass(AnnounceRequest.class);
        String userId = ctx.attribute("userId");
        if (userId == null) userId = req.peerId(); // fallback for backward compat
        String host = ctx.ip();
        peerRegistry.announce(userId, host, req.port(),
                req.documentIds() != null ? req.documentIds() : List.of());
        log.info("Announce: peer={} host={} port={} docs={}", userId, host, req.port(),
                req.documentIds() != null ? req.documentIds().size() : 0);
        ctx.status(200).result("OK");
    }

    private void handlePeers(Context ctx) {
        String documentId = ctx.queryParam("documentId");
        if (documentId == null || documentId.isBlank()) {
            ctx.status(400).result("documentId query parameter is required");
            return;
        }
        List<PeerResponse> peers = peerRegistry.findByDocument(documentId);
        ctx.json(peers);
    }

    private void handleDepart(Context ctx) {
        String peerId = ctx.queryParam("peerId");
        if (peerId == null || peerId.isBlank()) {
            ctx.status(400).result("peerId query parameter is required");
            return;
        }
        peerRegistry.depart(peerId);
        log.info("Depart: peer={}", peerId);
        ctx.status(200).result("OK");
    }

    private record BackendKeyResponse(String publicKey) {}

    static BackendKeyStore createDefaultKeyStore() {
        String dir = System.getenv("KEY_STORE_DIR");
        java.nio.file.Path keyDir = dir != null && !dir.isBlank()
                ? java.nio.file.Path.of(dir)
                : java.nio.file.Path.of(System.getProperty("user.home"), ".tradery", "rendezvous-keys");
        try {
            return new BackendKeyStore(keyDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize backend key store", e);
        }
    }
}
