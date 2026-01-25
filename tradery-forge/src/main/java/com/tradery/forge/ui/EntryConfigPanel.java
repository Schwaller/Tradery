package com.tradery.forge.ui;

import com.tradery.core.model.DcaMode;
import com.tradery.core.model.EntryOrderType;
import com.tradery.core.model.OffsetUnit;
import com.tradery.core.model.Strategy;
import com.tradery.forge.ui.base.ConfigurationPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Panel for configuring entry conditions and related settings.
 */
public class EntryConfigPanel extends ConfigurationPanel {

    private JTextArea entryEditor;
    private JCheckBox dcaEnabledCheckbox;
    private JSpinner dcaMaxEntriesSpinner;
    private JSpinner dcaBarsBetweenSpinner;
    private JComboBox<String> dcaModeCombo;
    private JPanel dcaDetailsPanel;
    private JPanel phaseContainer;
    private JPanel hoopContainer;

    // Order type controls
    private OrderTypeControl orderTypeControl;
    private JSpinner expirationBarsSpinner;
    private JLabel expirationLabel;
    private JPanel orderTypeDetailsPanel;

    private static final String[] DCA_MODES = {"Pause", "Abort", "Continue"};

    public EntryConfigPanel() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        entryEditor = new JTextArea(3, 20);
        entryEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        entryEditor.setLineWrap(true);
        entryEditor.setWrapStyleWord(true);

