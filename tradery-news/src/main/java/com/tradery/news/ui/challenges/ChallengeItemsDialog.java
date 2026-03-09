package com.tradery.news.ui.challenges;

import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.store.ChallengeStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Editable table of collected items from a structured list challenge.
 * Edits are saved as a new result so the forward-feed picks them up.
 */
public class ChallengeItemsDialog extends JDialog {

    private final Challenge challenge;
    private final ChallengeStore store;
    private final String subjectId;
    private final List<ChallengeOutput.Field> fieldDefs;
    private final DefaultTableModel tableModel;
    /** Per-row map of field_name -> reason text (parallel to tableModel rows). */
    private final List<Map<String, String>> rowReasons = new ArrayList<>();
    private boolean changed = false;

    public ChallengeItemsDialog(Window owner, Challenge challenge, ChallengeStore store, String subjectId) {
        super(owner, challenge.title() + " — Items", ModalityType.MODELESS);
        this.challenge = challenge;
        this.store = store;
        this.subjectId = subjectId;
        this.fieldDefs = challenge.output().fields() != null ? challenge.output().fields() : List.of();

        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

        // Build column names: field labels + status
        List<String> colNames = new ArrayList<>();
        for (ChallengeOutput.Field f : fieldDefs) {
            colNames.add(f.label() != null ? f.label() : f.name());
        }
        colNames.add("Status");

        tableModel = new DefaultTableModel(colNames.toArray(new String[0]), 0);

        // Load latest result items
        ChallengeResult latest = store.getLatestResult(challenge.id(), subjectId);
        if (latest != null && latest.itemResults() != null) {
            for (Map<String, String> item : latest.itemResults()) {
                Object[] row = new Object[fieldDefs.size() + 1];
                Map<String, String> reasons = new LinkedHashMap<>();
                for (int i = 0; i < fieldDefs.size(); i++) {
                    row[i] = item.getOrDefault(fieldDefs.get(i).name(), "");
                    String reason = item.get(fieldDefs.get(i).name() + "_reason");
                    if (reason != null) reasons.put(fieldDefs.get(i).name(), reason);
                }
                row[fieldDefs.size()] = item.getOrDefault("_status", "");
                tableModel.addRow(row);
                rowReasons.add(reasons);
            }
        }

        initUI();
        setMinimumSize(new Dimension(500, 350));
        setSize(new Dimension(700, 450));
        setLocationRelativeTo(owner);
    }

    public boolean wasChanged() { return changed; }

    private void initUI() {
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(52, 12, 12, 12));
        setContentPane(content);

        // Header
        JLabel header = new JLabel(challenge.title() + " — " + tableModel.getRowCount() + " items");
        header.setFont(new Font("SansSerif", Font.BOLD, 13));
        content.add(header, BorderLayout.NORTH);

        // Table
        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Status column gets a combo editor
        int statusCol = fieldDefs.size();
        table.getColumnModel().getColumn(statusCol).setCellEditor(
            new DefaultCellEditor(new JComboBox<>(new String[]{"", "removed"})));
        table.getColumnModel().getColumn(statusCol).setPreferredWidth(70);
        table.getColumnModel().getColumn(statusCol).setMaxWidth(90);

        // Strikethrough renderer for removed rows + reason tooltips
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                                                           boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                String status = str(t.getModel().getValueAt(row, statusCol));
                if ("removed".equals(status)) {
                    c.setForeground(UIManager.getColor("Label.disabledForeground"));
                    setFont(getFont().deriveFont(Font.ITALIC));
                } else {
                    c.setForeground(sel ? t.getSelectionForeground() : t.getForeground());
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                // Show _reason as tooltip on field cells
                String tip = null;
                if (col < fieldDefs.size() && row < rowReasons.size()) {
                    tip = rowReasons.get(row).get(fieldDefs.get(col).name());
                }
                setToolTipText(tip);
                return c;
            }
        });

        // Primary field column wider
        for (int i = 0; i < fieldDefs.size(); i++) {
            if (fieldDefs.get(i).primary()) {
                table.getColumnModel().getColumn(i).setPreferredWidth(150);
            }
        }

        content.add(new JScrollPane(table), BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new BorderLayout());

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addBtn = new JButton("+ Add");
        addBtn.addActionListener(e -> {
            Object[] row = new Object[fieldDefs.size() + 1];
            for (int i = 0; i < fieldDefs.size(); i++) row[i] = "";
            row[fieldDefs.size()] = "";
            tableModel.addRow(row);
            rowReasons.add(new LinkedHashMap<>());
        });
        leftBtns.add(addBtn);

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.addActionListener(e -> {
            int[] rows = table.getSelectedRows();
            for (int i = rows.length - 1; i >= 0; i--) {
                tableModel.removeRow(rows[i]);
                if (rows[i] < rowReasons.size()) rowReasons.remove(rows[i]);
            }
        });
        leftBtns.add(deleteBtn);

        JButton markRemovedBtn = new JButton("Mark Removed");
        markRemovedBtn.addActionListener(e -> {
            for (int row : table.getSelectedRows()) {
                tableModel.setValueAt("removed", row, statusCol);
            }
        });
        leftBtns.add(markRemovedBtn);

        buttons.add(leftBtns, BorderLayout.WEST);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        rightBtns.add(cancelBtn);

        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> save());
        rightBtns.add(saveBtn);
        getRootPane().setDefaultButton(saveBtn);

        buttons.add(rightBtns, BorderLayout.EAST);
        content.add(buttons, BorderLayout.SOUTH);
    }

    private void save() {
        // Build item maps from table
        List<Map<String, String>> items = new ArrayList<>();
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            Map<String, String> item = new LinkedHashMap<>();
            boolean hasData = false;
            for (int c = 0; c < fieldDefs.size(); c++) {
                String val = str(tableModel.getValueAt(r, c));
                if (!val.isEmpty()) {
                    item.put(fieldDefs.get(c).name(), val);
                    hasData = true;
                }
                // Preserve _reason from original data
                if (r < rowReasons.size()) {
                    String reason = rowReasons.get(r).get(fieldDefs.get(c).name());
                    if (reason != null) item.put(fieldDefs.get(c).name() + "_reason", reason);
                }
            }
            String status = str(tableModel.getValueAt(r, fieldDefs.size()));
            if (!status.isEmpty()) {
                item.put("_status", status);
            }
            if (hasData) items.add(item);
        }

        // Save as a new manual result
        ChallengeResult result = ChallengeResult.structuredList(
            challenge.id(), subjectId, 0, items, null, 0);
        result.setResolvedTier("manual");
        store.saveResult(result);
        changed = true;
        dispose();
    }

    private static String str(Object o) {
        return o != null ? o.toString().trim() : "";
    }
}
