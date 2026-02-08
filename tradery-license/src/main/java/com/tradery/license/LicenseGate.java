package com.tradery.license;

import javax.swing.*;

/**
 * Static entry point for license checking. Called at startup by each app.
 *
 * - GUI mode: shows LicenseDialog if no valid license
 * - Headless mode: logs error and exits if no valid license
 */
public class LicenseGate {

    private LicenseGate() {}

    /**
     * Check license and either continue or exit.
     *
     * @param headless true for CLI/daemon apps (no dialog, just exit), false for GUI apps
     */
    public static void checkOrExit(boolean headless) {
        LicenseConfig config = LicenseConfig.load();
        String key = config.getLicenseKey();

        if (key != null && !key.isBlank()) {
            LicenseKeyCodec.LicenseResult result = LicenseKeyCodec.validate(key);
            if (result.isValid()) {
                return; // License valid, continue
            }
        }

        // License missing, invalid, or expired
        if (headless) {
            System.err.println("========================================");
            System.err.println("  Plaiiin: No valid license found.");
            System.err.println("  Please run a GUI app to enter your key,");
            System.err.println("  or add it to ~/.tradery/license.yaml");
            System.err.println("========================================");
            System.exit(1);
        }

        // GUI mode: show dialog
        // Ensure we're on EDT or can invoke on it
        if (SwingUtilities.isEventDispatchThread()) {
            if (!LicenseDialog.showDialog(null)) {
                System.exit(0);
            }
        } else {
            final boolean[] result = {false};
            try {
                SwingUtilities.invokeAndWait(() -> {
                    result[0] = LicenseDialog.showDialog(null);
                });
            } catch (Exception e) {
                System.err.println("Failed to show license dialog: " + e.getMessage());
                System.exit(1);
            }
            if (!result[0]) {
                System.exit(0);
            }
        }
    }
}
