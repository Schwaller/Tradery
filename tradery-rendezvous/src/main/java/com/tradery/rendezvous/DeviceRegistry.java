package com.tradery.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory device registry. Tracks enrolled devices and revocations.
 * TODO: persist to disk/DB for production use.
 */
public class DeviceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistry.class);

    private final ConcurrentHashMap<String, DeviceEntry> devices = new ConcurrentHashMap<>();
    private final Set<String> revokedDeviceIds = ConcurrentHashMap.newKeySet();
    private final Set<String> revokedUserIds = ConcurrentHashMap.newKeySet();

    public record DeviceEntry(String deviceId, String userId, String devicePublicKey,
                              Instant enrolledAt, Instant lastSeenAt) {}

    /** Register a newly enrolled device. */
    public void register(String deviceId, String userId, String devicePublicKey) {
        Instant now = Instant.now();
        devices.put(deviceId, new DeviceEntry(deviceId, userId, devicePublicKey, now, now));
        log.info("Enrolled device {} for user {}", deviceId, userId);
    }

    /** Update last-seen timestamp. */
    public void touch(String deviceId) {
        devices.computeIfPresent(deviceId, (k, v) ->
                new DeviceEntry(v.deviceId, v.userId, v.devicePublicKey, v.enrolledAt, Instant.now()));
    }

    /** Check if a device or its user is revoked. */
    public boolean isRevoked(String deviceId, String userId) {
        return revokedDeviceIds.contains(deviceId) || revokedUserIds.contains(userId);
    }

    /** Revoke a specific device. */
    public void revokeDevice(String deviceId) {
        revokedDeviceIds.add(deviceId);
        log.info("Revoked device {}", deviceId);
    }

    /** Revoke all devices for a user. */
    public void revokeUser(String userId) {
        revokedUserIds.add(userId);
        log.info("Revoked all devices for user {}", userId);
    }

    /** List all devices for a user. */
    public List<DeviceEntry> devicesForUser(String userId) {
        List<DeviceEntry> result = new ArrayList<>();
        for (DeviceEntry e : devices.values()) {
            if (userId.equals(e.userId)) result.add(e);
        }
        return result;
    }

    public int size() { return devices.size(); }
}
