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
    private JPanel customAttrsContent;
    private JPanel layoutTogglePanel;
    private final Map<String, JComponent> attrInputComponents = new LinkedHashMap<>();
    private int selectedFormLayoutIndex = 0;

    private CoinEntity selectedEntity;
    private boolean isNewEntity = false;

    public EntityManagerFrame(EntityStore store, Consumer<Void> onDataChanged) {
        super("Entity Manager");
        this.store = store;
        this.onDataChanged = onDataChanged;

        // Integrated macOS title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

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
        JPanel contentPane = new JPanel(new BorderLayout(0, 0));

        // 52px header bar with centered title
        JPanel hBar = new JPanel(new BorderLayout());
        hBar.setPreferredSize(new Dimension(0, 52));
        JLabel windowTitle = new JLabel("Entity Manager", SwingConstants.CENTER);
        windowTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        windowTitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        hBar.add(windowTitle, BorderLayout.CENTER);
        JPanel hWrapper = new JPanel(new BorderLayout());
        hWrapper.add(hBar, BorderLayout.CENTER);
        hWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        contentPane.add(hWrapper, BorderLayout.NORTH);

        contentPane.add(createEntitiesTab(), BorderLayout.CENTER);
        setContentPane(contentPane);
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

        // Custom Attributes section (JSeparator + header with toggle + content)
        customAttrsPanel = new JPanel(new BorderLayout());
        customAttrsPanel.setVisible(false);

        JPanel customAttrsHeader = new JPanel(new BorderLayout());
        customAttrsHeader.add(new JSeparator(), BorderLayout.NORTH);

        JPanel headerRow = new JPanel(new BorderLayout());
        JLabel customAttrsLabel = new JLabel("Custom Attributes");
        customAttrsLabel.setFont(customAttrsLabel.getFont().deriveFont(Font.BOLD, customAttrsLabel.getFont().getSize() + 1f));
        customAttrsLabel.setBorder(BorderFactory.createEmptyBorder(8, 20, 4, 0));
        headerRow.add(customAttrsLabel, BorderLayout.WEST);

        layoutTogglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        layoutTogglePanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 20));
        layoutTogglePanel.setVisible(false);
        headerRow.add(layoutTogglePanel, BorderLayout.EAST);

        customAttrsHeader.add(headerRow, BorderLayout.CENTER);
        customAttrsPanel.add(customAttrsHeader, BorderLayout.NORTH);

        customAttrsContent = new JPanel();
        customAttrsContent.setLayout(new BoxLayout(customAttrsContent, BoxLayout.Y_AXIS));
        customAttrsContent.setBorder(BorderFactory.createEmptyBorder(0, 20, 10, 20));
        customAttrsPanel.add(customAttrsContent, BorderLayout.CENTER);

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

        EntitySearchDialog dialog = new EntitySearchDialog(this, selectedEntity, store, schemaRegistry);
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
        customAttrsContent.removeAll();
        customAttrsPanel.setVisible(false);
        layoutTogglePanel.removeAll();
        layoutTogglePanel.setVisible(false);
        selectedFormLayoutIndex = 0;
        customAttrsContent.revalidate();
        customAttrsContent.repaint();
    }

    private void populateCustomAttributes(CoinEntity entity, boolean editable) {
        clearCustomAttributes();

        if (schemaRegistry == null) return;

        // Find the schema type matching this entity's type
        String typeId = entity.type().name().toLowerCase();
        SchemaType schemaType = schemaRegistry.getType(typeId);
        if (schemaType == null || schemaType.attributes().isEmpty()) return;

        // Load stored values with provenance
        Map<String, AttributeValue> richValues = store.getAttributeValuesRich(entity.id(), typeId);

        List<FormLayout> layouts = schemaType.formLayouts();
        boolean hasLayouts = layouts != null && !layouts.isEmpty();

        // Build the layout toggle if multiple layouts exist
        if (hasLayouts && layouts.size() > 1) {
            buildLayoutToggle(layouts, schemaType, richValues, editable);
        }

        // Render fields for the active layout (or all attrs if no layouts)
        if (hasLayouts) {
            FormLayout active = layouts.get(Math.min(selectedFormLayoutIndex, layouts.size() - 1));
            renderFormLayout(active, schemaType, richValues, editable);
        } else {
            renderDefaultLayout(schemaType, richValues, editable);
        }

        customAttrsContent.revalidate();
        customAttrsContent.repaint();
    }

    private void buildLayoutToggle(List<FormLayout> layouts, SchemaType schemaType,
                                    Map<String, AttributeValue> richValues, boolean editable) {
        layoutTogglePanel.removeAll();

        int maxVisible = layouts.size() <= 4 ? layouts.size() : 3;
        ButtonGroup group = new ButtonGroup();

        for (int i = 0; i < maxVisible; i++) {
            int idx = i;
            JToggleButton btn = new JToggleButton(layouts.get(i).name());
            btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 11f));
            btn.setFocusPainted(false);
            btn.setSelected(idx == selectedFormLayoutIndex);
            btn.addActionListener(e -> {
                selectedFormLayoutIndex = idx;
                rebuildFormContent(schemaType, richValues, editable);
            });
            group.add(btn);
            layoutTogglePanel.add(btn);
        }

        if (layouts.size() > 4) {
            JButton moreBtn = new JButton("...");
            moreBtn.setFont(moreBtn.getFont().deriveFont(Font.PLAIN, 11f));
            moreBtn.setFocusPainted(false);
            moreBtn.addActionListener(e -> {
                JPopupMenu popup = new JPopupMenu();
                for (int i = 3; i < layouts.size(); i++) {
                    int idx = i;
                    JMenuItem item = new JMenuItem(layouts.get(i).name());
                    item.addActionListener(ev -> {
                        selectedFormLayoutIndex = idx;
                        // Rebuild toggle to reflect new selection
                        buildLayoutToggle(layouts, schemaType, richValues, editable);
                        rebuildFormContent(schemaType, richValues, editable);
                    });
                    popup.add(item);
                }
                popup.show(moreBtn, 0, moreBtn.getHeight());
            });
            layoutTogglePanel.add(moreBtn);
        }

        layoutTogglePanel.setVisible(true);
        layoutTogglePanel.revalidate();
    }

    private void rebuildFormContent(SchemaType schemaType,
                                     Map<String, AttributeValue> richValues, boolean editable) {
        attrInputComponents.clear();
        customAttrsContent.removeAll();

        List<FormLayout> layouts = schemaType.formLayouts();
        if (layouts != null && !layouts.isEmpty()) {
            FormLayout active = layouts.get(Math.min(selectedFormLayoutIndex, layouts.size() - 1));
            renderFormLayout(active, schemaType, richValues, editable);
        } else {
            renderDefaultLayout(schemaType, richValues, editable);
        }

        customAttrsContent.revalidate();
        customAttrsContent.repaint();
    }

    /** Render a specific FormLayout with row grouping. */
    private void renderFormLayout(FormLayout layout, SchemaType schemaType,
                                   Map<String, AttributeValue> richValues, boolean editable) {
        // Build rows: group fields with the same group string
        List<List<FormLayout.FormLayoutField>> rows = new ArrayList<>();
        Map<String, List<FormLayout.FormLayoutField>> groupMap = new LinkedHashMap<>();

        for (FormLayout.FormLayoutField f : layout.fields()) {
            if (f.group() != null && !f.group().isEmpty()) {
                groupMap.computeIfAbsent(f.group(), k -> new ArrayList<>()).add(f);
            } else {
                // Own row
                rows.add(List.of(f));
            }
        }

        // Insert grouped rows at position of first field in each group
        Set<String> insertedGroups = new HashSet<>();
        List<List<FormLayout.FormLayoutField>> orderedRows = new ArrayList<>();
        for (FormLayout.FormLayoutField f : layout.fields()) {
            if (f.group() != null && !f.group().isEmpty()) {
                if (!insertedGroups.contains(f.group())) {
                    List<FormLayout.FormLayoutField> groupFields = groupMap.get(f.group());
                    // Sort by displayOrder of referenced attribute
                    groupFields.sort(Comparator.comparingInt(gf -> {
                        SchemaAttribute a = findAttribute(schemaType, gf.attributeName());
                        return a != null ? a.displayOrder() : 0;
                    }));
                    orderedRows.add(groupFields);
                    insertedGroups.add(f.group());
                }
            } else {
                orderedRows.add(List.of(f));
            }
        }

        renderRows(orderedRows, schemaType, richValues, editable);
    }

    /** Default layout: all non-core attributes, one per row. */
    private void renderDefaultLayout(SchemaType schemaType,
                                      Map<String, AttributeValue> richValues, boolean editable) {
        List<List<FormLayout.FormLayoutField>> rows = new ArrayList<>();
        for (SchemaAttribute attr : schemaType.attributes()) {
            if ("name".equals(attr.name()) || "symbol".equals(attr.name()) ||
                "market_cap".equals(attr.name())) continue;
            rows.add(List.of(new FormLayout.FormLayoutField(attr.name(), null)));
        }
        renderRows(rows, schemaType, richValues, editable);
    }

    /** Render rows of fields into the customAttrsContent panel using GridBagLayout. */
    private void renderRows(List<List<FormLayout.FormLayoutField>> rows, SchemaType schemaType,
                             Map<String, AttributeValue> richValues, boolean editable) {
        // Compute max columns (for grid alignment)
        int maxCols = 1;
        for (List<FormLayout.FormLayoutField> row : rows) {
            maxCols = Math.max(maxCols, row.size());
        }

        JPanel grid = new JPanel(new GridBagLayout());
        int gridRow = 0;
        boolean hasContent = false;

        for (List<FormLayout.FormLayoutField> row : rows) {
            int colOffset = 0;
            for (int c = 0; c < row.size(); c++) {
                FormLayout.FormLayoutField f = row.get(c);
                SchemaAttribute attr = findAttribute(schemaType, f.attributeName());
                if (attr == null) continue;

                String displayLabel = attr.displayName(Locale.getDefault());
                AttributeValue av = richValues.get(attr.name());
                String storedValue = av != null ? av.value() : "";
                boolean attrEditable = editable && !attr.isSource();

                // Label
                GridBagConstraints lc = new GridBagConstraints();
                lc.anchor = GridBagConstraints.WEST;
                lc.insets = new Insets(4, c == 0 ? 5 : 15, 4, 10);
                lc.gridx = colOffset;
                lc.gridy = gridRow;

                JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
                labelPanel.setOpaque(false);
                labelPanel.add(new JLabel(displayLabel + ":"));
                if (attr.isDerived() && av != null && av.value() != null && !av.value().isEmpty()) {
                    labelPanel.add(createOriginBadge(av.origin()));
                }
                grid.add(labelPanel, lc);

                // Field
                GridBagConstraints fc = new GridBagConstraints();
                fc.fill = GridBagConstraints.HORIZONTAL;
                fc.insets = new Insets(4, 0, 4, 5);
                fc.gridx = colOffset + 1;
                fc.gridy = gridRow;
                // If single column in a multi-col layout, span remaining columns
                if (row.size() == 1 && maxCols > 1) {
                    fc.gridwidth = maxCols * 2 - 1;
                }
                fc.weightx = 1.0;

                JComponent input = createInputForAttribute(attr, storedValue, attrEditable);
                grid.add(input, fc);
                attrInputComponents.put(attr.name(), input);

                hasContent = true;
                colOffset += 2;
            }
            gridRow++;
        }

        if (hasContent) {
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            customAttrsContent.add(grid);
            customAttrsPanel.setVisible(true);
        }
    }

    private SchemaAttribute findAttribute(SchemaType schemaType, String name) {
        for (SchemaAttribute a : schemaType.attributes()) {
            if (a.name().equals(name)) return a;
        }
        return null;
    }

    private JLabel createOriginBadge(AttributeValue.Origin origin) {
        String text = switch (origin) {
            case SOURCE -> "src";
            case AI -> "ai";
            case USER -> "user";
        };
        Color color = switch (origin) {
            case SOURCE -> new Color(120, 140, 160);   // blue-grey
            case AI -> new Color(200, 170, 80);         // amber
            case USER -> new Color(100, 170, 100);      // green
        };
        JLabel badge = new JLabel(text);
        badge.setFont(new Font("SansSerif", Font.ITALIC, 9));
        badge.setForeground(color);
        return badge;
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
            // Skip SOURCE attributes — they're read-only
            if (attr.isSource()) continue;

            JComponent comp = attrInputComponents.get(attr.name());
            if (comp == null) continue;

            String value = readValueFromComponent(comp, attr);
            if (!value.isEmpty()) {
                store.saveAttributeValue(entityId, typeId, attr.name(), value, AttributeValue.Origin.USER);
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
