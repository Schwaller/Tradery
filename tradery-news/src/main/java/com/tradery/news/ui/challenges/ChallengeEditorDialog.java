package com.tradery.news.ui.challenges;

import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeEscalation;
import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.store.ChallengeStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Modal dialog for editing or creating an AI challenge.
 */
public class ChallengeEditorDialog extends JDialog {

    private final ChallengeStore store;
    private final Challenge challenge;
    private final boolean isNew;

    private static final String OUTPUT_TEXT = "Text";
    private static final String OUTPUT_STRUCTURED = "Structured";
    private static final String OUTPUT_STRUCTURED_LIST = "Structured List";

    private JTextField titleField;
    private JTextArea descriptionArea;
    private JComboBox<String> outputTypeCombo;
    private JComboBox<String> listBehaviorCombo;
    private JLabel listBehaviorLabel;
    private JComboBox<String> reasonDetailCombo;
    private JLabel reasonDetailLabel;
    private JSpinner orderSpinner;
    private JCheckBox enabledCheck;
    private JComboBox<String> refreshCombo;
    private JComboBox<String> tierCombo;
    private JCheckBox verifyCheck;

    // Structured fields table
    private DefaultTableModel fieldsTableModel;
    private JPanel fieldsPanel;

    private boolean saved = false;

    public ChallengeEditorDialog(Window owner, ChallengeStore store, Challenge challenge) {
        super(owner, challenge == null ? "New Challenge" : "Edit Challenge", ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.isNew = challenge == null;
        this.challenge = challenge != null ? challenge : new Challenge();

        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

        initUI();
        pack();
        setMinimumSize(new Dimension(520, 560));
        setLocationRelativeTo(owner);
    }

    public boolean wasSaved() { return saved; }

    private void initUI() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        setContentPane(contentPane);

        // Header bar
        JPanel hBar = new JPanel(new BorderLayout());
        hBar.setPreferredSize(new Dimension(0, 52));
        JLabel windowTitle = new JLabel(isNew ? "New Challenge" : "Edit Challenge", SwingConstants.CENTER);
        windowTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        windowTitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        hBar.add(windowTitle, BorderLayout.CENTER);
        JPanel hWrapper = new JPanel(new BorderLayout());
        hWrapper.add(hBar, BorderLayout.CENTER);
        hWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        contentPane.add(hWrapper, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(15, 15, 15, 15));
        contentPane.add(content, BorderLayout.CENTER);

        // Form fields
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.NORTHWEST;
        lc.insets = new Insets(5, 5, 5, 10);
        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(5, 0, 5, 5);

        int row = 0;

        // Title
        lc.gridx = 0; lc.gridy = row;
        form.add(new JLabel("Title:"), lc);
        fc.gridx = 1; fc.gridy = row++;
        titleField = new JTextField(challenge.title() != null ? challenge.title() : "", 25);
        form.add(titleField, fc);

        // Description
        lc.gridx = 0; lc.gridy = row;
        form.add(new JLabel("Description:"), lc);
        fc.gridx = 1; fc.gridy = row++;
        descriptionArea = new JTextArea(challenge.description() != null ? challenge.description() : "", 3, 25);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(0, 60));
        form.add(descScroll, fc);

        // Output type
        lc.gridx = 0; lc.gridy = row;
        form.add(new JLabel("Output:"), lc);
        fc.gridx = 1; fc.gridy = row++;
        outputTypeCombo = new JComboBox<>(new String[]{OUTPUT_TEXT, OUTPUT_STRUCTURED, OUTPUT_STRUCTURED_LIST});
        outputTypeCombo.setSelectedItem(outputTypeToLabel(challenge.output()));
        outputTypeCombo.addActionListener(e -> updateFieldsPanelVisibility());
        form.add(outputTypeCombo, fc);

        // List behavior (only visible for Structured List)
        lc.gridx = 0; lc.gridy = row;
        listBehaviorLabel = new JLabel("Behavior:");
        form.add(listBehaviorLabel, lc);
        fc.gridx = 1; fc.gridy = row++;
        listBehaviorCombo = new JComboBox<>(new String[]{"Tracking", "Snapshot"});
        listBehaviorCombo.setSelectedItem(
            challenge.output().listBehavior() == ChallengeOutput.ListBehavior.SNAPSHOT ? "Snapshot" : "Tracking");
        listBehaviorCombo.setToolTipText(
            "<html><b>Tracking</b> — entities persist across runs, temporal charts, removed tracking<br>"
            + "<b>Snapshot</b> — fresh list each run, no charts, no entity tracking</html>");
        form.add(listBehaviorCombo, fc);

