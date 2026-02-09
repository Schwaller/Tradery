package com.tradery.news.ui.coin;

import com.tradery.ui.controls.BorderlessTable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modal dialog for editing a schema type's name, color, label, and attributes.
 */
public class SchemaTypeEditorDialog extends JDialog {

    private final SchemaRegistry registry;
    private final SchemaType type;

    private JTextField nameField;
    private JTextField labelField;
    private JButton colorButton;
    private Color selectedColor;
    private JComboBox<SchemaType> fromCombo;
    private JComboBox<SchemaType> toCombo;
    private DefaultTableModel attrTableModel;

    // Form layouts editor state
    private DefaultListModel<FormLayout> layoutListModel;
    private JList<FormLayout> layoutList;
    private DefaultTableModel layoutFieldsTableModel;

    public SchemaTypeEditorDialog(Window owner, SchemaRegistry registry, SchemaType type) {
        super(owner, "Edit " + type.name(), ModalityType.APPLICATION_MODAL);
        this.registry = registry;
        this.type = type;
        this.selectedColor = type.color();

        // Integrated macOS title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

        initUI();
        pack();
        setMinimumSize(new Dimension(550, 600));
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        setContentPane(contentPane);

        // 52px header bar with centered title
        JPanel hBar = new JPanel(new BorderLayout());
        hBar.setPreferredSize(new Dimension(0, 52));
        JLabel windowTitle = new JLabel("Edit " + type.name(), SwingConstants.CENTER);
        windowTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        windowTitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        hBar.add(windowTitle, BorderLayout.CENTER);
        JPanel hWrapper = new JPanel(new BorderLayout());
        hWrapper.add(hBar, BorderLayout.CENTER);
        hWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        contentPane.add(hWrapper, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(15, 15, 15, 15));
        content.setBackground(new Color(45, 47, 51));
        contentPane.add(content, BorderLayout.CENTER);

        // Top form
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(45, 47, 51));

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(5, 5, 5, 10);

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = new Insets(5, 0, 5, 5);

        int row = 0;

