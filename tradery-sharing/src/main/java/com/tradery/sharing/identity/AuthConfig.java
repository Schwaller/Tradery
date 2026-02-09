package com.tradery.sharing.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists OAuth auth state to ~/.tradery/auth.yaml.
 * Stores refresh token for silent re-auth and cached user info.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);
    private static final Path CONFIG_PATH = Path.of(
            System.getProperty("user.home"), ".tradery", "auth.yaml"
    );
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private String refreshToken;
    private String email;
    private String displayName;
    private String userId;

    // Device enrollment (offline-capable auth for rendezvous)
    private String deviceId;
    private String deviceCredential;
    private String backendPublicKey;

    // Identity cert (serialized JSON)
    private String identityCertJson;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceCredential() { return deviceCredential; }
    public void setDeviceCredential(String deviceCredential) { this.deviceCredential = deviceCredential; }

    public String getBackendPublicKey() { return backendPublicKey; }
    public void setBackendPublicKey(String backendPublicKey) { this.backendPublicKey = backendPublicKey; }

    public String getIdentityCertJson() { return identityCertJson; }
    public void setIdentityCertJson(String identityCertJson) { this.identityCertJson = identityCertJson; }

    public boolean isDeviceEnrolled() {
        return deviceCredential != null && !deviceCredential.isBlank();
    }

    public boolean isLoggedIn() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    public static AuthConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                return YAML.readValue(CONFIG_PATH.toFile(), AuthConfig.class);
            } catch (IOException e) {
                log.warn("Failed to read auth config: {}", e.getMessage());
            }
        }
        return new AuthConfig();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            YAML.writeValue(CONFIG_PATH.toFile(), this);
        } catch (IOException e) {
            log.error("Failed to save auth config", e);
        }
    }

    public void clear() {
        this.refreshToken = null;
        this.email = null;
        this.displayName = null;
        this.userId = null;
        try {
            Files.deleteIfExists(CONFIG_PATH);
        } catch (IOException e) {
            log.warn("Failed to delete auth config file: {}", e.getMessage());
        }
    }
}