        // Reason detail (only visible for Structured/Structured List)
        lc.gridx = 0; lc.gridy = row;
        reasonDetailLabel = new JLabel("Justification:");
        form.add(reasonDetailLabel, lc);
        fc.gridx = 1; fc.gridy = row++;
        reasonDetailCombo = new JComboBox<>(new String[]{"None", "Brief", "Detailed", "Verbose"});
        reasonDetailCombo.setSelectedItem(reasonDetailToLabel(challenge.output().reasonDetail()));
        reasonDetailCombo.setToolTipText(
            "<html><b>None</b> — no justification for numeric values<br>"
            + "<b>Brief</b> — one sentence per score<br>"
            + "<b>Detailed</b> — a few sentences with evidence<br>"
            + "<b>Verbose</b> — thorough analysis with data points</html>");
        form.add(reasonDetailCombo, fc);

        // Tier
        ChallengeEscalation currentEsc = challenge.escalations().isEmpty()
            ? null : challenge.escalations().getFirst();
        lc.gridx = 0; lc.gridy = row;
        form.add(new JLabel("Tier:"), lc);
        fc.gridx = 1; fc.gridy = row++;
        JPanel tierRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tierCombo = new JComboBox<>(new String[]{"fast", "standard", "premium"});
        tierCombo.setSelectedItem(currentEsc != null ? currentEsc.tier() : "standard");
        tierRow.add(tierCombo);
        verifyCheck = new JCheckBox("Verify", currentEsc != null && currentEsc.verify());
        tierRow.add(verifyCheck);
        form.add(tierRow, fc);

        // Refresh interval
        lc.gridx = 0; lc.gridy = row;
        form.add(new JLabel("Refresh:"), lc);
        fc.gridx = 1; fc.gridy = row++;
        String[] refreshOptions = {"None", "1 hour", "4 hours", "12 hours", "1 day", "3 days", "7 days"};
        refreshCombo = new JComboBox<>(refreshOptions);
        refreshCombo.setSelectedItem(durationToLabel(challenge.refreshInterval()));
        form.add(refreshCombo, fc);

        // Display order + enabled
        lc.gridx = 0; lc.gridy = row;
        form.add(new JLabel("Order:"), lc);
        fc.gridx = 1; fc.gridy = row++;
        JPanel orderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        orderSpinner = new JSpinner(new SpinnerNumberModel(challenge.displayOrder(), 0, 999, 1));
        orderSpinner.setPreferredSize(new Dimension(60, 24));
        orderRow.add(orderSpinner);
        enabledCheck = new JCheckBox("Enabled", challenge.enabled());
        orderRow.add(enabledCheck);
        form.add(orderRow, fc);

        content.add(form, BorderLayout.NORTH);

        // Center: Fields table (for STRUCTURED output type)
        fieldsPanel = new JPanel(new BorderLayout(0, 5));
        fieldsPanel.setBorder(new EmptyBorder(8, 0, 0, 0));

        JLabel fieldsLabel = new JLabel("Output Fields:");
        fieldsLabel.setFont(fieldsLabel.getFont().deriveFont(Font.BOLD));
        fieldsPanel.add(fieldsLabel, BorderLayout.NORTH);

        String[] fieldCols = {"Name", "Label", "Type", "Min", "Max", "Primary", "Gap"};
        fieldsTableModel = new DefaultTableModel(fieldCols, 0) {
            @Override
            public Class<?> getColumnClass(int col) {
                return col == 5 ? Boolean.class : String.class;
            }
        };
        if (challenge.output().fields() != null) {
            for (ChallengeOutput.Field f : challenge.output().fields()) {
                boolean isNumeric = f.type() != ChallengeOutput.Field.FieldType.TEXT;
                fieldsTableModel.addRow(new Object[]{
                    f.name(), f.label(), f.type().name(),
                    isNumeric ? String.valueOf(f.minValue()) : "",
                    isNumeric ? String.valueOf(f.maxValue()) : "",
                    f.primary(),
                    isNumeric ? f.gapMode().name() : ""
                });
            }
        }
        JTable fieldsTable = new JTable(fieldsTableModel);
        fieldsTable.setRowHeight(22);
        fieldsTable.getColumnModel().getColumn(2).setCellEditor(
            new DefaultCellEditor(new JComboBox<>(new String[]{"TEXT", "NUMBER", "SCORE"})));
        fieldsTable.getColumnModel().getColumn(6).setCellEditor(
            new DefaultCellEditor(new JComboBox<>(new String[]{"CONNECT", "ZERO", "GAP"})));
        fieldsTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        fieldsTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        fieldsTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        fieldsTable.getColumnModel().getColumn(3).setPreferredWidth(40);
        fieldsTable.getColumnModel().getColumn(4).setPreferredWidth(40);
        fieldsTable.getColumnModel().getColumn(5).setPreferredWidth(40);
        fieldsTable.getColumnModel().getColumn(6).setPreferredWidth(60);

