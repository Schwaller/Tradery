package com.tradery.ui;

import com.tradery.data.DataConsumer;
import com.tradery.data.DataRequirement;
import com.tradery.data.DataRequirementsTracker;
import com.tradery.data.DataRequirementsTracker.RequirementState;
import com.tradery.data.DataRequirementsTracker.Status;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Always-on-top window showing data loading status grouped by consumer.
 * Displays progress for each data type (OHLC, AggTrades, Funding, OI) per consumer
 * (Backtest, Charts, Phase Preview, Hoop Preview).
 */
public class DataLoadingStatusWindow extends JDialog {

    private final List<DataRequirementsTracker> trackers = new java.util.ArrayList<>();
    private final JPanel contentPanel;
    private final Map<DataConsumer, ConsumerPanel> consumerPanels = new HashMap<>();
    private Timer refreshTimer;

    // Colors for status indicators
    private static final Color COLOR_PENDING = new Color(150, 150, 150);
    private static final Color COLOR_LOADING = new Color(100, 100, 180);
    private static final Color COLOR_READY = new Color(60, 140, 60);
    private static final Color COLOR_ERROR = new Color(180, 80, 80);

    public DataLoadingStatusWindow(Window owner, DataRequirementsTracker... trackerArray) {
        super(owner, "Data Loading Status", ModalityType.MODELESS);

        // Add all provided trackers
        for (DataRequirementsTracker t : trackerArray) {
            if (t != null) {
                trackers.add(t);
            }
        }

        setAlwaysOnTop(true);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        setResizable(false);

        // Content panel with vertical box layout
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        add(contentPanel);
        pack();

        // Start refresh timer
        refreshTimer = new Timer(250, e -> refresh());
        refreshTimer.start();

        // Wire up tracker callbacks for real-time updates
        for (DataRequirementsTracker t : trackers) {
            t.setOnStatusChange(state -> SwingUtilities.invokeLater(this::refresh));
        }
    }

