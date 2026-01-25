package com.tradery.desk.alert;

import com.tradery.desk.signal.SignalEvent;

/**
 * Interface for alert output channels.
 */
public interface AlertOutput {

    /**
     * Send a signal alert.
     */
    void send(SignalEvent signal);

    /**
     * Get the name of this output.
     */
    String getName();

    /**
     * Check if this output is enabled.
     */
    boolean isEnabled();
}
