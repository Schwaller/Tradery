package com.tradery.sharing.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stores encrypted UER blobs received from friends.
 * Each friend's backup is stored at ~/.tradery/backups/{hash(email)}/uer.enc
 */
public class FriendBackupStore {

    private static final Logger log = LoggerFactory.getLogger(FriendBackupStore.class);
    private static final Path BACKUPS_DIR = Path.of(System.getProperty("user.home"), ".tradery", "backups");

    private final Path backupsDir;

    public FriendBackupStore() {
        this(BACKUPS_DIR);
    }

    public FriendBackupStore(Path backupsDir) {
        this.backupsDir = backupsDir;
    }

    public record BackupEntry(String email, byte[] data, long updatedAt) {}

    /**
     * Store an encrypted UER blob from a friend.
     */
    public void store(String email, byte[] encryptedData, long updatedAt) throws IOException {
        Path dir = backupsDir.resolve(emailHash(email));
        Files.createDirectories(dir);
        Files.write(dir.resolve("uer.enc"), encryptedData);
        Files.writeString(dir.resolve("meta.txt"), email + "\n" + updatedAt);
        log.debug("Stored backup for {} ({} bytes)", email, encryptedData.length);
    }

    /**
     * Load an encrypted UER blob for a friend, or null if not found.
     */
    public BackupEntry load(String email) throws IOException {
        Path dir = backupsDir.resolve(emailHash(email));
        Path dataPath = dir.resolve("uer.enc");
        Path metaPath = dir.resolve("meta.txt");

        if (!Files.exists(dataPath)) return null;

        byte[] data = Files.readAllBytes(dataPath);
        long updatedAt = 0;
        if (Files.exists(metaPath)) {
            String[] lines = Files.readString(metaPath).split("\n");
            if (lines.length >= 2) {
                try { updatedAt = Long.parseLong(lines[1].trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return new BackupEntry(email, data, updatedAt);
    }

    static String emailHash(String email) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // SHA-256 always available
        }
    }
}
