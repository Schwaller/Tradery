package com.tradery.news.ui.coin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.tree.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Entity manager window with left navigation tree and right detail panel.
 */
public class EntityManagerFrame extends JFrame {

    private final EntityStore store;
    private final Consumer<Void> onDataChanged;

    private JTree entityTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    // Detail panel components
    private JPanel detailPanel;
    private JTextField idField;
    private JTextField nameField;
    private JTextField symbolField;
    private JComboBox<CoinEntity.Type> typeCombo;
    private JTextField parentIdField;
    private JTextField marketCapField;
    private JLabel sourceLabel;
    private JButton saveBtn;
    private JButton deleteBtn;

    private CoinEntity selectedEntity;
    private boolean isNewEntity = false;

    public EntityManagerFrame(EntityStore store, Consumer<Void> onDataChanged) {
        super("Entity Manager");
        this.store = store;
        this.onDataChanged = onDataChanged;

        setSize(900, 600);
        setLocationRelativeTo(null);
        initUI();
        loadEntities();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(30, 32, 36));

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(38, 40, 44));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        JButton addBtn = new JButton("+ New Entity");
        addBtn.addActionListener(e -> createNewEntity());
        toolbar.add(addBtn);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadEntities());
        toolbar.add(refreshBtn);

        mainPanel.add(toolbar, BorderLayout.NORTH);

        // Left: Tree navigation
        JPanel leftPanel = createLeftPanel();
        leftPanel.setPreferredSize(new Dimension(300, 0));

        // Right: Detail panel
        detailPanel = createDetailPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, detailPanel);
        splitPane.setDividerLocation(300);
        splitPane.setDividerSize(4);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(35, 37, 41));
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(50, 52, 56)));

        rootNode = new DefaultMutableTreeNode("Entities");
        treeModel = new DefaultTreeModel(rootNode);
        entityTree = new JTree(treeModel);
        entityTree.setRootVisible(false);
        entityTree.setShowsRootHandles(true);
        entityTree.setBackground(new Color(35, 37, 41));
        entityTree.setForeground(new Color(200, 200, 210));
        entityTree.setCellRenderer(new EntityTreeCellRenderer());
        entityTree.addTreeSelectionListener(e -> onTreeSelection());

        JScrollPane scroll = new JScrollPane(entityTree);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(new Color(35, 37, 41));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(38, 40, 44));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(new Color(38, 40, 44));

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(8, 5, 8, 10);

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = new Insets(8, 0, 8, 5);

        int row = 0;

        // Source label (read-only info)
        labelGbc.gridx = 0; labelGbc.gridy = row;
        form.add(createLabel("Source:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        sourceLabel = new JLabel("manual");
        sourceLabel.setForeground(new Color(140, 180, 140));
        form.add(sourceLabel, fieldGbc);

        // ID
        labelGbc.gridx = 0; labelGbc.gridy = row;
        form.add(createLabel("ID:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        idField = createTextField();
        idField.setToolTipText("Unique identifier");
        form.add(idField, fieldGbc);

        // Name
        labelGbc.gridx = 0; labelGbc.gridy = row;
        form.add(createLabel("Name:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        nameField = createTextField();
        form.add(nameField, fieldGbc);

        // Symbol
        labelGbc.gridx = 0; labelGbc.gridy = row;
        form.add(createLabel("Symbol:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        symbolField = createTextField();
        symbolField.setToolTipText("Ticker symbol (optional)");
        form.add(symbolField, fieldGbc);

        // Type
        labelGbc.gridx = 0; labelGbc.gridy = row;
        form.add(createLabel("Type:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        typeCombo = new JComboBox<>(CoinEntity.Type.values());
        typeCombo.setBackground(new Color(60, 62, 66));
        typeCombo.setForeground(new Color(200, 200, 210));
        form.add(typeCombo, fieldGbc);

        // Parent ID
        labelGbc.gridx = 0; labelGbc.gridy = row;
        form.add(createLabel("Parent ID:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        parentIdField = createTextField();
        parentIdField.setToolTipText("For L2s: the L1 chain ID");
        form.add(parentIdField, fieldGbc);

        // Market Cap
        labelGbc.gridx = 0; labelGbc.gridy = row;
        form.add(createLabel("Market Cap:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        marketCapField = createTextField();
        marketCapField.setToolTipText("Market cap in USD");
        form.add(marketCapField, fieldGbc);

        // Spacer
        labelGbc.gridx = 0; labelGbc.gridy = row++;
        labelGbc.weighty = 1.0;
        form.add(Box.createVerticalGlue(), labelGbc);

        panel.add(form, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(new Color(38, 40, 44));

        deleteBtn = new JButton("Delete");
        deleteBtn.setForeground(new Color(255, 100, 100));
        deleteBtn.addActionListener(e -> deleteEntity());
        deleteBtn.setEnabled(false);
        buttonPanel.add(deleteBtn);

        saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> saveEntity());
        saveBtn.setEnabled(false);
        buttonPanel.add(saveBtn);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        // Initially show placeholder
        showPlaceholder();

        return panel;
    }

    private void showPlaceholder() {
        idField.setText("");
        nameField.setText("");
        symbolField.setText("");
        typeCombo.setSelectedIndex(0);
        parentIdField.setText("");
        marketCapField.setText("");
        sourceLabel.setText("-");
        saveBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        selectedEntity = null;
        isNewEntity = false;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(180, 180, 190));
        return label;
    }

    private JTextField createTextField() {
        JTextField field = new JTextField(20);
        field.setBackground(new Color(60, 62, 66));
        field.setForeground(new Color(200, 200, 210));
        field.setCaretColor(new Color(200, 200, 210));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 72, 76)),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        return field;
    }

    private void loadEntities() {
        rootNode.removeAllChildren();

        // Group entities by source, then by type
        List<CoinEntity> allEntities = store.loadAllEntities();

        // Manual entities section
        DefaultMutableTreeNode manualNode = new DefaultMutableTreeNode("Manual Entities");
        Map<CoinEntity.Type, DefaultMutableTreeNode> manualTypeNodes = new TreeMap<>();

        // Built-in (coingecko) entities section
        DefaultMutableTreeNode builtInNode = new DefaultMutableTreeNode("CoinGecko (auto)");
        Map<CoinEntity.Type, DefaultMutableTreeNode> builtInTypeNodes = new TreeMap<>();

        for (CoinEntity entity : allEntities) {
            EntityTreeNode entityNode = new EntityTreeNode(entity);

            // Determine source from database
            boolean isManual = isManualEntity(entity.id());

            if (isManual) {
                CoinEntity.Type type = entity.type();
                manualTypeNodes.computeIfAbsent(type, t -> {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(t.name());
                    return node;
                }).add(entityNode);
            } else {
                CoinEntity.Type type = entity.type();
                builtInTypeNodes.computeIfAbsent(type, t -> {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(t.name());
                    return node;
                }).add(entityNode);
            }
        }

        // Add manual type nodes
        for (DefaultMutableTreeNode typeNode : manualTypeNodes.values()) {
            manualNode.add(typeNode);
        }
        if (manualNode.getChildCount() > 0) {
            rootNode.add(manualNode);
        }

        // Add built-in type nodes
        for (DefaultMutableTreeNode typeNode : builtInTypeNodes.values()) {
            builtInNode.add(typeNode);
        }
        if (builtInNode.getChildCount() > 0) {
            rootNode.add(builtInNode);
        }

        treeModel.reload();

        // Expand manual node by default
        if (manualNode.getChildCount() > 0) {
            entityTree.expandPath(new TreePath(new Object[]{rootNode, manualNode}));
        }
    }

    private boolean isManualEntity(String id) {
        // Check if entity is manual by querying the database
        try {
            java.lang.reflect.Method method = store.getClass().getDeclaredMethod("loadEntitiesBySource", String.class);
            List<CoinEntity> manualEntities = store.loadEntitiesBySource("manual");
            return manualEntities.stream().anyMatch(e -> e.id().equals(id));
        } catch (Exception e) {
            return false;
        }
    }

    private void onTreeSelection() {
        TreePath path = entityTree.getSelectionPath();
        if (path == null) {
            showPlaceholder();
            return;
        }

        Object selected = path.getLastPathComponent();
        if (selected instanceof EntityTreeNode entityNode) {
            showEntity(entityNode.entity);
        } else {
            showPlaceholder();
        }
    }

    private void showEntity(CoinEntity entity) {
        selectedEntity = entity;
        isNewEntity = false;

        idField.setText(entity.id());
        idField.setEnabled(false);  // Can't change ID of existing entity
        nameField.setText(entity.name());
        symbolField.setText(entity.symbol() != null ? entity.symbol() : "");
        typeCombo.setSelectedItem(entity.type());
        parentIdField.setText(entity.parentId() != null ? entity.parentId() : "");
        marketCapField.setText(entity.marketCap() > 0 ? String.valueOf((long) entity.marketCap()) : "");

        boolean isManual = isManualEntity(entity.id());
        sourceLabel.setText(isManual ? "manual" : "coingecko (auto)");
        sourceLabel.setForeground(isManual ? new Color(140, 180, 140) : new Color(140, 140, 180));

        // Only allow editing/deleting manual entities
        saveBtn.setEnabled(isManual);
        deleteBtn.setEnabled(isManual);
        nameField.setEnabled(isManual);
        symbolField.setEnabled(isManual);
        typeCombo.setEnabled(isManual);
        parentIdField.setEnabled(isManual);
        marketCapField.setEnabled(isManual);
    }

    private void createNewEntity() {
        selectedEntity = null;
        isNewEntity = true;

        idField.setText("");
        idField.setEnabled(true);
        nameField.setText("");
        nameField.setEnabled(true);
        symbolField.setText("");
        symbolField.setEnabled(true);
        typeCombo.setSelectedItem(CoinEntity.Type.VC);
        typeCombo.setEnabled(true);
        parentIdField.setText("");
        parentIdField.setEnabled(true);
        marketCapField.setText("");
        marketCapField.setEnabled(true);
        sourceLabel.setText("manual (new)");
        sourceLabel.setForeground(new Color(180, 180, 140));

        saveBtn.setEnabled(true);
        deleteBtn.setEnabled(false);

        idField.requestFocus();
    }

    private void saveEntity() {
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        String symbol = symbolField.getText().trim();
        CoinEntity.Type type = (CoinEntity.Type) typeCombo.getSelectedItem();
        String parentId = parentIdField.getText().trim();
        String marketCapStr = marketCapField.getText().trim();

        if (id.isEmpty()) {
            showError("ID is required");
            return;
        }
        if (name.isEmpty()) {
            showError("Name is required");
            return;
        }

        if (isNewEntity && store.entityExists(id)) {
            showError("An entity with ID '" + id + "' already exists");
            return;
        }

        double marketCap = 0;
        if (!marketCapStr.isEmpty()) {
            try {
                marketCap = Double.parseDouble(marketCapStr);
            } catch (NumberFormatException e) {
                showError("Invalid market cap");
                return;
            }
        }

        CoinEntity entity = new CoinEntity(
            id, name,
            symbol.isEmpty() ? null : symbol,
            type,
            parentId.isEmpty() ? null : parentId
        );
        entity.setMarketCap(marketCap);

        store.saveEntity(entity, "manual");
        loadEntities();

        if (onDataChanged != null) {
            onDataChanged.accept(null);
        }

        // Select the saved entity
        selectEntityInTree(id);
    }

    private void deleteEntity() {
        if (selectedEntity == null) return;

        int result = JOptionPane.showConfirmDialog(this,
            "Delete entity '" + selectedEntity.name() + "'?\nThis will also remove all relationships.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            store.deleteEntity(selectedEntity.id());
            loadEntities();
            showPlaceholder();

            if (onDataChanged != null) {
                onDataChanged.accept(null);
            }
        }
    }

    private void selectEntityInTree(String entityId) {
        // Find and select the entity node
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode sourceNode = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            for (int j = 0; j < sourceNode.getChildCount(); j++) {
                DefaultMutableTreeNode typeNode = (DefaultMutableTreeNode) sourceNode.getChildAt(j);
                for (int k = 0; k < typeNode.getChildCount(); k++) {
                    Object child = typeNode.getChildAt(k);
                    if (child instanceof EntityTreeNode entityNode && entityNode.entity.id().equals(entityId)) {
                        TreePath path = new TreePath(new Object[]{rootNode, sourceNode, typeNode, entityNode});
                        entityTree.setSelectionPath(path);
                        entityTree.scrollPathToVisible(path);
                        return;
                    }
                }
            }
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // Custom tree node for entities
    private static class EntityTreeNode extends DefaultMutableTreeNode {
        final CoinEntity entity;

        EntityTreeNode(CoinEntity entity) {
            super(entity);
            this.entity = entity;
        }

        @Override
        public String toString() {
            if (entity.symbol() != null) {
                return entity.name() + " (" + entity.symbol() + ")";
            }
            return entity.name();
        }
    }

    // Custom tree cell renderer
    private static class EntityTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            setBackground(sel ? new Color(60, 80, 100) : new Color(35, 37, 41));
            setBackgroundNonSelectionColor(new Color(35, 37, 41));
            setBackgroundSelectionColor(new Color(60, 80, 100));
            setTextNonSelectionColor(new Color(200, 200, 210));
            setTextSelectionColor(new Color(220, 220, 230));

            if (value instanceof EntityTreeNode entityNode) {
                setForeground(entityNode.entity.type().color());
                if (sel) {
                    setForeground(new Color(255, 255, 255));
                }
            }

            return this;
        }
    }
}