        fieldsPanel.add(new JScrollPane(fieldsTable), BorderLayout.CENTER);

        JPanel fieldBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton addFieldBtn = new JButton("+ Add");
        addFieldBtn.addActionListener(e -> fieldsTableModel.addRow(
            new Object[]{"field_" + (fieldsTableModel.getRowCount() + 1), "Field", "TEXT", "", "", false, ""}));
        fieldBtns.add(addFieldBtn);

        JButton removeFieldBtn = new JButton("- Remove");
        removeFieldBtn.addActionListener(e -> {
            int sel = fieldsTable.getSelectedRow();
            if (sel >= 0) fieldsTableModel.removeRow(sel);
        });
        fieldBtns.add(removeFieldBtn);

        JButton moveUpFieldBtn = new JButton("Up");
        moveUpFieldBtn.addActionListener(e -> moveRow(fieldsTableModel, fieldsTable, -1));
        fieldBtns.add(moveUpFieldBtn);

        JButton moveDownFieldBtn = new JButton("Down");
        moveDownFieldBtn.addActionListener(e -> moveRow(fieldsTableModel, fieldsTable, 1));
        fieldBtns.add(moveDownFieldBtn);

        fieldsPanel.add(fieldBtns, BorderLayout.SOUTH);
        content.add(fieldsPanel, BorderLayout.CENTER);

        updateFieldsPanelVisibility();

