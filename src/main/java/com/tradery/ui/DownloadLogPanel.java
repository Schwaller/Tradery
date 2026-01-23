package com.tradery.ui;

import com.tradery.data.DataType;
import com.tradery.data.log.DownloadEvent;
import com.tradery.data.log.DownloadLogStore;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel showing download event log with filtering and auto-scroll.
 */
public class DownloadLogPanel extends JPanel {

    private static final int MAX_DISPLAY_EVENTS = 200;

    private final JTable table;
    private final LogTableModel tableModel;
    private final JComboBox<String> filterCombo;
    private final JComboBox<String> dataTypeCombo;
    private final JCheckBox autoScrollCheckbox;
    private final JLabel countLabel;

    private String currentFilter = "All";
    private String currentDataTypeFilter = "All";
    private String pageKeyFilter = null;  // For filtering by selected page

    // Colors for event types
    private static final Color COLOR_ERROR = new Color(180, 80, 80);
    private static final Color COLOR_LOAD_STARTED = new Color(100, 100, 180);
    private static final Color COLOR_LOAD_COMPLETED = new Color(60, 140, 60);
    private static final Color COLOR_UPDATE = new Color(140, 140, 60);
    private static final Color COLOR_PAGE_LIFECYCLE = new Color(100, 100, 100);

    public DownloadLogPanel() {
        setLayout(new BorderLayout(0, 4));

        // Top toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        toolbar.add(new JLabel("Event:"));

        filterCombo = new JComboBox<>(new String[]{"All", "Errors Only", "Loading Events", "Page Lifecycle"});
        filterCombo.addActionListener(e -> {
            currentFilter = (String) filterCombo.getSelectedItem();
            refresh();
        });
        toolbar.add(filterCombo);

        toolbar.add(Box.createHorizontalStrut(8));

        toolbar.add(new JLabel("Data:"));

        dataTypeCombo = new JComboBox<>(new String[]{"All", "Candles", "Funding", "OI", "AggTrades", "Premium"});
        dataTypeCombo.addActionListener(e -> {
            currentDataTypeFilter = (String) dataTypeCombo.getSelectedItem();
            refresh();
        });
        toolbar.add(dataTypeCombo);

        toolbar.add(Box.createHorizontalStrut(12));

        autoScrollCheckbox = new JCheckBox("Auto-scroll", true);
        toolbar.add(autoScrollCheckbox);

        toolbar.add(Box.createHorizontalGlue());

        countLabel = new JLabel("0 events");
        countLabel.setForeground(Color.GRAY);
        toolbar.add(countLabel);

        add(toolbar, BorderLayout.NORTH);

        // Table
        tableModel = new LogTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(20);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);

        // Configure columns
        TableColumnModel columnModel = table.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(70);   // Time
        columnModel.getColumn(0).setMaxWidth(80);
        columnModel.getColumn(1).setPreferredWidth(120);  // Event Type
        columnModel.getColumn(1).setMaxWidth(140);
        columnModel.getColumn(2).setPreferredWidth(70);   // Data Type
        columnModel.getColumn(2).setMaxWidth(90);
        columnModel.getColumn(3).setPreferredWidth(120);  // Page Key
        columnModel.getColumn(4).setPreferredWidth(350);  // Message

