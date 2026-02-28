package com.tradery.ui;

import javax.swing.*;

public final class UiThread {
    private UiThread() {}

    public static void assertNotUiThread(String operation) {
        if (isUiThread())
            throw new IllegalStateException(operation + " must not be called from the UI thread");
    }

    public static void assertUiThread(String operation) {
        if (!isUiThread())
            throw new IllegalStateException(operation + " must be called from the UI thread");
    }

    public static boolean isUiThread() {
        return SwingUtilities.isEventDispatchThread();
    }
}
