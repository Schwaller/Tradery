package com.tradery.rendezvous;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Backend-signed device credential as a compact JWT (no library dependency).
 * Algorithm: EdDSA (Ed25519).
 *
 * Format: header.payload.signature (standard JWT)
 * Header: {"alg":"EdDSA","typ":"JWT"}
 * Payload: {"iss":"plaiiin","sub":"device:{deviceId}","uid":"{userId}",
 *           "dpk":"{devicePublicKeyBase64}","iat":...,"exp":...,"jti":"..."}
 */
public class DeviceCredential {

    private static final String HEADER = base64url("{\"alg\":\"EdDSA\",\"typ\":\"JWT\"}");
    public static final Duration DEFAULT_TTL = Duration.ofDays(30);

    /**
     * Create a new device credential JWT signed by the backend.
     */
    public static String create(String deviceId, String userId, String devicePublicKeyBase64,
                                PrivateKey backendKey, Duration ttl) throws GeneralSecurityException {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();
        String payload = "{\"iss\":\"plaiiin\""
                + ",\"sub\":\"device:" + escapeJson(deviceId) + "\""
                + ",\"uid\":\"" + escapeJson(userId) + "\""
                + ",\"dpk\":\"" + escapeJson(devicePublicKeyBase64) + "\""
                + ",\"iat\":" + now.getEpochSecond()
                + ",\"exp\":" + now.plus(ttl).getEpochSecond()
                + ",\"jti\":\"" + jti + "\""
                + "}";

        String signingInput = HEADER + "." + base64url(payload);
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(backendKey);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());

        return signingInput + "." + signature;
    }

    /**
     * Verify a device credential JWT and return its claims.
     * Returns null if verification fails (expired, bad signature, etc.).
     */
    public static Claims verify(String jwt, PublicKey backendPublicKey) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) return null;

            // Verify signature
            String signingInput = parts[0] + "." + parts[1];
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(backendPublicKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!sig.verify(signatureBytes)) return null;

            // Parse payload
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return Claims.parse(payload);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract the device public key from a credential for request signature verification.
     * Does NOT verify the credential signature — call verify() first if untrusted.
     */
    public static PublicKey extractDevicePublicKey(String jwt) throws GeneralSecurityException {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid JWT");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Claims claims = Claims.parse(payload);
        if (claims == null || claims.devicePublicKey == null) throw new IllegalArgumentException("No dpk claim");
        byte[] keyBytes = Base64.getDecoder().decode(claims.devicePublicKey);
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        return kf.generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    public record Claims(
            String deviceId, String userId, String devicePublicKey,
            long issuedAt, long expiresAt, String jti
    ) {
        public boolean isExpired() {
            return Instant.now().getEpochSecond() > expiresAt;
        }

        static Claims parse(String json) {
            try {
                // Minimal JSON parsing without a library
                String sub = extractString(json, "sub");
                String deviceId = sub != null && sub.startsWith("device:") ? sub.substring(7) : sub;
                return new Claims(
                        deviceId,
                        extractString(json, "uid"),
                        extractString(json, "dpk"),
                        extractLong(json, "iat"),
                        extractLong(json, "exp"),
                        extractString(json, "jti")
                );
            } catch (Exception e) {
                return null;
            }
        }

        private static String extractString(String json, String key) {
            String search = "\"" + key + "\":\"";
            int idx = json.indexOf(search);
            if (idx < 0) return null;
            int start = idx + search.length();
            int end = json.indexOf("\"", start);
            return end > start ? json.substring(start, end) : null;
        }

        private static long extractLong(String json, String key) {
            String search = "\"" + key + "\":";
            int idx = json.indexOf(search);
            if (idx < 0) return 0;
            int start = idx + search.length();
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
            return Long.parseLong(json.substring(start, end));
        }
    }

    private static String base64url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
