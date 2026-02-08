package com.tradery.news.ui;

import com.tradery.news.ui.coin.EntityStore;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for document sharing operations.
 * Uses only types available in tradery-news to avoid circular dependency
 * with tradery-sharing/tradery-documents.
 * Concrete implementation loaded via reflection at runtime.
 */
public interface SharingService {

    record SharingState(
        String visibility,        // LOCAL, PRIVATE, FRIENDS, PUBLIC
        String governanceType,    // OPEN, ADMIN_APPROVED, VOTING (null if LOCAL)
        double votingQuorum,      // default 0.51
        int memberCount,
        int connectedPeerCount
    ) {
        public boolean isShared() { return !"LOCAL".equals(visibility); }
    }

    record Member(String email, String role) {} // role: OWNER, ADMIN, MEMBER, VIEWER

    /** Get the current sharing state for a document. */
    SharingState getState(String docId);

    /** Get all members of a document. */
    List<Member> getMembers(String docId);

    /** Add a member by email with a given role. */
    void addMember(String docId, String email, String role) throws Exception;

    /** Remove a member by email. */
    void removeMember(String docId, String email) throws Exception;

    /** Enable sharing for a local document. Uses ownerEmail as the owner identity. */
    void enableSharing(String docId, String visibility, String governanceType,
                       double votingQuorum, String ownerEmail, Path docDir, EntityStore entityStore) throws Exception;

    /** Update sharing settings for an already-shared document. Also migrates owner if email differs from current. */
    void updateSharing(String docId, String visibility, String governanceType,
                       double votingQuorum, String ownerEmail) throws Exception;

    /** Disable sharing (revert to LOCAL). */
    void disableSharing(String docId) throws Exception;

    /** Request immediate sync with connected peers. */
    void syncNow(String docId);

    /** Whether the sharing module is available and initialized. */
    boolean isAvailable();

    /** Bootstrap peer infrastructure (LAN discovery) without requiring an explicit share. */
    default void bootstrap() {}

    /** Register an open document for sync (reuses the caller's EntityStore). */
    default void registerDocument(String docId, Path docDir, EntityStore entityStore) {}

    /** Unregister a document when its window closes. */
    default void unregisterDocument(String docId) {}

    // ==================== Friends ====================

    record FriendStatus(String email, String displayName, boolean online, long lastSeenMs) {}

    /** Get all friends with their online status. */
    default List<FriendStatus> getFriendsWithStatus() { return List.of(); }

    /** Check if a specific friend is on the LAN right now. */
    default boolean isFriendOnline(String email) { return false; }

    // ==================== Chat ====================

    record ChatMessage(String senderEmail, String text, long timestamp) {}

    /** Send a chat message to all connected friends on the LAN. */
    default void sendChat(String text) {}

    /** Register a listener for incoming chat messages. */
    default void addChatListener(Consumer<ChatMessage> listener) {}

    /** Remove a chat listener. */
    default void removeChatListener(Consumer<ChatMessage> listener) {}
}
