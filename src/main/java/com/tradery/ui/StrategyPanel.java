package com.tradery.ui;

import com.tradery.TraderyApp;
import com.tradery.model.Strategy;
import com.tradery.io.StrategyStore;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel for listing and editing strategies.
 * Left column of the main window.
 */
public class StrategyPanel extends JPanel {

    private final Consumer<String> onStrategySelected;
    private final StrategyStore strategyStore;

    private JList<Strategy> strategyList;
    private DefaultListModel<Strategy> listModel;
    private JTextArea entryEditor;
    private JTextArea exitEditor;
    private JTextField nameField;
    private JComboBox<String> stopLossTypeCombo;
    private JSpinner stopLossValueSpinner;
    private JComboBox<String> takeProfitTypeCombo;
    private JSpinner takeProfitValueSpinner;
    private JSpinner maxOpenTradesSpinner;
    private JSpinner minCandlesBetweenSpinner;

    private static final String[] SL_TYPES = {"No Stop Loss", "Stop Loss %", "Trailing Stop %", "Stop Loss ATR", "Trailing Stop ATR"};
    private static final String[] TP_TYPES = {"No Take Profit", "Take Profit %", "Take Profit ATR"};

    private Strategy currentStrategy;

    public StrategyPanel(Consumer<String> onStrategySelected) {
        this.onStrategySelected = onStrategySelected;
        this.strategyStore = new StrategyStore(new File(TraderyApp.USER_DIR, "strategies"));

        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        setBackground(Color.WHITE);
        setOpaque(true);

        initializeComponents();
        layoutComponents();
        loadStrategies();
    }

