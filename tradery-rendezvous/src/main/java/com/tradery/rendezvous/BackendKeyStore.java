package com.tradery.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Manages the backend's Ed25519 signing keypair for issuing device credentials.
 * The keypair is persisted to disk so credentials survive server restarts.
 */
public class BackendKeyStore {

    private static final Logger log = LoggerFactory.getLogger(BackendKeyStore.class);

    private final KeyPair keyPair;

    public BackendKeyStore(Path keyDir) throws GeneralSecurityException, IOException {
        Path privPath = keyDir.resolve("backend.key");
        Path pubPath = keyDir.resolve("backend.pub");

        if (Files.exists(privPath) && Files.exists(pubPath)) {
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            byte[] privBytes = Base64.getDecoder().decode(Files.readString(privPath).trim());
            byte[] pubBytes = Base64.getDecoder().decode(Files.readString(pubPath).trim());
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            this.keyPair = new KeyPair(pub, priv);
            log.info("Loaded backend signing key from {}", keyDir);
        } else {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            this.keyPair = kpg.generateKeyPair();
            Files.createDirectories(keyDir);
            Files.writeString(privPath, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
            Files.writeString(pubPath, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            log.info("Generated new backend signing key at {}", keyDir);
        }
    }

    public PrivateKey signingKey() { return keyPair.getPrivate(); }
    public PublicKey verifyKey() { return keyPair.getPublic(); }

    /** Base64-encoded public key for distribution to clients. */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}