        dcaEnabledCheckbox = new JCheckBox("DCA");
        dcaMaxEntriesSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        dcaBarsBetweenSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 100, 1));
        dcaModeCombo = new JComboBox<>(DCA_MODES);
        dcaEnabledCheckbox.addActionListener(e -> updateDcaVisibility());

        // Order type controls - [type][value][unit] format
        orderTypeControl = new OrderTypeControl(0.5, -50.0, 50.0, 0.05);
        expirationBarsSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 500, 1));
        orderTypeControl.addChangeListener(this::updateOrderTypeVisibility);

        // Wire up change listeners
        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                fireChange();
            }

            public void removeUpdate(DocumentEvent e) {
                fireChange();
            }

            public void changedUpdate(DocumentEvent e) {
                fireChange();
            }
        };

        entryEditor.getDocument().addDocumentListener(docListener);
        dcaEnabledCheckbox.addActionListener(e -> fireChange());
        dcaMaxEntriesSpinner.addChangeListener(e -> fireChange());
        dcaBarsBetweenSpinner.addChangeListener(e -> fireChange());
        dcaModeCombo.addActionListener(e -> fireChange());

        // Order type change listeners
        orderTypeControl.addChangeListener(this::fireChange);
        expirationBarsSpinner.addChangeListener(e -> fireChange());
    }

    private void layoutComponents() {
        // Header with label (outside the blue box)
        JLabel entryLabel = new JLabel("Entry");
        entryLabel.setForeground(Color.GRAY);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(entryLabel, BorderLayout.CENTER);
        headerPanel.setBorder(new EmptyBorder(0, 0, 4, 0));

        // Badge-styled content panel
        BadgePanel conditionPanel = new BadgePanel(new BorderLayout(0, 2));

        // Container for phase selection panel (inside the badge)
        phaseContainer = new JPanel(new BorderLayout());
        phaseContainer.setOpaque(false);

        // Container for hoop pattern selection panel
        hoopContainer = new JPanel(new BorderLayout());
        hoopContainer.setOpaque(false);

        // Add phase and hoop selection at top of blue box
        JPanel filterStack = new JPanel();
        filterStack.setLayout(new BoxLayout(filterStack, BoxLayout.Y_AXIS));
        filterStack.setOpaque(false);
        filterStack.add(phaseContainer);
        filterStack.add(hoopContainer);
        conditionPanel.add(filterStack, BorderLayout.NORTH);

        JScrollPane entryScroll = new JScrollPane(entryEditor);

        // Wrap scroll pane in layered pane for info button overlay
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(200, 60));

        JButton infoButton = createInfoButton();
        layeredPane.add(entryScroll, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(infoButton, JLayeredPane.PALETTE_LAYER);

        // Layout components on resize
        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                entryScroll.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
                infoButton.setBounds(layeredPane.getWidth() - 22, layeredPane.getHeight() - 22, 18, 18);
            }
        });

        JPanel scrollWrapper = new JPanel(new BorderLayout());
        scrollWrapper.setOpaque(false);
        scrollWrapper.add(Box.createVerticalStrut(4), BorderLayout.NORTH);
        scrollWrapper.add(layeredPane, BorderLayout.CENTER);
        conditionPanel.add(scrollWrapper, BorderLayout.CENTER);

        // Order Type section - [type][value][unit] layout
        JPanel orderTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        orderTypePanel.setOpaque(false);
        orderTypePanel.add(orderTypeControl);

        orderTypeDetailsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        orderTypeDetailsPanel.setOpaque(false);

        expirationLabel = new JLabel("Expires:");
        expirationLabel.setForeground(Color.GRAY);
        orderTypeDetailsPanel.add(expirationLabel);
        orderTypeDetailsPanel.add(expirationBarsSpinner);
        JLabel barsLabel = new JLabel("bars");
        barsLabel.setForeground(Color.GRAY);
        orderTypeDetailsPanel.add(barsLabel);

        JPanel orderTypeWrapper = new JPanel(new BorderLayout(0, 0));
        orderTypeWrapper.setOpaque(false);
        orderTypeWrapper.add(orderTypePanel, BorderLayout.NORTH);
        orderTypeWrapper.add(orderTypeDetailsPanel, BorderLayout.CENTER);

        // DCA section
        JPanel dcaCheckboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        dcaCheckboxPanel.setOpaque(false);
        dcaCheckboxPanel.add(dcaEnabledCheckbox);

        dcaDetailsPanel = new JPanel(new GridBagLayout());
        dcaDetailsPanel.setOpaque(false);
        dcaDetailsPanel.setVisible(false);

        JLabel maxEntriesLabel = new JLabel("Max entries:");
        maxEntriesLabel.setForeground(Color.GRAY);
        dcaDetailsPanel.add(maxEntriesLabel, gbc(0, 0, false));
        dcaDetailsPanel.add(dcaMaxEntriesSpinner, gbc(1, 0, true));

        JLabel barsBetweenLabel = new JLabel("Bars between:");
        barsBetweenLabel.setForeground(Color.GRAY);
        dcaDetailsPanel.add(barsBetweenLabel, gbc(0, 1, false));
        dcaDetailsPanel.add(dcaBarsBetweenSpinner, gbc(1, 1, true));

        JLabel modeLabel = new JLabel("Signal Loss:");
        modeLabel.setForeground(Color.GRAY);
        dcaDetailsPanel.add(modeLabel, gbc(0, 2, false));
        dcaDetailsPanel.add(dcaModeCombo, gbc(1, 2, true));

        JPanel dcaWrapper = new JPanel(new BorderLayout(0, 0));
        dcaWrapper.setOpaque(false);
        dcaWrapper.add(dcaCheckboxPanel, BorderLayout.NORTH);
        dcaWrapper.add(dcaDetailsPanel, BorderLayout.CENTER);

        // Combine order type and DCA sections
        JPanel bottomSection = new JPanel();
        bottomSection.setLayout(new BoxLayout(bottomSection, BoxLayout.Y_AXIS));
        bottomSection.setOpaque(false);
        bottomSection.add(orderTypeWrapper);
        bottomSection.add(Box.createVerticalStrut(4));
        bottomSection.add(dcaWrapper);
        conditionPanel.add(bottomSection, BorderLayout.SOUTH);

        // Combine header (outside blue box) and content (blue box)
        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.setOpaque(false);
        wrapper.add(headerPanel, BorderLayout.NORTH);
        wrapper.add(conditionPanel, BorderLayout.CENTER);

        add(wrapper, BorderLayout.CENTER);
    }

    private GridBagConstraints gbc(int x, int y, boolean fill) {
        return new GridBagConstraints(x, y, 1, 1, fill ? 1 : 0, 0,
                GridBagConstraints.WEST, fill ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE,
                new Insets(2, 0, 2, 4), 0, 0);
    }

    private JButton createInfoButton() {
        JButton btn = new JButton("\u24D8"); // circled i
        btn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        Color normal = UIManager.getColor("Label.disabledForeground");
        Color hover = UIManager.getColor("Component.accentColor");
        if (normal == null) normal = Color.GRAY;
        if (hover == null) hover = new Color(70, 130, 180);
        final Color normalColor = normal;
        final Color hoverColor = hover;
        btn.setForeground(normalColor);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("DSL Reference");
        btn.addActionListener(e -> DslHelpDialog.show(this));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setForeground(hoverColor);
            }

            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setForeground(normalColor);
            }
        });
        return btn;
    }

    private void updateDcaVisibility() {
        dcaDetailsPanel.setVisible(dcaEnabledCheckbox.isSelected());
        revalidate();
        repaint();
    }

    private void updateOrderTypeVisibility() {
        boolean isMarket = orderTypeControl.getOrderType() == EntryOrderType.MARKET;

        // Show expiration only for non-market orders
        expirationLabel.setVisible(!isMarket);
        expirationBarsSpinner.setVisible(!isMarket);
        orderTypeDetailsPanel.setVisible(!isMarket);

        revalidate();
        repaint();
    }

    /**
     * Inject the phase selection panel to display between label and DSL field.
     */
    public void setPhaseSelectionPanel(JPanel panel) {
        phaseContainer.removeAll();
        if (panel != null) {
            phaseContainer.add(panel, BorderLayout.CENTER);
        }
        phaseContainer.revalidate();
        phaseContainer.repaint();
    }

    /**
     * Inject the hoop pattern selection panel to display below phase selection.
     */
    public void setHoopPatternSelectionPanel(JPanel panel) {
        hoopContainer.removeAll();
        if (panel != null) {
            hoopContainer.add(panel, BorderLayout.CENTER);
        }
        hoopContainer.revalidate();
        hoopContainer.repaint();
    }

    public void loadFrom(Strategy strategy) {
        setSuppressChangeEvents(true);
        try {
            if (strategy != null) {
                entryEditor.setText(strategy.getEntry());
                dcaEnabledCheckbox.setSelected(strategy.isDcaEnabled());
                dcaMaxEntriesSpinner.setValue(strategy.getDcaMaxEntries());
                dcaBarsBetweenSpinner.setValue(strategy.getDcaBarsBetween());
                DcaMode mode = strategy.getDcaMode();
                dcaModeCombo.setSelectedIndex(mode == DcaMode.ABORT ? 1 : mode == DcaMode.CONTINUE ? 2 : 0);
                updateDcaVisibility();

                // Load order type settings
                EntryOrderType orderType = strategy.getEntrySettings().getOrderType();
                OffsetUnit unit = strategy.getEntrySettings().getOrderOffsetUnit();
                Double value = strategy.getEntrySettings().getOrderOffsetValue();
                // For trailing, use trailingReversePercent
                if (orderType == EntryOrderType.TRAILING) {
                    Double reversal = strategy.getEntrySettings().getTrailingReversePercent();
                    value = reversal != null ? reversal : 1.0;
                }
                orderTypeControl.setValues(orderType, value != null ? value : 0.5, unit);
                Integer expiration = strategy.getEntrySettings().getExpirationBars();
                expirationBarsSpinner.setValue(expiration != null ? expiration : 10);
                updateOrderTypeVisibility();
            } else {
                entryEditor.setText("");
                dcaEnabledCheckbox.setSelected(false);
                dcaMaxEntriesSpinner.setValue(3);
                dcaBarsBetweenSpinner.setValue(1);
                dcaModeCombo.setSelectedIndex(0);
                updateDcaVisibility();
                orderTypeControl.setValues(EntryOrderType.MARKET, 0.5, OffsetUnit.PERCENT);
                expirationBarsSpinner.setValue(10);
                updateOrderTypeVisibility();
            }
        } finally {
            setSuppressChangeEvents(false);
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;
        strategy.setEntry(entryEditor.getText().trim());
        strategy.setDcaEnabled(dcaEnabledCheckbox.isSelected());
        strategy.setDcaMaxEntries(((Number) dcaMaxEntriesSpinner.getValue()).intValue());
        strategy.setDcaBarsBetween(((Number) dcaBarsBetweenSpinner.getValue()).intValue());
        DcaMode[] modes = {DcaMode.PAUSE, DcaMode.ABORT, DcaMode.CONTINUE};
        strategy.setDcaMode(modes[dcaModeCombo.getSelectedIndex()]);

        // Apply order type settings
        EntryOrderType orderType = orderTypeControl.getOrderType();
        strategy.getEntrySettings().setOrderType(orderType);

        if (orderType != EntryOrderType.MARKET) {
            if (orderType == EntryOrderType.TRAILING) {
                // Trailing uses reversal percent
                strategy.getEntrySettings().setTrailingReversePercent(orderTypeControl.getValue());
                strategy.getEntrySettings().setOrderOffsetValue(null);
                strategy.getEntrySettings().setOrderOffsetUnit(OffsetUnit.PERCENT);
            } else {
                // Stop/Limit use offset value and unit
                strategy.getEntrySettings().setOrderOffsetValue(orderTypeControl.getValue());
                strategy.getEntrySettings().setOrderOffsetUnit(orderTypeControl.getUnit());
                strategy.getEntrySettings().setTrailingReversePercent(null);
            }
            strategy.getEntrySettings().setExpirationBars(((Number) expirationBarsSpinner.getValue()).intValue());
        } else {
            strategy.getEntrySettings().setOrderOffsetValue(null);
            strategy.getEntrySettings().setOrderOffsetUnit(OffsetUnit.MARKET);
            strategy.getEntrySettings().setTrailingReversePercent(null);
            strategy.getEntrySettings().setExpirationBars(null);
        }
    }

}
