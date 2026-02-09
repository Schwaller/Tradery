package com.tradery.news.ui;

/**
 * A friend entry persisted in intel-config.yaml.
 */
public class FriendConfig {

    private String email;
    private String displayName;
    private long addedAt;
    private FriendshipCertData issuedCert;    // cert WE signed about THEM
    private FriendshipCertData receivedCert;  // cert THEY signed about US

    public FriendConfig() {}

    public FriendConfig(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
        this.addedAt = System.currentTimeMillis();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public long getAddedAt() { return addedAt; }
    public void setAddedAt(long addedAt) { this.addedAt = addedAt; }

    public FriendshipCertData getIssuedCert() { return issuedCert; }
    public void setIssuedCert(FriendshipCertData issuedCert) { this.issuedCert = issuedCert; }

    public FriendshipCertData getReceivedCert() { return receivedCert; }
    public void setReceivedCert(FriendshipCertData receivedCert) { this.receivedCert = receivedCert; }

    /** Returns displayName if set, otherwise email. */
    public String label() {
        return displayName != null && !displayName.isBlank() ? displayName : email;
    }
}
