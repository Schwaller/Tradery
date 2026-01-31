package com.tradery.desk.alert;

import com.tradery.desk.signal.SignalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Audio alert using system beep or custom sounds.
 */
public class AudioAlert implements AlertOutput {

    private static final Logger log = LoggerFactory.getLogger(AudioAlert.class);

    private boolean enabled = true;
    private final boolean isMacOS;

    public AudioAlert() {
        this.isMacOS = System.getProperty("os.name").toLowerCase().contains("mac");
    }

    public AudioAlert(boolean enabled) {
        this();
        this.enabled = enabled;
    }

    @Override
    public void send(SignalEvent signal) {
        if (!enabled) {
            return;
        }

        try {
            if (isMacOS) {
                // Use macOS afplay for better sound
                String sound = signal.type() == SignalEvent.SignalType.ENTRY
                    ? "/System/Library/Sounds/Ping.aiff"
                    : "/System/Library/Sounds/Pop.aiff";

                ProcessBuilder pb = new ProcessBuilder("afplay", sound);
                pb.start();
            } else {
                // Fall back to Java toolkit beep
                Toolkit.getDefaultToolkit().beep();
            }
        } catch (Exception e) {
            log.debug("Failed to play audio: {}", e.getMessage());
            // Fall back to toolkit beep
            try {
                Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public String getName() {
        return "Audio";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
