package com.tradery.sharing.identity;

import java.security.KeyPair;

/**
 * Represents the current user's session — Keycloak auth + local signing key.
 * Null/absent when operating in offline (LOCAL) mode.
 */
public class UserSession {

    private final String userId;
    private final String displayName;
    private final String accessToken;
    private final String refreshToken;
    private final KeyPair signingKeyPair;
    private long tokenExpiresAt;

    public UserSession(String userId, String displayName,
                       String accessToken, String refreshToken,
                       long tokenExpiresAt, KeyPair signingKeyPair) {
        this.userId = userId;
        this.displayName = displayName;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.signingKeyPair = signingKeyPair;
    }

    public String userId() { return userId; }
    public String displayName() { return displayName; }
    public String accessToken() { return accessToken; }
    public String refreshToken() { return refreshToken; }
    public KeyPair signingKeyPair() { return signingKeyPair; }

    public boolean isTokenExpired() {
        return System.currentTimeMillis() >= tokenExpiresAt;
    }
}
