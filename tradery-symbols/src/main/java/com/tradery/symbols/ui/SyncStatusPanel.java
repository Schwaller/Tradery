package com.tradery.symbols.ui;

import com.tradery.symbols.service.SymbolService;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

/**
 * Small status bar showing pair count and last sync time.
 */
public class SyncStatusPanel extends JPanel {

    private final JLabel statusLabel;

    public SyncStatusPanel(SymbolService service) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        add(statusLabel);

        refresh(service);
    }

    public void refresh(SymbolService service) {
        if (!service.isDatabaseAvailable()) {
            statusLabel.setText("Symbol database not available");
            return;
        }

        SymbolService.SyncStatus status = service.getSyncStatus();
        String text = String.format("%,d pairs", status.pairCount());

        if (status.lastSync() != null) {
            text += " | Last sync: " + formatRelativeTime(status.lastSync());
        }

        statusLabel.setText(text);
    }

    private static String formatRelativeTime(Instant instant) {
        Duration d = Duration.between(instant, Instant.now());
        long minutes = d.toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = d.toHours();
        if (hours < 24) return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        long days = d.toDays();
        return days + " day" + (days == 1 ? "" : "s") + " ago";
    }
}
