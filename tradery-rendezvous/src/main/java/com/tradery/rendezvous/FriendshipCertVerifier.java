package com.tradery.rendezvous;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Server-side verification of friendship certs.
 * Matches the canonical format from CertSigner in tradery-sharing,
 * but implemented independently (no shared dependency).
 *
 * A friendship cert proves: "issuerEmail granted subjectEmail friend access."
 * To query someone's presence, the requester must present a cert where:
 *   - issuerEmail = the target user being queried (they signed the cert)
 *   - subjectEmail = the requester (they are the authorized subject)
 */
public class FriendshipCertVerifier {

    /**
     * Parsed friendship cert from the X-Friendship-Cert header.
     */
    public record FriendshipCert(
        String issuerEmail,
        String issuerPublicKey,
        String subjectEmail,
        long issuedAt,
        String signature
    ) {}

    /**
     * Parse a friendship cert from JSON string.
     * Minimal parsing — no Jackson dependency on the rendezvous server.
     */
    public static FriendshipCert parseCert(String json) {
        try {
            String issuerEmail = extractString(json, "issuerEmail");
            String issuerPublicKey = extractString(json, "issuerPublicKey");
            String subjectEmail = extractString(json, "subjectEmail");
            long issuedAt = extractLong(json, "issuedAt");
            String signature = extractString(json, "signature");

            if (issuerEmail == null || issuerPublicKey == null || subjectEmail == null || signature == null) {
                return null;
            }
            return new FriendshipCert(issuerEmail, issuerPublicKey, subjectEmail, issuedAt, signature);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verify a friendship cert:
     * 1. Ed25519 signature is valid against issuerPublicKey
     * 2. issuerEmail matches targetUserId (cert was issued BY the person being queried)
     * 3. subjectEmail matches requesterUserId (cert was issued FOR the requester)
     *
     * @return true if cert is valid and authorized
     */
    public static boolean verify(FriendshipCert cert, String targetUserId, String requesterUserId) {
        if (cert == null) return false;

        // Check that the cert was issued by the target for the requester
        if (!cert.issuerEmail().equals(targetUserId)) return false;
        if (!cert.subjectEmail().equals(requesterUserId)) return false;

        // Verify Ed25519 signature
        try {
            byte[] canonical = friendshipCertCanonical(
                    cert.issuerEmail(), cert.issuerPublicKey(), cert.subjectEmail(), cert.issuedAt());
            byte[] keyBytes = Base64.getDecoder().decode(cert.issuerPublicKey());
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(keyBytes));
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(pubKey);
            sig.update(canonical);
            return sig.verify(Base64.getDecoder().decode(cert.signature()));
        } catch (Exception e) {
            return false;
        }
    }

    /** Same canonical format as CertSigner.friendshipCertCanonical(). */
    private static byte[] friendshipCertCanonical(String issuerEmail, String issuerPublicKey,
                                                   String subjectEmail, long issuedAt) {
        String canonical = String.join("\0", "FRIENDSHIP_CERT",
                issuerEmail, issuerPublicKey, subjectEmail, String.valueOf(issuedAt));
        return canonical.getBytes(StandardCharsets.UTF_8);
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
