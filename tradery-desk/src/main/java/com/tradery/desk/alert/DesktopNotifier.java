package com.tradery.desk.alert;

import com.tradery.desk.signal.SignalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Desktop notifications using macOS osascript.
 * Falls back gracefully on non-macOS systems.
 */
public class DesktopNotifier implements AlertOutput {

    private static final Logger log = LoggerFactory.getLogger(DesktopNotifier.class);

    private boolean enabled = true;
    private final boolean isMacOS;

    public DesktopNotifier() {
        this.isMacOS = System.getProperty("os.name").toLowerCase().contains("mac");
        if (!isMacOS) {
            log.info("Desktop notifications only supported on macOS");
        }
    }

    public DesktopNotifier(boolean enabled) {
        this();
        this.enabled = enabled;
    }

    @Override
    public void send(SignalEvent signal) {
        if (!enabled || !isMacOS) {
            return;
        }

        String title = String.format("%s %s Signal",
            signal.type().getEmoji(),
            signal.type().getLabel()
        );

        String message = String.format("%s v%d\\n%s %s @ %.2f",
            signal.strategyName(),
            signal.strategyVersion(),
            signal.symbol(),
            signal.timeframe(),
            signal.price()
        );

        // Use osascript for macOS notifications
        String script = String.format(
            "display notification \"%s\" with title \"%s\" sound name \"Glass\"",
            message.replace("\"", "\\\""),
            title.replace("\"", "\\\"")
        );

        try {
            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            log.debug("Failed to show notification: {}", e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "Desktop";
    }

    @Override
    public boolean isEnabled() {
        return enabled && isMacOS;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
