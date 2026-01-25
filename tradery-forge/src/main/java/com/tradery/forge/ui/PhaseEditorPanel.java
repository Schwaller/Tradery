package com.tradery.forge.ui;

import com.tradery.core.model.Phase;
import com.tradery.forge.ui.base.ConfigurationPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.Instant;

/**
 * Panel for editing a single phase definition.
 */
public class PhaseEditorPanel extends ConfigurationPanel {

    private JTextField nameField;
    private JTextField categoryField;
    private JTextArea descriptionArea;
    private JComboBox<String> symbolCombo;
    private JComboBox<String> timeframeCombo;
    private JTextArea conditionArea;
    private JLabel builtInBadge;

    private Phase phase;

    private static final String[] SYMBOLS = {
        "BTCUSDT", "ETHUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT",
        "SOLUSDT", "DOGEUSDT", "DOTUSDT", "AVAXUSDT", "MATICUSDT"
    };

    private static final String[] TIMEFRAMES = {
        "1h", "4h", "1d", "1w"
    };

    public PhaseEditorPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        nameField = new JTextField();
        nameField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        categoryField = new JTextField();
        categoryField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        descriptionArea = new JTextArea(2, 20);
        descriptionArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        symbolCombo = new JComboBox<>(SYMBOLS);
        symbolCombo.setEditable(true); // Allow custom symbols

        timeframeCombo = new JComboBox<>(TIMEFRAMES);
        timeframeCombo.setSelectedItem("1d"); // Default to daily

        conditionArea = new JTextArea(4, 20);
        conditionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        conditionArea.setLineWrap(true);
        conditionArea.setWrapStyleWord(true);

        builtInBadge = new JLabel("BUILT-IN");
        builtInBadge.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        builtInBadge.setForeground(new Color(100, 100, 100));
        builtInBadge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(150, 150, 150), 1),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        builtInBadge.setVisible(false);

        // Wire up change listeners
        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { fireChange(); }
            public void removeUpdate(DocumentEvent e) { fireChange(); }
            public void changedUpdate(DocumentEvent e) { fireChange(); }
        };

        nameField.getDocument().addDocumentListener(docListener);
        categoryField.getDocument().addDocumentListener(docListener);
        descriptionArea.getDocument().addDocumentListener(docListener);
        conditionArea.getDocument().addDocumentListener(docListener);
        symbolCombo.addActionListener(e -> fireChange());
        timeframeCombo.addActionListener(e -> fireChange());
    }

    private void layoutComponents() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Name with badge
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel nameLabel = new JLabel("Name:");
        nameLabel.setForeground(Color.GRAY);
        formPanel.add(nameLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        JPanel nameRow = new JPanel(new BorderLayout(8, 0));
        nameRow.setOpaque(false);
        nameRow.add(nameField, BorderLayout.CENTER);
        nameRow.add(builtInBadge, BorderLayout.EAST);
        formPanel.add(nameRow, gbc);

        // Category
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel categoryLabel = new JLabel("Category:");
        categoryLabel.setForeground(Color.GRAY);
        formPanel.add(categoryLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        formPanel.add(categoryField, gbc);

        // Symbol
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel symbolLabel = new JLabel("Symbol:");
        symbolLabel.setForeground(Color.GRAY);
        formPanel.add(symbolLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        formPanel.add(symbolCombo, gbc);

        // Timeframe
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel tfLabel = new JLabel("Timeframe:");
        tfLabel.setForeground(Color.GRAY);
        formPanel.add(tfLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        formPanel.add(timeframeCombo, gbc);

        // Condition with DSL help
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel condLabel = new JLabel("Condition:");
        condLabel.setForeground(Color.GRAY);
        formPanel.add(condLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 1;
        JScrollPane condScroll = new JScrollPane(conditionArea);

        // Wrap scroll pane in layered pane for info button overlay
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(200, 80));

        JButton infoButton = createInfoButton();
        layeredPane.add(condScroll, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(infoButton, JLayeredPane.PALETTE_LAYER);

        // Layout components on resize
        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                condScroll.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
                infoButton.setBounds(layeredPane.getWidth() - 22, layeredPane.getHeight() - 22, 18, 18);
            }
        });

        formPanel.add(layeredPane, gbc);

        // Description
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel descLabel = new JLabel("Description:");
        descLabel.setForeground(Color.GRAY);
        formPanel.add(descLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.weighty = 0;
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(200, 50));
        formPanel.add(descScroll, gbc);

        add(formPanel, BorderLayout.CENTER);
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

    public void loadFrom(Phase phase) {
        this.phase = phase;
        setSuppressChangeEvents(true);
        try {
            if (phase != null) {
                nameField.setText(phase.getName() != null ? phase.getName() : "");
                categoryField.setText(phase.getCategory() != null ? phase.getCategory() : "");
                descriptionArea.setText(phase.getDescription() != null ? phase.getDescription() : "");
                symbolCombo.setSelectedItem(phase.getSymbol() != null ? phase.getSymbol() : "BTCUSDT");
                timeframeCombo.setSelectedItem(phase.getTimeframe() != null ? phase.getTimeframe() : "1d");
                conditionArea.setText(phase.getCondition() != null ? phase.getCondition() : "");
                builtInBadge.setVisible(phase.isBuiltIn());
            } else {
                nameField.setText("");
                categoryField.setText("");
                descriptionArea.setText("");
                symbolCombo.setSelectedItem("BTCUSDT");
                timeframeCombo.setSelectedItem("1d");
                conditionArea.setText("");
                builtInBadge.setVisible(false);
            }
        } finally {
            setSuppressChangeEvents(false);
        }
    }

    public void applyTo(Phase phase) {
        if (phase == null) return;
        phase.setName(nameField.getText().trim());
        String cat = categoryField.getText().trim();
        phase.setCategory(cat.isEmpty() ? null : cat);
        phase.setDescription(descriptionArea.getText().trim());
        phase.setSymbol((String) symbolCombo.getSelectedItem());
        phase.setTimeframe((String) timeframeCombo.getSelectedItem());
        phase.setCondition(conditionArea.getText().trim());
    }

    public Phase getPhase() {
        if (phase != null) {
            applyTo(phase);
        }
        return phase;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        nameField.setEnabled(enabled);
        nameField.setEditable(enabled);
        categoryField.setEnabled(enabled);
        categoryField.setEditable(enabled);
        descriptionArea.setEnabled(enabled);
        descriptionArea.setEditable(enabled);
        symbolCombo.setEnabled(enabled);
        timeframeCombo.setEnabled(enabled);
        conditionArea.setEnabled(enabled);
        conditionArea.setEditable(enabled);
    }
}
