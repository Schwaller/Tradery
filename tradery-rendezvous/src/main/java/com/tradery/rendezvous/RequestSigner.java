package com.tradery.rendezvous;

import java.nio.charset.StandardCharsets;
import java.security.*;

/**
 * Request signing and verification using Ed25519.
 * Signing input: method + "\n" + path + "\n" + timestamp + "\n" + sha256(body)
 */
public class RequestSigner {

    /** Verify a request signature from a device. */
    public static boolean verify(PublicKey deviceKey, String method, String path,
                                 String timestamp, byte[] body, byte[] signature) {
        try {
            String input = signingInput(method, path, timestamp, body);
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(deviceKey);
            sig.update(input.getBytes(StandardCharsets.UTF_8));
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /** Create a request signature with a device private key. */
    public static byte[] sign(PrivateKey deviceKey, String method, String path,
                              String timestamp, byte[] body) throws GeneralSecurityException {
        String input = signingInput(method, path, timestamp, body);
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(deviceKey);
        sig.update(input.getBytes(StandardCharsets.UTF_8));
        return sig.sign();
    }

    private static String signingInput(String method, String path, String timestamp, byte[] body) {
        return method + "\n" + path + "\n" + timestamp + "\n" + sha256hex(body);
    }

    private static String sha256hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data != null ? data : new byte[0]);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
