package com.tradery.trader.ui;

import com.tradery.execution.journal.ExecutionEvent;
import com.tradery.execution.journal.ExecutionJournal;

import javax.swing.*;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Scrolling execution event log.
 */
public class ExecutionLogPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final JTextArea logArea;

    public ExecutionLogPanel() {
        setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setLineWrap(true);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Subscribe to journal events for real-time display.
     */
    public void subscribe(ExecutionJournal journal) {
        journal.subscribe(this::appendEvent);

        // Load today's events
        for (ExecutionEvent event : journal.readToday()) {
            appendEvent(event);
        }
    }

    public void appendEvent(ExecutionEvent event) {
        SwingUtilities.invokeLater(() -> {
            String time = TIME_FMT.format(event.getTimestamp());
            logArea.append(time + "  " + event.getSummary() + "\n");

            // Auto-scroll to bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void clear() {
        logArea.setText("");
    }
}
