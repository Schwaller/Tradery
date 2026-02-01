package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.ui.charts.ChartConfig;
import com.tradery.symbols.ui.SymbolComboBox;

import javax.swing.*;
import java.awt.*;

/**
 * Toolbar for backtest controls and settings.
 */
public class ControlPanel extends JToolBar {

    private SymbolComboBox symbolCombo;
    private JComboBox<String> resolutionCombo;

    private JButton newButton;
    private JButton saveButton;
    private JButton deleteButton;
    private JButton runButton;
    private JToggleButton candlestickToggle;

    private Runnable onNew;
    private Runnable onSave;
    private Runnable onDelete;
    private Runnable onRun;

    public ControlPanel() {
        setFloatable(false);
        setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        // Strategy buttons
        newButton = new JButton("New");
        saveButton = new JButton("Save");
        deleteButton = new JButton("Delete");

        newButton.addActionListener(e -> { if (onNew != null) onNew.run(); });
        saveButton.addActionListener(e -> { if (onSave != null) onSave.run(); });
        deleteButton.addActionListener(e -> { if (onDelete != null) onDelete.run(); });

        // Symbol selection
        symbolCombo = new SymbolComboBox(ApplicationContext.getInstance().getSymbolService());
        symbolCombo.setMaximumSize(symbolCombo.getPreferredSize());

        // Resolution/timeframe
        resolutionCombo = new JComboBox<>(new String[]{
            "1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w"
        });
        resolutionCombo.setSelectedItem("1h");
        resolutionCombo.setMaximumSize(resolutionCombo.getPreferredSize());

        // Run button
        runButton = new JButton("Run Backtest");
        runButton.addActionListener(e -> { if (onRun != null) onRun.run(); });

        // Candlestick toggle
        candlestickToggle = new JToggleButton("Candles");
        candlestickToggle.setToolTipText("Toggle between line and candlestick chart");
        candlestickToggle.setSelected(ChartConfig.getInstance().isCandlestickMode());
        candlestickToggle.addActionListener(e -> {
            ChartConfig.getInstance().setCandlestickMode(candlestickToggle.isSelected());
        });
    }

    private void layoutComponents() {
        // Strategy buttons
        add(newButton);
        add(Box.createHorizontalStrut(4));
        add(saveButton);
        add(Box.createHorizontalStrut(4));
        add(deleteButton);

        add(Box.createHorizontalStrut(24));
        addSeparator();
        add(Box.createHorizontalStrut(24));

        // Help button (before symbol selector)
        add(createHelpButton());
        add(Box.createHorizontalStrut(16));

        // Symbol
        add(new JLabel("Symbol:"));
        add(Box.createHorizontalStrut(4));
        add(symbolCombo);

        add(Box.createHorizontalStrut(16));

        // Resolution
        add(new JLabel("Timeframe:"));
        add(Box.createHorizontalStrut(4));
        add(resolutionCombo);

        add(Box.createHorizontalStrut(16));

        // Candlestick toggle
        add(candlestickToggle);

        add(Box.createHorizontalGlue());

        // Run button on right
        add(runButton);
    }

    public void setOnNew(Runnable onNew) {
        this.onNew = onNew;
    }

    public void setOnSave(Runnable onSave) {
        this.onSave = onSave;
    }

    public void setOnDelete(Runnable onDelete) {
        this.onDelete = onDelete;
    }

    public void setOnRun(Runnable onRun) {
        this.onRun = onRun;
    }

    public void setRunEnabled(boolean enabled) {
        runButton.setEnabled(enabled);
    }

    public String getSymbol() {
        return symbolCombo.getSelectedSymbol();
    }

    public String getResolution() {
        return (String) resolutionCombo.getSelectedItem();
    }

    private JButton createHelpButton() {
        JButton btn = new JButton("?");
        btn.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 11));
        btn.setMargin(new java.awt.Insets(2, 6, 2, 6));
        btn.setFocusPainted(false);
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        btn.setToolTipText("Strategy Guide");
        btn.addActionListener(e -> StrategyHelpDialog.show(this));
        return btn;
    }
}
