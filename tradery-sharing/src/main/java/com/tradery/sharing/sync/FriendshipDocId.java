package com.tradery.sharing.sync;

import java.util.UUID;

/**
 * Computes a deterministic document ID for friend-pair discovery via rendezvous.
 * Both peers independently compute the same UUID from their sorted emails.
 */
public final class FriendshipDocId {

    private FriendshipDocId() {}

    public static String compute(String emailA, String emailB) {
        String min = emailA.compareTo(emailB) < 0 ? emailA : emailB;
        String max = emailA.compareTo(emailB) < 0 ? emailB : emailA;
        return UUID.nameUUIDFromBytes(("friendship:" + min + ":" + max).getBytes()).toString();
    }
}
