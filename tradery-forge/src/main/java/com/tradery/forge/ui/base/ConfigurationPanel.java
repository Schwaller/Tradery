package com.tradery.forge.ui.base;

import javax.swing.*;

/**
 * Base class for configuration panels that support change notification.
 * Provides common onChange callback handling and event suppression during bulk updates.
 */
public abstract class ConfigurationPanel extends JPanel {

    private Runnable onChange;
    private boolean suppressChangeEvents = false;

    /**
     * Sets the callback to be invoked when panel values change.
     *
     * @param onChange the callback to run on changes
     */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Fires the change callback if not suppressed.
     * Call this method from subclasses when values change.
     */
    protected void fireChange() {
        if (!suppressChangeEvents && onChange != null) {
            onChange.run();
        }
    }

    /**
     * Executes an action with change events suppressed.
     * Use this when loading data to prevent triggering change callbacks.
     *
     * @param action the action to run without firing change events
     */
    protected void suppressChanges(Runnable action) {
        suppressChangeEvents = true;
        try {
            action.run();
        } finally {
            suppressChangeEvents = false;
        }
    }

    /**
     * Directly sets the suppress flag. Use suppressChanges() when possible.
     * This is available for cases where try-finally blocks span multiple methods.
     *
     * @param suppress true to suppress change events
     */
    protected void setSuppressChangeEvents(boolean suppress) {
        this.suppressChangeEvents = suppress;
    }

    /**
     * @return true if change events are currently suppressed
     */
    protected boolean isSuppressingChanges() {
        return suppressChangeEvents;
    }
}
