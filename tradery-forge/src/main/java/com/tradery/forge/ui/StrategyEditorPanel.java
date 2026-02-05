package com.tradery.forge.ui;

import com.tradery.core.model.Strategy;
import com.tradery.forge.ApplicationContext;

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
    private JTextArea notesArea;
    private EntryConfigPanel entryConfigPanel;
    private ExitConfigPanel exitConfigPanel;
    private boolean suppressNoteEvents = false;

    private Strategy strategy;
    private Runnable onChange;

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

        // Notes text area for strategy concept with placeholder
        notesArea = new JTextArea(1, 40) {
            @Override
            public Dimension getPreferredScrollableViewportSize() {
                // Dynamic height: min 1 line, max 7 lines
                FontMetrics fm = getFontMetrics(getFont());
                int lineHeight = fm.getHeight();
                int lineCount = getLineCount();
                int visibleLines = Math.max(1, Math.min(7, lineCount));
                return new Dimension(super.getPreferredScrollableViewportSize().width,
                                     visibleLines * lineHeight + getInsets().top + getInsets().bottom);
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

        // Trade settings at top
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(tradeSettingsPanel, BorderLayout.NORTH);

        // Notes below trade settings
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        notesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        notesScroll.setBorder(null);
        notesScroll.setViewportBorder(null);
        headerPanel.add(notesScroll, BorderLayout.SOUTH);

        topPanel.add(headerPanel, BorderLayout.NORTH);

        // Flow diagram below notes
        JPanel flowWrapper = new JPanel(new BorderLayout());
        flowWrapper.setOpaque(false);
        flowWrapper.add(flowDiagramPanel, BorderLayout.CENTER);
        flowWrapper.add(new JSeparator(), BorderLayout.SOUTH);
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

    /**
     * Set the strategy to edit
     */
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
        tradeSettingsPanel.loadFrom(strategy);
        phaseSelectionPanel.loadFrom(strategy);
        hoopPatternSelectionPanel.loadFrom(strategy);
        flowDiagramPanel.setStrategy(strategy);

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
        tradeSettingsPanel.applyTo(strategy);
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
