package com.tradery.news.ui.coin;

import java.security.SecureRandom;

/**
 * Minimal ULID (Universally Unique Lexicographically Sortable Identifier) generator.
 * 48-bit timestamp (ms) + 80-bit random, encoded as 26-char Crockford base32.
 */
public final class Ulid {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {}

    public static String generate() {
        long time = System.currentTimeMillis();
        byte[] rand = new byte[10]; // 80 bits
        RANDOM.nextBytes(rand);

        char[] chars = new char[26];

        // Encode 48-bit timestamp into first 10 characters (high to low)
        chars[0] = ENCODING[(int) ((time >>> 45) & 0x1F)];
        chars[1] = ENCODING[(int) ((time >>> 40) & 0x1F)];
        chars[2] = ENCODING[(int) ((time >>> 35) & 0x1F)];
        chars[3] = ENCODING[(int) ((time >>> 30) & 0x1F)];
        chars[4] = ENCODING[(int) ((time >>> 25) & 0x1F)];
        chars[5] = ENCODING[(int) ((time >>> 20) & 0x1F)];
        chars[6] = ENCODING[(int) ((time >>> 15) & 0x1F)];
        chars[7] = ENCODING[(int) ((time >>> 10) & 0x1F)];
        chars[8] = ENCODING[(int) ((time >>> 5) & 0x1F)];
        chars[9] = ENCODING[(int) (time & 0x1F)];

        // Encode 80-bit random into last 16 characters
        // 10 bytes = 80 bits, 16 * 5 = 80 bits
        chars[10] = ENCODING[(rand[0] >>> 3) & 0x1F];
        chars[11] = ENCODING[((rand[0] << 2) | ((rand[1] & 0xFF) >>> 6)) & 0x1F];
        chars[12] = ENCODING[((rand[1] & 0xFF) >>> 1) & 0x1F];
        chars[13] = ENCODING[((rand[1] << 4) | ((rand[2] & 0xFF) >>> 4)) & 0x1F];
        chars[14] = ENCODING[((rand[2] << 1) | ((rand[3] & 0xFF) >>> 7)) & 0x1F];
        chars[15] = ENCODING[((rand[3] & 0xFF) >>> 2) & 0x1F];
        chars[16] = ENCODING[((rand[3] << 3) | ((rand[4] & 0xFF) >>> 5)) & 0x1F];
        chars[17] = ENCODING[(rand[4]) & 0x1F];
        chars[18] = ENCODING[(rand[5] >>> 3) & 0x1F];
        chars[19] = ENCODING[((rand[5] << 2) | ((rand[6] & 0xFF) >>> 6)) & 0x1F];
        chars[20] = ENCODING[((rand[6] & 0xFF) >>> 1) & 0x1F];
        chars[21] = ENCODING[((rand[6] << 4) | ((rand[7] & 0xFF) >>> 4)) & 0x1F];
        chars[22] = ENCODING[((rand[7] << 1) | ((rand[8] & 0xFF) >>> 7)) & 0x1F];
        chars[23] = ENCODING[((rand[8] & 0xFF) >>> 2) & 0x1F];
        chars[24] = ENCODING[((rand[8] << 3) | ((rand[9] & 0xFF) >>> 5)) & 0x1F];
        chars[25] = ENCODING[(rand[9]) & 0x1F];

        return new String(chars);
    }
}
