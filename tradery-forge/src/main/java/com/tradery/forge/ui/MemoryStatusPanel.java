package com.tradery.forge.ui;

import com.tradery.forge.ui.controls.StatusBadge;

import javax.swing.*;
import java.awt.*;

/**
 * Panel showing JVM memory usage and per-manager memory breakdown.
 * Displayed in the status bar.
 */
public class MemoryStatusPanel extends JPanel {

    private final StatusBadge heapLabel;
    private final Timer refreshTimer;


    public MemoryStatusPanel() {
        setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        setOpaque(false);

        // JVM heap label
        heapLabel = new StatusBadge("Heap --");
        heapLabel.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        add(heapLabel);

        // Refresh every 2 seconds
        refreshTimer = new Timer(2000, e -> refresh());
        refreshTimer.start();

        // Initial refresh
        refresh();
    }

    public void refresh() {
        // JVM heap usage
        Runtime rt = Runtime.getRuntime();
        long usedBytes = rt.totalMemory() - rt.freeMemory();
        long maxBytes = rt.maxMemory();
        double usedPct = (double) usedBytes / maxBytes * 100;

        heapLabel.setText(String.format("Heap %s/%s", formatBytes(usedBytes), formatBytes(maxBytes)));

        // Color based on usage
        Color heapBg = StatusBadge.BG_IDLE;
        if (usedPct > 80) heapBg = StatusBadge.BG_ERROR;
        else if (usedPct > 60) heapBg = StatusBadge.BG_WARNING;
        heapLabel.setStatusColor(heapBg);

        heapLabel.setToolTipText(String.format(
            "<html><b>JVM Heap Memory</b><br>" +
            "Used: %s (%.1f%%)<br>" +
            "Max: %s</html>",
            formatBytes(usedBytes), usedPct, formatBytes(maxBytes)));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return formatWithSeparator(bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return formatWithSeparator(bytes / (1024 * 1024)) + " MB";
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** Format number with ' as thousand separator (e.g., 1'234) */
    private String formatWithSeparator(long value) {
        if (value < 1000) return String.valueOf(value);
        StringBuilder sb = new StringBuilder();
        String str = String.valueOf(value);
        int len = str.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) sb.append("'");
            sb.append(str.charAt(i));
        }
        return sb.toString();
    }

    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }
}
