package com.tradery.news.ui.coin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Dialog for creating or editing entities (coins, ETFs, VCs, exchanges, etc.)
 */
public class EntityEditorDialog extends JDialog {

    private final EntityStore store;
    private final CoinEntity existingEntity;  // null for new entity
    private final Consumer<CoinEntity> onSave;

    private JTextField idField;
    private JTextField nameField;
    private JTextField symbolField;
    private JComboBox<CoinEntity.Type> typeCombo;
    private JTextField parentIdField;
    private JTextField marketCapField;

    public EntityEditorDialog(Window owner, EntityStore store, CoinEntity existingEntity, Consumer<CoinEntity> onSave) {
        super(owner, existingEntity == null ? "Add Entity" : "Edit Entity", ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.existingEntity = existingEntity;
        this.onSave = onSave;

        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(15, 15, 15, 15));
        content.setBackground(new Color(45, 47, 51));

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(5, 5, 5, 10);

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = new Insets(5, 0, 5, 5);

        int row = 0;

        // ID field
        labelGbc.gridx = 0; labelGbc.gridy = row;
        content.add(createLabel("ID:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        idField = createTextField();
        idField.setToolTipText("Unique identifier (e.g., 'my-vc', 'grayscale-sol-trust')");
        if (existingEntity != null) {
            idField.setText(existingEntity.id());
            idField.setEnabled(false);  // Can't change ID
        }
        content.add(idField, fieldGbc);

        // Name field
        labelGbc.gridx = 0; labelGbc.gridy = row;
        content.add(createLabel("Name:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        nameField = createTextField();
        nameField.setToolTipText("Display name (e.g., 'Grayscale Solana Trust')");
        if (existingEntity != null) {
            nameField.setText(existingEntity.name());
        }
        content.add(nameField, fieldGbc);

        // Symbol field
        labelGbc.gridx = 0; labelGbc.gridy = row;
        content.add(createLabel("Symbol:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        symbolField = createTextField();
        symbolField.setToolTipText("Ticker symbol (optional, e.g., 'GSOL')");
        if (existingEntity != null && existingEntity.symbol() != null) {
            symbolField.setText(existingEntity.symbol());
        }
        content.add(symbolField, fieldGbc);

        // Type combo
        labelGbc.gridx = 0; labelGbc.gridy = row;
        content.add(createLabel("Type:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        typeCombo = new JComboBox<>(CoinEntity.Type.values());
        typeCombo.setBackground(new Color(60, 62, 66));
        typeCombo.setForeground(new Color(200, 200, 210));
        if (existingEntity != null) {
            typeCombo.setSelectedItem(existingEntity.type());
        } else {
            typeCombo.setSelectedItem(CoinEntity.Type.VC);  // Default to VC for manual adds
        }
        content.add(typeCombo, fieldGbc);

        // Parent ID field
        labelGbc.gridx = 0; labelGbc.gridy = row;
        content.add(createLabel("Parent ID:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        parentIdField = createTextField();
        parentIdField.setToolTipText("For L2s: the L1 chain ID (e.g., 'ethereum')");
        if (existingEntity != null && existingEntity.parentId() != null) {
            parentIdField.setText(existingEntity.parentId());
        }
        content.add(parentIdField, fieldGbc);

        // Market cap field
        labelGbc.gridx = 0; labelGbc.gridy = row;
        content.add(createLabel("Market Cap:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        marketCapField = createTextField();
        marketCapField.setToolTipText("Optional market cap in USD (e.g., '1000000000' for $1B)");
        if (existingEntity != null && existingEntity.marketCap() > 0) {
            marketCapField.setText(String.valueOf((long) existingEntity.marketCap()));
        }
        content.add(marketCapField, fieldGbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(new Color(45, 47, 51));

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        buttonPanel.add(cancelBtn);

        JButton saveBtn = new JButton(existingEntity == null ? "Add" : "Save");
        saveBtn.addActionListener(e -> save());
        buttonPanel.add(saveBtn);

        labelGbc.gridx = 0; labelGbc.gridy = row;
        labelGbc.gridwidth = 2;
        labelGbc.anchor = GridBagConstraints.EAST;
        labelGbc.insets = new Insets(15, 5, 5, 5);
        content.add(buttonPanel, labelGbc);

        setContentPane(content);
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
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        String symbol = symbolField.getText().trim();
        CoinEntity.Type type = (CoinEntity.Type) typeCombo.getSelectedItem();
        String parentId = parentIdField.getText().trim();
        String marketCapStr = marketCapField.getText().trim();

        // Validation
        if (id.isEmpty()) {
            showError("ID is required");
            idField.requestFocus();
            return;
        }
        if (name.isEmpty()) {
            showError("Name is required");
            nameField.requestFocus();
            return;
        }

        // Check for duplicate ID on new entity
        if (existingEntity == null && store.entityExists(id)) {
            showError("An entity with ID '" + id + "' already exists");
            idField.requestFocus();
            return;
        }

        // Parse market cap
        double marketCap = 0;
        if (!marketCapStr.isEmpty()) {
            try {
                marketCap = Double.parseDouble(marketCapStr);
            } catch (NumberFormatException e) {
                showError("Invalid market cap - must be a number");
                marketCapField.requestFocus();
                return;
            }
        }

        // Create entity
        CoinEntity entity = new CoinEntity(
            id,
            name,
            symbol.isEmpty() ? null : symbol,
            type,
            parentId.isEmpty() ? null : parentId
        );
        entity.setMarketCap(marketCap);

        // Save to store
        store.saveEntity(entity, "manual");

        // Notify callback
        if (onSave != null) {
            onSave.accept(entity);
        }

        dispose();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Validation Error", JOptionPane.ERROR_MESSAGE);
    }
}