        // ID (read-only)
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(createLabel("ID:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField idField = createTextField(type.id());
        idField.setEnabled(false);
        formPanel.add(idField, fieldGbc);

        // Name
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(createLabel("Name:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        nameField = createTextField(type.name());
        formPanel.add(nameField, fieldGbc);

        // Color
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(createLabel("Color:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        colorButton = new JButton();
        colorButton.setPreferredSize(new Dimension(80, 25));
        colorButton.setBackground(selectedColor);
        colorButton.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Choose Color", selectedColor);
            if (c != null) {
                selectedColor = c;
                colorButton.setBackground(c);
            }
        });
        formPanel.add(colorButton, fieldGbc);

        // Relationship-specific fields
        if (type.isRelationship()) {
            // Label
            labelGbc.gridx = 0; labelGbc.gridy = row;
            formPanel.add(createLabel("Label:"), labelGbc);
            fieldGbc.gridx = 1; fieldGbc.gridy = row++;
            labelField = createTextField(type.label() != null ? type.label() : "");
            formPanel.add(labelField, fieldGbc);

            // From type
            List<SchemaType> entityTypes = registry.entityTypes();
            labelGbc.gridx = 0; labelGbc.gridy = row;
            formPanel.add(createLabel("From Type:"), labelGbc);
            fieldGbc.gridx = 1; fieldGbc.gridy = row++;
            fromCombo = new JComboBox<>(entityTypes.toArray(new SchemaType[0]));
            fromCombo.setRenderer(new SchemaTypeRenderer());
            selectById(fromCombo, type.fromTypeId());
            formPanel.add(fromCombo, fieldGbc);

            // To type
            labelGbc.gridx = 0; labelGbc.gridy = row;
            formPanel.add(createLabel("To Type:"), labelGbc);
            fieldGbc.gridx = 1; fieldGbc.gridy = row++;
            toCombo = new JComboBox<>(entityTypes.toArray(new SchemaType[0]));
            toCombo.setRenderer(new SchemaTypeRenderer());
            selectById(toCombo, type.toTypeId());
            formPanel.add(toCombo, fieldGbc);
        }

        content.add(formPanel, BorderLayout.NORTH);

        // Center: vertically stacked Attributes table + Form Layouts
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(new Color(45, 47, 51));

        // --- Attributes table ---
        JPanel attrPanel = new JPanel(new BorderLayout(0, 5));
        attrPanel.setBackground(new Color(45, 47, 51));

        JLabel attrLabel = createLabel("Attributes:");
        attrLabel.setFont(attrLabel.getFont().deriveFont(Font.BOLD));
        attrPanel.add(attrLabel, BorderLayout.NORTH);

        String[] columns = {"Name", "Type", "Required", "Labels", "Config"};
        attrTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row1, int col) { return false; }
        };
        for (SchemaAttribute attr : type.attributes()) {
            int labelCount = attr.labels() != null ? attr.labels().size() : 0;
            attrTableModel.addRow(new Object[]{
                attr.name(), attr.dataType(), attr.required() ? "Yes" : "",
                labelCount > 0 ? labelCount + " lang" : "",
                attr.configSummary()
            });
        }

        JTable attrTable = new BorderlessTable(attrTableModel);
        attrTable.setBackground(new Color(35, 37, 41));
        attrTable.setForeground(new Color(200, 200, 210));
        attrTable.setSelectionBackground(new Color(60, 80, 100));
        attrTable.setRowHeight(22);
        attrTable.getTableHeader().setBackground(new Color(45, 47, 51));
        attrTable.getTableHeader().setForeground(new Color(180, 180, 190));

        // Double-click to edit attribute
        attrTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int selectedRow = attrTable.getSelectedRow();
                    if (selectedRow >= 0) {
                        editAttribute(selectedRow);
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(attrTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 62, 66)));
        tableScroll.getViewport().setBackground(new Color(35, 37, 41));
        attrPanel.add(tableScroll, BorderLayout.CENTER);

        // Attribute buttons
        JPanel attrBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        attrBtnPanel.setBackground(new Color(45, 47, 51));

        JButton addAttrBtn = new JButton("+ Add");
        addAttrBtn.addActionListener(e -> addAttribute());
        attrBtnPanel.add(addAttrBtn);

        JButton removeAttrBtn = new JButton("- Remove");
        removeAttrBtn.addActionListener(e -> {
            int selectedRow = attrTable.getSelectedRow();
            if (selectedRow >= 0) {
                String attrName = (String) attrTableModel.getValueAt(selectedRow, 0);
                registry.removeAttribute(type.id(), attrName);
                attrTableModel.removeRow(selectedRow);
            }
        });
        attrBtnPanel.add(removeAttrBtn);

        attrPanel.add(attrBtnPanel, BorderLayout.SOUTH);
        centerPanel.add(attrPanel);

        // --- Form Layouts section ---
        if (type.isEntity()) {
            centerPanel.add(Box.createVerticalStrut(10));
            centerPanel.add(createFormLayoutsPanel());
        }

        content.add(centerPanel, BorderLayout.CENTER);

        // Bottom buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(new Color(45, 47, 51));

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        buttonPanel.add(cancelBtn);

        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> save());
        buttonPanel.add(saveBtn);

