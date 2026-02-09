package com.tradery.sharing;

import com.tradery.sharing.identity.RegistryEncryption;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;

class RegistryEncryptionTest {

    @Test
    void encryptDecrypt_roundTrip_succeeds() throws GeneralSecurityException {
        String plaintext = "{\"email\":\"alice@example.com\",\"friends\":[]}";
        String password = "correct-horse-battery-staple";

        byte[] encrypted = RegistryEncryption.encrypt(plaintext, password);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 28); // salt(16) + iv(12) + at least some ciphertext

        String decrypted = RegistryEncryption.decrypt(encrypted, password);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void decrypt_wrongPassword_fails() throws GeneralSecurityException {
        String plaintext = "secret data";
        byte[] encrypted = RegistryEncryption.encrypt(plaintext, "right-password");

        assertThrows(GeneralSecurityException.class, () ->
                RegistryEncryption.decrypt(encrypted, "wrong-password"));
    }

    @Test
    void decrypt_truncatedData_fails() {
        byte[] tooShort = new byte[20]; // less than salt + iv + tag
        assertThrows(GeneralSecurityException.class, () ->
                RegistryEncryption.decrypt(tooShort, "password"));
    }

    @Test
    void encrypt_differentCallsProduceDifferentOutput() throws GeneralSecurityException {
        String plaintext = "same input";
        String password = "same password";

        byte[] a = RegistryEncryption.encrypt(plaintext, password);
        byte[] b = RegistryEncryption.encrypt(plaintext, password);

        // Different salt + IV → different ciphertext (both should decrypt to same)
        assertNotEquals(java.util.HexFormat.of().formatHex(a),
                java.util.HexFormat.of().formatHex(b));
        assertEquals(plaintext, RegistryEncryption.decrypt(a, password));
        assertEquals(plaintext, RegistryEncryption.decrypt(b, password));
    }
}