        // Bottom buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        if (!isNew) {
            JButton deleteBtn = new JButton("Delete");
            deleteBtn.setForeground(new Color(220, 60, 60));
            deleteBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete challenge '" + challenge.title() + "'?",
                    "Delete Challenge", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    store.deleteChallenge(challenge.id());
                    saved = true;
                    dispose();
                }
            });
            buttonPanel.add(deleteBtn);
            buttonPanel.add(Box.createHorizontalGlue());
        }

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        buttonPanel.add(cancelBtn);

        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> save());
        buttonPanel.add(saveBtn);
        getRootPane().setDefaultButton(saveBtn);

        content.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void updateFieldsPanelVisibility() {
        String selected = (String) outputTypeCombo.getSelectedItem();
        boolean isStructured = OUTPUT_STRUCTURED.equals(selected) || OUTPUT_STRUCTURED_LIST.equals(selected);
        boolean isList = OUTPUT_STRUCTURED_LIST.equals(selected);
        fieldsPanel.setVisible(isStructured);
        listBehaviorLabel.setVisible(isList);
        listBehaviorCombo.setVisible(isList);
        reasonDetailLabel.setVisible(isStructured);
        reasonDetailCombo.setVisible(isStructured);
    }

    private void save() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Set ID for new challenges
        if (isNew) {
            String id = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (id.isEmpty()) id = UUID.randomUUID().toString().substring(0, 8);
            if (store.getChallenge(id) != null) {
                id = id + "-" + UUID.randomUUID().toString().substring(0, 4);
            }
            challenge.setId(id);
        }

        challenge.setTitle(title);
        challenge.setDescription(descriptionArea.getText().trim());
        challenge.setDisplayOrder((int) orderSpinner.getValue());
        challenge.setEnabled(enabledCheck.isSelected());
        challenge.setRefreshInterval(labelToDuration((String) refreshCombo.getSelectedItem()));

        // Output type + fields
        String outputLabel = (String) outputTypeCombo.getSelectedItem();
        ChallengeOutput output = challenge.output();
        output.setType(labelToOutputType(outputLabel));
        output.setListMode(OUTPUT_STRUCTURED_LIST.equals(outputLabel));
        if (output.listMode()) {
            output.setListBehavior("Snapshot".equals(listBehaviorCombo.getSelectedItem())
                ? ChallengeOutput.ListBehavior.SNAPSHOT : ChallengeOutput.ListBehavior.TRACKING);
        }
        if (output.type() == ChallengeOutput.Type.STRUCTURED) {
            output.setReasonDetail(labelToReasonDetail((String) reasonDetailCombo.getSelectedItem()));
        }

        if (output.type() == ChallengeOutput.Type.STRUCTURED) {
            List<ChallengeOutput.Field> fields = new ArrayList<>();
            for (int i = 0; i < fieldsTableModel.getRowCount(); i++) {
                String name = str(fieldsTableModel.getValueAt(i, 0));
                String label = str(fieldsTableModel.getValueAt(i, 1));
                String typeStr = str(fieldsTableModel.getValueAt(i, 2));
                String minStr = str(fieldsTableModel.getValueAt(i, 3));
                String maxStr = str(fieldsTableModel.getValueAt(i, 4));
                boolean primary = Boolean.TRUE.equals(fieldsTableModel.getValueAt(i, 5));
                String gapStr = str(fieldsTableModel.getValueAt(i, 6));

                ChallengeOutput.Field.FieldType fType;
                try { fType = ChallengeOutput.Field.FieldType.valueOf(typeStr); }
                catch (Exception e) { fType = ChallengeOutput.Field.FieldType.TEXT; }

                ChallengeOutput.Field f = new ChallengeOutput.Field(name, label, fType);
                f.setPrimary(primary);
                if (fType != ChallengeOutput.Field.FieldType.TEXT) {
                    try { f.setMinValue(Double.parseDouble(minStr)); } catch (NumberFormatException ignored) {}
                    try { f.setMaxValue(Double.parseDouble(maxStr)); } catch (NumberFormatException ignored) {}
                    try { f.setGapMode(ChallengeOutput.Field.GapMode.valueOf(gapStr)); }
                    catch (Exception ignored) { /* keep default CONNECT */ }
                }
                fields.add(f);
            }
            output.setFields(fields);
        } else {
            output.setFields(new ArrayList<>());
        }
        challenge.setOutput(output);

        // Single escalation from tier/verify fields
        String tier = (String) tierCombo.getSelectedItem();
        boolean verify = verifyCheck.isSelected();
        ChallengeEscalation esc = new ChallengeEscalation("Run", tier != null ? tier : "standard");
        esc.setVerify(verify);
        challenge.setEscalations(List.of(esc));

        store.saveChallenge(challenge);
        saved = true;
        dispose();
    }

    private static void moveRow(DefaultTableModel model, JTable table, int direction) {
        int idx = table.getSelectedRow();
        int target = idx + direction;
        if (idx < 0 || target < 0 || target >= model.getRowCount()) return;
        model.moveRow(idx, idx, target);
        table.setRowSelectionInterval(target, target);
    }

    private static String str(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    private static String durationToLabel(Duration d) {
        if (d == null) return "None";
        long hours = d.toHours();
        if (hours <= 1) return "1 hour";
        if (hours <= 4) return "4 hours";
        if (hours <= 12) return "12 hours";
        if (hours <= 24) return "1 day";
        if (hours <= 72) return "3 days";
        return "7 days";
    }

    private static Duration labelToDuration(String label) {
        if (label == null || "None".equals(label)) return null;
        return switch (label) {
            case "1 hour" -> Duration.ofHours(1);
            case "4 hours" -> Duration.ofHours(4);
            case "12 hours" -> Duration.ofHours(12);
            case "1 day" -> Duration.ofDays(1);
            case "3 days" -> Duration.ofDays(3);
            case "7 days" -> Duration.ofDays(7);
            default -> null;
        };
    }

    private static String outputTypeToLabel(ChallengeOutput output) {
        if (output.type() == ChallengeOutput.Type.STRUCTURED) {
            return output.listMode() ? OUTPUT_STRUCTURED_LIST : OUTPUT_STRUCTURED;
        }
        return OUTPUT_TEXT;
    }

    private static ChallengeOutput.Type labelToOutputType(String label) {
        return switch (label) {
            case OUTPUT_STRUCTURED, OUTPUT_STRUCTURED_LIST -> ChallengeOutput.Type.STRUCTURED;
            default -> ChallengeOutput.Type.TEXT;
        };
    }

    private static String reasonDetailToLabel(ChallengeOutput.ReasonDetail rd) {
        if (rd == null) return "None";
        return switch (rd) {
            case BRIEF -> "Brief";
            case DETAILED -> "Detailed";
            case VERBOSE -> "Verbose";
            default -> "None";
        };
    }

    private static ChallengeOutput.ReasonDetail labelToReasonDetail(String label) {
        if (label == null) return ChallengeOutput.ReasonDetail.NONE;
        return switch (label) {
            case "Brief" -> ChallengeOutput.ReasonDetail.BRIEF;
            case "Detailed" -> ChallengeOutput.ReasonDetail.DETAILED;
            case "Verbose" -> ChallengeOutput.ReasonDetail.VERBOSE;
            default -> ChallengeOutput.ReasonDetail.NONE;
        };
    }
}
