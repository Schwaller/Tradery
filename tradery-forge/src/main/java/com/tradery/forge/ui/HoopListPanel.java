package com.tradery.forge.ui;

import com.tradery.core.model.Hoop;
import com.tradery.ui.controls.BorderlessTable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel for editing a list of hoops within a pattern.
 * Provides a table view with add/remove/move buttons.
 */
public class HoopListPanel extends JPanel {

    private JTable hoopTable;
    private HoopTableModel tableModel;
    private JButton addButton;
    private JButton removeButton;
    private JButton moveUpButton;
    private JButton moveDownButton;

    private Runnable onChange;

    public HoopListPanel() {
        setLayout(new BorderLayout(0, 4));
        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        tableModel = new HoopTableModel();
        hoopTable = new BorderlessTable(tableModel);
        hoopTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hoopTable.setRowHeight(24);
        hoopTable.getTableHeader().setReorderingAllowed(false);

        // Set column widths
        hoopTable.getColumnModel().getColumn(0).setPreferredWidth(100); // Name
        hoopTable.getColumnModel().getColumn(1).setPreferredWidth(60);  // Min %
        hoopTable.getColumnModel().getColumn(2).setPreferredWidth(60);  // Max %
        hoopTable.getColumnModel().getColumn(3).setPreferredWidth(60);  // Distance
        hoopTable.getColumnModel().getColumn(4).setPreferredWidth(60);  // Tolerance
        hoopTable.getColumnModel().getColumn(5).setPreferredWidth(100); // Anchor Mode

        // Centered numeric columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        hoopTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        hoopTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        hoopTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        hoopTable.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);

        // Anchor mode combo box editor
        JComboBox<Hoop.AnchorMode> anchorCombo = new JComboBox<>(Hoop.AnchorMode.values());
        hoopTable.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(anchorCombo));

