package com.tradery.news.ui.coin;

import com.tradery.news.ui.IntelConfig;

import com.tradery.ui.controls.ThinSplitPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Settings window with tabs for Entities and Relationships.
 */
public class EntityManagerFrame extends JFrame {

    private final EntityStore store;
    private final Consumer<Void> onDataChanged;
    private SchemaRegistry schemaRegistry;

    // Entity tab components
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
    private JTextArea categoriesArea;
    private JLabel sourceLabel;
    private JButton saveBtn;
    private JButton deleteBtn;
    private JButton searchRelatedBtn;

    // Custom attributes section
    private JPanel customAttrsPanel;
    private final Map<String, JComponent> attrInputComponents = new LinkedHashMap<>();

    private CoinEntity selectedEntity;
    private boolean isNewEntity = false;

    public EntityManagerFrame(EntityStore store, Consumer<Void> onDataChanged) {
        super("Entity Manager");
        this.store = store;
        this.onDataChanged = onDataChanged;

        // Restore window size/position from config or use large default
        IntelConfig config = IntelConfig.get();
        if (config.getSettingsWidth() > 0 && config.getSettingsHeight() > 0) {
            setSize(config.getSettingsWidth(), config.getSettingsHeight());
            if (config.getSettingsX() >= 0 && config.getSettingsY() >= 0) {
                setLocation(config.getSettingsX(), config.getSettingsY());
            } else {
                setLocationRelativeTo(null);
            }
        } else {
            // Default: 80% of screen size
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int width = (int) (screen.width * 0.8);
            int height = (int) (screen.height * 0.8);
            setSize(width, height);
            setLocationRelativeTo(null);
        }

        // Save window state on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                IntelConfig cfg = IntelConfig.get();
                cfg.setSettingsWidth(getWidth());
                cfg.setSettingsHeight(getHeight());
                cfg.setSettingsX(getX());
                cfg.setSettingsY(getY());
                cfg.save();
            }
        });

        initUI();
        loadEntities();
    }

    public void setSchemaRegistry(SchemaRegistry registry) {
        this.schemaRegistry = registry;
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        mainPanel.add(createEntitiesTab(), BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    private JPanel createEntitiesTab() {
        JPanel panel = new JPanel(new BorderLayout());

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton addBtn = new JButton("+ New Entity");
        addBtn.addActionListener(e -> createNewEntity());
        toolbar.add(addBtn);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadEntities());
        toolbar.add(refreshBtn);

        panel.add(toolbar, BorderLayout.NORTH);

        // Left: Tree navigation
        JPanel leftPanel = createLeftPanel();
        leftPanel.setPreferredSize(new Dimension(300, 0));

        // Right: Detail panel
        detailPanel = createDetailPanel();

        ThinSplitPane splitPane = new ThinSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, detailPanel);
        splitPane.setDividerLocation(300);
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private String getEntityDisplayName(String entityId) {
        CoinEntity entity = store.getEntity(entityId);
        if (entity != null) {
            if (entity.symbol() != null) {
                return entity.name() + " (" + entity.symbol() + ")";
            }
            return entity.name();
        }
        return entityId;
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        rootNode = new DefaultMutableTreeNode("Entities");
        treeModel = new DefaultTreeModel(rootNode);
        entityTree = new JTree(treeModel);
        entityTree.setRootVisible(false);
        entityTree.setShowsRootHandles(true);
        entityTree.setCellRenderer(new EntityTreeCellRenderer());
        entityTree.addTreeSelectionListener(e -> onTreeSelection());

        JScrollPane scroll = new JScrollPane(entityTree);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        // Vertical separator on the right
        panel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.EAST);

        return panel;
    }

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Form
        JPanel form = new JPanel(new GridBagLayout());

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

        // Categories
        labelGbc.gridx = 0; labelGbc.gridy = row;
        labelGbc.anchor = GridBagConstraints.NORTHWEST;
        form.add(createLabel("Categories:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        fieldGbc.fill = GridBagConstraints.BOTH;
        fieldGbc.weighty = 1.0;
        categoriesArea = new JTextArea(4, 20);
        categoriesArea.setEditable(false);
        categoriesArea.setLineWrap(true);
        categoriesArea.setWrapStyleWord(true);
        categoriesArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
        JScrollPane catScroll = new JScrollPane(categoriesArea);
        form.add(catScroll, fieldGbc);

        // Reset constraints
        labelGbc.anchor = GridBagConstraints.WEST;
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weighty = 0;

        // Custom Attributes section
        customAttrsPanel = new JPanel();
        customAttrsPanel.setLayout(new BoxLayout(customAttrsPanel, BoxLayout.Y_AXIS));
        customAttrsPanel.setBorder(BorderFactory.createTitledBorder("Custom Attributes"));
        customAttrsPanel.setVisible(false);

        // Wrap form + custom attrs in a vertical box inside a scroll pane
        JPanel formWrapper = new JPanel();
        formWrapper.setLayout(new BoxLayout(formWrapper, BoxLayout.Y_AXIS));
        formWrapper.add(form);
        formWrapper.add(Box.createVerticalStrut(10));
        formWrapper.add(customAttrsPanel);
        formWrapper.add(Box.createVerticalGlue());

        JScrollPane formScroll = new JScrollPane(formWrapper);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(formScroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        searchRelatedBtn = new JButton("Search Related...");
        searchRelatedBtn.setToolTipText("Use AI to discover related entities");
        searchRelatedBtn.addActionListener(e -> showSearchRelatedDialog());
        searchRelatedBtn.setEnabled(false);
        buttonPanel.add(searchRelatedBtn);

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
        categoriesArea.setText("");
        sourceLabel.setText("-");
        saveBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        searchRelatedBtn.setEnabled(false);
        selectedEntity = null;
        isNewEntity = false;
        clearCustomAttributes();
    }

    private JLabel createLabel(String text) {
        return new JLabel(text);
    }

    private JTextField createTextField() {
        JTextField field = new JTextField(20);
        field.setBorder(BorderFactory.createCompoundBorder(
            field.getBorder(),
            BorderFactory.createEmptyBorder(3, 4, 3, 4)
        ));
        return field;
    }

    private Set<String> manualEntityIds = new HashSet<>();

    private void loadEntities() {
        rootNode.removeAllChildren();

        // Load manual entity IDs first for quick lookup (exclude NEWS_SOURCE - shown in separate tab)
        List<CoinEntity> manualEntities = store.loadEntitiesBySource("manual");
        manualEntityIds.clear();
        int manualCount = 0;
        for (CoinEntity e : manualEntities) {
            if (e.type() != CoinEntity.Type.NEWS_SOURCE) {
                manualEntityIds.add(e.id());
                manualCount++;
            }
        }

        // Load CoinGecko entities
        List<CoinEntity> cgEntities = store.loadEntitiesBySource("coingecko");

        // Manual entities section
        DefaultMutableTreeNode manualNode = new DefaultMutableTreeNode("Manual (" + manualCount + ")");
        Map<CoinEntity.Type, DefaultMutableTreeNode> manualTypeNodes = new TreeMap<>();

        // CoinGecko entities section
        DefaultMutableTreeNode cgNode = new DefaultMutableTreeNode("CoinGecko (" + cgEntities.size() + ")");
        Map<CoinEntity.Type, DefaultMutableTreeNode> cgTypeNodes = new TreeMap<>();

        // Add manual entities (excluding NEWS_SOURCE)
        for (CoinEntity entity : manualEntities) {
            if (entity.type() == CoinEntity.Type.NEWS_SOURCE) continue;
            EntityTreeNode entityNode = new EntityTreeNode(entity);
            CoinEntity.Type type = entity.type();
            manualTypeNodes.computeIfAbsent(type, t -> new DefaultMutableTreeNode(t.name())).add(entityNode);
        }

        // Add CoinGecko entities
        for (CoinEntity entity : cgEntities) {
            EntityTreeNode entityNode = new EntityTreeNode(entity);
            CoinEntity.Type type = entity.type();
            cgTypeNodes.computeIfAbsent(type, t -> new DefaultMutableTreeNode(t.name())).add(entityNode);
        }

        // Add manual type nodes to tree
        for (CoinEntity.Type type : CoinEntity.Type.values()) {
            if (type == CoinEntity.Type.NEWS_SOURCE) continue;
            DefaultMutableTreeNode typeNode = manualTypeNodes.get(type);
            if (typeNode != null) {
                manualNode.add(typeNode);
            }
        }
        rootNode.add(manualNode);

        // Add CoinGecko type nodes to tree
        for (CoinEntity.Type type : CoinEntity.Type.values()) {
            if (type == CoinEntity.Type.NEWS_SOURCE) continue;
            DefaultMutableTreeNode typeNode = cgTypeNodes.get(type);
            if (typeNode != null) {
                cgNode.add(typeNode);
            }
        }
        rootNode.add(cgNode);

        treeModel.reload();

        // Expand manual node by default
        entityTree.expandPath(new TreePath(new Object[]{rootNode, manualNode}));
    }

    private boolean isManualEntity(String id) {
        return manualEntityIds.contains(id);
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

        // Categories
        if (entity.categories().isEmpty()) {
            categoriesArea.setText("(none)");
        } else {
            categoriesArea.setText(String.join(", ", entity.categories()));
        }

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

        // Enable search related for entity types that support it
        boolean canSearch = entity.type() != CoinEntity.Type.NEWS_SOURCE;
        searchRelatedBtn.setEnabled(canSearch);

        // Custom attributes
        populateCustomAttributes(entity, isManual);
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
        categoriesArea.setText("(add comma-separated categories)");
        sourceLabel.setText("manual (new)");
        sourceLabel.setForeground(new Color(180, 180, 140));

        saveBtn.setEnabled(true);
        deleteBtn.setEnabled(false);
        clearCustomAttributes();

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

        // Save custom attribute values
        saveCustomAttributeValues(id, type);

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

    private void showSearchRelatedDialog() {
        if (selectedEntity == null) return;

        EntitySearchDialog dialog = new EntitySearchDialog(this, selectedEntity, store);
        dialog.setVisible(true);

        // Refresh after dialog closes in case entities were added
        loadEntities();
        if (onDataChanged != null) {
            onDataChanged.accept(null);
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

    // ==================== CUSTOM ATTRIBUTES ====================

    private void clearCustomAttributes() {
        attrInputComponents.clear();
        customAttrsPanel.removeAll();
        customAttrsPanel.setVisible(false);
        customAttrsPanel.revalidate();
        customAttrsPanel.repaint();
    }

    private void populateCustomAttributes(CoinEntity entity, boolean editable) {
        clearCustomAttributes();

        if (schemaRegistry == null) return;

        // Find the schema type matching this entity's type
        String typeId = entity.type().name().toLowerCase();
        SchemaType schemaType = schemaRegistry.getType(typeId);
        if (schemaType == null || schemaType.attributes().isEmpty()) return;

        // Load stored values
        Map<String, String> storedValues = store.getAttributeValues(entity.id(), typeId);

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 5, 4, 10);
        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(4, 0, 4, 5);

        int row = 0;
        for (SchemaAttribute attr : schemaType.attributes()) {
            // Skip hardcoded fields
            if ("name".equals(attr.name()) || "symbol".equals(attr.name()) ||
                "market_cap".equals(attr.name())) continue;

            String displayLabel = attr.displayName(Locale.getDefault());
            String storedValue = storedValues.getOrDefault(attr.name(), "");

            lc.gridx = 0; lc.gridy = row;
            grid.add(new JLabel(displayLabel + ":"), lc);

            fc.gridx = 1; fc.gridy = row;
            JComponent input = createInputForAttribute(attr, storedValue, editable);
            grid.add(input, fc);
            attrInputComponents.put(attr.name(), input);

            row++;
        }

        if (row > 0) {
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            customAttrsPanel.add(grid);
            customAttrsPanel.setVisible(true);
        }

        customAttrsPanel.revalidate();
        customAttrsPanel.repaint();
    }

    private JComponent createInputForAttribute(SchemaAttribute attr, String value, boolean editable) {
        return switch (attr.dataType()) {
            case SchemaAttribute.BOOLEAN -> {
                JCheckBox cb = new JCheckBox();
                cb.setSelected("true".equalsIgnoreCase(value));
                cb.setEnabled(editable);
                yield cb;
            }
            case SchemaAttribute.ENUM -> {
                List<?> values = attr.configValue("values");
                JComboBox<String> combo = new JComboBox<>();
                combo.addItem(""); // empty option
                if (values != null) {
                    for (Object v : values) combo.addItem(String.valueOf(v));
                }
                combo.setSelectedItem(value);
                combo.setEnabled(editable);
                yield combo;
            }
            case SchemaAttribute.LIST -> {
                JTextArea area = new JTextArea(3, 20);
                area.setText(value);
                area.setEditable(editable);
                area.setLineWrap(true);
                JScrollPane sp = new JScrollPane(area);
                yield sp;
            }
            case SchemaAttribute.URL -> {
                JPanel urlPanel = new JPanel(new BorderLayout(5, 0));
                JTextField tf = new JTextField(value, 20);
                tf.setEnabled(editable);
                urlPanel.add(tf, BorderLayout.CENTER);
                JButton openBtn = new JButton("Open");
                openBtn.addActionListener(e -> {
                    String url = tf.getText().trim();
                    if (!url.isEmpty()) {
                        try {
                            Desktop.getDesktop().browse(java.net.URI.create(url));
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                });
                urlPanel.add(openBtn, BorderLayout.EAST);
                // Store the text field reference for reading value later
                urlPanel.putClientProperty("textField", tf);
                yield urlPanel;
            }
            default -> {
                // TEXT, NUMBER, CURRENCY, PERCENTAGE, DATE, TIME, DATETIME, DATETIME_TZ
                JTextField tf = new JTextField(value, 20);
                tf.setEnabled(editable);
                String hint = switch (attr.dataType()) {
                    case SchemaAttribute.CURRENCY -> {
                        String sym = attr.configValue("currencySymbol", "");
                        yield sym + " amount";
                    }
                    case SchemaAttribute.PERCENTAGE -> "0.15 = 15%";
                    case SchemaAttribute.DATE -> attr.configValue("format", "yyyy-MM-dd");
                    case SchemaAttribute.TIME -> attr.configValue("format", "HH:mm");
                    case SchemaAttribute.DATETIME -> "epoch ms";
                    case SchemaAttribute.DATETIME_TZ -> "ISO-8601 with zone";
                    case SchemaAttribute.NUMBER -> {
                        String unit = attr.configValue("unit");
                        yield unit != null ? unit : "";
                    }
                    default -> "";
                };
                if (!hint.isEmpty()) tf.setToolTipText(hint);
                yield tf;
            }
        };
    }

    private String readValueFromComponent(JComponent comp, SchemaAttribute attr) {
        if (comp instanceof JCheckBox cb) {
            return String.valueOf(cb.isSelected());
        } else if (comp instanceof JComboBox<?> combo) {
            Object sel = combo.getSelectedItem();
            return sel != null ? sel.toString() : "";
        } else if (comp instanceof JScrollPane sp && sp.getViewport().getView() instanceof JTextArea ta) {
            return ta.getText().trim();
        } else if (comp instanceof JPanel panel) {
            // URL panel with textField in client property
            Object tf = panel.getClientProperty("textField");
            if (tf instanceof JTextField textField) {
                return textField.getText().trim();
            }
        } else if (comp instanceof JTextField tf) {
            return tf.getText().trim();
        }
        return "";
    }

    private void saveCustomAttributeValues(String entityId, CoinEntity.Type entityType) {
        if (schemaRegistry == null || attrInputComponents.isEmpty()) return;

        String typeId = entityType.name().toLowerCase();
        SchemaType schemaType = schemaRegistry.getType(typeId);
        if (schemaType == null) return;

        for (SchemaAttribute attr : schemaType.attributes()) {
            JComponent comp = attrInputComponents.get(attr.name());
            if (comp == null) continue;

            String value = readValueFromComponent(comp, attr);
            if (!value.isEmpty()) {
                store.saveAttributeValue(entityId, typeId, attr.name(), value);
            }
        }
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

    // Custom tree cell renderer - uses FlatLaf defaults, just colorizes entity type
    private static class EntityTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof EntityTreeNode entityNode && !sel) {
                setForeground(entityNode.entity.type().color());
            }

            return this;
        }
    }
}
