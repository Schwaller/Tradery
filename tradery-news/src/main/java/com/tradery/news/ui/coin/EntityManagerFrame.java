package com.tradery.news.ui.coin;

import com.tradery.news.fetch.RssFetcher;
import com.tradery.news.ui.IntelConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Settings window with tabs for Entities and Relationships.
 */
public class EntityManagerFrame extends JFrame {

    private final EntityStore store;
    private final Consumer<Void> onDataChanged;

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

    private CoinEntity selectedEntity;
    private boolean isNewEntity = false;

    // Relationships tab components
    private JTable relationshipTable;
    private DefaultTableModel relationshipTableModel;

    // News Sources tab components
    private JList<RssFetcher> rssSourceList;
    private DefaultListModel<RssFetcher> rssSourceListModel;

    public EntityManagerFrame(EntityStore store, Consumer<Void> onDataChanged) {
        super("Settings");
        this.store = store;
        this.onDataChanged = onDataChanged;

        setSize(950, 650);
        setLocationRelativeTo(null);
        initUI();
        loadEntities();
        loadRelationships();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(30, 32, 36));

        // Tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(38, 40, 44));
        tabbedPane.setForeground(new Color(200, 200, 210));

        // Entities tab
        tabbedPane.addTab("Entities", createEntitiesTab());

        // Relationships tab
        tabbedPane.addTab("Relationships", createRelationshipsTab());

        // News Sources tab
        tabbedPane.addTab("News Sources", createNewsSourcesTab());

