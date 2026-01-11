package com.tradery.ui;

import com.tradery.data.CandleStore;
import com.tradery.engine.PhaseAnalyzer;
import com.tradery.io.PhaseStore;
import com.tradery.model.*;
import com.tradery.model.PhaseAnalysisResult.Recommendation;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Window displaying phase analysis results in a sortable table.
 * Non-modal JFrame that can stay open while user works on strategy.
 */
public class PhaseAnalysisWindow extends JFrame {

    private JTable table;
    private PhaseAnalysisTableModel tableModel;
    private JCheckBox ignoreFiltersCheckbox;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton applyButton;
    private JButton closeButton;

    private final CandleStore candleStore;
    private final PhaseStore phaseStore;

    // Analysis inputs
    private List<Trade> trades;
    private List<Candle> candles;
    private String timeframe;
    private Strategy strategy;

    // Callback to apply phases to strategy
    private Consumer<List<String>> onRequirePhases;
    private Consumer<List<String>> onExcludePhases;

    private SwingWorker<List<PhaseAnalysisResult>, PhaseAnalyzer.Progress> currentWorker;

    public PhaseAnalysisWindow(Frame parent, CandleStore candleStore, PhaseStore phaseStore) {
        super("Phase Analysis");
        this.candleStore = candleStore;
        this.phaseStore = phaseStore;

        // macOS integrated title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        initializeComponents();
        layoutComponents();

        setSize(900, 500);
        setLocationRelativeTo(parent);
    }

    public void setOnRequirePhases(Consumer<List<String>> callback) {
        this.onRequirePhases = callback;
    }

    public void setOnExcludePhases(Consumer<List<String>> callback) {
        this.onExcludePhases = callback;
    }

    /**
     * Set data and run analysis.
     */
    public void analyze(List<Trade> trades, List<Candle> candles, String timeframe, Strategy strategy) {
        this.trades = trades;
        this.candles = candles;
        this.timeframe = timeframe;
        this.strategy = strategy;

        runAnalysis();
    }