    /**
     * Refresh the display based on current tracker state.
     */
    public void refresh() {
        // Aggregate states from all trackers
        Map<DataConsumer, List<RequirementState>> statesByConsumer = new java.util.EnumMap<>(DataConsumer.class);
        for (DataRequirementsTracker tracker : trackers) {
            Map<DataConsumer, List<RequirementState>> trackerStates = tracker.getStatesByConsumer();
            for (Map.Entry<DataConsumer, List<RequirementState>> entry : trackerStates.entrySet()) {
                statesByConsumer.computeIfAbsent(entry.getKey(), k -> new java.util.ArrayList<>())
                    .addAll(entry.getValue());
            }
        }

        // Update or create panels for each consumer
        for (DataConsumer consumer : DataConsumer.values()) {
            List<RequirementState> states = statesByConsumer.get(consumer);
            ConsumerPanel panel = consumerPanels.get(consumer);

            if (states == null || states.isEmpty()) {
                // Remove panel if no requirements for this consumer
                if (panel != null) {
                    contentPanel.remove(panel);
                    consumerPanels.remove(consumer);
                }
            } else {
                // Create panel if needed
                if (panel == null) {
                    panel = new ConsumerPanel(consumer);
                    consumerPanels.put(consumer, panel);
                    contentPanel.add(panel);
                    contentPanel.add(Box.createVerticalStrut(8));
                }
                // Update with current states
                panel.update(states);
            }
        }

        // Show "no active data loading" message if empty
        if (consumerPanels.isEmpty()) {
            if (contentPanel.getComponentCount() == 0) {
                JLabel emptyLabel = new JLabel("No active data loading");
                emptyLabel.setForeground(Color.GRAY);
                emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                contentPanel.add(emptyLabel);
            }
        } else {
            // Remove empty label if we have consumers
            for (Component comp : contentPanel.getComponents()) {
                if (comp instanceof JLabel && "No active data loading".equals(((JLabel) comp).getText())) {
                    contentPanel.remove(comp);
                    break;
                }
            }
        }

        contentPanel.revalidate();
        contentPanel.repaint();
        pack();
    }

    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        super.dispose();
    }

    /**
     * Panel displaying data requirements for a single consumer.
     */
    private class ConsumerPanel extends JPanel {
        private final DataConsumer consumer;
        private final JLabel headerLabel;
        private final JLabel statusLabel;
        private final JPanel dataTypesPanel;
        private final Map<String, DataTypeRow> dataTypeRows = new HashMap<>();

        ConsumerPanel(DataConsumer consumer) {
            this.consumer = consumer;
            setLayout(new BorderLayout(0, 4));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)),
                new EmptyBorder(8, 8, 8, 8)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

            // Header with consumer name and overall status
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setOpaque(false);

            headerLabel = new JLabel(consumer.getDisplayName());
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 12f));

            statusLabel = new JLabel("Ready");
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 10f));
            statusLabel.setForeground(COLOR_READY);

            headerPanel.add(headerLabel, BorderLayout.WEST);
            headerPanel.add(statusLabel, BorderLayout.EAST);

            add(headerPanel, BorderLayout.NORTH);

            // Data types panel
            dataTypesPanel = new JPanel();
            dataTypesPanel.setLayout(new BoxLayout(dataTypesPanel, BoxLayout.Y_AXIS));
            dataTypesPanel.setOpaque(false);

            add(dataTypesPanel, BorderLayout.CENTER);
        }

        void update(List<RequirementState> states) {
            // Calculate overall status
            boolean anyLoading = false;
            boolean anyError = false;
            boolean allReady = true;

            for (RequirementState state : states) {
                if (state.status() == Status.FETCHING || state.status() == Status.CHECKING) {
                    anyLoading = true;
                    allReady = false;
                } else if (state.status() == Status.ERROR) {
                    anyError = true;
                    allReady = false;
                } else if (state.status() == Status.PENDING) {
                    allReady = false;
                }
            }

            // Update overall status label
            if (anyLoading) {
                statusLabel.setText("Loading...");
                statusLabel.setForeground(COLOR_LOADING);
            } else if (anyError) {
                statusLabel.setText("Error");
                statusLabel.setForeground(COLOR_ERROR);
            } else if (allReady) {
                statusLabel.setText("Ready");
                statusLabel.setForeground(COLOR_READY);
            } else {
                statusLabel.setText("Pending");
                statusLabel.setForeground(COLOR_PENDING);
            }

            // Update or create rows for each data type
            for (RequirementState state : states) {
                String dataType = state.requirement().dataType();
                DataTypeRow row = dataTypeRows.get(dataType);

                if (row == null) {
                    row = new DataTypeRow(dataType);
                    dataTypeRows.put(dataType, row);
                    dataTypesPanel.add(row);
                    dataTypesPanel.add(Box.createVerticalStrut(4));
                }

                row.update(state);
            }

            // Remove rows for data types no longer in states
            java.util.Set<String> currentTypes = new java.util.HashSet<>();
            for (RequirementState state : states) {
                currentTypes.add(state.requirement().dataType());
            }
            dataTypeRows.keySet().removeIf(type -> {
                if (!currentTypes.contains(type)) {
                    DataTypeRow row = dataTypeRows.get(type);
                    dataTypesPanel.remove(row);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Row displaying status for a single data type.
     */
    private class DataTypeRow extends JPanel {
        private final String dataType;
        private final JLabel nameLabel;
        private final JProgressBar progressBar;
        private final JLabel statusLabel;

        DataTypeRow(String dataType) {
            this.dataType = dataType;
            setLayout(new BorderLayout(8, 0));
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

            // Data type name (show base type without timeframe suffix for readability)
            String displayName = dataType;
            int colonIndex = dataType.indexOf(':');
            if (colonIndex > 0) {
                displayName = dataType.substring(0, colonIndex) + " (" + dataType.substring(colonIndex + 1) + ")";
            }

            nameLabel = new JLabel(displayName);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));
            nameLabel.setPreferredSize(new Dimension(100, 20));

            // Progress bar
            progressBar = new JProgressBar(0, 100);
            progressBar.setPreferredSize(new Dimension(120, 14));
            progressBar.setStringPainted(true);
            progressBar.setFont(progressBar.getFont().deriveFont(9f));

            // Status icon/text
            statusLabel = new JLabel();
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 10f));
            statusLabel.setPreferredSize(new Dimension(60, 20));
            statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            add(nameLabel, BorderLayout.WEST);
            add(progressBar, BorderLayout.CENTER);
            add(statusLabel, BorderLayout.EAST);
        }

        void update(RequirementState state) {
            int percent = state.progressPercent();
            progressBar.setValue(percent);

            switch (state.status()) {
                case PENDING -> {
                    progressBar.setString("Pending");
                    progressBar.setValue(0);
                    statusLabel.setText("queued");
                    statusLabel.setForeground(COLOR_PENDING);
                }
                case CHECKING -> {
                    progressBar.setString("Checking...");
                    progressBar.setIndeterminate(true);
                    statusLabel.setText("");
                    statusLabel.setForeground(COLOR_LOADING);
                }
                case FETCHING -> {
                    progressBar.setIndeterminate(false);
                    if (state.expected() > 0) {
                        progressBar.setString(state.loaded() + "/" + state.expected());
                    } else {
                        progressBar.setString("Loading...");
                    }
                    statusLabel.setText(percent + "%");
                    statusLabel.setForeground(COLOR_LOADING);
                }
                case READY -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Ready");
                    statusLabel.setText("\u2713"); // Checkmark
                    statusLabel.setForeground(COLOR_READY);
                }
                case ERROR -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    String msg = state.message() != null ? state.message() : "Error";
                    progressBar.setString(msg.length() > 20 ? msg.substring(0, 17) + "..." : msg);
                    statusLabel.setText("\u2717"); // X mark
                    statusLabel.setForeground(COLOR_ERROR);
                    setToolTipText(state.message());
                }
            }
        }
    }

    /**
     * Show the window positioned relative to the given component.
     */
    public void showNear(Component component) {
        if (component != null) {
            Point location = component.getLocationOnScreen();
            // Position above and to the left of the component
            setLocation(
                Math.max(0, location.x - getWidth() + component.getWidth()),
                Math.max(0, location.y - getHeight() - 5)
            );
        }
        setVisible(true);
        refresh();
    }
}
