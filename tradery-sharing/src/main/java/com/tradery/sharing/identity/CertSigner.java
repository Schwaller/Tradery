package com.tradery.sharing.identity;

import com.tradery.news.ui.FriendshipCertData;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * Ed25519 signing and verification for identity and friendship certs.
 * Follows the FactSigner pattern: null-byte-delimited canonical bytes → Ed25519 signature → Base64.
 */
public class CertSigner {

    private final KeyPair keyPair;

    public CertSigner(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * Create a self-signed identity cert for the given email.
     */
    public IdentityCert createIdentityCert(String email) throws GeneralSecurityException {
        long issuedAt = System.currentTimeMillis();
        String pubKey = publicKeyBase64();
        byte[] canonical = identityCertCanonical(email, pubKey, issuedAt);
        String sig = sign(canonical);
        return new IdentityCert(email, pubKey, issuedAt, sig);
    }

    /**
     * Create a friendship cert: "I ({issuerEmail}) accept {subjectEmail} as a friend."
     */
    public FriendshipCertData createFriendshipCert(String issuerEmail, String subjectEmail)
            throws GeneralSecurityException {
        long issuedAt = System.currentTimeMillis();
        String pubKey = publicKeyBase64();
        byte[] canonical = friendshipCertCanonical(issuerEmail, pubKey, subjectEmail, issuedAt);
        String sig = sign(canonical);
        return new FriendshipCertData(issuerEmail, pubKey, subjectEmail, issuedAt, sig);
    }

    /**
     * Verify an identity cert's self-signature.
     */
    public static boolean verifyIdentityCert(IdentityCert cert) throws GeneralSecurityException {
        byte[] canonical = identityCertCanonical(cert.email(), cert.publicKey(), cert.issuedAt());
        PublicKey pubKey = KeyPairStore.decodePublicKey(cert.publicKey());
        return verify(canonical, cert.signature(), pubKey);
    }

    /**
     * Verify a friendship cert's issuer signature.
     */
    public static boolean verifyFriendshipCert(FriendshipCertData cert) throws GeneralSecurityException {
        byte[] canonical = friendshipCertCanonical(
                cert.issuerEmail(), cert.issuerPublicKey(), cert.subjectEmail(), cert.issuedAt());
        PublicKey pubKey = KeyPairStore.decodePublicKey(cert.issuerPublicKey());
        return verify(canonical, cert.signature(), pubKey);
    }

    private String sign(byte[] data) throws GeneralSecurityException {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(data);
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    private static boolean verify(byte[] data, String signatureBase64, PublicKey publicKey)
            throws GeneralSecurityException {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(Base64.getDecoder().decode(signatureBase64));
    }

    private static byte[] identityCertCanonical(String email, String publicKey, long issuedAt) {
        String canonical = String.join("\0", "IDENTITY_CERT", email, publicKey, String.valueOf(issuedAt));
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] friendshipCertCanonical(String issuerEmail, String issuerPublicKey,
                                                   String subjectEmail, long issuedAt) {
        String canonical = String.join("\0", "FRIENDSHIP_CERT",
                issuerEmail, issuerPublicKey, subjectEmail, String.valueOf(issuedAt));
        return canonical.getBytes(StandardCharsets.UTF_8);
    }
}
