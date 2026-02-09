package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.tradery.news.ui.coin.CoinEntity;
import com.tradery.news.ui.coin.EntityStore;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dedicated window for CoinGecko settings and entity browsing.
 * Singleton — only one instance at a time.
 */
public class CoinGeckoWindow extends JFrame {

    private static CoinGeckoWindow instance;

    private final EntityStore entityStore;
    private final Runnable onRefresh;
    private final CoinTableModel tableModel;

    private CoinGeckoWindow(EntityStore entityStore, Runnable onRefresh) {
        super("CoinGecko");
        this.entityStore = entityStore;
        this.onRefresh = onRefresh;
        this.tableModel = new CoinTableModel();

        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(600, 500);
        setMinimumSize(new Dimension(400, 300));

        initUI();
        reloadTable();
    }

    /**
     * Show the singleton window. Creates it on first call.
     */
    public static void open(EntityStore entityStore, Runnable onRefresh, Component relativeTo) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new CoinGeckoWindow(entityStore, onRefresh);
            instance.setLocationRelativeTo(relativeTo);
        }
        instance.reloadTable();
        instance.setVisible(true);
        instance.toFront();
        instance.requestFocus();
    }

    public static void disposeInstance() {
        if (instance != null) {
            instance.dispose();
            instance = null;
        }
    }

    private void initUI() {
        JPanel content = new JPanel(new BorderLayout());

        // 52px header bar
        JPanel hBar = new JPanel(new BorderLayout());
        hBar.setPreferredSize(new Dimension(0, 52));
        JLabel title = new JLabel("CoinGecko", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(UIManager.getColor("Label.disabledForeground"));
        hBar.add(title, BorderLayout.CENTER);

        JPanel hWrapper = new JPanel(new BorderLayout());
        hWrapper.add(hBar, BorderLayout.CENTER);
        hWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        content.add(hWrapper, BorderLayout.NORTH);

        // Settings panel
        JPanel settingsPanel = createSettingsPanel();
        content.add(settingsPanel, BorderLayout.CENTER);

        setContentPane(content);
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        IntelConfig config = IntelConfig.get();

        // Top: settings controls
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 0, 4, 8);

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(4, 0, 4, 0);

        int row = 0;

        // Enable/disable
        lc.gridx = 0; lc.gridy = row;
        fc.gridx = 1; fc.gridy = row++;
        JCheckBox enabledCheck = new JCheckBox("Enable CoinGecko data source");
        enabledCheck.setSelected(config.isCoinGeckoEnabled());
        fc.gridwidth = 2;
        controls.add(enabledCheck, fc);
        fc.gridwidth = 1;

        // Coin limit
        lc.gridx = 0; lc.gridy = row;
        controls.add(new JLabel("Coin limit:"), lc);
        fc.gridx = 1; fc.gridy = row++;
        String[] limitLabels = {"50", "100", "200", "500", "1000", "All"};
        int[] limitValues = {50, 100, 200, 500, 1000, 0};
        JComboBox<String> limitCombo = new JComboBox<>(limitLabels);
        int currentLimit = config.getCoinGeckoLimit();
        boolean found = false;
        for (int i = 0; i < limitValues.length; i++) {
            if (limitValues[i] == currentLimit) {
                limitCombo.setSelectedIndex(i);
                found = true;
                break;
            }
        }
        if (!found) limitCombo.setSelectedItem(String.valueOf(currentLimit));
        controls.add(limitCombo, fc);

        // Cache duration
        lc.gridx = 0; lc.gridy = row;
        controls.add(new JLabel("Cache duration:"), lc);
        fc.gridx = 1; fc.gridy = row++;
        String[] cacheLabels = {"1 hour", "3 hours", "6 hours", "12 hours", "24 hours"};
        int[] cacheValues = {1, 3, 6, 12, 24};
        JComboBox<String> cacheCombo = new JComboBox<>(cacheLabels);
        for (int i = 0; i < cacheValues.length; i++) {
            if (cacheValues[i] == config.getCoinGeckoCacheHours()) {
                cacheCombo.setSelectedIndex(i);
                break;
            }
        }
        controls.add(cacheCombo, fc);

        // Request delay
        lc.gridx = 0; lc.gridy = row;
        controls.add(new JLabel("Request delay:"), lc);
        fc.gridx = 1; fc.gridy = row++;
        String[] delayLabels = {"1.5s (fast)", "5s", "10s", "20s (safe)", "30s"};
        int[] delayValues = {1500, 5000, 10000, 20000, 30000};
        JComboBox<String> delayCombo = new JComboBox<>(delayLabels);
        int currentDelay = config.getCoinGeckoRequestDelayMs();
        for (int i = 0; i < delayValues.length; i++) {
            if (delayValues[i] == currentDelay) {
                delayCombo.setSelectedIndex(i);
                break;
            }
        }
        controls.add(delayCombo, fc);

        // Fetch categories
        lc.gridx = 0; lc.gridy = row;
        fc.gridx = 1; fc.gridy = row++;
        JCheckBox categoriesCheck = new JCheckBox("Fetch categories (1 request per coin)");
        categoriesCheck.setSelected(config.isCoinGeckoFetchCategories());
        fc.gridwidth = 2;
        controls.add(categoriesCheck, fc);
        fc.gridwidth = 1;

        // Fetch Now button
        lc.gridx = 0; lc.gridy = row;
        fc.gridx = 1; fc.gridy = row++;
        JButton fetchBtn = new JButton("Fetch Now");
        fetchBtn.addActionListener(e -> {
            fetchBtn.setEnabled(false);
            fetchBtn.setText("Fetching...");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    onRefresh.run();
                    return null;
                }

                @Override
                protected void done() {
                    fetchBtn.setEnabled(true);
                    fetchBtn.setText("Fetch Now");
                    reloadTable();
                }
            }.execute();
        });
        controls.add(fetchBtn, fc);

        // Save settings on change
        enabledCheck.addActionListener(e -> {
            config.setCoinGeckoEnabled(enabledCheck.isSelected());
            config.save();
        });
        limitCombo.addActionListener(e -> {
            int idx = limitCombo.getSelectedIndex();
            if (idx >= 0 && idx < limitValues.length) {
                config.setCoinGeckoLimit(limitValues[idx]);
                config.save();
            }
        });
        cacheCombo.addActionListener(e -> {
            int idx = cacheCombo.getSelectedIndex();
            if (idx >= 0 && idx < cacheValues.length) {
                config.setCoinGeckoCacheHours(cacheValues[idx]);
                config.save();
            }
        });
        delayCombo.addActionListener(e -> {
            int idx = delayCombo.getSelectedIndex();
            if (idx >= 0 && idx < delayValues.length) {
                config.setCoinGeckoRequestDelayMs(delayValues[idx]);
                config.save();
            }
        });
        categoriesCheck.addActionListener(e -> {
            config.setCoinGeckoFetchCategories(categoriesCheck.isSelected());
            config.save();
        });

        panel.add(controls, BorderLayout.NORTH);

        // Separator
        panel.add(new JSeparator(), BorderLayout.CENTER);

        // Bottom: entity table
        JTable table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(200); // Name
        table.getColumnModel().getColumn(1).setPreferredWidth(80);  // Symbol
        table.getColumnModel().getColumn(2).setPreferredWidth(80);  // Type
        table.getColumnModel().getColumn(3).setPreferredWidth(120); // Market Cap

        // Right-align market cap
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);

        // Wrap separator + table
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JSeparator(), BorderLayout.NORTH);
        bottomPanel.add(scroll, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        // Make the table take the remaining space
        panel.setLayout(new BorderLayout());
        panel.add(controls, BorderLayout.NORTH);
        panel.add(bottomPanel, BorderLayout.CENTER);

        return panel;
    }

    private void reloadTable() {
        List<CoinEntity> entities = entityStore.loadEntitiesBySource("coingecko");
        entities.sort(Comparator.comparingDouble(CoinEntity::marketCap).reversed());
        tableModel.setEntities(entities);
    }

    private static class CoinTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Name", "Symbol", "Type", "Market Cap"};
        private static final NumberFormat MCF = NumberFormat.getIntegerInstance();

        private List<CoinEntity> entities = new ArrayList<>();

        void setEntities(List<CoinEntity> entities) {
            this.entities = entities;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return entities.size(); }

        @Override
        public int getColumnCount() { return COLUMNS.length; }

        @Override
        public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            CoinEntity e = entities.get(row);
            return switch (col) {
                case 0 -> e.name();
                case 1 -> e.symbol() != null ? e.symbol() : "";
                case 2 -> e.type().name();
                case 3 -> e.marketCap() > 0 ? "$" + MCF.format((long) e.marketCap()) : "";
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int row, int col) { return false; }
    }
}