        content.add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(saveBtn);
    }

    private void addAttribute() {
        SchemaAttribute attr = ErdPanel.showAttributeEditorDialog(this, type, null);
        if (attr != null) {
            registry.addAttribute(type.id(), attr);
            int labelCount = attr.labels() != null ? attr.labels().size() : 0;
            attrTableModel.addRow(new Object[]{
                attr.name(), attr.dataType(), attr.required() ? "Yes" : "",
                labelCount > 0 ? labelCount + " lang" : "",
                attr.configSummary()
            });
        }
    }

    private void editAttribute(int tableRow) {
        String attrName = (String) attrTableModel.getValueAt(tableRow, 0);
        SchemaAttribute existing = type.attributes().stream()
            .filter(a -> a.name().equals(attrName))
            .findFirst().orElse(null);
        if (existing == null) return;

        SchemaAttribute updated = ErdPanel.showAttributeEditorDialog(this, type, existing);
        if (updated != null) {
            registry.addAttribute(type.id(), updated);
            int labelCount = updated.labels() != null ? updated.labels().size() : 0;
            attrTableModel.setValueAt(updated.name(), tableRow, 0);
            attrTableModel.setValueAt(updated.dataType(), tableRow, 1);
            attrTableModel.setValueAt(updated.required() ? "Yes" : "", tableRow, 2);
            attrTableModel.setValueAt(labelCount > 0 ? labelCount + " lang" : "", tableRow, 3);
            attrTableModel.setValueAt(updated.configSummary(), tableRow, 4);
        }
    }

    private JPanel createFormLayoutsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBackground(new Color(45, 47, 51));

        JLabel title = createLabel("Form Layouts:");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        panel.add(title, BorderLayout.NORTH);

        // Left: layout list
        layoutListModel = new DefaultListModel<>();
        List<FormLayout> existing = type.formLayouts();
        if (existing != null) {
            for (FormLayout fl : existing) layoutListModel.addElement(fl);
        }

        layoutList = new JList<>(layoutListModel);
        layoutList.setBackground(new Color(35, 37, 41));
        layoutList.setForeground(new Color(200, 200, 210));
        layoutList.setSelectionBackground(new Color(60, 80, 100));
        layoutList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FormLayout fl) setText(fl.name());
                setBackground(isSelected ? new Color(60, 80, 100) : new Color(35, 37, 41));
                setForeground(new Color(200, 200, 210));
                return this;
            }
        });
        layoutList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onLayoutSelected();
        });

        JScrollPane listScroll = new JScrollPane(layoutList);
        listScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 62, 66)));
        listScroll.setPreferredSize(new Dimension(150, 100));
        listScroll.getViewport().setBackground(new Color(35, 37, 41));

        // Layout list buttons
        JPanel listBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        listBtnPanel.setBackground(new Color(45, 47, 51));
        JButton addLayoutBtn = new JButton("+");
        addLayoutBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Layout name:", "New Form Layout",
                JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                FormLayout fl = new FormLayout(name.trim(), new ArrayList<>());
                layoutListModel.addElement(fl);
                layoutList.setSelectedIndex(layoutListModel.size() - 1);
            }
        });
        listBtnPanel.add(addLayoutBtn);

        JButton renameLayoutBtn = new JButton("Rename");
        renameLayoutBtn.addActionListener(e -> {
            FormLayout sel = layoutList.getSelectedValue();
            if (sel == null) return;
            String name = JOptionPane.showInputDialog(this, "Layout name:", sel.name());
            if (name != null && !name.trim().isEmpty()) {
                sel.setName(name.trim());
                layoutList.repaint();
            }
        });
        listBtnPanel.add(renameLayoutBtn);

        JButton removeLayoutBtn = new JButton("-");
        removeLayoutBtn.addActionListener(e -> {
            int idx = layoutList.getSelectedIndex();
            if (idx >= 0) layoutListModel.remove(idx);
        });
        listBtnPanel.add(removeLayoutBtn);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 3));
        leftPanel.setBackground(new Color(45, 47, 51));
        leftPanel.add(listScroll, BorderLayout.CENTER);
        leftPanel.add(listBtnPanel, BorderLayout.SOUTH);

        // Right: fields table for selected layout
        JPanel rightPanel = new JPanel(new BorderLayout(0, 3));
        rightPanel.setBackground(new Color(45, 47, 51));

        String[] fieldCols = {"Attribute", "Group"};
        layoutFieldsTableModel = new DefaultTableModel(fieldCols, 0) {
            @Override
            public boolean isCellEditable(int r, int col) { return col == 1; } // group is editable
        };

        JTable fieldsTable = new BorderlessTable(layoutFieldsTableModel);
        fieldsTable.setBackground(new Color(35, 37, 41));
        fieldsTable.setForeground(new Color(200, 200, 210));
        fieldsTable.setSelectionBackground(new Color(60, 80, 100));
        fieldsTable.setRowHeight(22);
        fieldsTable.getTableHeader().setBackground(new Color(45, 47, 51));
        fieldsTable.getTableHeader().setForeground(new Color(180, 180, 190));

        // Sync edits back to the FormLayout
        layoutFieldsTableModel.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                syncFieldsToSelectedLayout();
            }
        });

        JScrollPane fieldsScroll = new JScrollPane(fieldsTable);
        fieldsScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 62, 66)));
        fieldsScroll.getViewport().setBackground(new Color(35, 37, 41));
        rightPanel.add(fieldsScroll, BorderLayout.CENTER);

        // Field buttons
        JPanel fieldBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        fieldBtnPanel.setBackground(new Color(45, 47, 51));

        JButton addFieldBtn = new JButton("+ Add Field");
        addFieldBtn.addActionListener(e -> {
            FormLayout sel = layoutList.getSelectedValue();
            if (sel == null) return;

            // Show chooser with available attributes (+ virtual fields like categories)
            List<String> available = new ArrayList<>();
            java.util.Set<String> used = new java.util.HashSet<>();
            for (FormLayout.FormLayoutField f : sel.fields()) used.add(f.attributeName());
            for (SchemaAttribute a : type.attributes()) {
                if (!used.contains(a.name())) available.add(a.name());
            }
            if (type.isEntity() && !used.contains("categories")) available.add("categories");
            if (available.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All attributes are already in this layout.");
                return;
            }
            String chosen = (String) JOptionPane.showInputDialog(this, "Select attribute:",
                "Add Field", JOptionPane.PLAIN_MESSAGE, null,
                available.toArray(new String[0]), available.get(0));
            if (chosen != null) {
                sel.fields().add(new FormLayout.FormLayoutField(chosen, null));
                layoutFieldsTableModel.addRow(new Object[]{chosen, ""});
            }
        });
        fieldBtnPanel.add(addFieldBtn);

        JButton moveUpBtn = new JButton("Up");
        moveUpBtn.addActionListener(e -> moveFieldRow(fieldsTable, -1));
        fieldBtnPanel.add(moveUpBtn);

        JButton moveDownBtn = new JButton("Down");
        moveDownBtn.addActionListener(e -> moveFieldRow(fieldsTable, 1));
        fieldBtnPanel.add(moveDownBtn);

        JButton removeFieldBtn = new JButton("-");
        removeFieldBtn.addActionListener(e -> {
            int idx = fieldsTable.getSelectedRow();
            if (idx >= 0) {
                layoutFieldsTableModel.removeRow(idx);
                syncFieldsToSelectedLayout();
            }
        });
        fieldBtnPanel.add(removeFieldBtn);

        rightPanel.add(fieldBtnPanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(150);
        splitPane.setBackground(new Color(45, 47, 51));
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private void onLayoutSelected() {
        layoutFieldsTableModel.setRowCount(0);
        FormLayout sel = layoutList.getSelectedValue();
        if (sel == null) return;
        for (FormLayout.FormLayoutField f : sel.fields()) {
            layoutFieldsTableModel.addRow(new Object[]{f.attributeName(), f.group() != null ? f.group() : ""});
        }
    }

    private void syncFieldsToSelectedLayout() {
        FormLayout sel = layoutList.getSelectedValue();
        if (sel == null) return;
        List<FormLayout.FormLayoutField> fields = new ArrayList<>();
        for (int i = 0; i < layoutFieldsTableModel.getRowCount(); i++) {
            String attr = (String) layoutFieldsTableModel.getValueAt(i, 0);
            String group = (String) layoutFieldsTableModel.getValueAt(i, 1);
            if (group != null && group.trim().isEmpty()) group = null;
            fields.add(new FormLayout.FormLayoutField(attr, group));
        }
        sel.setFields(fields);
    }

    private void moveFieldRow(JTable table, int direction) {
        int idx = table.getSelectedRow();
        int target = idx + direction;
        if (idx < 0 || target < 0 || target >= layoutFieldsTableModel.getRowCount()) return;
        layoutFieldsTableModel.moveRow(idx, idx, target);
        table.setRowSelectionInterval(target, target);
        syncFieldsToSelectedLayout();
    }

    private void save() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        type.setName(name);
        type.setColor(selectedColor);

        if (type.isRelationship()) {
            type.setLabel(labelField.getText().trim());
            SchemaType from = (SchemaType) fromCombo.getSelectedItem();
            SchemaType to = (SchemaType) toCombo.getSelectedItem();
            type.setFromTypeId(from != null ? from.id() : null);
            type.setToTypeId(to != null ? to.id() : null);
        }

        // Save form layouts
        if (layoutListModel != null) {
            List<FormLayout> layouts = new ArrayList<>();
            for (int i = 0; i < layoutListModel.size(); i++) {
                layouts.add(layoutListModel.get(i));
            }
            type.setFormLayouts(layouts.isEmpty() ? null : layouts);
        }

        registry.save(type);
        dispose();
    }

    private void selectById(JComboBox<SchemaType> combo, String id) {
        if (id == null) return;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).id().equals(id)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(180, 180, 190));
        return label;
    }

    private JTextField createTextField(String value) {
        JTextField field = new JTextField(value, 20);
        field.setBackground(new Color(60, 62, 66));
        field.setForeground(new Color(200, 200, 210));
        field.setCaretColor(new Color(200, 200, 210));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 72, 76)),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        return field;
    }

    private static class SchemaTypeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SchemaType type) {
                setText(type.name());
                if (!isSelected) setForeground(type.color());
            }
            return this;
        }
    }
}