        // Custom renderer for colored event types
        table.setDefaultRenderer(Object.class, new LogTableCellRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Set filter to show only events for a specific page.
     */
    public void setPageKeyFilter(String pageKey) {
        this.pageKeyFilter = pageKey;
        refresh();
    }

    /**
     * Clear page key filter.
     */
    public void clearPageKeyFilter() {
        this.pageKeyFilter = null;
        refresh();
    }

    /**
     * Refresh the log display.
     */
    public void refresh() {
        DownloadLogStore logStore = DownloadLogStore.getInstance();
        List<DownloadEvent> allEvents;

        // Get events based on filter
        if (pageKeyFilter != null) {
            allEvents = logStore.getPageLog(pageKeyFilter);
        } else {
            allEvents = logStore.getGlobalLog(MAX_DISPLAY_EVENTS * 2);  // Get extra for filtering
        }

        // Apply view filter
        List<DownloadEvent> filteredEvents = new ArrayList<>();
        for (DownloadEvent event : allEvents) {
            if (filteredEvents.size() >= MAX_DISPLAY_EVENTS) break;

            boolean include = switch (currentFilter) {
                case "All" -> true;
                case "Errors Only" -> event.isError();
                case "Loading Events" -> event.isLoadingEvent();
                case "Page Lifecycle" -> event.eventType() == DownloadEvent.EventType.PAGE_CREATED ||
                                         event.eventType() == DownloadEvent.EventType.PAGE_RELEASED;
                default -> true;
            };

            if (include) {
                filteredEvents.add(event);
            }
        }

        // Update table
        tableModel.setEvents(filteredEvents);

        // Update count
        countLabel.setText(filteredEvents.size() + " events" +
            (pageKeyFilter != null ? " (filtered)" : ""));

        // Auto-scroll to bottom if enabled
        if (autoScrollCheckbox.isSelected() && table.getRowCount() > 0) {
            table.scrollRectToVisible(table.getCellRect(0, 0, true));
        }
    }

    // ========== Table Model ==========

    private static class LogTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Time", "Event", "Type", "Page", "Message"};
        private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

        private List<DownloadEvent> events = new ArrayList<>();

        public void setEvents(List<DownloadEvent> events) {
            this.events = new ArrayList<>(events);
            fireTableDataChanged();
        }

        public DownloadEvent getEventAt(int row) {
            if (row >= 0 && row < events.size()) {
                return events.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return events.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= events.size()) return "";

            DownloadEvent event = events.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> TIME_FORMATTER.format(Instant.ofEpochMilli(event.timestamp()));
                case 1 -> formatEventType(event.eventType());
                case 2 -> event.dataType().getDisplayName();
                case 3 -> simplifyPageKey(event.pageKey());
                case 4 -> event.message();
                default -> "";
            };
        }

        private String formatEventType(DownloadEvent.EventType type) {
            return switch (type) {
                case PAGE_CREATED -> "Page created";
                case LOAD_STARTED -> "Start loading";
                case LOAD_COMPLETED -> "Loading complete";
                case UPDATE_STARTED -> "Start update";
                case UPDATE_COMPLETED -> "Update complete";
                case ERROR -> "Error";
                case PAGE_RELEASED -> "Page released";
                case LISTENER_ADDED -> "Consumer added";
                case LISTENER_REMOVED -> "Consumer removed";
            };
        }

        private String simplifyPageKey(String pageKey) {
            // Extract just symbol/timeframe from key like "CANDLES:BTCUSDT:1h:123456:789012"
            if (pageKey == null) return "";
            String[] parts = pageKey.split(":");
            if (parts.length >= 3) {
                return parts[1] + (parts.length > 3 && !parts[2].matches("\\d+") ? "/" + parts[2] : "");
            }
            return pageKey;
        }
    }

    // ========== Cell Renderer ==========

    private class LogTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            LogTableModel model = (LogTableModel) table.getModel();
            DownloadEvent event = model.getEventAt(row);

            if (!isSelected && event != null) {
                Color rowColor = getColorForEventType(event.eventType());
                if (column == 1) {
                    // Event type column gets full color
                    setForeground(rowColor);
                } else if (event.isError()) {
                    // Error rows get error color
                    setForeground(rowColor);
                } else {
                    setForeground(table.getForeground());
                }
            }

            // Alternating row background
            if (!isSelected) {
                if (row % 2 == 0) {
                    setBackground(table.getBackground());
                } else {
                    Color bg = table.getBackground();
                    setBackground(new Color(
                        Math.max(0, bg.getRed() - 10),
                        Math.max(0, bg.getGreen() - 10),
                        Math.max(0, bg.getBlue() - 10)
                    ));
                }
            }

            setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            return this;
        }

        private Color getColorForEventType(DownloadEvent.EventType type) {
            return switch (type) {
                case ERROR -> COLOR_ERROR;
                case LOAD_STARTED -> COLOR_LOAD_STARTED;
                case LOAD_COMPLETED -> COLOR_LOAD_COMPLETED;
                case UPDATE_STARTED, UPDATE_COMPLETED -> COLOR_UPDATE;
                case PAGE_CREATED, PAGE_RELEASED, LISTENER_ADDED, LISTENER_REMOVED -> COLOR_PAGE_LIFECYCLE;
            };
        }
    }
}
