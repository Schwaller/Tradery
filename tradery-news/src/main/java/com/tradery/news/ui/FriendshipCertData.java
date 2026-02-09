package com.tradery.news.ui;

/**
 * Serializable friendship cert data — stored in FriendConfig (intel-config.yaml).
 * Lives in tradery-news so FriendConfig can reference it without depending on tradery-sharing.
 */
public record FriendshipCertData(
    String issuerEmail,
    String issuerPublicKey,
    String subjectEmail,
    long issuedAt,
    String signature
) {}
