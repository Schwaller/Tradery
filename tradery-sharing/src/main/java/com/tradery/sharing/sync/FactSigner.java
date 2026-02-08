package com.tradery.sharing.sync;

import com.tradery.news.ui.coin.FactStore;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * Ed25519 signing and verification for facts.
 * Used for P2P integrity verification — ensures facts haven't been tampered with in transit.
 */
public class FactSigner {

    private final KeyPair keyPair;

    public FactSigner(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    /**
     * Generate a new Ed25519 key pair.
     */
    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        return kpg.generateKeyPair();
    }

    /**
     * Sign a fact's content, returning a Base64-encoded signature.
     */
    public String sign(FactStore.Fact fact) throws GeneralSecurityException {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(canonicalBytes(fact));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    /**
     * Verify a fact's signature using the author's public key.
     */
    public static boolean verify(FactStore.Fact fact, String signatureBase64, PublicKey publicKey)
            throws GeneralSecurityException {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(publicKey);
        sig.update(canonicalBytes(fact));
        return sig.verify(Base64.getDecoder().decode(signatureBase64));
    }

    /**
     * Encode the public key as Base64 for wire transport / storage.
     */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * Canonical byte representation for signing: concatenation of all fact fields.
     */
    private static byte[] canonicalBytes(FactStore.Fact fact) {
        String canonical = String.join("\0",
                fact.id(),
                fact.entityId(),
                fact.attribute(),
                fact.value() != null ? fact.value() : "",
                fact.source(),
                fact.peerId(),
                String.valueOf(fact.lclock()),
                String.valueOf(fact.wallClock()));
        return canonical.getBytes(StandardCharsets.UTF_8);
    }
}
