package com.tradery.forge.ui;

import com.tradery.core.model.Strategy;
import com.tradery.forge.ApplicationContext;
import com.tradery.ui.controls.BorderlessScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Panel for editing a single strategy's DSL conditions and trade management settings.
 * Composes EntryConfigPanel and ExitConfigPanel with a separator between them.
 */
public class StrategyEditorPanel extends JPanel {

    private TradeSettingsPanel tradeSettingsPanel;
    private PhaseSelectionPanel phaseSelectionPanel;
    private HoopPatternSelectionPanel hoopPatternSelectionPanel;
    private FlowDiagramPanel flowDiagramPanel;
    private JTextField nameField;
    private JTextArea notesArea;
    private JSpinner minCandlesBetweenSpinner;
    private EntryConfigPanel entryConfigPanel;
    private ExitConfigPanel exitConfigPanel;
    private boolean suppressNoteEvents = false;
    private boolean suppressNameEvents = false;

    private Strategy strategy;
    private Runnable onChange;
    private Runnable onNameChange;

    public StrategyEditorPanel() {
        setLayout(new BorderLayout());

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        tradeSettingsPanel = new TradeSettingsPanel();
        phaseSelectionPanel = new PhaseSelectionPanel(
            ApplicationContext.getInstance().getPhaseStore()
        );
        hoopPatternSelectionPanel = new HoopPatternSelectionPanel();
        flowDiagramPanel = new FlowDiagramPanel();

        // Editable strategy name field
        nameField = new JTextField();
        nameField.setFont(new Font("SansSerif", Font.BOLD, 14));
        nameField.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onNameFieldChange(); }
            @Override public void removeUpdate(DocumentEvent e) { onNameFieldChange(); }
            @Override public void changedUpdate(DocumentEvent e) { onNameFieldChange(); }
        });

        // Notes text area for strategy concept with placeholder
        notesArea = new JTextArea(1, 40) {
            @Override
            public Dimension getPreferredScrollableViewportSize() {
                // Dynamic height based on wrapped lines: min 1, max 10
                FontMetrics fm = getFontMetrics(getFont());
                int lineHeight = fm.getHeight();
                int wrappedLines = countWrappedLines();
                int visibleLines = Math.max(1, Math.min(10, wrappedLines));
                return new Dimension(super.getPreferredScrollableViewportSize().width,
                                     visibleLines * lineHeight + getInsets().top + getInsets().bottom);
            }

            private int countWrappedLines() {
                try {
                    int height = (int) getUI().getRootView(this).getView(0)
                        .getPreferredSpan(javax.swing.text.View.Y_AXIS);
                    FontMetrics fm = getFontMetrics(getFont());
                    return Math.max(1, (int) Math.ceil((double) height / fm.getHeight()));
                } catch (Exception e) {
                    return getLineCount(); // fallback to logical lines
                }
            }
        };
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        notesArea.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        setupNotesPlaceholder();
        notesArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { onNotesChange(); updateNotesSize(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onNotesChange(); updateNotesSize(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onNotesChange(); updateNotesSize(); }
        });

        minCandlesBetweenSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
        minCandlesBetweenSpinner.addChangeListener(e -> onStrategyChange());

        entryConfigPanel = new EntryConfigPanel();
        exitConfigPanel = new ExitConfigPanel();

        // Add padding to sub-panels
        tradeSettingsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        phaseSelectionPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        hoopPatternSelectionPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        flowDiagramPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        entryConfigPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        exitConfigPanel.setBorder(null);

        // Wire up change listeners - all update the flow diagram
        tradeSettingsPanel.setOnChange(this::onStrategyChange);
        phaseSelectionPanel.setOnChange(this::onStrategyChange);
        hoopPatternSelectionPanel.setOnChange(this::onStrategyChange);
        entryConfigPanel.setOnChange(this::onStrategyChange);
        exitConfigPanel.setOnChange(this::onStrategyChange);
    }

    private static final String NOTES_PLACEHOLDER = "Notes...";
    private boolean showingPlaceholder = false;

    private void setupNotesPlaceholder() {
        showPlaceholder();
        notesArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (showingPlaceholder) {
                    suppressNoteEvents = true;
                    notesArea.setText("");
                    notesArea.setForeground(UIManager.getColor("TextArea.foreground"));
                    showingPlaceholder = false;
                    suppressNoteEvents = false;
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (notesArea.getText().isEmpty()) {
                    showPlaceholder();
                }
            }
        });
    }

    private void showPlaceholder() {
        suppressNoteEvents = true;
        notesArea.setText(NOTES_PLACEHOLDER);
        notesArea.setForeground(Color.GRAY);
        showingPlaceholder = true;
        suppressNoteEvents = false;
    }

    private void onNameFieldChange() {
        if (!suppressNameEvents && strategy != null) {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) {
                strategy.setName(name);
                if (onNameChange != null) onNameChange.run();
                fireChange();
            }
        }
    }

    private void onNotesChange() {
        // Notes changes only update the strategy but don't trigger recomputation
        if (!suppressNoteEvents && strategy != null) {
            String notes = showingPlaceholder ? null : notesArea.getText();
            strategy.setNotes(notes != null && !notes.isBlank() ? notes : null);
        }
    }

    private void updateNotesSize() {
        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }

    private void onStrategyChange() {
        // Update flow diagram when any strategy property changes
        if (strategy != null) {
            applyToStrategy(strategy);
            flowDiagramPanel.setStrategy(strategy);
        }
        fireChange();
    }

    private void layoutComponents() {
        // Top section: trade settings + notes + flow diagram
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        // Name + notes + trade settings
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);

        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, nameField.getPreferredSize().height));
        headerPanel.add(nameField);

        BorderlessScrollPane notesScroll = new BorderlessScrollPane(notesArea);
        notesScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        notesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        notesScroll.setViewportBorder(null);
        headerPanel.add(notesScroll);

        tradeSettingsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, tradeSettingsPanel.getPreferredSize().height));
        tradeSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(tradeSettingsPanel);

        topPanel.add(headerPanel, BorderLayout.NORTH);

        // Flow diagram below notes
        JPanel flowWrapper = new JPanel(new BorderLayout());
        flowWrapper.setOpaque(false);
        flowWrapper.add(flowDiagramPanel, BorderLayout.CENTER);

        // Min candles between, centered below flow diagram
        JPanel minCandlesPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        minCandlesPanel.setOpaque(false);
        JLabel minCandlesLabel = new JLabel("Minimum bars between entry and exit:");
        minCandlesLabel.setForeground(Color.GRAY);
        minCandlesPanel.add(minCandlesLabel);
        minCandlesPanel.add(minCandlesBetweenSpinner);

        JPanel flowBottomPanel = new JPanel(new BorderLayout());
        flowBottomPanel.setOpaque(false);
        flowBottomPanel.add(minCandlesPanel, BorderLayout.NORTH);
        flowBottomPanel.add(new JSeparator(), BorderLayout.SOUTH);
        flowWrapper.add(flowBottomPanel, BorderLayout.SOUTH);

        topPanel.add(flowWrapper, BorderLayout.SOUTH);

        // Center: entry and exit panels side by side (50/50)
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 0, 0));
        centerPanel.setOpaque(false);

        // Entry panel with phase and hoop pattern selection injected
        entryConfigPanel.setPhaseSelectionPanel(phaseSelectionPanel);
        entryConfigPanel.setHoopPatternSelectionPanel(hoopPatternSelectionPanel);

        // Entry with separator on right
        JPanel entryWrapper = new JPanel(new BorderLayout());
        entryWrapper.setOpaque(false);
        entryWrapper.add(entryConfigPanel, BorderLayout.CENTER);
        entryWrapper.add(new JSeparator(JSeparator.VERTICAL), BorderLayout.EAST);

        centerPanel.add(entryWrapper);
        centerPanel.add(exitConfigPanel);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void setOnNameChange(Runnable onNameChange) {
        this.onNameChange = onNameChange;
    }

    /**
     * Set the strategy to edit
     */
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
        tradeSettingsPanel.loadFrom(strategy);
        minCandlesBetweenSpinner.setValue(strategy != null ? strategy.getMinCandlesBetweenTrades() : 0);
        phaseSelectionPanel.loadFrom(strategy);
        hoopPatternSelectionPanel.loadFrom(strategy);
        flowDiagramPanel.setStrategy(strategy);

        // Load name
        suppressNameEvents = true;
        try {
            nameField.setText(strategy != null ? strategy.getName() : "");
        } finally {
            suppressNameEvents = false;
        }

        // Load notes
        suppressNoteEvents = true;
        try {
            String notes = strategy != null ? strategy.getNotes() : null;
            if (notes != null && !notes.isEmpty()) {
                notesArea.setText(notes);
                notesArea.setForeground(UIManager.getColor("TextArea.foreground"));
                showingPlaceholder = false;
            } else {
                showPlaceholder();
            }
        } finally {
            suppressNoteEvents = false;
        }

        entryConfigPanel.loadFrom(strategy);
        exitConfigPanel.loadFrom(strategy);
    }

    /**
     * Apply current UI values to the strategy
     */
    public void applyToStrategy(Strategy strategy) {
        if (strategy == null) return;
        String name = nameField.getText().trim();
        if (!name.isEmpty()) strategy.setName(name);
        tradeSettingsPanel.applyTo(strategy);
        strategy.setMinCandlesBetweenTrades(((Number) minCandlesBetweenSpinner.getValue()).intValue());
        phaseSelectionPanel.applyTo(strategy);
        hoopPatternSelectionPanel.applyTo(strategy);
        String notes = showingPlaceholder ? null : notesArea.getText();
        strategy.setNotes(notes != null && !notes.isBlank() ? notes : null);
        entryConfigPanel.applyTo(strategy);
        exitConfigPanel.applyTo(strategy);
    }

    /**
     * Get the current strategy with UI values applied
     */
    public Strategy getStrategy() {
        if (strategy != null) {
            applyToStrategy(strategy);
        }
        return strategy;
    }
}
