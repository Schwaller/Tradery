package com.tradery.forge.ui;

import com.tradery.core.model.*;
import com.tradery.core.model.PhaseAnalysisResult.Recommendation;
import com.tradery.forge.analysis.PhaseAnalyzer;
import com.tradery.forge.data.sqlite.SqliteDataStore;
import com.tradery.forge.io.PhaseStore;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private JPanel detailsPanel;
    private JLabel detailsLabel;
    private PhasePreviewChart previewChart;

    // Column tooltips
    private static final String[] COLUMN_TOOLTIPS = {
        "Phase name",
        "Phase category (Technical, Time, Calendar, Moon, Funding, Custom)",
        "Number of trades that entered while this phase was ACTIVE",
        "Win rate (%) for trades that entered while phase was ACTIVE",
        "Total return (%) for trades that entered while phase was ACTIVE",
        "Profit Factor for trades that entered while phase was ACTIVE",
        "Number of trades that entered while this phase was INACTIVE",
        "Win rate (%) for trades that entered while phase was INACTIVE",
        "Total return (%) for trades that entered while phase was INACTIVE",
        "Profit Factor for trades that entered while phase was INACTIVE",
        "Recommendation: REQUIRE (better in phase), EXCLUDE (worse in phase), or neutral"
    };

    private final SqliteDataStore dataStore;
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

    public PhaseAnalysisWindow(Frame parent, SqliteDataStore dataStore, PhaseStore phaseStore) {
        super("Phase Analysis");
        this.dataStore = dataStore;
        this.phaseStore = phaseStore;

        // macOS integrated title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        initializeComponents();
        layoutComponents();

        setSize(1000, 700);
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

        // Column header tooltips
        table.setTableHeader(new JTableHeader(table.getColumnModel()) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int col = columnAtPoint(e.getPoint());
                if (col >= 0 && col < COLUMN_TOOLTIPS.length) {
                    return COLUMN_TOOLTIPS[col];
                }
                return null;
            }
        });

        // Row selection listener for details panel and preview chart
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetailsPanel();
                updatePreviewChart();
            }
        });

        // Preview chart for selected phase
        previewChart = new PhasePreviewChart();

        // Details panel
        detailsPanel = new JPanel(new BorderLayout(8, 4));
        detailsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(8, 0, 0, 0)
        ));
        detailsLabel = new JLabel("Select a phase to see details");
        detailsLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        detailsLabel.setForeground(Color.GRAY);
        detailsPanel.add(detailsLabel, BorderLayout.CENTER);

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

        // Table panel with details
        JPanel tablePanel = new JPanel(new BorderLayout(0, 8));

        // Table scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        // Details panel below table
        tablePanel.add(detailsPanel, BorderLayout.SOUTH);

        // Split pane: table on top, chart preview on bottom
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, previewChart);
        splitPane.setResizeWeight(0.5);  // 50% each initially
        splitPane.setDividerLocation(250);
        splitPane.setOneTouchExpandable(true);

        contentPane.add(splitPane, BorderLayout.CENTER);

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
                PhaseAnalyzer analyzer = new PhaseAnalyzer(dataStore, phaseStore);
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

    private void updateDetailsPanel() {
        int row = table.getSelectedRow();
        if (row < 0) {
            detailsLabel.setText("Select a phase to see details");
            detailsLabel.setForeground(Color.GRAY);
            return;
        }

        PhaseAnalysisResult r = tableModel.getResultAt(row);
        if (r == null) {
            detailsLabel.setText("No data");
            return;
        }

        // Build detailed HTML summary
        StringBuilder sb = new StringBuilder("<html><body style='font-family: monospace; font-size: 11px;'>");

        // Phase info
        sb.append("<b>").append(r.phaseName()).append("</b> (").append(r.phaseCategory()).append(")<br><br>");

        // Comparison table
        sb.append("<table cellpadding='2'>");
        sb.append("<tr><td></td><td><b>In Phase</b></td><td><b>Out of Phase</b></td><td><b>Difference</b></td></tr>");

        // Trades
        sb.append("<tr><td>Trades:</td><td>").append(r.tradesInPhase()).append("</td>");
        sb.append("<td>").append(r.tradesOutOfPhase()).append("</td>");
        sb.append("<td>-</td></tr>");

        // Get theme colors
        String successHex = getSuccessHex();
        String errorHex = getErrorHex();
        String neutralHex = getNeutralHex();

        // Win Rate
        double wrDiff = r.winRateDifference();
        String wrColor = wrDiff > 0 ? successHex : (wrDiff < 0 ? errorHex : neutralHex);
        sb.append("<tr><td>Win Rate:</td><td>").append(String.format("%.1f%%", r.winRateInPhase())).append("</td>");
        sb.append("<td>").append(String.format("%.1f%%", r.winRateOutOfPhase())).append("</td>");
        sb.append("<td style='color:").append(wrColor).append("'>").append(String.format("%+.1f%%", wrDiff)).append("</td></tr>");

        // Return
        double retDiff = r.returnDifference();
        String retColor = retDiff > 0 ? successHex : (retDiff < 0 ? errorHex : neutralHex);
        sb.append("<tr><td>Return:</td><td>").append(String.format("%.1f%%", r.totalReturnInPhase())).append("</td>");
        sb.append("<td>").append(String.format("%.1f%%", r.totalReturnOutOfPhase())).append("</td>");
        sb.append("<td style='color:").append(retColor).append("'>").append(String.format("%+.1f%%", retDiff)).append("</td></tr>");

        // Profit Factor
        double pfDiff = r.profitFactorDifference();
        String pfColor = pfDiff > 0 ? successHex : (pfDiff < 0 ? errorHex : neutralHex);
        String pfIn = r.profitFactorInPhase() > 99 ? ">99" : String.format("%.2f", r.profitFactorInPhase());
        String pfOut = r.profitFactorOutOfPhase() > 99 ? ">99" : String.format("%.2f", r.profitFactorOutOfPhase());
        sb.append("<tr><td>Profit Factor:</td><td>").append(pfIn).append("</td>");
        sb.append("<td>").append(pfOut).append("</td>");
        sb.append("<td style='color:").append(pfColor).append("'>").append(String.format("%+.2f", pfDiff)).append("</td></tr>");

        sb.append("</table><br>");

        // Recommendation explanation
        String recText;
        String recColor;
        switch (r.recommendation()) {
            case REQUIRE -> {
                recText = "REQUIRE this phase - performance is significantly better when active";
                recColor = successHex;
            }
            case EXCLUDE -> {
                recText = "EXCLUDE this phase - performance is significantly worse when active";
                recColor = errorHex;
            }
            default -> {
                recText = "No strong recommendation - difference not significant or insufficient data";
                recColor = neutralHex;
            }
        }
        sb.append("<span style='color:").append(recColor).append("'><b>").append(recText).append("</b></span>");

        sb.append("</body></html>");

        detailsLabel.setText(sb.toString());
        detailsLabel.setForeground(Color.BLACK);
    }

    /**
     * Update the preview chart with the selected phase.
     */
    private void updatePreviewChart() {
        int row = table.getSelectedRow();
        if (row < 0) {
            previewChart.setPhase(null);
            return;
        }

        PhaseAnalysisResult r = tableModel.getResultAt(row);
        if (r == null) {
            previewChart.setPhase(null);
            return;
        }

        // Load the phase from store and show in chart
        try {
            Phase phase = phaseStore.load(r.phaseId());
            previewChart.setPhase(phase);
        } catch (Exception e) {
            System.err.println("Failed to load phase for preview: " + e.getMessage());
            previewChart.setPhase(null);
        }
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
                    setForeground(getSuccessColor());
                } else if ("EXCLUDE".equals(text)) {
                    setForeground(getErrorColor());
                } else {
                    setForeground(UIManager.getColor("Label.disabledForeground"));
                }
            }

            return c;
        }
    }

    // Theme-aware colors using FlatLaf
    private static Color getSuccessColor() {
        Color c = UIManager.getColor("Actions.Green");
        return c != null ? c : new Color(76, 175, 80);
    }

    private static Color getErrorColor() {
        Color c = UIManager.getColor("Actions.Red");
        return c != null ? c : new Color(244, 67, 54);
    }

    private static String getSuccessHex() {
        Color c = getSuccessColor();
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String getErrorHex() {
        Color c = getErrorColor();
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String getNeutralHex() {
        Color c = UIManager.getColor("Label.disabledForeground");
        if (c == null) c = Color.GRAY;
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
