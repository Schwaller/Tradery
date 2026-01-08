package com.tradery.ui;

import com.tradery.model.DcaMode;
import com.tradery.model.Strategy;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Panel for configuring entry conditions and related settings.
 */
public class EntryConfigPanel extends JPanel {

    private JTextArea entryEditor;
    private JCheckBox dcaEnabledCheckbox;
    private JSpinner dcaMaxEntriesSpinner;
    private JSpinner dcaBarsBetweenSpinner;
    private JComboBox<String> dcaModeCombo;
    private JPanel dcaDetailsPanel;
    private JPanel phaseContainer;
    private JPanel hoopContainer;

    private static final String[] DCA_MODES = {"Pause", "Abort", "Continue"};

    private Runnable onChange;
    private boolean suppressChangeEvents = false;

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

        // Wire up change listeners
        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { fireChange(); }
            public void removeUpdate(DocumentEvent e) { fireChange(); }
            public void changedUpdate(DocumentEvent e) { fireChange(); }
        };

        entryEditor.getDocument().addDocumentListener(docListener);
        dcaEnabledCheckbox.addActionListener(e -> fireChange());
        dcaMaxEntriesSpinner.addChangeListener(e -> fireChange());
        dcaBarsBetweenSpinner.addChangeListener(e -> fireChange());
        dcaModeCombo.addActionListener(e -> fireChange());
    }

    private void layoutComponents() {
        // Header with label (outside the blue box)
        JLabel entryLabel = new JLabel("Entry");
        entryLabel.setForeground(Color.GRAY);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(entryLabel, BorderLayout.CENTER);

        // Blue-bordered content panel
        JPanel conditionPanel = new JPanel(new BorderLayout(0, 2));

        // Container for phase selection panel (inside the blue box)
        phaseContainer = new JPanel(new BorderLayout());
        phaseContainer.setOpaque(false);

        // Container for hoop pattern selection panel
        hoopContainer = new JPanel(new BorderLayout());
        hoopContainer.setOpaque(false);

        // Subtle grouped background with rounded border (matching exit zones)
        conditionPanel.setOpaque(true);
        Color baseColor = UIManager.getColor("Panel.background");
        Color tint = UIManager.getColor("Component.accentColor");
        if (tint == null) tint = new Color(100, 140, 180);
        // Mix 5% of accent color with background for subtle tint
        conditionPanel.setBackground(new Color(
            (int)(baseColor.getRed() * 0.95 + tint.getRed() * 0.05),
            (int)(baseColor.getGreen() * 0.95 + tint.getGreen() * 0.05),
            (int)(baseColor.getBlue() * 0.95 + tint.getBlue() * 0.05)
        ));
        conditionPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 60), 1, true),
            BorderFactory.createEmptyBorder(8, 10, 10, 10)
        ));

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
        conditionPanel.add(dcaWrapper, BorderLayout.SOUTH);

        // Combine header (outside blue box) and content (blue box)
        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
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

    private void fireChange() {
        if (!suppressChangeEvents && onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
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
        suppressChangeEvents = true;
        try {
            if (strategy != null) {
                entryEditor.setText(strategy.getEntry());
                dcaEnabledCheckbox.setSelected(strategy.isDcaEnabled());
                dcaMaxEntriesSpinner.setValue(strategy.getDcaMaxEntries());
                dcaBarsBetweenSpinner.setValue(strategy.getDcaBarsBetween());
                DcaMode mode = strategy.getDcaMode();
                dcaModeCombo.setSelectedIndex(mode == DcaMode.ABORT ? 1 : mode == DcaMode.CONTINUE ? 2 : 0);
                updateDcaVisibility();
            } else {
                entryEditor.setText("");
                dcaEnabledCheckbox.setSelected(false);
                dcaMaxEntriesSpinner.setValue(3);
                dcaBarsBetweenSpinner.setValue(1);
                dcaModeCombo.setSelectedIndex(0);
                updateDcaVisibility();
            }
        } finally {
            suppressChangeEvents = false;
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
    }
}
