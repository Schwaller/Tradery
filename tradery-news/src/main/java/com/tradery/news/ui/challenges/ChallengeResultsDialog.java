package com.tradery.news.ui.challenges;

import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.store.ChallengeStore;
import com.tradery.ui.controls.ThinSplitPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tradery.news.ui.challenges.ChallengeTheme.*;

/**
 * Unified results management dialog for a challenge.
 * Top: results table (select, delete, clear errors).
 * Bottom: detail of selected result — editable items table for structured lists,
 * read-only detail for structured single / text / list / error.
 */
public class ChallengeResultsDialog extends JDialog {

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Challenge challenge;
    private final ChallengeStore store;
    private final List<ChallengeResult> results;
    private final DefaultTableModel resultsTableModel;
    private final JTable resultsTable;
    private final JLabel headerLabel;
    private final JPanel detailPanel;
    private boolean changed = false;

    // Items editing state (for structured list results)
    private DefaultTableModel itemsTableModel;
    private JTable itemsTable;
    private List<Map<String, String>> itemsRowReasons;
    private ChallengeResult editingResult;

    public ChallengeResultsDialog(Window owner, Challenge challenge, ChallengeStore store) {
        super(owner, challenge.title() + " — Results", ModalityType.MODELESS);
        this.challenge = challenge;
        this.store = store;
        this.results = new ArrayList<>(store.getResultsForChallenge(challenge.id(), 200));

        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

        resultsTableModel = new DefaultTableModel(
            new String[]{"Time", "Tier", "Duration", "Signal", "Type", "Summary"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        for (ChallengeResult r : results) {
            resultsTableModel.addRow(resultToRow(r));
        }

        resultsTable = new JTable(resultsTableModel);
        headerLabel = new JLabel();
        detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBackground(bgMain());

        initUI();
        updateHeader();
        setMinimumSize(new Dimension(650, 450));
        setSize(new Dimension(800, 600));
        setLocationRelativeTo(owner);
    }

    public boolean wasChanged() { return changed; }

    private void initUI() {
        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBorder(new EmptyBorder(48, 12, 12, 12));
        setContentPane(content);

        // Header + buttons bar
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        topBar.add(headerLabel, BorderLayout.WEST);

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton deleteBtn = new JButton("Delete Selected");
        deleteBtn.addActionListener(e -> deleteSelected());
        actionBtns.add(deleteBtn);

        JButton clearErrorsBtn = new JButton("Clear Errors");
        clearErrorsBtn.addActionListener(e -> clearErrors());
        actionBtns.add(clearErrorsBtn);

        topBar.add(actionBtns, BorderLayout.EAST);
        content.add(topBar, BorderLayout.NORTH);

        // Results table
        resultsTable.setRowHeight(24);
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(110);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(50);
        resultsTable.getColumnModel().getColumn(5).setPreferredWidth(300);

        resultsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                                                           boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                if (row < results.size() && results.get(row).hasError()) {
                    c.setForeground(sel ? t.getSelectionForeground() : new Color(220, 80, 80));
                } else {
                    c.setForeground(sel ? t.getSelectionForeground() : t.getForeground());
                }
                return c;
            }
        });

        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = resultsTable.getSelectedRow();
                if (row >= 0 && row < results.size()) {
                    showDetail(results.get(row));
                } else {
                    clearDetail();
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(resultsTable);
        tableScroll.setPreferredSize(new Dimension(0, 180));

        // Detail panel in scroll
        JScrollPane detailScroll = new JScrollPane(detailPanel);
        detailScroll.setBorder(BorderFactory.createEmptyBorder());
        detailScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Split
        ThinSplitPane split = new ThinSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailScroll);
        split.setDividerLocation(200);
        split.setResizeWeight(0.35);
        content.add(split, BorderLayout.CENTER);

        // Bottom buttons
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(new EmptyBorder(8, 0, 0, 0));

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        // Items editing buttons (shown/hidden dynamically)
        JButton addItemBtn = new JButton("+ Add Item");
        addItemBtn.setVisible(false);
        addItemBtn.addActionListener(e -> addItem());
        leftBtns.add(addItemBtn);

        JButton markRemovedBtn = new JButton("Mark Removed");
        markRemovedBtn.setVisible(false);
        markRemovedBtn.addActionListener(e -> markItemsRemoved());
        leftBtns.add(markRemovedBtn);

        JButton saveItemsBtn = new JButton("Save Items");
        saveItemsBtn.setVisible(false);
        saveItemsBtn.addActionListener(e -> saveItems());
        leftBtns.add(saveItemsBtn);

