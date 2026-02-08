package com.tradery.news.ui;

/**
 * A friend entry persisted in intel-config.yaml.
 */
public class FriendConfig {

    private String email;
    private String displayName;
    private long addedAt;

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

    /** Returns displayName if set, otherwise email. */
    public String label() {
        return displayName != null && !displayName.isBlank() ? displayName : email;
    }
}
