package com.tradery.news.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Log panel for displaying AI interactions, data processing events, and system messages.
 */
public class IntelLogPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextPane logPane;
    private final StyledDocument doc;
    private final Style timestampStyle;
    private final Style infoStyle;
    private final Style successStyle;
    private final Style warningStyle;
    private final Style errorStyle;
    private final Style aiStyle;
    private final Style dataStyle;

    private static IntelLogPanel instance;

    public IntelLogPanel() {
        super(new BorderLayout());
        setBackground(new Color(30, 32, 36));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 52, 56)));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(35, 37, 41));
        header.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel titleLabel = new JLabel("Activity Log");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        titleLabel.setForeground(new Color(150, 150, 160));
        header.add(titleLabel, BorderLayout.WEST);

        JButton clearBtn = new JButton("Clear");
        clearBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        clearBtn.setMargin(new Insets(2, 6, 2, 6));
        clearBtn.addActionListener(e -> clear());
        header.add(clearBtn, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // Log pane
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(new Color(25, 27, 31));
        logPane.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        logPane.setBorder(new EmptyBorder(8, 8, 8, 8));

        doc = logPane.getStyledDocument();

        // Define styles
        timestampStyle = doc.addStyle("timestamp", null);
        StyleConstants.setForeground(timestampStyle, new Color(100, 100, 110));
        StyleConstants.setFontSize(timestampStyle, 10);

        infoStyle = doc.addStyle("info", null);
        StyleConstants.setForeground(infoStyle, new Color(180, 180, 190));

        successStyle = doc.addStyle("success", null);
        StyleConstants.setForeground(successStyle, new Color(100, 200, 120));

        warningStyle = doc.addStyle("warning", null);
        StyleConstants.setForeground(warningStyle, new Color(220, 180, 80));

        errorStyle = doc.addStyle("error", null);
        StyleConstants.setForeground(errorStyle, new Color(220, 100, 100));

        aiStyle = doc.addStyle("ai", null);
        StyleConstants.setForeground(aiStyle, new Color(150, 130, 255));

        dataStyle = doc.addStyle("data", null);
        StyleConstants.setForeground(dataStyle, new Color(100, 180, 220));

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        instance = this;
    }

    /**
     * Get the singleton instance (for static logging methods).
     */
    public static IntelLogPanel getInstance() {
        return instance;
    }

    /**
     * Log an info message.
     */
    public void info(String message) {
        log(message, infoStyle, null);
    }

    /**
     * Log a success message.
     */
    public void success(String message) {
        log(message, successStyle, "OK");
    }

    /**
     * Log a warning message.
     */
    public void warn(String message) {
        log(message, warningStyle, "WARN");
    }

    /**
     * Log an error message.
     */
    public void error(String message) {
        log(message, errorStyle, "ERR");
    }

    /**
     * Log an AI-related message.
     */
    public void ai(String message) {
        log(message, aiStyle, "AI");
    }

    /**
     * Log a data processing message.
     */
    public void data(String message) {
        log(message, dataStyle, "DATA");
    }

    private void log(String message, Style style, String tag) {
        SwingUtilities.invokeLater(() -> {
            try {
                String timestamp = LocalTime.now().format(TIME_FMT);
                doc.insertString(doc.getLength(), timestamp + " ", timestampStyle);

                if (tag != null) {
                    Style tagStyle = style;
                    doc.insertString(doc.getLength(), "[" + tag + "] ", tagStyle);
                }

                doc.insertString(doc.getLength(), message + "\n", style);

                // Auto-scroll to bottom
                logPane.setCaretPosition(doc.getLength());

                // Limit log size (keep last 500 lines)
                trimLog(500);
            } catch (BadLocationException e) {
                // Ignore
            }
        });
    }

    private void trimLog(int maxLines) {
        try {
            String text = doc.getText(0, doc.getLength());
            int lines = text.split("\n").length;
            if (lines > maxLines) {
                int removeLines = lines - maxLines;
                int pos = 0;
                for (int i = 0; i < removeLines; i++) {
                    int next = text.indexOf('\n', pos);
                    if (next >= 0) pos = next + 1;
                }
                if (pos > 0) {
                    doc.remove(0, pos);
                }
            }
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    /**
     * Clear all log entries.
     */
    public void clear() {
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    // Static convenience methods
    public static void logInfo(String message) {
        if (instance != null) instance.info(message);
    }

    public static void logSuccess(String message) {
        if (instance != null) instance.success(message);
    }

    public static void logWarn(String message) {
        if (instance != null) instance.warn(message);
    }

    public static void logError(String message) {
        if (instance != null) instance.error(message);
    }

    public static void logAI(String message) {
        if (instance != null) instance.ai(message);
    }

    public static void logData(String message) {
        if (instance != null) instance.data(message);
    }
}
