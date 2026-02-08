package com.tradery.documents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for a document — a self-contained entity database with its own
 * schema, members, and governance. Serialized as document.yaml.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {

    @JsonProperty("id")
    private String id;
    @JsonProperty("name")
    private String name;
    @JsonProperty("owner_id")
    private String ownerId;
    @JsonProperty("visibility")
    private Visibility visibility = Visibility.LOCAL;
    @JsonProperty("governance")
    private Governance governance;
    @JsonProperty("template_id")
    private String templateId;
    @JsonProperty("created_at")
    private long createdAt;

    public Document() {}

    public Document(String id, String name) {
        this.id = id;
        this.name = name;
        this.visibility = Visibility.LOCAL;
        this.createdAt = System.currentTimeMillis();
    }

    public String id() { return id; }
    public void setId(String id) { this.id = id; }

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public String ownerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public Visibility visibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }

    public Governance governance() { return governance; }
    public void setGovernance(Governance governance) { this.governance = governance; }

    public String templateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public long createdAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    @JsonIgnore
    public boolean isLocal() { return visibility == Visibility.LOCAL; }
    @JsonIgnore
    public boolean isShared() { return visibility != Visibility.LOCAL; }

    public enum Visibility {
        LOCAL, PRIVATE, FRIENDS, PUBLIC
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Governance {
        @JsonProperty("type")
        private Type type = Type.OPEN;
        @JsonProperty("voting_quorum")
        private double votingQuorum = 0.51;

        public Governance() {}

        public Governance(Type type) {
            this.type = type;
        }

        public Type type() { return type; }
        public void setType(Type type) { this.type = type; }

        public double votingQuorum() { return votingQuorum; }
        public void setVotingQuorum(double votingQuorum) { this.votingQuorum = votingQuorum; }

        public enum Type {
            OPEN, ADMIN_APPROVED, VOTING
        }
    }
}
