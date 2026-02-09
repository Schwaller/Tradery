package com.tradery.sharing.identity;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * AES-256-GCM encryption for the User Encrypted Registry.
 * Key derived from user password via PBKDF2-HMAC-SHA256 (600k iterations).
 *
 * Wire format: [16-byte salt][12-byte IV][ciphertext + GCM tag]
 */
public class RegistryEncryption {

    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int KEY_LENGTH_BITS = 256;

    /**
     * Encrypt plaintext JSON with the given password.
     * Returns wire-format bytes: salt + IV + ciphertext.
     */
    public static byte[] encrypt(String plaintext, String password) throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();

        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        ByteBuffer buf = ByteBuffer.allocate(SALT_LENGTH + IV_LENGTH + ciphertext.length);
        buf.put(salt);
        buf.put(iv);
        buf.put(ciphertext);
        return buf.array();
    }

    /**
     * Decrypt wire-format bytes with the given password.
     * Returns plaintext JSON string.
     *
     * @throws GeneralSecurityException if password is wrong or data is corrupted
     */
    public static String decrypt(byte[] data, String password) throws GeneralSecurityException {
        if (data.length < SALT_LENGTH + IV_LENGTH + GCM_TAG_BITS / 8) {
            throw new GeneralSecurityException("Encrypted data too short");
        }

        ByteBuffer buf = ByteBuffer.wrap(data);

        byte[] salt = new byte[SALT_LENGTH];
        buf.get(salt);

        byte[] iv = new byte[IV_LENGTH];
        buf.get(iv);

        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }
}
