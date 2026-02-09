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

    record ChatMessage(String senderEmail, String recipientEmail, String text, long timestamp) {}

    /** Send a chat message to a specific peer. */
    default void sendChat(String recipientEmail, String text) {}

    /** Register a listener for incoming chat messages. */
    default void addChatListener(Consumer<ChatMessage> listener) {}

    /** Remove a chat listener. */
    default void removeChatListener(Consumer<ChatMessage> listener) {}

    /** Notify that the local friend list has changed (triggers re-announce to connected peers). */
    default void onFriendListChanged() {}

    /** Called when a friend is added — creates and stores friendship cert. */
    default void onFriendAdded(String friendEmail) {}

    /** Bootstrap recovery from a friend's backup. */
    default void bootstrapRecovery(String password, String friendEmail) {}

    /** Check if a peer email is a mutual friend (both sides have added each other). */
    default boolean isMutualFriend(String email) { return false; }

    // ==================== Network Status ====================

    record NetworkStatus(
        String email,              // logged-in email, or null
        boolean deviceEnrolled,    // has rendezvous device credential
        int serverPort,            // PeerServer listen port, 0 if not started
        String portMapping,        // "NAT-PMP", "UPnP", null if none
        String publicIp,           // from STUN or port mapping, null if unknown
        boolean lanActive,         // LAN discovery running
        int lanPeerCount,          // visible LAN peers
        boolean rendezvousAvailable, // device enrolled + rendezvous reachable
        int connectedPeers,        // unique peer emails connected
        int connectedDevices       // unique device IDs connected
    ) {}

    default NetworkStatus getNetworkStatus() { return null; }

    // ==================== Auth ====================

    /** Trigger browser-based Keycloak login. Returns true on success. */
    default boolean login() { return false; }

    /** Clear stored auth tokens and session. */
    default void logout() {}

    /** Get email from authenticated session, or null if not logged in. */
    default String getAuthenticatedEmail() { return null; }
}
