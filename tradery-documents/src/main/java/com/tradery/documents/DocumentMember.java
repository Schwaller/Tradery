package com.tradery.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A member of a shared document with a specific role.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentMember {

    @JsonProperty("user_id")
    private String userId;
    private Role role;

    public DocumentMember() {}

    public DocumentMember(String userId, Role role) {
        this.userId = userId;
        this.role = role;
    }

    public String userId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Role role() { return role; }
    public void setRole(Role role) { this.role = role; }

    public enum Role {
        OWNER, ADMIN, MEMBER, VIEWER
    }
}