        // Selection listener
        hoopTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonStates();
            }
        });

        // Buttons
        addButton = new JButton("+");
        addButton.setToolTipText("Add hoop");
        addButton.addActionListener(e -> addHoop());

        removeButton = new JButton("−");
        removeButton.setToolTipText("Remove hoop");
        removeButton.addActionListener(e -> removeHoop());
        removeButton.setEnabled(false);

        moveUpButton = new JButton("↑");
        moveUpButton.setToolTipText("Move up");
        moveUpButton.addActionListener(e -> moveHoopUp());
        moveUpButton.setEnabled(false);

        moveDownButton = new JButton("↓");
        moveDownButton.setToolTipText("Move down");
        moveDownButton.addActionListener(e -> moveHoopDown());
        moveDownButton.setEnabled(false);
    }

    private void layoutComponents() {
        JScrollPane scrollPane = new JScrollPane(hoopTable);
        scrollPane.setPreferredSize(new Dimension(0, 150));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(moveUpButton);
        buttonPanel.add(moveDownButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void updateButtonStates() {
        int selected = hoopTable.getSelectedRow();
        int count = tableModel.getRowCount();

        removeButton.setEnabled(selected >= 0);
        moveUpButton.setEnabled(selected > 0);
        moveDownButton.setEnabled(selected >= 0 && selected < count - 1);
    }

    private void addHoop() {
        int count = tableModel.getRowCount();
        String name = "hoop-" + (count + 1);
        Hoop newHoop = new Hoop(name, -2.0, 2.0, 5, 2, Hoop.AnchorMode.ACTUAL_HIT);
        tableModel.addHoop(newHoop);
        hoopTable.setRowSelectionInterval(count, count);
        fireChange();
    }

    private void removeHoop() {
        int selected = hoopTable.getSelectedRow();
        if (selected >= 0) {
            tableModel.removeHoop(selected);
            if (tableModel.getRowCount() > 0) {
                int newSelection = Math.min(selected, tableModel.getRowCount() - 1);
                hoopTable.setRowSelectionInterval(newSelection, newSelection);
            }
            fireChange();
        }
    }

    private void moveHoopUp() {
        int selected = hoopTable.getSelectedRow();
        if (selected > 0) {
            tableModel.moveHoop(selected, selected - 1);
            hoopTable.setRowSelectionInterval(selected - 1, selected - 1);
            fireChange();
        }
    }

    private void moveHoopDown() {
        int selected = hoopTable.getSelectedRow();
        if (selected >= 0 && selected < tableModel.getRowCount() - 1) {
            tableModel.moveHoop(selected, selected + 1);
            hoopTable.setRowSelectionInterval(selected + 1, selected + 1);
            fireChange();
        }
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void setHoops(List<Hoop> hoops) {
        tableModel.setHoops(hoops);
        updateButtonStates();
    }

    public List<Hoop> getHoops() {
        return tableModel.getHoops();
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        hoopTable.setEnabled(enabled);
        addButton.setEnabled(enabled);
        if (!enabled) {
            removeButton.setEnabled(false);
            moveUpButton.setEnabled(false);
            moveDownButton.setEnabled(false);
        } else {
            updateButtonStates();
        }
    }

    /**
     * Table model for the hoop list.
     */
    private class HoopTableModel extends AbstractTableModel {
        private final List<Hoop> hoops = new ArrayList<>();
        private final String[] columnNames = {"Name", "Min %", "Max %", "Distance", "Tolerance", "Anchor Mode"};

        @Override
        public int getRowCount() {
            return hoops.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> String.class;
                case 1, 2 -> Double.class;
                case 3, 4 -> Integer.class;
                case 5 -> Hoop.AnchorMode.class;
                default -> Object.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Hoop hoop = hoops.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> hoop.name();
                case 1 -> hoop.minPricePercent();
                case 2 -> hoop.maxPricePercent();
                case 3 -> hoop.distance();
                case 4 -> hoop.tolerance();
                case 5 -> hoop.anchorMode();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            Hoop old = hoops.get(rowIndex);
            Hoop updated = switch (columnIndex) {
                case 0 -> new Hoop((String) value, old.minPricePercent(), old.maxPricePercent(),
                    old.distance(), old.tolerance(), old.anchorMode());
                case 1 -> new Hoop(old.name(), parseDouble(value), old.maxPricePercent(),
                    old.distance(), old.tolerance(), old.anchorMode());
                case 2 -> new Hoop(old.name(), old.minPricePercent(), parseDouble(value),
                    old.distance(), old.tolerance(), old.anchorMode());
                case 3 -> new Hoop(old.name(), old.minPricePercent(), old.maxPricePercent(),
                    parseInt(value, 1), old.tolerance(), old.anchorMode());
                case 4 -> new Hoop(old.name(), old.minPricePercent(), old.maxPricePercent(),
                    old.distance(), parseInt(value, 0), old.anchorMode());
                case 5 -> new Hoop(old.name(), old.minPricePercent(), old.maxPricePercent(),
                    old.distance(), old.tolerance(), (Hoop.AnchorMode) value);
                default -> old;
            };
            hoops.set(rowIndex, updated);
            fireTableCellUpdated(rowIndex, columnIndex);
            fireChange();
        }

        private Double parseDouble(Object value) {
            if (value == null) return null;
            if (value instanceof Double) return (Double) value;
            try {
                String s = value.toString().trim();
                if (s.isEmpty()) return null;
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private int parseInt(Object value, int defaultVal) {
            if (value == null) return defaultVal;
            if (value instanceof Integer) return (Integer) value;
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }

        public void setHoops(List<Hoop> newHoops) {
            hoops.clear();
            if (newHoops != null) {
                hoops.addAll(newHoops);
            }
            fireTableDataChanged();
        }

        public List<Hoop> getHoops() {
            return new ArrayList<>(hoops);
        }

        public void addHoop(Hoop hoop) {
            hoops.add(hoop);
            fireTableRowsInserted(hoops.size() - 1, hoops.size() - 1);
        }

        public void removeHoop(int index) {
            hoops.remove(index);
            fireTableRowsDeleted(index, index);
        }

        public void moveHoop(int from, int to) {
            Hoop hoop = hoops.remove(from);
            hoops.add(to, hoop);
            fireTableDataChanged();
        }
    }
}
