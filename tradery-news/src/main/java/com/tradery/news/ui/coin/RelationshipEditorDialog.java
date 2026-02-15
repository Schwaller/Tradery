package com.tradery.news.ui.coin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for creating relationships between entities.
 */
public class RelationshipEditorDialog extends JDialog {

    private final EntityStore store;
    private final SchemaRegistry schemaRegistry;
    private final List<CoinEntity> entities;
    private final Consumer<CoinRelationship> onSave;

    private JComboBox<EntityItem> fromCombo;
    private JComboBox<EntityItem> toCombo;
    private JComboBox<SchemaType> typeCombo;
    private JTextField noteField;

    public RelationshipEditorDialog(Window owner, EntityStore store, SchemaRegistry schemaRegistry,
                                    List<CoinEntity> entities,
                                    String preselectedFromId, Consumer<CoinRelationship> onSave) {
        super(owner, "Add Relationship", ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.schemaRegistry = schemaRegistry;
        this.entities = entities;
        this.onSave = onSave;

        // Integrated macOS title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

        initUI(preselectedFromId);
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI(String preselectedFromId) {
        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        setContentPane(contentPane);

        // 52px header bar with centered title
        JPanel hBar = new JPanel(new BorderLayout());
        hBar.setPreferredSize(new Dimension(0, 52));
        JLabel windowTitle = new JLabel("Add Relationship", SwingConstants.CENTER);
        windowTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        windowTitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        hBar.add(windowTitle, BorderLayout.CENTER);
        JPanel hWrapper = new JPanel(new BorderLayout());
        hWrapper.add(hBar, BorderLayout.CENTER);
        hWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        contentPane.add(hWrapper, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(15, 15, 15, 15));
        content.setBackground(new Color(45, 47, 51));
        contentPane.add(content, BorderLayout.CENTER);

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(5, 5, 5, 10);

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = new Insets(5, 0, 5, 5);

        int row = 0;

        // Build sorted entity list for combos
        List<EntityItem> entityItems = entities.stream()
            .sorted(Comparator.comparing(CoinEntity::name, String.CASE_INSENSITIVE_ORDER))
            .map(EntityItem::new)
            .toList();

        // From entity
        labelGbc.gridx = 0; labelGbc.gridy = row;
        content.add(createLabel("From:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        fromCombo = new JComboBox<>(entityItems.toArray(new EntityItem[0]));
        fromCombo.setBackground(new Color(60, 62, 66));
        fromCombo.setForeground(new Color(200, 200, 210));
        fromCombo.setMaximumRowCount(15);
        // Preselect if provided
        if (preselectedFromId != null) {
            for (int i = 0; i < fromCombo.getItemCount(); i++) {
                if (fromCombo.getItemAt(i).entity.id().equals(preselectedFromId)) {
                    fromCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        content.add(fromCombo, fieldGbc);

        // Relationship type (schema-driven)
        labelGbc.gridx = 0; labelGbc.gridy = row;
        content.add(createLabel("Relationship:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        List<SchemaType> relTypes = schemaRegistry.relationshipTypes();
        typeCombo = new JComboBox<>(relTypes.toArray(new SchemaType[0]));
        typeCombo.setBackground(new Color(60, 62, 66));
        typeCombo.setForeground(new Color(200, 200, 210));
        typeCombo.setRenderer(new RelationshipTypeRenderer());
        content.add(typeCombo, fieldGbc);

        // To entity
        labelGbc.gridx = 0; labelGbc.gridy = row;
        content.add(createLabel("To:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        toCombo = new JComboBox<>(entityItems.toArray(new EntityItem[0]));
        toCombo.setBackground(new Color(60, 62, 66));
        toCombo.setForeground(new Color(200, 200, 210));
        toCombo.setMaximumRowCount(15);
        content.add(toCombo, fieldGbc);

        // Note field
        labelGbc.gridx = 0; labelGbc.gridy = row;
        content.add(createLabel("Note:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        noteField = createTextField();
        noteField.setToolTipText("Optional note about this relationship");
        content.add(noteField, fieldGbc);

        // Preview label
        JLabel previewLabel = new JLabel(" ");
        previewLabel.setForeground(new Color(140, 180, 140));
        previewLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        labelGbc.gridx = 0; labelGbc.gridy = row++;
        labelGbc.gridwidth = 2;
        labelGbc.anchor = GridBagConstraints.CENTER;
        content.add(previewLabel, labelGbc);

        // Update preview when selection changes
        Runnable updatePreview = () -> {
            EntityItem from = (EntityItem) fromCombo.getSelectedItem();
            EntityItem to = (EntityItem) toCombo.getSelectedItem();
            SchemaType type = (SchemaType) typeCombo.getSelectedItem();
            if (from != null && to != null && type != null) {
                String label = type.label() != null ? type.label() : type.id();
                previewLabel.setText(from.entity.name() + " " + label + " " + to.entity.name());
            }
        };
        fromCombo.addActionListener(e -> updatePreview.run());
        toCombo.addActionListener(e -> updatePreview.run());
        typeCombo.addActionListener(e -> updatePreview.run());
        updatePreview.run();

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(new Color(45, 47, 51));

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        buttonPanel.add(cancelBtn);

        JButton saveBtn = new JButton("Add");
        saveBtn.addActionListener(e -> save());
        buttonPanel.add(saveBtn);

        labelGbc.gridx = 0; labelGbc.gridy = row;
        labelGbc.gridwidth = 2;
        labelGbc.anchor = GridBagConstraints.EAST;
        labelGbc.insets = new Insets(15, 5, 5, 5);
        content.add(buttonPanel, labelGbc);

        getRootPane().setDefaultButton(saveBtn);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(180, 180, 190));
        return label;
    }

    private JTextField createTextField() {
        JTextField field = new JTextField(25);
        field.setBackground(new Color(60, 62, 66));
        field.setForeground(new Color(200, 200, 210));
        field.setCaretColor(new Color(200, 200, 210));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 72, 76)),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        return field;
    }

    private void save() {
        EntityItem fromItem = (EntityItem) fromCombo.getSelectedItem();
        EntityItem toItem = (EntityItem) toCombo.getSelectedItem();
        SchemaType type = (SchemaType) typeCombo.getSelectedItem();
        String note = noteField.getText().trim();

        if (fromItem == null || toItem == null || type == null) {
            showError("Please select both entities and a relationship type");
            return;
        }

        if (fromItem.entity.id().equals(toItem.entity.id())) {
            showError("Cannot create relationship to self");
            return;
        }

        // Check for duplicate
        if (store.relationshipExists(fromItem.entity.id(), toItem.entity.id(), type.id())) {
            showError("This relationship already exists");
            return;
        }

        // Create and save
        CoinRelationship rel = new CoinRelationship(
            fromItem.entity.id(),
            toItem.entity.id(),
            type.id(),
            note.isEmpty() ? null : note
        );
        store.saveRelationship(rel, "manual");

        if (onSave != null) {
            onSave.accept(rel);
        }

        dispose();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Validation Error", JOptionPane.ERROR_MESSAGE);
    }

    // Wrapper for combo display
    private static class EntityItem {
        final CoinEntity entity;

        EntityItem(CoinEntity entity) {
            this.entity = entity;
        }

        @Override
        public String toString() {
            if (entity.symbol() != null) {
                return entity.name() + " (" + entity.symbol() + ") - " + entity.type();
            }
            return entity.name() + " - " + entity.type();
        }
    }

    // Custom renderer for relationship types (schema-driven)
    private static class RelationshipTypeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SchemaType st) {
                String label = st.label() != null ? st.label() : st.id();
                setText(st.id() + " (" + label + ")");
                if (!isSelected) {
                    setForeground(st.color());
                }
            }
            return this;
        }
    }
}
