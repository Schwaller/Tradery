package com.tradery.news.ui.coin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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

    public SchemaTypeEditorDialog(Window owner, SchemaRegistry registry, SchemaType type) {
        super(owner, "Edit " + type.name(), ModalityType.APPLICATION_MODAL);
        this.registry = registry;
        this.type = type;
        this.selectedColor = type.color();

        initUI();
        pack();
        setMinimumSize(new Dimension(450, 400));
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(15, 15, 15, 15));
        content.setBackground(new Color(45, 47, 51));

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

        // Attributes table
        JPanel attrPanel = new JPanel(new BorderLayout(0, 5));
        attrPanel.setBackground(new Color(45, 47, 51));

        JLabel attrLabel = createLabel("Attributes:");
        attrLabel.setFont(attrLabel.getFont().deriveFont(Font.BOLD));
        attrPanel.add(attrLabel, BorderLayout.NORTH);

        String[] columns = {"Name", "Type", "Required"};
        attrTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row1, int col) { return false; }
        };
        for (SchemaAttribute attr : type.attributes()) {
            attrTableModel.addRow(new Object[]{attr.name(), attr.dataType(), attr.required() ? "Yes" : ""});
        }

        JTable attrTable = new JTable(attrTableModel);
        attrTable.setBackground(new Color(35, 37, 41));
        attrTable.setForeground(new Color(200, 200, 210));
        attrTable.setGridColor(new Color(50, 52, 56));
        attrTable.setSelectionBackground(new Color(60, 80, 100));
        attrTable.setRowHeight(22);
        attrTable.getTableHeader().setBackground(new Color(45, 47, 51));
        attrTable.getTableHeader().setForeground(new Color(180, 180, 190));

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
        content.add(attrPanel, BorderLayout.CENTER);

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

        setContentPane(content);
        getRootPane().setDefaultButton(saveBtn);
    }

    private void addAttribute() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
        JTextField nameInput = new JTextField();
        JComboBox<String> typeInput = new JComboBox<>(new String[]{
            SchemaAttribute.TEXT, SchemaAttribute.NUMBER, SchemaAttribute.LIST, SchemaAttribute.BOOLEAN
        });
        JCheckBox reqCheck = new JCheckBox("Required");

        panel.add(new JLabel("Name:"));
        panel.add(nameInput);
        panel.add(new JLabel("Type:"));
        panel.add(typeInput);
        panel.add(new JLabel());
        panel.add(reqCheck);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Attribute",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameInput.getText().trim().toLowerCase().replaceAll("\\s+", "_");
            if (name.isEmpty()) return;

            String dataType = (String) typeInput.getSelectedItem();
            boolean required = reqCheck.isSelected();

            SchemaAttribute attr = new SchemaAttribute(name, dataType, required, type.attributes().size());
            registry.addAttribute(type.id(), attr);
            attrTableModel.addRow(new Object[]{name, dataType, required ? "Yes" : ""});
        }
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
