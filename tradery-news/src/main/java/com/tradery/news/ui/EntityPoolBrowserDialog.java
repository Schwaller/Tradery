package com.tradery.news.ui;

import com.tradery.news.ui.coin.CoinEntity;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.coin.SchemaType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog for browsing the entity pool in USER_CURATED mode.
 * Shows unaccepted entities with search, multi-select, and accept actions.
 */
public class EntityPoolBrowserDialog extends JDialog {

    private final EntityStore entityStore;
    private final SchemaRegistry schemaRegistry;
    private final Runnable onChanged;

    private DefaultListModel<CoinEntity> listModel;
    private JList<CoinEntity> entityList;
    private JTextField searchField;
    private JLabel countLabel;
    private List<CoinEntity> allUnaccepted = new ArrayList<>();

    public EntityPoolBrowserDialog(JFrame owner, EntityStore entityStore,
                                    SchemaRegistry schemaRegistry, Runnable onChanged) {
        super(owner, "Entity Pool", ModalityType.APPLICATION_MODAL);
        this.entityStore = entityStore;
        this.schemaRegistry = schemaRegistry;
        this.onChanged = onChanged;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        initComponents();
        loadPool();

        setSize(420, 500);
        setResizable(true);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(52, 16, 12, 16));
        setContentPane(content);

        // Title + count
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Unaccepted Entities");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        countLabel = new JLabel();
        countLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        countLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerPanel.add(countLabel, BorderLayout.EAST);
        content.add(headerPanel, BorderLayout.NORTH);

        // Center: search + list
        JPanel centerPanel = new JPanel(new BorderLayout(0, 6));
        centerPanel.setOpaque(false);

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search entities...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        centerPanel.add(searchField, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        entityList = new JList<>(listModel);
        entityList.setCellRenderer(new PoolEntityRenderer());
        entityList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scroll = new JScrollPane(entityList);
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        centerPanel.add(scroll, BorderLayout.CENTER);

        content.add(centerPanel, BorderLayout.CENTER);

        // Bottom: buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);

        JButton acceptAllBtn = new JButton("Accept All");
        acceptAllBtn.addActionListener(e -> onAcceptAll());
        buttonPanel.add(acceptAllBtn);

        JButton acceptBtn = new JButton("Accept Selected");
        acceptBtn.addActionListener(e -> onAcceptSelected());
        buttonPanel.add(acceptBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonPanel.add(closeBtn);

        content.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadPool() {
        allUnaccepted = entityStore.loadUnacceptedEntities();
        applyFilter();
    }

    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase();
        listModel.clear();
        for (CoinEntity e : allUnaccepted) {
            if (query.isEmpty() || matchesSearch(e, query)) {
                listModel.addElement(e);
            }
        }
        int accepted = entityStore.getAcceptedCount();
        countLabel.setText(listModel.size() + " available  |  " + accepted + " accepted");
    }

    private boolean matchesSearch(CoinEntity entity, String query) {
        if (entity.name() != null && entity.name().toLowerCase().contains(query)) return true;
        if (entity.symbol() != null && entity.symbol().toLowerCase().contains(query)) return true;
        if (entity.type() != null && entity.type().name().toLowerCase().contains(query)) return true;
        return false;
    }

    private void onAcceptSelected() {
        List<CoinEntity> selected = entityList.getSelectedValuesList();
        if (selected.isEmpty()) return;
        List<String> ids = new ArrayList<>();
        for (CoinEntity e : selected) ids.add(e.id());
        entityStore.factStore().acceptEntities(ids);
        loadPool();
        onChanged.run();
    }

    private void onAcceptAll() {
        if (allUnaccepted.isEmpty()) return;
        List<String> ids = new ArrayList<>();
        for (CoinEntity e : allUnaccepted) ids.add(e.id());
        entityStore.factStore().acceptEntities(ids);
        loadPool();
        onChanged.run();
    }

    private class PoolEntityRenderer extends JPanel implements ListCellRenderer<CoinEntity> {
        private final JLabel dot = new JLabel("\u25CF ");
        private final JLabel nameLabel = new JLabel();
        private final JLabel typeLabel = new JLabel();

        PoolEntityRenderer() {
            setLayout(new BorderLayout(4, 0));
            setBorder(new EmptyBorder(4, 6, 4, 6));
            dot.setFont(new Font("SansSerif", Font.PLAIN, 10));
            nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            typeLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            typeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

            JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            leftPanel.setOpaque(false);
            leftPanel.add(dot);
            leftPanel.add(nameLabel);
            add(leftPanel, BorderLayout.CENTER);
            add(typeLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CoinEntity> list,
                CoinEntity entity, int index, boolean isSelected, boolean cellHasFocus) {

            String displayName = entity.name();
            if (entity.symbol() != null && !entity.symbol().isEmpty()) {
                displayName += " (" + entity.symbol() + ")";
            }
            nameLabel.setText(displayName);
            typeLabel.setText(entity.type().name().toLowerCase());

            // Type color dot
            Color typeColor = Color.GRAY;
            if (schemaRegistry != null) {
                String typeId = entity.type().name().toLowerCase();
                SchemaType st = schemaRegistry.getType(typeId);
                if (st != null && st.color() != null) {
                    typeColor = st.color();
                }
            }
            dot.setForeground(typeColor);

            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            setOpaque(true);
            return this;
        }
    }
}