        // Store refs for visibility toggling
        addItemBtn.putClientProperty("role", "items");
        markRemovedBtn.putClientProperty("role", "items");
        saveItemsBtn.putClientProperty("role", "items");

        bottomBar.add(leftBtns, BorderLayout.WEST);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightBtns.add(closeBtn);
        bottomBar.add(rightBtns, BorderLayout.EAST);

        content.add(bottomBar, BorderLayout.SOUTH);

        // Keep reference to items buttons panel
        detailPanel.putClientProperty("leftBtns", leftBtns);
    }

    private void showDetail(ChallengeResult result) {
        detailPanel.removeAll();
        editingResult = null;
        itemsTableModel = null;
        itemsTable = null;
        itemsRowReasons = null;
        setItemButtonsVisible(false);

        if (result.hasError()) {
            showErrorDetail(result);
        } else if (result.itemResults() != null && !result.itemResults().isEmpty()) {
            showItemsDetail(result);
        } else if (result.fields() != null && !result.fields().isEmpty()) {
            showFieldsDetail(result);
        } else if (result.textResult() != null) {
            showTextDetail(result);
        } else if (result.listResult() != null) {
            showListDetail(result);
        }

        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void clearDetail() {
        detailPanel.removeAll();
        setItemButtonsVisible(false);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    // ==================== Detail Renderers ====================

    private void showErrorDetail(ChallengeResult result) {
        JPanel p = makeDetailContent();
        addDetailLabel(p, "Error", new Font("SansSerif", Font.BOLD, 13), new Color(220, 60, 60));
        if (result.error() != null) {
            addDetailWrapped(p, result.error(), textSecondary());
        }
        addMetaFooter(p, result);
        detailPanel.add(p, BorderLayout.CENTER);
    }

    private void showFieldsDetail(ChallengeResult result) {
        List<ChallengeOutput.Field> fieldDefs = challenge.output().fields() != null
            ? challenge.output().fields() : List.of();

        JPanel p = makeDetailContent();

        for (Map.Entry<String, String> entry : result.fields().entrySet()) {
            String fieldName = entry.getKey();
            if (fieldName.endsWith("_reason")) continue;
            String val = entry.getValue();
            String reason = result.fields().get(fieldName + "_reason");

            String label = fieldName;
            for (ChallengeOutput.Field f : fieldDefs) {
                if (f.name().equals(fieldName)) {
                    label = f.label() != null ? f.label() : f.name();
                    break;
                }
            }

            p.add(Box.createVerticalStrut(8));
            addDetailLabel(p, label, new Font("SansSerif", Font.BOLD, 11), textMuted());

            boolean isNumber = false;
            try { Double.parseDouble(val); isNumber = true; } catch (NumberFormatException ignored) {}

            if (isNumber) {
                addDetailLabel(p, val, new Font("SansSerif", Font.BOLD, 16), textPrimary());
            } else {
                addDetailWrapped(p, val, textPrimary());
            }

            if (reason != null && !reason.isBlank()) {
                p.add(Box.createVerticalStrut(2));
                addDetailWrapped(p, reason, textMuted());
            }
        }

        addMetaFooter(p, result);
        detailPanel.add(p, BorderLayout.CENTER);
    }

    private void showItemsDetail(ChallengeResult result) {
        editingResult = result;
        List<ChallengeOutput.Field> fieldDefs = challenge.output().fields() != null
            ? challenge.output().fields() : List.of();

        // Build columns: fields + Status
        List<String> colNames = new ArrayList<>();
        for (ChallengeOutput.Field f : fieldDefs) {
            colNames.add(f.label() != null ? f.label() : f.name());
        }
        colNames.add("Status");

        itemsTableModel = new DefaultTableModel(colNames.toArray(new String[0]), 0);
        itemsRowReasons = new ArrayList<>();

        for (Map<String, String> item : result.itemResults()) {
            Object[] row = new Object[fieldDefs.size() + 1];
            Map<String, String> reasons = new LinkedHashMap<>();
            for (int i = 0; i < fieldDefs.size(); i++) {
                row[i] = item.getOrDefault(fieldDefs.get(i).name(), "");
                String reason = item.get(fieldDefs.get(i).name() + "_reason");
                if (reason != null) reasons.put(fieldDefs.get(i).name(), reason);
            }
            row[fieldDefs.size()] = item.getOrDefault("_status", "");
            itemsTableModel.addRow(row);
            itemsRowReasons.add(reasons);
        }

        itemsTable = new JTable(itemsTableModel);
        itemsTable.setRowHeight(24);
        itemsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Status column combo editor
        int statusCol = fieldDefs.size();
        itemsTable.getColumnModel().getColumn(statusCol).setCellEditor(
            new DefaultCellEditor(new JComboBox<>(new String[]{"", "removed"})));
        itemsTable.getColumnModel().getColumn(statusCol).setPreferredWidth(70);
        itemsTable.getColumnModel().getColumn(statusCol).setMaxWidth(90);

        // Renderer: strikethrough removed, reason tooltips
        itemsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
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
                String tip = null;
                if (col < fieldDefs.size() && row < itemsRowReasons.size()) {
                    tip = itemsRowReasons.get(row).get(fieldDefs.get(col).name());
                }
                setToolTipText(tip);
                return c;
            }
        });

        // Primary column wider
        for (int i = 0; i < fieldDefs.size(); i++) {
            if (fieldDefs.get(i).primary()) {
                itemsTable.getColumnModel().getColumn(i).setPreferredWidth(150);
            }
        }

        detailPanel.add(new JScrollPane(itemsTable), BorderLayout.CENTER);

        // Meta bar at bottom of detail
        JPanel metaBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        metaBar.setBackground(bgMain());
        metaBar.add(makeMetaLabel(result));
        detailPanel.add(metaBar, BorderLayout.SOUTH);

        setItemButtonsVisible(true);
    }

    private void showTextDetail(ChallengeResult result) {
        JPanel p = makeDetailContent();
        addDetailWrapped(p, result.textResult(), textPrimary());
        addMetaFooter(p, result);
        detailPanel.add(p, BorderLayout.CENTER);
    }

    private void showListDetail(ChallengeResult result) {
        JPanel p = makeDetailContent();
        for (String item : result.listResult()) {
            addDetailLabel(p, "\u2022 " + item, new Font("SansSerif", Font.PLAIN, 12), textPrimary());
        }
        addMetaFooter(p, result);
        detailPanel.add(p, BorderLayout.CENTER);
    }

    // ==================== Items Editing ====================

    private void addItem() {
        if (itemsTableModel == null) return;
        List<ChallengeOutput.Field> fieldDefs = challenge.output().fields() != null
            ? challenge.output().fields() : List.of();
        Object[] row = new Object[fieldDefs.size() + 1];
        for (int i = 0; i <= fieldDefs.size(); i++) row[i] = "";
        itemsTableModel.addRow(row);
        itemsRowReasons.add(new LinkedHashMap<>());
    }

    private void markItemsRemoved() {
        if (itemsTable == null) return;
        List<ChallengeOutput.Field> fieldDefs = challenge.output().fields() != null
            ? challenge.output().fields() : List.of();
        int statusCol = fieldDefs.size();
        for (int row : itemsTable.getSelectedRows()) {
            itemsTableModel.setValueAt("removed", row, statusCol);
        }
    }

    private void saveItems() {
        if (itemsTableModel == null || editingResult == null) return;
        List<ChallengeOutput.Field> fieldDefs = challenge.output().fields() != null
            ? challenge.output().fields() : List.of();

        List<Map<String, String>> items = new ArrayList<>();
        for (int r = 0; r < itemsTableModel.getRowCount(); r++) {
            Map<String, String> item = new LinkedHashMap<>();
            boolean hasData = false;
            for (int c = 0; c < fieldDefs.size(); c++) {
                String val = str(itemsTableModel.getValueAt(r, c));
                if (!val.isEmpty()) {
                    item.put(fieldDefs.get(c).name(), val);
                    hasData = true;
                }
                if (r < itemsRowReasons.size()) {
                    String reason = itemsRowReasons.get(r).get(fieldDefs.get(c).name());
                    if (reason != null) item.put(fieldDefs.get(c).name() + "_reason", reason);
                }
            }
            String status = str(itemsTableModel.getValueAt(r, fieldDefs.size()));
            if (!status.isEmpty()) item.put("_status", status);
            if (hasData) items.add(item);
        }

        ChallengeResult newResult = ChallengeResult.structuredList(
            challenge.id(), editingResult.subjectId(), 0, items, null, 0);
        newResult.setResolvedTier("manual");
        store.saveResult(newResult);

        // Add to local list and table
        results.add(newResult);
        resultsTableModel.addRow(resultToRow(newResult));
        changed = true;
        updateHeader();

        // Select the new row
        int newRow = results.size() - 1;
        resultsTable.setRowSelectionInterval(newRow, newRow);
        resultsTable.scrollRectToVisible(resultsTable.getCellRect(newRow, 0, true));
    }

    // ==================== Result Table Actions ====================

    private void deleteSelected() {
        int[] rows = resultsTable.getSelectedRows();
        if (rows.length == 0) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete " + rows.length + " result(s)?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        for (int i = rows.length - 1; i >= 0; i--) {
            int row = rows[i];
            if (row < results.size()) {
                store.deleteResult(results.get(row).id());
                results.remove(row);
                resultsTableModel.removeRow(row);
            }
        }
        changed = true;
        updateHeader();
        clearDetail();
    }

    private void clearErrors() {
        List<Integer> errorRows = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).hasError()) errorRows.add(i);
        }
        if (errorRows.isEmpty()) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete " + errorRows.size() + " error result(s)?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        for (int i = errorRows.size() - 1; i >= 0; i--) {
            int row = errorRows.get(i);
            store.deleteResult(results.get(row).id());
            results.remove(row);
            resultsTableModel.removeRow(row);
        }
        changed = true;
        updateHeader();
        clearDetail();
    }

    // ==================== Helpers ====================

    private void updateHeader() {
        headerLabel.setText(challenge.title() + " — " + results.size() + " results");
    }

    private void setItemButtonsVisible(boolean visible) {
        JPanel leftBtns = (JPanel) detailPanel.getClientProperty("leftBtns");
        if (leftBtns == null) return;
        for (Component c : leftBtns.getComponents()) {
            if (c instanceof JButton btn && "items".equals(btn.getClientProperty("role"))) {
                btn.setVisible(visible);
            }
        }
    }

    private JPanel makeDetailContent() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(bgMain());
        p.setBorder(new EmptyBorder(8, 8, 8, 8));
        return p;
    }

    private void addDetailLabel(JPanel p, String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label);
    }

    private void addDetailWrapped(JPanel p, String text, Color color) {
        JLabel label = new JLabel("<html><body style='width:500px'>" + escapeHtml(text) + "</body></html>");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label);
    }

    private void addMetaFooter(JPanel p, ChallengeResult result) {
        p.add(Box.createVerticalStrut(12));
        JSeparator sep = new JSeparator();
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        p.add(sep);
        p.add(Box.createVerticalStrut(4));

        JLabel meta = makeMetaLabel(result);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(meta);
    }

    private JLabel makeMetaLabel(ChallengeResult result) {
        String time = TIME_FMT.format(Instant.ofEpochMilli(result.timestamp()));
        StringBuilder sb = new StringBuilder(time);
        if (result.durationMs() > 0) sb.append("  ·  ").append(formatDuration(result.durationMs()));
        if (result.resolvedTier() != null) sb.append("  ·  ").append(result.resolvedTier());
        if (result.verified()) sb.append("  ·  verified");
        if (result.hasSignal()) sb.append("  ·  signal: ").append(String.format("%.1f", result.signalValue()));

        JLabel label = new JLabel(sb.toString());
        label.setFont(new Font("SansSerif", Font.PLAIN, 10));
        label.setForeground(textMuted());
        return label;
    }

    private Object[] resultToRow(ChallengeResult r) {
        String time = TIME_FMT.format(Instant.ofEpochMilli(r.timestamp()));
        String tier = r.resolvedTier() != null ? r.resolvedTier() : "";
        String duration = r.durationMs() > 0 ? formatDuration(r.durationMs()) : "";
        String signal = r.hasSignal() ? String.format("%.1f", r.signalValue()) : "";
        String type = r.hasError() ? "ERROR" : (r.outputType() != null ? r.outputType().name() : "");
        String summary = summarize(r);
        return new Object[]{time, tier, duration, signal, type, summary};
    }

    private static String summarize(ChallengeResult r) {
        if (r.hasError()) return r.error();
        if (r.fields() != null && !r.fields().isEmpty()) {
            for (var entry : r.fields().entrySet()) {
                if (!entry.getKey().endsWith("_reason")) {
                    String v = entry.getValue();
                    return v.length() > 80 ? v.substring(0, 77) + "..." : v;
                }
            }
        }
        if (r.itemResults() != null) return r.itemResults().size() + " items";
        if (r.textResult() != null) {
            String t = r.textResult();
            return t.length() > 80 ? t.substring(0, 77) + "..." : t;
        }
        if (r.listResult() != null) return r.listResult().size() + " items";
        return "";
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        return String.format("%.1fs", ms / 1000.0);
    }

    private static String str(Object o) {
        return o != null ? o.toString().trim() : "";
    }
}
