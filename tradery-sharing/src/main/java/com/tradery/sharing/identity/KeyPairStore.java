package com.tradery.sharing.identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Persists the local Ed25519 key pair to ~/.tradery/keys/.
 * Generated on first login, reused for all subsequent sessions.
 */
public class KeyPairStore {

    private static final Path KEYS_DIR = Path.of(System.getProperty("user.home"), ".tradery", "keys");
    private static final String PRIVATE_KEY_FILE = "ed25519.key";
    private static final String PUBLIC_KEY_FILE = "ed25519.pub";

    private final Path keysDir;

    public KeyPairStore() {
        this(KEYS_DIR);
    }

    public KeyPairStore(Path keysDir) {
        this.keysDir = keysDir;
    }

    /**
     * Load or generate the Ed25519 key pair.
     * Generates a new pair on first call, persists it, returns the same pair on subsequent calls.
     */
    public KeyPair loadOrGenerate() throws GeneralSecurityException, IOException {
        Path privPath = keysDir.resolve(PRIVATE_KEY_FILE);
        Path pubPath = keysDir.resolve(PUBLIC_KEY_FILE);

        if (Files.exists(privPath) && Files.exists(pubPath)) {
            return loadKeyPair(privPath, pubPath);
        }

        // Generate new key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();

        // Persist
        Files.createDirectories(keysDir);
        Files.writeString(privPath, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        Files.writeString(pubPath, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        return keyPair;
    }

    /**
     * Load the public key only (for verifying remote peers).
     */
    public static PublicKey decodePublicKey(String base64) throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(base64);
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        return kf.generatePublic(new X509EncodedKeySpec(encoded));
    }

    private KeyPair loadKeyPair(Path privPath, Path pubPath) throws GeneralSecurityException, IOException {
        KeyFactory kf = KeyFactory.getInstance("Ed25519");

        byte[] privEncoded = Base64.getDecoder().decode(Files.readString(privPath).trim());
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privEncoded));

        byte[] pubEncoded = Base64.getDecoder().decode(Files.readString(pubPath).trim());
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubEncoded));

        return new KeyPair(publicKey, privateKey);
    }
}
