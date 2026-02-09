package com.tradery.sharing.identity;

import com.tradery.news.ui.FriendshipCertData;

import java.util.List;

/**
 * Plaintext user encrypted registry (UER) content.
 * Serialized to JSON, then encrypted with AES-256-GCM for storage/distribution.
 */
public record UserRegistry(
    String email,
    String privateKey,
    String publicKey,
    IdentityCert identityCert,
    List<FriendEntry> friends
) {
    public record FriendEntry(
        String email,
        String displayName,
        FriendshipCertData issuedCert,
        FriendshipCertData receivedCert
    ) {}
}
