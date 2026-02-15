package com.tradery.sharing.sync;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.tradery.news.ui.FriendshipCertData;
import com.tradery.news.ui.coin.FactStore;
import com.tradery.sharing.identity.IdentityCert;

import java.util.List;

/**
 * Wire protocol messages for P2P fact sync.
 * Length-prefixed JSON over UDP.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NetworkMessage.Hello.class, name = "HELLO"),
    @JsonSubTypes.Type(value = NetworkMessage.SyncRequest.class, name = "SYNC_REQUEST"),
    @JsonSubTypes.Type(value = NetworkMessage.SyncResponse.class, name = "SYNC_RESPONSE"),
    @JsonSubTypes.Type(value = NetworkMessage.SyncDone.class, name = "SYNC_DONE"),
    @JsonSubTypes.Type(value = NetworkMessage.MemberUpdate.class, name = "MEMBER_UPDATE"),
    @JsonSubTypes.Type(value = NetworkMessage.ChatMessage.class, name = "CHAT"),
    @JsonSubTypes.Type(value = NetworkMessage.CertExchange.class, name = "CERT_EXCHANGE"),
    @JsonSubTypes.Type(value = NetworkMessage.BackupStore.class, name = "BACKUP_STORE"),
    @JsonSubTypes.Type(value = NetworkMessage.BackupRequest.class, name = "BACKUP_REQUEST"),
    @JsonSubTypes.Type(value = NetworkMessage.BackupResponse.class, name = "BACKUP_RESPONSE"),
    @JsonSubTypes.Type(value = NetworkMessage.FriendImportOffer.class, name = "FRIEND_IMPORT_OFFER"),
    @JsonSubTypes.Type(value = NetworkMessage.PerfRequest.class, name = "PERF_REQUEST"),
    @JsonSubTypes.Type(value = NetworkMessage.PerfAccept.class, name = "PERF_ACCEPT"),
    @JsonSubTypes.Type(value = NetworkMessage.PerfReject.class, name = "PERF_REJECT"),
    @JsonSubTypes.Type(value = NetworkMessage.PerfPing.class, name = "PERF_PING"),
    @JsonSubTypes.Type(value = NetworkMessage.PerfPong.class, name = "PERF_PONG"),
})
public sealed interface NetworkMessage {

    /** Initial handshake: identify peer with identity cert and shared documents. */
    record Hello(
        String peerId,
        String deviceId,
        IdentityCert identityCert,
        List<String> documentIds
    ) implements NetworkMessage {}

    /** Request facts from a document since a given logical clock. */
    record SyncRequest(
        String documentId,
        long sinceLclock
    ) implements NetworkMessage {}

    /** Response with facts for a document. */
    record SyncResponse(
        String documentId,
        List<FactStore.Fact> facts
    ) implements NetworkMessage {}

    /** Signal that all facts for a document have been sent. */
    record SyncDone(
        String documentId
    ) implements NetworkMessage {}

    /** Propagate member list changes for a document. */
    record MemberUpdate(
        String documentId,
        List<MemberEntry> members
    ) implements NetworkMessage {
        public record MemberEntry(String userId, String role) {}
    }

    /** Chat message — directed to a specific peer (recipientId), or broadcast if null. */
    record ChatMessage(
        String senderId,
        String recipientId,
        String text,
        long timestamp
    ) implements NetworkMessage {}

    /** Friendship proof: sends a cert that the REMOTE peer signed about US (proving they accepted us). */
    record CertExchange(
        FriendshipCertData proofCert
    ) implements NetworkMessage {}

    /** Push encrypted UER backup to a mutual friend for storage. */
    record BackupStore(
        String ownerEmail,
        byte[] encryptedRegistry,
        long updatedAt
    ) implements NetworkMessage {}

    /** Request a friend's stored backup of our encrypted UER (for recovery). */
    record BackupRequest(
        String email
    ) implements NetworkMessage {}

    /** Response with stored encrypted UER backup. */
    record BackupResponse(
        String ownerEmail,
        byte[] encryptedRegistry,
        long updatedAt
    ) implements NetworkMessage {}

    /** Offer old friendship certs when detecting a key mismatch (password reset flow). */
    record FriendImportOffer(
        FriendshipCertData theirOldCertAboutUs,
        FriendshipCertData ourOldCertAboutThem
    ) implements NetworkMessage {}

    /** Request to run a performance test with a peer. */
    record PerfRequest(String testId) implements NetworkMessage {}

    /** Accept a performance test request. */
    record PerfAccept(String testId) implements NetworkMessage {}

    /** Reject a performance test request. */
    record PerfReject(String testId) implements NetworkMessage {}

    /** Performance test ping — measures latency and throughput. */
    record PerfPing(String testId, int seq, long sendTs, byte[] payload) implements NetworkMessage {}

    /** Performance test pong — echo of a ping. */
    record PerfPong(String testId, int seq, long sendTs, byte[] payload) implements NetworkMessage {}
}
