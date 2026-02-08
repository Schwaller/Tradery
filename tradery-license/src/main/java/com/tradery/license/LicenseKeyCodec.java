package com.tradery.license;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Encode/decode/validate license keys using HMAC-SHA256.
 *
 * Key format: XXXX-XXXX-XXXX-XXXX (16 base32 chars encoding 10 bytes)
 * - Bytes 0-3: Expiry date as days since epoch (uint32)
 * - Bytes 4-5: Feature flags (uint16, reserved for future use)
 * - Bytes 6-9: First 4 bytes of HMAC-SHA256(secret, bytes[0..5])
 */
public class LicenseKeyCodec {

    // Base32 alphabet without ambiguous characters (no I, O, 0, 1)
    private static final String BASE32 = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final LocalDate EPOCH = LocalDate.of(2024, 1, 1);

    // HMAC secret - embedded in the app binary
    private static final byte[] HMAC_SECRET = {
        (byte) 0x54, (byte) 0x72, (byte) 0x61, (byte) 0x64, // "Trad"
        (byte) 0x65, (byte) 0x72, (byte) 0x79, (byte) 0x4C, // "eryL"
        (byte) 0x69, (byte) 0x63, (byte) 0x4B, (byte) 0x65, // "icKe"
        (byte) 0x79, (byte) 0x53, (byte) 0x33, (byte) 0x63, // "yS3c"
        (byte) 0x72, (byte) 0x33, (byte) 0x74, (byte) 0x21, // "r3t!"
        (byte) 0xA7, (byte) 0xB2, (byte) 0xC3, (byte) 0xD4,
        (byte) 0xE5, (byte) 0xF6, (byte) 0x07, (byte) 0x18,
        (byte) 0x29, (byte) 0x3A, (byte) 0x4B, (byte) 0x5C
    };

    /**
     * Generate a license key that expires after the given number of days.
     */
    public static String generate(int daysValid) {
        return generate(daysValid, (short) 0);
    }

    /**
     * Generate a license key with expiry and feature flags.
     */
    public static String generate(int daysValid, short featureFlags) {
        LocalDate expiry = LocalDate.now().plusDays(daysValid);
        int expiryDays = (int) ChronoUnit.DAYS.between(EPOCH, expiry);

        byte[] payload = new byte[6];
        ByteBuffer buf = ByteBuffer.wrap(payload);
        buf.putInt(expiryDays);
        buf.putShort(featureFlags);

        byte[] hmac = computeHmac(payload);

        // Combine payload + first 4 bytes of HMAC = 10 bytes total
        byte[] keyBytes = new byte[10];
        System.arraycopy(payload, 0, keyBytes, 0, 6);
        System.arraycopy(hmac, 0, keyBytes, 6, 4);

        return formatKey(encodeBase32(keyBytes));
    }

    /**
     * Decode and validate a license key.
     * Returns a result with validity, expiry date, and feature flags.
     */
    public static LicenseResult validate(String key) {
        if (key == null || key.isBlank()) {
            return LicenseResult.invalid("No license key provided");
        }

        // Strip dashes and whitespace, uppercase
        String clean = key.replace("-", "").replace(" ", "").toUpperCase();

        if (clean.length() != 16) {
            return LicenseResult.invalid("Invalid key format");
        }

        // Validate characters
        for (char c : clean.toCharArray()) {
            if (BASE32.indexOf(c) < 0) {
                return LicenseResult.invalid("Invalid key format");
            }
        }

        byte[] keyBytes;
        try {
            keyBytes = decodeBase32(clean);
        } catch (IllegalArgumentException e) {
            return LicenseResult.invalid("Invalid key format");
        }

        if (keyBytes.length != 10) {
            return LicenseResult.invalid("Invalid key format");
        }

        // Extract payload and signature
        byte[] payload = new byte[6];
        byte[] sig = new byte[4];
        System.arraycopy(keyBytes, 0, payload, 0, 6);
        System.arraycopy(keyBytes, 6, sig, 0, 4);

        // Verify HMAC
        byte[] expectedHmac = computeHmac(payload);
        boolean hmacValid = true;
        for (int i = 0; i < 4; i++) {
            if (sig[i] != expectedHmac[i]) {
                hmacValid = false;
            }
        }
        if (!hmacValid) {
            return LicenseResult.invalid("Invalid license key");
        }

        // Decode payload
        ByteBuffer buf = ByteBuffer.wrap(payload);
        int expiryDays = buf.getInt();
        short featureFlags = buf.getShort();

        LocalDate expiryDate = EPOCH.plusDays(expiryDays);

        if (LocalDate.now().isAfter(expiryDate)) {
            return LicenseResult.expired(expiryDate, featureFlags);
        }

        return LicenseResult.valid(expiryDate, featureFlags);
    }

    private static byte[] computeHmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_SECRET, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /**
     * Encode bytes to base32 string.
     * 10 bytes = 80 bits -> 16 base32 chars (5 bits each)
     */
    static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsInBuffer = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5;
                int index = (buffer >> bitsInBuffer) & 0x1F;
                sb.append(BASE32.charAt(index));
            }
        }
        if (bitsInBuffer > 0) {
            int index = (buffer << (5 - bitsInBuffer)) & 0x1F;
            sb.append(BASE32.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Decode base32 string to bytes.
     * 16 base32 chars (5 bits each) = 80 bits -> 10 bytes
     */
    static byte[] decodeBase32(String encoded) {
        int buffer = 0;
        int bitsInBuffer = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        for (char c : encoded.toCharArray()) {
            int val = BASE32.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid base32 character: " + c);
            }
            buffer = (buffer << 5) | val;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                out.write((buffer >> bitsInBuffer) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    /**
     * Format raw 16-char string as XXXX-XXXX-XXXX-XXXX.
     */
    static String formatKey(String raw) {
        if (raw.length() != 16) throw new IllegalArgumentException("Expected 16 chars, got " + raw.length());
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" +
               raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }

    /**
     * Result of license key validation.
     */
    public record LicenseResult(Status status, LocalDate expiryDate, short featureFlags, String message) {

        public enum Status { VALID, EXPIRED, INVALID }

        public boolean isValid() {
            return status == Status.VALID;
        }

        public boolean isExpired() {
            return status == Status.EXPIRED;
        }

        static LicenseResult valid(LocalDate expiry, short flags) {
            return new LicenseResult(Status.VALID, expiry, flags, null);
        }

        static LicenseResult expired(LocalDate expiry, short flags) {
            return new LicenseResult(Status.EXPIRED, expiry, flags, "License expired on " + expiry);
        }

        static LicenseResult invalid(String message) {
            return new LicenseResult(Status.INVALID, null, (short) 0, message);
        }
    }
}
