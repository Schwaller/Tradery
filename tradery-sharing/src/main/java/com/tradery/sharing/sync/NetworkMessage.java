package com.tradery.sharing.sync;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.tradery.news.ui.coin.FactStore;

import java.util.List;

/**
 * Wire protocol messages for P2P fact sync.
 * Length-prefixed JSON over TLS TCP.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NetworkMessage.Hello.class, name = "HELLO"),
    @JsonSubTypes.Type(value = NetworkMessage.SyncRequest.class, name = "SYNC_REQUEST"),
    @JsonSubTypes.Type(value = NetworkMessage.SyncResponse.class, name = "SYNC_RESPONSE"),
    @JsonSubTypes.Type(value = NetworkMessage.SyncDone.class, name = "SYNC_DONE"),
    @JsonSubTypes.Type(value = NetworkMessage.MemberUpdate.class, name = "MEMBER_UPDATE"),
    @JsonSubTypes.Type(value = NetworkMessage.ChatMessage.class, name = "CHAT"),
    @JsonSubTypes.Type(value = NetworkMessage.FriendshipAck.class, name = "FRIENDSHIP_ACK"),
})
public sealed interface NetworkMessage {

    /** Initial handshake: identify peer and shared documents. */
    record Hello(
        String peerId,
        String deviceId,
        String publicKey,
        String token,
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

    /** Sent after HELLO exchange to inform peer of friendship status. */
    record FriendshipAck(
        boolean isFriend
    ) implements NetworkMessage {}
}
