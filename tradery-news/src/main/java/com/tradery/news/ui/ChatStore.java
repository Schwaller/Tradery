package com.tradery.news.ui;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite persistence for chat messages.
 * Stores all messages at ~/.tradery/chat.db.
 */
public class ChatStore implements AutoCloseable {

    private final Connection conn;

    public record Message(long id, String peerEmail, String senderEmail, String text, long timestamp, boolean read) {}
    public record Conversation(String peerEmail, String lastMessage, long lastTimestamp, int unreadCount) {}

    public ChatStore(Path dbPath) {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        peer_email TEXT NOT NULL,
                        sender_email TEXT NOT NULL,
                        text TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        is_read INTEGER NOT NULL DEFAULT 0
                    )
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_peer ON messages(peer_email, timestamp)");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open chat database: " + e.getMessage(), e);
        }
    }

    /** Save a message. peerEmail is the "other" person in the conversation (NOT us). */
    public void saveMessage(String peerEmail, String senderEmail, String text, long timestamp) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO messages (peer_email, sender_email, text, timestamp, is_read) VALUES (?, ?, ?, ?, 0)")) {
            ps.setString(1, peerEmail);
            ps.setString(2, senderEmail);
            ps.setString(3, text);
            ps.setLong(4, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save chat message: " + e.getMessage(), e);
        }
    }

    /** Get messages for a conversation, newest first. */
    public List<Message> getMessages(String peerEmail, int limit, int offset) {
        List<Message> msgs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, peer_email, sender_email, text, timestamp, is_read FROM messages " +
                "WHERE peer_email = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, peerEmail);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                msgs.add(new Message(
                    rs.getLong(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getLong(5), rs.getInt(6) == 1
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read messages: " + e.getMessage(), e);
        }
        // Reverse so oldest first for display
        java.util.Collections.reverse(msgs);
        return msgs;
    }

    /** Get all conversations with last message and unread count. */
    public List<Conversation> getConversations() {
        List<Conversation> convos = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("""
                SELECT m.peer_email,
                       m.text,
                       m.timestamp,
                       COALESCE(u.unread, 0) as unread_count
                FROM messages m
                INNER JOIN (
                    SELECT peer_email, MAX(timestamp) as max_ts
                    FROM messages GROUP BY peer_email
                ) latest ON m.peer_email = latest.peer_email AND m.timestamp = latest.max_ts
                LEFT JOIN (
                    SELECT peer_email, COUNT(*) as unread
                    FROM messages WHERE is_read = 0 GROUP BY peer_email
                ) u ON m.peer_email = u.peer_email
                ORDER BY m.timestamp DESC
            """);
            while (rs.next()) {
                convos.add(new Conversation(
                    rs.getString(1), rs.getString(2),
                    rs.getLong(3), rs.getInt(4)
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read conversations: " + e.getMessage(), e);
        }
        return convos;
    }

    /** Mark all messages in a conversation as read. */
    public void markRead(String peerEmail) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE messages SET is_read = 1 WHERE peer_email = ? AND is_read = 0")) {
            ps.setString(1, peerEmail);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark messages as read: " + e.getMessage(), e);
        }
    }

    /** Total unread count across all conversations. */
    public int unreadCount() {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages WHERE is_read = 0");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    /** Unread count for a specific conversation. */
    public int unreadCount(String peerEmail) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM messages WHERE peer_email = ? AND is_read = 0")) {
            ps.setString(1, peerEmail);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    /** Delete all messages in a conversation. */
    public void clearConversation(String peerEmail) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM messages WHERE peer_email = ?")) {
            ps.setString(1, peerEmail);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear conversation: " + e.getMessage(), e);
        }
    }

    /** Get all distinct peer emails we've chatted with. */
    public List<String> getAllPeerEmails() {
        List<String> emails = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT DISTINCT peer_email FROM messages ORDER BY peer_email");
            while (rs.next()) emails.add(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list peers: " + e.getMessage(), e);
        }
        return emails;
    }

    @Override
    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