        // Config tab
        tabbedPane.addTab("AI", createAiConfigTab());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    private JPanel createEntitiesTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 32, 36));

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

        panel.add(toolbar, BorderLayout.NORTH);

        // Left: Tree navigation
        JPanel leftPanel = createLeftPanel();
        leftPanel.setPreferredSize(new Dimension(300, 0));

        // Right: Detail panel
        detailPanel = createDetailPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, detailPanel);
        splitPane.setDividerLocation(300);
        splitPane.setDividerSize(4);
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createRelationshipsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 32, 36));

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(38, 40, 44));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        JButton addRelBtn = new JButton("+ Add Relationship");
        addRelBtn.addActionListener(e -> showAddRelationshipDialog());
        toolbar.add(addRelBtn);

        JButton deleteRelBtn = new JButton("Delete Selected");
        deleteRelBtn.addActionListener(e -> deleteSelectedRelationship());
        toolbar.add(deleteRelBtn);

        JButton refreshRelBtn = new JButton("Refresh");
        refreshRelBtn.addActionListener(e -> loadRelationships());
        toolbar.add(refreshRelBtn);

        panel.add(toolbar, BorderLayout.NORTH);

        // Relationship table
        String[] columns = {"From", "Relationship", "To", "Note", "Source"};
        relationshipTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        relationshipTable = new JTable(relationshipTableModel);
        relationshipTable.setBackground(new Color(35, 37, 41));
        relationshipTable.setForeground(new Color(200, 200, 210));
        relationshipTable.setGridColor(new Color(50, 52, 56));
        relationshipTable.setSelectionBackground(new Color(60, 80, 100));
        relationshipTable.setSelectionForeground(new Color(220, 220, 230));
        relationshipTable.getTableHeader().setBackground(new Color(45, 47, 51));
        relationshipTable.getTableHeader().setForeground(new Color(180, 180, 190));
        relationshipTable.setRowHeight(24);

        // Column widths
        relationshipTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        relationshipTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        relationshipTable.getColumnModel().getColumn(2).setPreferredWidth(180);
        relationshipTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        relationshipTable.getColumnModel().getColumn(4).setPreferredWidth(80);

        JScrollPane scroll = new JScrollPane(relationshipTable);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(new Color(35, 37, 41));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createNewsSourcesTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 32, 36));

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(38, 40, 44));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        JButton addRssBtn = new JButton("+ Add Feed");
        addRssBtn.addActionListener(e -> showAddRssFeedDialog());
        toolbar.add(addRssBtn);

        JButton removeRssBtn = new JButton("Remove");
        removeRssBtn.addActionListener(e -> removeSelectedRssFeed());
        toolbar.add(removeRssBtn);

        toolbar.addSeparator();

        JButton resetBtn = new JButton("Reset to Defaults");
        resetBtn.addActionListener(e -> resetRssFeedsToDefaults());
        toolbar.add(resetBtn);

        panel.add(toolbar, BorderLayout.NORTH);

        // List of RSS sources
        rssSourceListModel = new DefaultListModel<>();
        rssSourceList = new JList<>(rssSourceListModel);
        rssSourceList.setBackground(new Color(35, 37, 41));
        rssSourceList.setForeground(new Color(200, 200, 210));
        rssSourceList.setSelectionBackground(new Color(60, 80, 100));
        rssSourceList.setSelectionForeground(new Color(220, 220, 230));
        rssSourceList.setCellRenderer(new RssSourceCellRenderer());

        JScrollPane scroll = new JScrollPane(rssSourceList);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(new Color(35, 37, 41));
        panel.add(scroll, BorderLayout.CENTER);

        // Load sources
        loadNewsSources();

        return panel;
    }

    private void loadNewsSources() {
        if (rssSourceListModel == null) return;
        rssSourceListModel.clear();

        // Add built-in sources
        for (RssFetcher fetcher : RssFetcher.defaultSources()) {
            rssSourceListModel.addElement(fetcher);
        }

        // Add custom sources from database (NEWS_SOURCE entities)
        for (CoinEntity entity : store.loadEntitiesBySource("manual")) {
            if (entity.type() == CoinEntity.Type.NEWS_SOURCE && entity.symbol() != null) {
                // symbol contains the RSS URL for custom feeds
                RssFetcher customFetcher = new RssFetcher(
                    entity.id().replace("rss-", ""),
                    entity.name(),
                    entity.symbol()  // URL stored in symbol field
                );
                rssSourceListModel.addElement(customFetcher);
            }
        }
    }

    private static class RssSourceCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof RssFetcher fetcher) {
                setText(fetcher.getSourceName());
                setForeground(isSelected ? new Color(220, 220, 230) : CoinEntity.Type.NEWS_SOURCE.color());
            }
            return this;
        }
    }

    private JPanel createAiConfigTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 32, 36));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        IntelConfig config = IntelConfig.get();

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(30, 32, 36));

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(10, 5, 10, 15);

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = new Insets(10, 0, 10, 5);

        int row = 0;

        // AI Provider
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel providerLabel = new JLabel("AI Provider:");
        providerLabel.setForeground(new Color(180, 180, 190));
        formPanel.add(providerLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JComboBox<IntelConfig.AiProvider> providerCombo = new JComboBox<>(IntelConfig.AiProvider.values());
        providerCombo.setSelectedItem(config.getAiProvider());
        providerCombo.setBackground(new Color(60, 62, 66));
        providerCombo.setForeground(new Color(200, 200, 210));
        formPanel.add(providerCombo, fieldGbc);

        // Claude path
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel claudeLabel = new JLabel("Claude CLI path:");
        claudeLabel.setForeground(new Color(180, 180, 190));
        formPanel.add(claudeLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField claudeField = new JTextField(config.getClaudePath());
        claudeField.setBackground(new Color(60, 62, 66));
        claudeField.setForeground(new Color(200, 200, 210));
        claudeField.setCaretColor(new Color(200, 200, 210));
        formPanel.add(claudeField, fieldGbc);

        // Codex path
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel codexLabel = new JLabel("Codex CLI path:");
        codexLabel.setForeground(new Color(180, 180, 190));
        formPanel.add(codexLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField codexField = new JTextField(config.getCodexPath());
        codexField.setBackground(new Color(60, 62, 66));
        codexField.setForeground(new Color(200, 200, 210));
        codexField.setCaretColor(new Color(200, 200, 210));
        formPanel.add(codexField, fieldGbc);

        // Timeout
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel timeoutLabel = new JLabel("Timeout (seconds):");
        timeoutLabel.setForeground(new Color(180, 180, 190));
        formPanel.add(timeoutLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JSpinner timeoutSpinner = new JSpinner(new SpinnerNumberModel(config.getAiTimeoutSeconds(), 10, 300, 10));
        formPanel.add(timeoutSpinner, fieldGbc);

        // Spacer
        labelGbc.gridx = 0; labelGbc.gridy = row;
        labelGbc.weighty = 1.0;
        formPanel.add(new JLabel(), labelGbc);

        panel.add(formPanel, BorderLayout.NORTH);

        // Save button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(new Color(30, 32, 36));

        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> {
            config.setAiProvider((IntelConfig.AiProvider) providerCombo.getSelectedItem());
            config.setClaudePath(claudeField.getText().trim());
            config.setCodexPath(codexField.getText().trim());
            config.setAiTimeoutSeconds((Integer) timeoutSpinner.getValue());
            config.save();
            JOptionPane.showMessageDialog(this, "AI settings saved.", "Saved", JOptionPane.INFORMATION_MESSAGE);
        });
        buttonPanel.add(saveBtn);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadRelationships() {
        if (relationshipTableModel == null) return;
        relationshipTableModel.setRowCount(0);

        List<CoinRelationship> relationships = store.loadAllRelationships();
        for (CoinRelationship rel : relationships) {
            String fromName = getEntityDisplayName(rel.fromId());
            String toName = getEntityDisplayName(rel.toId());

            relationshipTableModel.addRow(new Object[]{
                fromName,
                rel.type().label(),
                toName,
                rel.note() != null ? rel.note() : "",
                "manual"  // Source is manual by default for visible relationships
            });
        }
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

    private void showAddRelationshipDialog() {
        List<CoinEntity> allEntities = store.loadAllEntities();
        RelationshipEditorDialog dialog = new RelationshipEditorDialog(
            this, store, allEntities, null,
            rel -> {
                loadRelationships();
                if (onDataChanged != null) {
                    onDataChanged.accept(null);
                }
            }
        );
        dialog.setVisible(true);
    }

    private void deleteSelectedRelationship() {
        int row = relationshipTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a relationship to delete",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
            "Delete this relationship?", "Confirm Delete",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            // Get relationship data from table
            List<CoinRelationship> relationships = store.loadAllRelationships();
            if (row < relationships.size()) {
                CoinRelationship rel = relationships.get(row);
                store.deleteRelationship(rel.fromId(), rel.toId(), rel.type());
                loadRelationships();
                if (onDataChanged != null) {
                    onDataChanged.accept(null);
                }
            }
        }
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

        // Categories
        labelGbc.gridx = 0; labelGbc.gridy = row;
        labelGbc.anchor = GridBagConstraints.NORTHWEST;
        form.add(createLabel("Categories:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        fieldGbc.fill = GridBagConstraints.BOTH;
        fieldGbc.weighty = 1.0;
        categoriesArea = new JTextArea(4, 20);
        categoriesArea.setBackground(new Color(50, 52, 56));
        categoriesArea.setForeground(new Color(180, 200, 180));
        categoriesArea.setCaretColor(new Color(200, 200, 210));
        categoriesArea.setEditable(false);
        categoriesArea.setLineWrap(true);
        categoriesArea.setWrapStyleWord(true);
        categoriesArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
        JScrollPane catScroll = new JScrollPane(categoriesArea);
        catScroll.setBorder(BorderFactory.createLineBorder(new Color(70, 72, 76)));
        form.add(catScroll, fieldGbc);

        // Reset constraints
        labelGbc.anchor = GridBagConstraints.WEST;
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weighty = 0;

        panel.add(form, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(new Color(38, 40, 44));

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

    private void showAddRssFeedDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
        JTextField idField = new JTextField();
        JTextField nameField = new JTextField();
        JTextField urlField = new JTextField();

        panel.add(new JLabel("ID (unique):"));
        panel.add(idField);
        panel.add(new JLabel("Name:"));
        panel.add(nameField);
        panel.add(new JLabel("RSS URL:"));
        panel.add(urlField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add RSS Feed",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String id = idField.getText().trim();
            String name = nameField.getText().trim();
            String url = urlField.getText().trim();

            if (id.isEmpty() || name.isEmpty() || url.isEmpty()) {
                showError("All fields are required");
                return;
            }

            // Save as a NEWS_SOURCE entity with URL in symbol field
            CoinEntity rssEntity = new CoinEntity("rss-" + id, name, url, CoinEntity.Type.NEWS_SOURCE);
            store.saveEntity(rssEntity, "manual");
            loadEntities();
            loadNewsSources();

            if (onDataChanged != null) {
                onDataChanged.accept(null);
            }
        }
    }

    private void removeSelectedRssFeed() {
        RssFetcher selected = rssSourceList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a feed to remove", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check if it's a custom feed (exists in database)
        String entityId = "rss-" + selected.getSourceId();
        if (!store.entityExists(entityId)) {
            JOptionPane.showMessageDialog(this, "Built-in feeds cannot be removed", "Cannot Remove", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
            "Remove RSS feed '" + selected.getSourceName() + "'?",
            "Confirm Remove", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            store.deleteEntity(entityId);
            loadEntities();
            loadNewsSources();
        }
    }

    private void resetRssFeedsToDefaults() {
        int result = JOptionPane.showConfirmDialog(this,
            "Reset RSS feeds to factory defaults?\nThis will remove any custom feeds.",
            "Reset to Defaults", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            // Delete all manual NEWS_SOURCE entities
            for (CoinEntity entity : store.loadEntitiesBySource("manual")) {
                if (entity.type() == CoinEntity.Type.NEWS_SOURCE) {
                    store.deleteEntity(entity.id());
                }
            }
            loadEntities();
            loadNewsSources();
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
