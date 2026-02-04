package com.tradery.ui.dashboard;

import javax.swing.*;

/**
 * An extra section that an app can add to the dashboard.
 * Appears in the sidebar with a label and optional status dot,
 * and shows a content panel when selected.
 */
public record DashboardSection(
    String id,
    String label,
    JComponent contentPanel,
    StatusColor statusColor
) {
    public enum StatusColor {
        OK, WARNING, ERROR, IDLE, NONE
    }

    public DashboardSection(String id, String label, JComponent contentPanel) {
        this(id, label, contentPanel, StatusColor.NONE);
    }
}