    private void initializeComponents() {
        // Checkbox for ignoring strategy phase filters
        ignoreFiltersCheckbox = new JCheckBox("Ignore strategy phase filters for baseline");
        ignoreFiltersCheckbox.addActionListener(e -> runAnalysis());

        // Table
        tableModel = new PhaseAnalysisTableModel();
        table = new JTable(tableModel);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Column widths
        int[] widths = {150, 70, 40, 60, 70, 60, 40, 60, 70, 60, 70};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Sort on header click
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                if (col >= 0) {
                    tableModel.sortBy(col);
                }
            }
        });

        // Custom renderers
        for (int i = 2; i <= 9; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(new NumericCellRenderer());
        }
        table.getColumnModel().getColumn(10).setCellRenderer(new RecommendationCellRenderer());

        // Progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        // Status label
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(Color.GRAY);

        // Buttons
        applyButton = new JButton("Apply Recommended");
        applyButton.setEnabled(false);
        applyButton.addActionListener(e -> applyRecommended());

        closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 8));
        contentPane.setBorder(BorderFactory.createEmptyBorder(28, 12, 12, 12));

        // Top panel
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));

        // Title
        JLabel titleLabel = new JLabel("Phase Analysis");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        topPanel.add(titleLabel, BorderLayout.NORTH);

        // Checkbox
        topPanel.add(ignoreFiltersCheckbox, BorderLayout.CENTER);

        contentPane.add(topPanel, BorderLayout.NORTH);

        // Table scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        // Bottom panel with progress and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));

        // Progress panel
        JPanel progressPanel = new JPanel(new BorderLayout(8, 0));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(statusLabel, BorderLayout.SOUTH);
        bottomPanel.add(progressPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.add(applyButton);
        buttonPanel.add(closeButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        contentPane.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);
    }

    private void runAnalysis() {
        if (trades == null || candles == null || candles.isEmpty()) {
            statusLabel.setText("No data to analyze");
            return;
        }

        // Cancel any running analysis
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }

        // Filter trades based on checkbox
        List<Trade> tradesToAnalyze = trades;
        // Note: The "ignore filters" checkbox affects the display text but the actual
        // filtering was already done in the backtest. For true baseline comparison,
        // we would need to re-run backtest without phase filters.
        // For now, we analyze the trades that were actually taken.

        progressBar.setVisible(true);
        progressBar.setValue(0);
        applyButton.setEnabled(false);
        statusLabel.setText("Analyzing phases...");

        currentWorker = new SwingWorker<>() {
            @Override
            protected List<PhaseAnalysisResult> doInBackground() throws Exception {
                PhaseAnalyzer analyzer = new PhaseAnalyzer(candleStore, phaseStore);
                return analyzer.analyzePhases(tradesToAnalyze, candles, timeframe, progress -> {
                    if (!isCancelled()) {
                        publish(progress);
                    }
                });
            }

            @Override
            protected void process(java.util.List<PhaseAnalyzer.Progress> chunks) {
                if (!chunks.isEmpty()) {
                    PhaseAnalyzer.Progress p = chunks.get(chunks.size() - 1);
                    int pct = (int) ((p.current() * 100.0) / p.total());
                    progressBar.setValue(pct);
                    progressBar.setString(p.current() + "/" + p.total() + " - " + p.phaseName());
                }
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    if (!isCancelled()) {
                        List<PhaseAnalysisResult> results = get();
                        displayResults(results);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Analysis failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        currentWorker.execute();
    }

    private void displayResults(List<PhaseAnalysisResult> results) {
        tableModel.setResults(results);

        // Count recommendations
        int requireCount = 0;
        int excludeCount = 0;
        for (PhaseAnalysisResult r : results) {
            if (r.recommendation() == Recommendation.REQUIRE) requireCount++;
            else if (r.recommendation() == Recommendation.EXCLUDE) excludeCount++;
        }

        String summary;
        if (requireCount == 0 && excludeCount == 0) {
            summary = "No strong recommendations. Try running more trades.";
        } else {
            List<String> parts = new ArrayList<>();
            if (requireCount > 0) parts.add(requireCount + " to REQUIRE");
            if (excludeCount > 0) parts.add(excludeCount + " to EXCLUDE");
            summary = "Recommendations: " + String.join(", ", parts);
        }
        statusLabel.setText(summary);

        applyButton.setEnabled(requireCount > 0 || excludeCount > 0);
    }

    private void applyRecommended() {
        List<String> toRequire = new ArrayList<>();
        List<String> toExclude = new ArrayList<>();

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            PhaseAnalysisResult r = tableModel.getResultAt(i);
            if (r != null) {
                if (r.recommendation() == Recommendation.REQUIRE) {
                    toRequire.add(r.phaseId());
                } else if (r.recommendation() == Recommendation.EXCLUDE) {
                    toExclude.add(r.phaseId());
                }
            }
        }

        if (onRequirePhases != null && !toRequire.isEmpty()) {
            onRequirePhases.accept(toRequire);
        }
        if (onExcludePhases != null && !toExclude.isEmpty()) {
            onExcludePhases.accept(toExclude);
        }

        statusLabel.setText("Applied " + toRequire.size() + " required, " + toExclude.size() + " excluded phases");
    }

    /**
     * Renderer for numeric columns with formatting.
     */
    private static class NumericCellRenderer extends DefaultTableCellRenderer {
        NumericCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            if (value instanceof Integer i) {
                setText(String.valueOf(i));
            } else if (value instanceof Double d) {
                if (Double.isInfinite(d) || d > 99) {
                    setText(">99");
                } else {
                    setText(String.format("%.1f", d));
                }
            } else {
                setText(value != null ? value.toString() : "");
            }
        }
    }

    /**
     * Renderer for recommendation column with color coding.
     */
    private class RecommendationCellRenderer extends DefaultTableCellRenderer {
        private static final Color REQUIRE_COLOR = new Color(76, 175, 80);
        private static final Color EXCLUDE_COLOR = new Color(244, 67, 54);

        RecommendationCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(getFont().deriveFont(Font.BOLD));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                String text = value != null ? value.toString() : "";
                if ("REQUIRE".equals(text)) {
                    setForeground(REQUIRE_COLOR);
                } else if ("EXCLUDE".equals(text)) {
                    setForeground(EXCLUDE_COLOR);
                } else {
                    setForeground(Color.GRAY);
                }
            }

            return c;
        }
    }
}
