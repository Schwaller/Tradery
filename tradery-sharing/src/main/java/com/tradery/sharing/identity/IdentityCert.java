package com.tradery.sharing.identity;

/**
 * Self-signed identity attestation: "I am {email}, verified by Keycloak."
 * Signed with the user's Ed25519 key.
 */
public record IdentityCert(
    String email,
    String publicKey,
    long issuedAt,
    String signature
) {}