    private void initializeComponents() {
        // Strategy list
        listModel = new DefaultListModel<>();
        strategyList = new JList<>(listModel);
        strategyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        strategyList.setCellRenderer(new StrategyCellRenderer());
        strategyList.addListSelectionListener(this::onSelectionChanged);

        // Editor fields
        nameField = new JTextField();
        nameField.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        entryEditor = new JTextArea(3, 20);
        entryEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        entryEditor.setLineWrap(true);
        entryEditor.setWrapStyleWord(true);

        exitEditor = new JTextArea(3, 20);
        exitEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        exitEditor.setLineWrap(true);
        exitEditor.setWrapStyleWord(true);

        // SL/TP fields
        stopLossTypeCombo = new JComboBox<>(SL_TYPES);
        stopLossValueSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.1, 100.0, 0.5));
        stopLossTypeCombo.addActionListener(e -> updateSlSpinnerVisibility());

        takeProfitTypeCombo = new JComboBox<>(TP_TYPES);
        takeProfitValueSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.1, 100.0, 0.5));
        takeProfitTypeCombo.addActionListener(e -> updateTpSpinnerVisibility());

        // Trade management fields
        maxOpenTradesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        minCandlesBetweenSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
    }

    private void updateSlSpinnerVisibility() {
        boolean hasValue = stopLossTypeCombo.getSelectedIndex() > 0;
        stopLossValueSpinner.setEnabled(hasValue);
    }

    private void updateTpSpinnerVisibility() {
        boolean hasValue = takeProfitTypeCombo.getSelectedIndex() > 0;
        takeProfitValueSpinner.setEnabled(hasValue);
    }

    private void layoutComponents() {
        // Top: Title (with left padding)
        JLabel title = new JLabel("Strategies");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(Color.GRAY);
        title.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 0));
        title.setOpaque(false);

        // List below title
        JPanel listPanel = new JPanel(new BorderLayout(0, 0));
        listPanel.setOpaque(false);
        listPanel.add(title, BorderLayout.NORTH);
        JScrollPane listScroll = new JScrollPane(strategyList);
        listScroll.setBorder(BorderFactory.createEmptyBorder(0, -2, 0, 8));
        listPanel.add(listScroll, BorderLayout.CENTER);

        // Separator line
        JPanel separatorLine = new JPanel();
        separatorLine.setPreferredSize(new Dimension(0, 1));
        separatorLine.setBackground(new Color(200, 200, 200));

        // Bottom: Editor
        JPanel editorContent = new JPanel(new BorderLayout(0, 8));
        editorContent.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        editorContent.setOpaque(false);

        JPanel editorPanel = new JPanel(new BorderLayout(0, 0));
        editorPanel.setOpaque(false);
        editorPanel.add(separatorLine, BorderLayout.NORTH);
        editorPanel.add(editorContent, BorderLayout.CENTER);

        // Name field
        JPanel namePanel = new JPanel(new BorderLayout(4, 0));
        namePanel.setOpaque(false);
        namePanel.add(new JLabel("Name:"), BorderLayout.WEST);
        namePanel.add(nameField, BorderLayout.CENTER);

        // Entry condition
        JPanel entryPanel = new JPanel(new BorderLayout(0, 2));
        entryPanel.setOpaque(false);
        JLabel entryLabel = new JLabel("Entry:");
        entryLabel.setForeground(Color.GRAY);
        entryPanel.add(entryLabel, BorderLayout.NORTH);
        JScrollPane entryScroll = new JScrollPane(entryEditor);
        entryPanel.add(entryScroll, BorderLayout.CENTER);

        // Exit condition
        JPanel exitPanel = new JPanel(new BorderLayout(0, 2));
        exitPanel.setOpaque(false);
        JLabel exitLabel = new JLabel("Exit:");
        exitLabel.setForeground(Color.GRAY);
        exitPanel.add(exitLabel, BorderLayout.NORTH);
        JScrollPane exitScroll = new JScrollPane(exitEditor);
        exitPanel.add(exitScroll, BorderLayout.CENTER);

        // Stop-loss / Take-profit rows
        JPanel slTpPanel = new JPanel(new GridLayout(4, 1, 0, 4));
        slTpPanel.setOpaque(false);

        JPanel slPanel = new JPanel(new BorderLayout(4, 0));
        slPanel.setOpaque(false);
        slPanel.add(stopLossTypeCombo, BorderLayout.CENTER);
        slPanel.add(stopLossValueSpinner, BorderLayout.EAST);

        JPanel tpPanel = new JPanel(new BorderLayout(4, 0));
        tpPanel.setOpaque(false);
        tpPanel.add(takeProfitTypeCombo, BorderLayout.CENTER);
        tpPanel.add(takeProfitValueSpinner, BorderLayout.EAST);

        JPanel maxTradesPanel = new JPanel(new BorderLayout(4, 0));
        maxTradesPanel.setOpaque(false);
        JLabel maxTradesLabel = new JLabel("Max Open Trades:");
        maxTradesLabel.setForeground(Color.GRAY);
        maxTradesPanel.add(maxTradesLabel, BorderLayout.CENTER);
        maxTradesPanel.add(maxOpenTradesSpinner, BorderLayout.EAST);

        JPanel minCandlesPanel = new JPanel(new BorderLayout(4, 0));
        minCandlesPanel.setOpaque(false);
        JLabel minCandlesLabel = new JLabel("Min Candles Between:");
        minCandlesLabel.setForeground(Color.GRAY);
        minCandlesPanel.add(minCandlesLabel, BorderLayout.CENTER);
        minCandlesPanel.add(minCandlesBetweenSpinner, BorderLayout.EAST);

        slTpPanel.add(slPanel);
        slTpPanel.add(tpPanel);
        slTpPanel.add(maxTradesPanel);
        slTpPanel.add(minCandlesPanel);

        // Conditions combined
        JPanel conditionsPanel = new JPanel(new BorderLayout(0, 8));
        conditionsPanel.setOpaque(false);

        JPanel entryExitPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        entryExitPanel.setOpaque(false);
        entryExitPanel.add(entryPanel);
        entryExitPanel.add(exitPanel);

        conditionsPanel.add(entryExitPanel, BorderLayout.CENTER);
        conditionsPanel.add(slTpPanel, BorderLayout.SOUTH);

        editorContent.add(namePanel, BorderLayout.NORTH);
        editorContent.add(conditionsPanel, BorderLayout.CENTER);

        // Split between list and editor
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setBorder(null);
        split.setDividerSize(8);
        split.setOpaque(false);
        split.setTopComponent(listPanel);
        split.setBottomComponent(editorPanel);
        split.setResizeWeight(0.4);
        split.setDividerLocation(200);

        add(split, BorderLayout.CENTER);
    }

    private void loadStrategies() {
        listModel.clear();
        List<Strategy> strategies = strategyStore.loadAll();

        if (strategies.isEmpty()) {
            // Create a sample strategy
            Strategy sample = new Strategy(
                "rsi-reversal",
                "RSI Reversal",
                "Buy oversold, sell overbought",
                "RSI(14) < 30",
                "RSI(14) > 70",
                true
            );
            strategyStore.save(sample);
            strategies.add(sample);
        }

        for (Strategy s : strategies) {
            listModel.addElement(s);
        }

        if (!strategies.isEmpty()) {
            strategyList.setSelectedIndex(0);
        }
    }

    /**
     * Reload strategies from disk (called when files change externally)
     */
    public void reloadStrategies() {
        String selectedId = currentStrategy != null ? currentStrategy.getId() : null;

        listModel.clear();
        List<Strategy> strategies = strategyStore.loadAll();

        for (Strategy s : strategies) {
            listModel.addElement(s);
        }

        // Try to re-select the previously selected strategy
        if (selectedId != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).getId().equals(selectedId)) {
                    strategyList.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void onSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        Strategy selected = strategyList.getSelectedValue();
        if (selected != null) {
            currentStrategy = selected;
            nameField.setText(selected.getName());
            entryEditor.setText(selected.getEntry());
            exitEditor.setText(selected.getExit());

            // Set SL type and value
            stopLossTypeCombo.setSelectedIndex(slTypeToIndex(selected.getStopLossType()));
            stopLossValueSpinner.setValue(selected.getStopLossValue() != null ? selected.getStopLossValue() : 2.0);
            updateSlSpinnerVisibility();

            // Set TP type and value
            takeProfitTypeCombo.setSelectedIndex(tpTypeToIndex(selected.getTakeProfitType()));
            takeProfitValueSpinner.setValue(selected.getTakeProfitValue() != null ? selected.getTakeProfitValue() : 5.0);
            updateTpSpinnerVisibility();

            // Set trade management values
            maxOpenTradesSpinner.setValue(selected.getMaxOpenTrades());
            minCandlesBetweenSpinner.setValue(selected.getMinCandlesBetweenTrades());

            onStrategySelected.accept(selected.getId());
        }
    }

    private int slTypeToIndex(String type) {
        if (type == null) return 0;
        return switch (type) {
            case "fixed_percent" -> 1;
            case "trailing_percent" -> 2;
            case "fixed_atr" -> 3;
            case "trailing_atr" -> 4;
            default -> 0;
        };
    }

    private String indexToSlType(int index) {
        return switch (index) {
            case 1 -> "fixed_percent";
            case 2 -> "trailing_percent";
            case 3 -> "fixed_atr";
            case 4 -> "trailing_atr";
            default -> "none";
        };
    }

    private int tpTypeToIndex(String type) {
        if (type == null) return 0;
        return switch (type) {
            case "fixed_percent" -> 1;
            case "fixed_atr" -> 2;
            default -> 0;
        };
    }

    private String indexToTpType(int index) {
        return switch (index) {
            case 1 -> "fixed_percent";
            case 2 -> "fixed_atr";
            default -> "none";
        };
    }

    public void saveStrategy() {
        if (currentStrategy == null) return;

        currentStrategy.setName(nameField.getText().trim());
        currentStrategy.setEntry(entryEditor.getText().trim());
        currentStrategy.setExit(exitEditor.getText().trim());

        String slType = indexToSlType(stopLossTypeCombo.getSelectedIndex());
        currentStrategy.setStopLossType(slType);
        currentStrategy.setStopLossValue("none".equals(slType) ? null : ((Number) stopLossValueSpinner.getValue()).doubleValue());

        String tpType = indexToTpType(takeProfitTypeCombo.getSelectedIndex());
        currentStrategy.setTakeProfitType(tpType);
        currentStrategy.setTakeProfitValue("none".equals(tpType) ? null : ((Number) takeProfitValueSpinner.getValue()).doubleValue());

        strategyStore.save(currentStrategy);
        strategyList.repaint();

        JOptionPane.showMessageDialog(this,
            "Strategy saved to ~/.tradery/strategies/" + currentStrategy.getId() + ".json",
            "Saved",
            JOptionPane.INFORMATION_MESSAGE);
    }

    public void createNewStrategy() {
        String name = JOptionPane.showInputDialog(this,
            "Enter strategy name:",
            "New Strategy",
            JOptionPane.PLAIN_MESSAGE);

        if (name != null && !name.trim().isEmpty()) {
            String id = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
            Strategy strategy = new Strategy(
                id,
                name.trim(),
                "",
                "RSI(14) < 30",
                "RSI(14) > 70",
                true
            );
            strategyStore.save(strategy);
            listModel.addElement(strategy);
            strategyList.setSelectedValue(strategy, true);
        }
    }

    public void deleteStrategy() {
        Strategy selected = strategyList.getSelectedValue();
        if (selected == null) return;

        int result = JOptionPane.showConfirmDialog(this,
            "Delete strategy '" + selected.getName() + "'?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            strategyStore.delete(selected.getId());
            listModel.removeElement(selected);
            currentStrategy = null;
        }
    }

    public Strategy getSelectedStrategy() {
        // Sync UI values to strategy before returning
        if (currentStrategy != null) {
            currentStrategy.setName(nameField.getText().trim());
            currentStrategy.setEntry(entryEditor.getText().trim());
            currentStrategy.setExit(exitEditor.getText().trim());

            String slType = indexToSlType(stopLossTypeCombo.getSelectedIndex());
            currentStrategy.setStopLossType(slType);
            currentStrategy.setStopLossValue("none".equals(slType) ? null : ((Number) stopLossValueSpinner.getValue()).doubleValue());

            String tpType = indexToTpType(takeProfitTypeCombo.getSelectedIndex());
            currentStrategy.setTakeProfitType(tpType);
            currentStrategy.setTakeProfitValue("none".equals(tpType) ? null : ((Number) takeProfitValueSpinner.getValue()).doubleValue());

            currentStrategy.setMaxOpenTrades(((Number) maxOpenTradesSpinner.getValue()).intValue());
            currentStrategy.setMinCandlesBetweenTrades(((Number) minCandlesBetweenSpinner.getValue()).intValue());
        }
        return currentStrategy;
    }

    /**
     * Custom renderer for strategy list items
     */
    private static class StrategyCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof Strategy s) {
                setText(s.getName());
                setToolTipText(s.getEntry() + " → " + s.getExit());
                if (!s.isEnabled()) {
                    setForeground(Color.GRAY);
                }
            }

            return this;
        }
    }
}
