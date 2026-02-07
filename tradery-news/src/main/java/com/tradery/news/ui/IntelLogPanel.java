package com.tradery.news.ui;

import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.ThinSplitPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Log panel for displaying AI interactions, data processing events, and system messages.
 * Supports expandable entries with detailed view for AI prompts/responses.
 */
public class IntelLogPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_ENTRIES = 200;

    private final DefaultListModel<LogEntry> listModel;
    private final JList<LogEntry> entryList;
    private final JTextArea detailArea;
    private final ThinSplitPane splitPane;

    private static IntelLogPanel instance;

    public IntelLogPanel() {
        super(new BorderLayout());
        setOpaque(false);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
            new EmptyBorder(4, 8, 4, 8)
        ));

        JLabel titleLabel = new JLabel("Activity Log");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        header.add(titleLabel, BorderLayout.WEST);

        JButton clearBtn = new JButton("Clear");
        clearBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        clearBtn.setMargin(new Insets(2, 6, 2, 6));
        clearBtn.addActionListener(e -> clear());
        header.add(clearBtn, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // Entry list
        listModel = new DefaultListModel<>();
        entryList = new JList<>(listModel);
        entryList.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        entryList.setCellRenderer(new LogEntryCellRenderer());
        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showEntryDetail(entryList.getSelectedValue());
            }
        });

        BorderlessScrollPane listScroll = new BorderlessScrollPane(entryList);
        listScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Detail area (same background as list)
        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
        detailArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setText("Select a log entry to view details");

        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")));

        // Split pane (list takes 66%)
        splitPane = new ThinSplitPane(JSplitPane.VERTICAL_SPLIT, listScroll, detailScroll);
        splitPane.setResizeWeight(0.66);

        add(splitPane, BorderLayout.CENTER);

        instance = this;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // Set initial divider location to 66% after component is added
        SwingUtilities.invokeLater(() -> {
            int height = splitPane.getHeight();
            if (height > 0) {
                splitPane.setDividerLocation((int) (height * 0.66));
            }
        });
    }

    private void showEntryDetail(LogEntry entry) {
        if (entry == null) {
            detailArea.setText("Select a log entry to view details");
            detailArea.setForeground(UIManager.getColor("Label.disabledForeground"));
            return;
        }

        detailArea.setForeground(entry.type.color);

        if (entry.detail != null && !entry.detail.isEmpty()) {
            detailArea.setText(entry.detail);
        } else {
            detailArea.setText(entry.message);
        }
        detailArea.setCaretPosition(0);
    }

    /**
     * Get the singleton instance (for static logging methods).
     */
    public static IntelLogPanel getInstance() {
        return instance;
    }

    // ==================== Logging methods ====================

    public void info(String message) {
        addEntry(new LogEntry(LogType.INFO, message, null));
    }

    public void info(String message, String detail) {
        addEntry(new LogEntry(LogType.INFO, message, detail));
    }

    public void success(String message) {
        addEntry(new LogEntry(LogType.SUCCESS, message, null));
    }

    public void warn(String message) {
        addEntry(new LogEntry(LogType.WARNING, message, null));
    }

    public void error(String message) {
        addEntry(new LogEntry(LogType.ERROR, message, null));
    }

    public void error(String message, String detail) {
        addEntry(new LogEntry(LogType.ERROR, message, detail));
    }

    public void ai(String message) {
        addEntry(new LogEntry(LogType.AI, message, null));
    }

    public void ai(String summary, String prompt, String response) {
        StringBuilder detail = new StringBuilder();
        if (prompt != null) {
            detail.append("━━━ PROMPT ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            detail.append(prompt);
            detail.append("\n\n");
        }
        if (response != null) {
            detail.append("━━━ RESPONSE ━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            detail.append(response);
        }
        addEntry(new LogEntry(LogType.AI, summary, detail.toString()));
    }

    public void data(String message) {
        addEntry(new LogEntry(LogType.DATA, message, null));
    }

    private void addEntry(LogEntry entry) {
        SwingUtilities.invokeLater(() -> {
            listModel.addElement(entry);

            // Trim old entries
            while (listModel.size() > MAX_ENTRIES) {
                listModel.remove(0);
            }

            // Auto-scroll to bottom
            entryList.ensureIndexIsVisible(listModel.size() - 1);
        });
    }

    public void clear() {
        listModel.clear();
        detailArea.setText("Select a log entry to view details");
        detailArea.setForeground(UIManager.getColor("Label.disabledForeground"));
    }

    // ==================== Static convenience methods ====================

    public static void logInfo(String message) {
        if (instance != null) instance.info(message);
    }

    public static void logInfo(String message, String detail) {
        if (instance != null) instance.info(message, detail);
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

    public static void logError(String message, String detail) {
        if (instance != null) instance.error(message, detail);
    }

    public static void logAI(String message) {
        if (instance != null) instance.ai(message);
    }

    public static void logAI(String summary, String prompt, String response) {
        if (instance != null) instance.ai(summary, prompt, response);
    }

    public static void logData(String message) {
        if (instance != null) instance.data(message);
    }

    // ==================== Inner classes ====================

    public enum LogType {
        INFO("INFO", new Color(140, 140, 150)),
        SUCCESS("OK", new Color(140, 160, 140)),
        WARNING("WARN", new Color(170, 160, 130)),
        ERROR("ERR", new Color(180, 130, 130)),
        AI("AI", new Color(150, 130, 255)),
        DATA("DATA", new Color(130, 150, 170));

        final String tag;
        final Color color;

        LogType(String tag, Color color) {
            this.tag = tag;
            this.color = color;
        }
    }

    public static class LogEntry {
        final LocalTime time;
        final LogType type;
        final String message;
        final String detail;  // Optional expanded content

        LogEntry(LogType type, String message, String detail) {
            this.time = LocalTime.now();
            this.type = type;
            this.message = message;
            this.detail = detail;
        }

        boolean hasDetail() {
            return detail != null && !detail.isEmpty();
        }
    }

    private static class LogEntryCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof LogEntry entry) {
                String timestamp = entry.time.format(TIME_FMT);
                String prefix = entry.hasDetail() ? "▸ " : "  ";
                setText(prefix + timestamp + " [" + entry.type.tag + "] " + entry.message);

                if (!isSelected) {
                    setForeground(entry.type.color);
                }
            }

            return this;
        }
    }
}
