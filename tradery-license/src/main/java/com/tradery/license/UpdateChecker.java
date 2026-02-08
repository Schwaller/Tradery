package com.tradery.license;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Background update checker. Runs on SwingWorker, non-blocking, never prevents startup.
 * Checks at most once every 24 hours.
 */
public class UpdateChecker {

    private static final Path CHECK_FILE = Path.of(System.getProperty("user.home"),
            ".tradery", "update-check.yaml");
    private static final long CHECK_INTERVAL_HOURS = 24;

    private UpdateChecker() {}

    /**
     * Check for updates asynchronously. Safe to call from main thread.
     * Shows a non-modal notification if a newer version is available.
     *
     * @param currentVersion current app version (e.g. "1.0.0")
     * @param updateUrl      URL to fetch latest.json from
     */
    public static void checkAsync(String currentVersion, String updateUrl) {
        new SwingWorker<UpdateInfo, Void>() {
            @Override
            protected UpdateInfo doInBackground() {
                try {
                    return checkForUpdate(currentVersion, updateUrl);
                } catch (Exception e) {
                    // Silently fail - update check should never disrupt the app
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    UpdateInfo info = get();
                    if (info != null) {
                        UpdateNotification.show(info);
                    }
                } catch (Exception e) {
                    // Silently fail
                }
            }
        }.execute();
    }

    private static UpdateInfo checkForUpdate(String currentVersion, String updateUrl) throws IOException {
        // Load check state
        CheckState state = loadCheckState();

        // Skip if checked recently
        if (state.lastCheckTime != null) {
            Instant lastCheck = Instant.parse(state.lastCheckTime);
            if (lastCheck.plus(CHECK_INTERVAL_HOURS, ChronoUnit.HOURS).isAfter(Instant.now())) {
                return null;
            }
        }

        // Fetch latest version info
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(updateUrl)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            ObjectMapper json = new ObjectMapper();
            UpdateInfo info = json.readValue(response.body().string(), UpdateInfo.class);

            // Save check timestamp
            state.lastCheckTime = Instant.now().toString();
            saveCheckState(state);

            // Compare versions
            if (info.latestVersion != null && isNewer(info.latestVersion, currentVersion)) {
                // Skip if user chose to skip this version
                if (info.latestVersion.equals(state.skippedVersion)) {
                    return null;
                }
                return info;
            }
        }

        return null;
    }

    /**
     * Simple version comparison: splits on dots and compares numerically.
     */
    static boolean isNewer(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");

        int len = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < len; i++) {
            int l = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            int c = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false; // equal
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --- Check state persistence ---

    private static CheckState loadCheckState() {
        try {
            if (Files.exists(CHECK_FILE)) {
                return yamlMapper().readValue(CHECK_FILE.toFile(), CheckState.class);
            }
        } catch (IOException e) {
            // Ignore
        }
        return new CheckState();
    }

    private static void saveCheckState(CheckState state) {
        try {
            Files.createDirectories(CHECK_FILE.getParent());
            yamlMapper().writeValue(CHECK_FILE.toFile(), state);
        } catch (IOException e) {
            // Ignore
        }
    }

    static void skipVersion(String version) {
        CheckState state = loadCheckState();
        state.skippedVersion = version;
        saveCheckState(state);
    }

    private static ObjectMapper yamlMapper() {
        YAMLFactory factory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        return new ObjectMapper(factory);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CheckState {
        public String lastCheckTime;
        public String skippedVersion;
    }

    /**
     * Update info from the remote endpoint.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateInfo {
        public String latestVersion;
        public String downloadUrl;
        public String releaseNotes;
        public String releaseDate;
    }
}
