package com.tradery.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tradery.TraderyApp;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Manages app lock/heartbeat file for external tool integration.
 *
 * Creates ~/.tradery/.lock with:
 * - PID of running instance
 * - Start time
 * - Last heartbeat (updated every 5 seconds)
 *
 * External tools (like Claude CLI) can:
 * - Check if file exists
 * - Read heartbeat to verify app is actually running (not stale)
 * - Detect if app crashed (stale heartbeat)
 */
public class AppLock {

    private static final File LOCK_FILE = new File(TraderyApp.USER_DIR, ".lock");
    private static final long HEARTBEAT_INTERVAL_MS = 5000; // 5 seconds
    private static final long STALE_THRESHOLD_MS = 15000;   // 15 seconds = considered dead

    private static AppLock instance;
    private Timer heartbeatTimer;
    private final ObjectMapper mapper;
    private LockInfo lockInfo;

    /**
     * Lock file contents
     */
    public record LockInfo(
        long pid,
        String startTime,
        String lastHeartbeat,
        String version
    ) {}

    private AppLock() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static synchronized AppLock getInstance() {
        if (instance == null) {
            instance = new AppLock();
        }
        return instance;
    }

    /**
     * Check if another instance is already running.
     * Returns true if lock file exists with recent heartbeat.
     */
    public boolean isAnotherInstanceRunning() {
        if (!LOCK_FILE.exists()) {
            return false;
        }

        try {
            LockInfo existing = mapper.readValue(LOCK_FILE, LockInfo.class);
            Instant lastBeat = Instant.parse(existing.lastHeartbeat());
            long age = System.currentTimeMillis() - lastBeat.toEpochMilli();

            if (age < STALE_THRESHOLD_MS) {
                // Recent heartbeat - another instance is running
                return true;
            }

            // Stale lock file - previous instance crashed
            System.out.println("Removing stale lock file (last heartbeat: " + existing.lastHeartbeat() + ")");
            LOCK_FILE.delete();
            return false;

        } catch (Exception e) {
            // Corrupt lock file - remove it
            LOCK_FILE.delete();
            return false;
        }
    }

    /**
     * Acquire the lock and start heartbeat.
     * Call this when the app starts.
     */
    public void acquire() {
        long pid = ProcessHandle.current().pid();
        Instant now = Instant.now();

        lockInfo = new LockInfo(
            pid,
            now.toString(),
            now.toString(),
            TraderyApp.VERSION
        );

        writeLockFile();
        startHeartbeat();

        // Ensure lock is released on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(this::release));

        System.out.println("App lock acquired (PID: " + pid + ")");
    }

    /**
     * Release the lock and stop heartbeat.
     * Called automatically on shutdown.
     */
    public void release() {
        stopHeartbeat();

        if (LOCK_FILE.exists()) {
            LOCK_FILE.delete();
            System.out.println("App lock released");
        }
    }

    private void startHeartbeat() {
        heartbeatTimer = new Timer("AppLock-Heartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateHeartbeat();
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
    }

    private void updateHeartbeat() {
        if (lockInfo != null) {
            lockInfo = new LockInfo(
                lockInfo.pid(),
                lockInfo.startTime(),
                Instant.now().toString(),
                lockInfo.version()
            );
            writeLockFile();
        }
    }

    private void writeLockFile() {
        try {
            mapper.writeValue(LOCK_FILE, lockInfo);
        } catch (IOException e) {
            System.err.println("Failed to write lock file: " + e.getMessage());
        }
    }

    /**
     * Read the current lock info (for external tools).
     * Returns null if no lock or stale.
     */
    public static LockInfo readLockInfo() {
        if (!LOCK_FILE.exists()) {
            return null;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            LockInfo info = mapper.readValue(LOCK_FILE, LockInfo.class);

            // Check if stale
            Instant lastBeat = Instant.parse(info.lastHeartbeat());
            long age = System.currentTimeMillis() - lastBeat.toEpochMilli();

            if (age > STALE_THRESHOLD_MS) {
                return null; // Stale
            }

            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if the app is currently running (static helper for external use).
     */
    public static boolean isAppRunning() {
        return readLockInfo() != null;
    }
}
