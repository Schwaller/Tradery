package com.tradery.ui;

import com.tradery.ui.help.HelpStyles;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog explaining strategy concepts at a user level.
 * Focuses on "what" and "why" rather than JSON structure.
 * Can be used by AI to explain how strategies work.
 */
public class StrategyHelpDialog extends JDialog {

    private static StrategyHelpDialog instance;

    private JList<TocEntry> tocList;
    private DefaultListModel<TocEntry> tocModel;
    private JEditorPane helpPane;
    private JScrollPane contentScrollPane;
    private boolean isScrollingFromToc = false;
    private boolean isUpdatingFromScroll = false;
    private List<TocEntry> tocEntries;

    // Search
    private JTextField searchField;
    private JLabel searchResultLabel;
    private List<int[]> searchMatches = new ArrayList<>();
    private int currentMatchIndex = -1;
    private Highlighter.HighlightPainter searchHighlightPainter;
    private Highlighter.HighlightPainter currentMatchPainter;

    /**
     * Represents a table of contents entry.
     */
    private static class TocEntry {
        final String id;      // HTML anchor id (e.g., "toc-0")
        final String title;   // Display text
        final int level;      // 2 for h2, 3 for h3
        int yPosition;        // Calculated after render

        TocEntry(String id, String title, int level) {
            this.id = id;
            this.title = title;
            this.level = level;
            this.yPosition = 0;
        }
    }

    public StrategyHelpDialog(Window owner) {
        super(owner, "Strategy Guide", ModalityType.MODELESS);

        // Integrated title bar look (macOS)
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        initComponents();
    }

    private void initComponents() {
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        // Initialize search highlight painters
        Color highlightColor = new Color(255, 255, 0, 100); // Yellow with transparency
        Color currentMatchColor = new Color(255, 150, 0, 150); // Orange for current match
        searchHighlightPainter = new DefaultHighlighter.DefaultHighlightPainter(highlightColor);
        currentMatchPainter = new DefaultHighlighter.DefaultHighlightPainter(currentMatchColor);

        // Title bar area (44px height for macOS traffic lights + search box clearance)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 44));

        // Left spacer for traffic lights
        JPanel leftSpacer = new JPanel();
        leftSpacer.setPreferredSize(new Dimension(70, 0));
        leftSpacer.setOpaque(false);

        // Title in center
        JLabel titleLabel = new JLabel("Strategy Guide", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        // Search panel on right (8px right margin, vertically centered)
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        searchPanel.setOpaque(false);
        searchPanel.setBorder(new EmptyBorder(0, 0, 0, 8));

        searchField = new JTextField(12);
        searchField.putClientProperty("JTextField.placeholderText", "Search...");
        searchField.setFont(searchField.getFont().deriveFont(11f));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { performSearch(); }
            @Override public void removeUpdate(DocumentEvent e) { performSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { performSearch(); }
        });
        // Enter goes to next match, Shift+Enter goes to previous
        searchField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        goToPreviousMatch();
                    } else {
                        goToNextMatch();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchField.setText("");
                    clearSearchHighlights();
                }
            }
        });

        searchResultLabel = new JLabel("");
        searchResultLabel.setFont(searchResultLabel.getFont().deriveFont(10f));
        searchResultLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        searchPanel.add(searchResultLabel);
        searchPanel.add(searchField);

        // Wrapper to vertically center the search panel
        JPanel searchWrapper = new JPanel(new GridBagLayout());
        searchWrapper.setOpaque(false);
        searchWrapper.add(searchPanel);

        titleBar.add(leftSpacer, BorderLayout.WEST);
        titleBar.add(titleLabel, BorderLayout.CENTER);
        titleBar.add(searchWrapper, BorderLayout.EAST);

        // Build content and TOC
        tocEntries = new ArrayList<>();
        String content = buildHelpContent(tocEntries);

        // Create TOC list
        tocModel = new DefaultListModel<>();
        for (TocEntry entry : tocEntries) {
            tocModel.addElement(entry);
        }
        tocList = new JList<>(tocModel);
        tocList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tocList.setCellRenderer(new TocCellRenderer());
        tocList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isUpdatingFromScroll) {
                scrollToSelectedSection();
            }
        });

        JScrollPane tocScrollPane = new JScrollPane(tocList);
        tocScrollPane.setBorder(null);
        tocScrollPane.setPreferredSize(new Dimension(180, 0));

        // Create content pane
        helpPane = new JEditorPane("text/html", content);
        helpPane.setEditable(false);
        helpPane.setCaretPosition(0);
        helpPane.setBorder(new EmptyBorder(4, 4, 4, 4));
        helpPane.setBackground(UIManager.getColor("Panel.background"));

        contentScrollPane = new JScrollPane(helpPane);
        contentScrollPane.setBorder(null);

        // Track scroll position to update TOC selection
        contentScrollPane.getViewport().addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (!isScrollingFromToc) {
                    updateTocSelectionFromScroll();
                }
            }
        });

        // Calculate positions after HTML is rendered
        SwingUtilities.invokeLater(() -> {
            SwingUtilities.invokeLater(this::calculateTocPositions);
        });

        // Create split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(tocScrollPane);
        splitPane.setRightComponent(contentScrollPane);
        splitPane.setDividerLocation(180);
        splitPane.setDividerSize(1);
        splitPane.setResizeWeight(0);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(closeButton);

        // Wrap content with separators
        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.add(new JSeparator(), BorderLayout.NORTH);
        mainContent.add(splitPane, BorderLayout.CENTER);
        mainContent.setPreferredSize(new Dimension(1000, 700));

        // Button panel with separator above
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JSeparator(), BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        mainContent.add(bottomPanel, BorderLayout.SOUTH);

        contentPane.add(titleBar, BorderLayout.NORTH);
        contentPane.add(mainContent, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(getOwner());
    }

    private void calculateTocPositions() {
        if (helpPane.getDocument() instanceof HTMLDocument doc) {
            for (TocEntry entry : tocEntries) {
                Element element = doc.getElement(entry.id);
                if (element != null) {
                    try {
                        Rectangle rect = helpPane.modelToView(element.getStartOffset());
                        if (rect != null) {
                            entry.yPosition = rect.y;
                        }
                    } catch (BadLocationException e) {
                        // Ignore
                    }
                }
            }
        }
    }

    private void scrollToSelectedSection() {
        TocEntry selected = tocList.getSelectedValue();
        if (selected == null) return;

        isScrollingFromToc = true;
        try {
            if (helpPane.getDocument() instanceof HTMLDocument doc) {
                Element element = doc.getElement(selected.id);
                if (element != null) {
                    try {
                        Rectangle rect = helpPane.modelToView(element.getStartOffset());
                        if (rect != null) {
                            // Scroll to position with a small offset from top
                            rect.y = Math.max(0, rect.y - 10);
                            rect.height = contentScrollPane.getViewport().getHeight();
                            helpPane.scrollRectToVisible(rect);
                        }
                    } catch (BadLocationException e) {
                        // Ignore
                    }
                }
            }
        } finally {
            // Reset flag after a short delay to allow scroll to complete
            Timer timer = new Timer(100, e -> isScrollingFromToc = false);
            timer.setRepeats(false);
            timer.start();
        }
    }

    private void updateTocSelectionFromScroll() {
        if (tocEntries.isEmpty()) return;

        Rectangle viewRect = contentScrollPane.getViewport().getViewRect();
        int targetY = viewRect.y + viewRect.height / 3; // Upper third of view

        TocEntry bestMatch = tocEntries.get(0);
        for (TocEntry entry : tocEntries) {
            if (entry.yPosition <= targetY) {
                bestMatch = entry;
            } else {
                break;
            }
        }

        isUpdatingFromScroll = true;
        try {
            tocList.setSelectedValue(bestMatch, true);
        } finally {
            isUpdatingFromScroll = false;
        }
    }

    /**
     * Custom renderer for TOC entries with indentation based on heading level.
     */
    private class TocCellRenderer extends JPanel implements ListCellRenderer<TocEntry> {
        private final JLabel label;

        public TocCellRenderer() {
            setLayout(new BorderLayout());
            label = new JLabel();
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends TocEntry> list,
                TocEntry value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            // Indentation: 8px for h2, 20px for h3
            int leftPadding = value.level == 2 ? 8 : 20;
            setBorder(BorderFactory.createEmptyBorder(3, leftPadding, 3, 8));

            label.setText(value.title);
            label.setFont(label.getFont().deriveFont(value.level == 2 ? Font.BOLD : Font.PLAIN, 11f));

            if (isSelected) {
                setBackground(UIManager.getColor("List.selectionBackground"));
                label.setForeground(UIManager.getColor("List.selectionForeground"));
                setOpaque(true);
            } else {
                setBackground(list.getBackground());
                label.setForeground(value.level == 2
                    ? UIManager.getColor("Label.foreground")
                    : UIManager.getColor("Label.disabledForeground"));
                setOpaque(false);
            }

            return this;
        }
    }

    private String buildHelpContent(List<TocEntry> toc) {
        // Get theme colors
        HelpStyles.ThemeColors colors = HelpStyles.getThemeColors();

        // Build TOC entries
        int tocIndex = 0;
        toc.add(new TocEntry("toc-" + tocIndex++, "Overview", 2));
        toc.add(new TocEntry("toc-" + tocIndex++, "How It Works", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Key Concepts", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Charts & Visualizations", 2));
        toc.add(new TocEntry("toc-" + tocIndex++, "Price Chart", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Equity Curve", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Indicator Charts", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Chart Overlays", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Performance Metrics", 2));
        toc.add(new TocEntry("toc-" + tocIndex++, "Core Metrics", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Trade Quality Metrics", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Indicators Overview", 2));
        toc.add(new TocEntry("toc-" + tocIndex++, "Price & Trend", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Momentum & Volatility", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Orderflow & Volume", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Entry Settings", 2));
        toc.add(new TocEntry("toc-" + tocIndex++, "Entry Condition", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Trade Limits", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Order Types", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "DCA", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Exit Settings", 2));
        toc.add(new TocEntry("toc-" + tocIndex++, "Exit Zones", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Zone Settings", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Stop Loss Types", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Take Profit Types", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Partial Exits", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Zone Phase Filters", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Phases", 2));
        toc.add(new TocEntry("toc-" + tocIndex++, "Session Phases", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Day/Time Phases", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Technical Phases", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Calendar Phases", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Funding Phases", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Custom Phases", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Hoop Patterns", 2));
        toc.add(new TocEntry("toc-" + tocIndex++, "How Hoops Work", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Hoop Settings", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Pattern Examples", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Combine Modes", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Tips", 2));

        String css = HelpStyles.buildCss(colors);

        return """
            <html>
            <head>
            <style>
            %s
            </style>
            </head>
            <body>""".formatted(css) + """

            <h1>Strategy Guide</h1>

            <!-- ==================== OVERVIEW SECTION ==================== -->

            <h2 id="toc-0">Overview</h2>

            <p><b>Tradery</b> is a backtesting engine for cryptocurrency trading strategies. It lets you define trading rules using a simple DSL (Domain Specific Language), test them against historical data, and analyze the results to improve your approach.</p>

            <h3 id="toc-1">How It Works</h3>

            <p>The backtester simulates your strategy bar-by-bar through historical price data:</p>

            <div class="flow">
                Load Candles &rarr; Check Phases &rarr; Evaluate Entry &rarr; Manage Position &rarr; Check Exit &rarr; Record Trade
            </div>

            <p><b>For each bar, the engine:</b></p>
            <ul>
                <li>Evaluates all <b>phase conditions</b> to determine market regime</li>
                <li>Checks if <b>entry conditions</b> are met (and phases allow trading)</li>
                <li>Manages open positions through <b>exit zones</b> based on P&L</li>
                <li>Applies <b>stop losses</b>, <b>take profits</b>, and <b>trailing stops</b></li>
                <li>Records detailed trade data for analysis</li>
            </ul>

            <div class="box">
                <b>Data Sources:</b> OHLCV candles from Binance (auto-cached), plus optional funding rates, open interest, and aggregated trades for orderflow analysis.
            </div>

            <h3 id="toc-2">Key Concepts</h3>

            <table>
                <tr><th>Concept</th><th>What It Does</th><th>Example</th></tr>
                <tr><td><b>DSL Condition</b></td><td>Formula that evaluates to true/false</td><td>RSI(14) &lt; 30 AND close &gt; SMA(200)</td></tr>
                <tr><td><b>Entry Signal</b></td><td>When to open a new position</td><td>Buy when RSI oversold in uptrend</td></tr>
                <tr><td><b>Exit Zone</b></td><td>Rules based on current P&L</td><td>If P&L &gt; 5%%, use trailing stop</td></tr>
                <tr><td><b>Phase</b></td><td>Market filter (multi-timeframe)</td><td>Only trade during "Uptrend" on daily</td></tr>
                <tr><td><b>Hoop Pattern</b></td><td>Chart pattern detection</td><td>Detect double bottoms, flags</td></tr>
                <tr><td><b>Candle Pattern</b></td><td>Built-in candlestick detection</td><td>SHOOTING_STAR AND IS_BULLISH[1]</td></tr>
            </table>

            <div class="tip">
                <b>The Strategy Flow:</b><br>
                <span style="font-family: monospace;">Phases</span> filter <i>when</i> you can trade &rarr;
                <span style="font-family: monospace;">Entry</span> defines <i>what</i> triggers a trade &rarr;
                <span style="font-family: monospace;">Exit Zones</span> control <i>how</i> you manage and close
            </div>

            <p><b>After backtesting, you get:</b></p>
            <ul>
                <li><b>Metrics:</b> Win rate, profit factor, Sharpe ratio, max drawdown</li>
                <li><b>Trade Analysis:</b> Performance by hour, day, and active phases</li>
                <li><b>Individual Trades:</b> Entry/exit prices, MFE/MAE, indicator values</li>
                <li><b>Suggestions:</b> AI-generated improvement recommendations</li>
            </ul>

            <div class="box">
                <b>Workflow:</b> Create strategy &rarr; Run backtest &rarr; Analyze results &rarr; Refine conditions &rarr; Repeat
            </div>

            <!-- ==================== CHARTS SECTION ==================== -->

            <h2 id="toc-3">Charts &amp; Visualizations</h2>

            <p>Tradery provides multiple charts to help you understand strategy performance and market conditions.</p>

            <h3 id="toc-4">Price Chart</h3>
            <p>The main chart displays <b>candlesticks</b> showing Open, High, Low, Close prices for each bar:</p>
            <ul>
                <li><b>Green candles:</b> Close &gt; Open (bullish/up move)</li>
                <li><b>Red candles:</b> Close &lt; Open (bearish/down move)</li>
                <li><b>Wicks:</b> High and low prices reached during the bar</li>
            </ul>
            <p><b>Trade markers</b> show entry/exit points:</p>
            <ul>
                <li><span style="color:#4CAF50;">Green triangle up:</span> Entry point</li>
                <li><span style="color:#F44336;">Red triangle down:</span> Exit point</li>
                <li>Connecting line shows trade duration and direction</li>
            </ul>
            <p><b>Volume bars</b> (below price) show trading activity per bar. Higher volume = stronger conviction.</p>

            <h3 id="toc-5">Equity Curve</h3>
            <p>Shows your strategy's account value over time:</p>
            <ul>
                <li><b>Line rising:</b> Account growing (winning trades)</li>
                <li><b>Line falling:</b> Drawdown period (losing trades)</li>
                <li><b>Steepness:</b> Rate of gains/losses</li>
            </ul>
            <div class="box">
                <b>What to look for:</b><br/>
                - <b>Smooth upward slope:</b> Consistent returns, good strategy<br/>
                - <b>Jagged/volatile:</b> Inconsistent, may need refinement<br/>
                - <b>Long flat periods:</b> Strategy not finding trades<br/>
                - <b>Sharp drops:</b> Drawdowns - check if acceptable
            </div>

            <h3 id="toc-6">Indicator Charts</h3>
            <p>Sub-charts below the price chart showing technical indicators:</p>
            <table>
                <tr><th>Chart</th><th>What It Shows</th><th>How to Read</th></tr>
                <tr><td><b>RSI</b></td><td>Momentum (0-100)</td><td>&lt;30 oversold, &gt;70 overbought</td></tr>
                <tr><td><b>MACD</b></td><td>Trend momentum</td><td>Line crosses signal = trend change</td></tr>
                <tr><td><b>ATR</b></td><td>Volatility</td><td>Higher = more volatile, wider stops needed</td></tr>
                <tr><td><b>Stochastic</b></td><td>Momentum (0-100)</td><td>&lt;20 oversold, &gt;80 overbought</td></tr>
                <tr><td><b>ADX</b></td><td>Trend strength</td><td>&gt;25 trending, &lt;20 ranging</td></tr>
                <tr><td><b>Range Position</b></td><td>Position in price range</td><td>-1 at low, +1 at high</td></tr>
                <tr><td><b>Delta</b></td><td>Buy vs sell pressure</td><td>Positive = buyers dominate</td></tr>
                <tr><td><b>CVD</b></td><td>Cumulative delta</td><td>Rising = sustained buying</td></tr>
                <tr><td><b>Funding</b></td><td>Futures funding rate</td><td>High = overleveraged longs</td></tr>
            </table>
            <div class="tip">
                Right-click on the chart area to enable/disable indicator charts and configure their parameters.
            </div>

            <h3 id="toc-7">Chart Overlays</h3>
            <p>Overlays are drawn directly on the price chart:</p>
            <table>
                <tr><th>Overlay</th><th>What It Shows</th><th>Usage</th></tr>
                <tr><td><b>SMA/EMA</b></td><td>Moving averages</td><td>Trend direction, support/resistance</td></tr>
                <tr><td><b>Bollinger Bands</b></td><td>Volatility envelope</td><td>Upper/lower bands show overbought/oversold</td></tr>
                <tr><td><b>High/Low</b></td><td>N-period high/low</td><td>Range breakout levels</td></tr>
                <tr><td><b>VWAP</b></td><td>Volume-weighted average price</td><td>Fair value, institutional reference</td></tr>
                <tr><td><b>Daily POC</b></td><td>Point of control</td><td>Price level with most volume</td></tr>
                <tr><td><b>Ichimoku</b></td><td>Cloud system</td><td>Trend, support/resistance, momentum</td></tr>
                <tr><td><b>Rays</b></td><td>Auto-detected trendlines</td><td>Dynamic support/resistance from highs/lows</td></tr>
            </table>

            <!-- ==================== METRICS SECTION ==================== -->

            <h2 id="toc-8">Performance Metrics</h2>

            <p>After running a backtest, you'll see these metrics. Understanding them is key to improving your strategy.</p>

            <h3 id="toc-9">Core Metrics</h3>
            <table>
                <tr><th>Metric</th><th>What It Measures</th><th>Good Values</th></tr>
                <tr><td><b>Total Trades</b></td><td>Number of completed trades</td><td>Enough for statistical significance (30+)</td></tr>
                <tr><td><b>Win Rate</b></td><td>Percentage of profitable trades</td><td>&gt;50%%, but depends on risk:reward</td></tr>
                <tr><td><b>Profit Factor</b></td><td>Gross profits / Gross losses</td><td>&gt;1.5 good, &gt;2.0 excellent</td></tr>
                <tr><td><b>Total Return</b></td><td>Overall P&L as %% of initial capital</td><td>Context-dependent (vs buy &amp; hold)</td></tr>
                <tr><td><b>Max Drawdown</b></td><td>Largest peak-to-trough decline</td><td>&lt;20%% conservative, &lt;30%% aggressive</td></tr>
                <tr><td><b>Sharpe Ratio</b></td><td>Risk-adjusted returns</td><td>&gt;1.0 good, &gt;2.0 excellent</td></tr>
                <tr><td><b>Avg Win</b></td><td>Average profit on winning trades</td><td>Should be &gt; Avg Loss for low win rates</td></tr>
                <tr><td><b>Avg Loss</b></td><td>Average loss on losing trades</td><td>Controls risk per trade</td></tr>
            </table>

            <div class="box">
                <b>Key relationships:</b><br/>
                - <b>Win Rate + Avg Win/Loss:</b> A 30%% win rate is fine if wins are 3x larger than losses<br/>
                - <b>Profit Factor:</b> Directly shows if strategy makes money (must be &gt;1.0)<br/>
                - <b>Sharpe Ratio:</b> Accounts for volatility - high returns with low volatility = better
            </div>

            <h3 id="toc-10">Trade Quality Metrics</h3>
            <p>These metrics help you optimize exit timing:</p>
            <table>
                <tr><th>Metric</th><th>What It Measures</th><th>What It Tells You</th></tr>
                <tr><td><b>MFE</b></td><td>Max Favorable Excursion</td><td>Best unrealized profit during trade</td></tr>
                <tr><td><b>MAE</b></td><td>Max Adverse Excursion</td><td>Worst unrealized drawdown during trade</td></tr>
                <tr><td><b>Capture Ratio</b></td><td>Actual P&L / MFE</td><td>How much of the move you captured</td></tr>
                <tr><td><b>Pain Ratio</b></td><td>|MAE| / MFE</td><td>How much pain you endured vs potential gain</td></tr>
            </table>

            <div class="tip">
                <b>MFE/MAE Analysis:</b><br/>
                - <b>Low capture ratio (&lt;50%%):</b> Exits are too early, consider trailing stops<br/>
                - <b>High MAE on winners:</b> Tight stops might be cutting winners short<br/>
                - <b>MFE &gt; 0 on losers:</b> Trades went profitable but reversed - tighten take profits
            </div>

            <p><b>Additional metrics:</b></p>
            <table>
                <tr><td><b>Total Fees</b></td><td>Trading fees (maker/taker) deducted from P&L</td></tr>
                <tr><td><b>Holding Costs</b></td><td>Funding rate payments (futures) - can be positive (cost) or negative (earnings)</td></tr>
                <tr><td><b>Max Capital</b></td><td>Peak capital used - shows how much of your account is at risk</td></tr>
                <tr><td><b>Final Equity</b></td><td>Ending account value after all trades</td></tr>
            </table>

            <!-- ==================== INDICATORS SECTION ==================== -->

            <h2 id="toc-11">Indicators Overview</h2>

            <p>Indicators are the building blocks of your DSL conditions. Here's a quick reference of the most commonly used ones. For full syntax, use the <b>DSL Reference</b> (click ? in the condition editor).</p>

            <h3 id="toc-12">Price &amp; Trend</h3>
            <table>
                <tr><th>Indicator</th><th>Syntax</th><th>What It Does</th></tr>
                <tr><td><b>Price</b></td><td>price, close, open, high, low</td><td>Current bar's OHLC values</td></tr>
                <tr><td><b>SMA</b></td><td>SMA(period)</td><td>Simple moving average - smooths price</td></tr>
                <tr><td><b>EMA</b></td><td>EMA(period)</td><td>Exponential MA - faster response to changes</td></tr>
                <tr><td><b>ADX</b></td><td>ADX(period)</td><td>Trend strength (0-100). &gt;25 = trending</td></tr>
                <tr><td><b>PLUS_DI/MINUS_DI</b></td><td>PLUS_DI(14), MINUS_DI(14)</td><td>Directional indicators. +DI &gt; -DI = uptrend</td></tr>
                <tr><td><b>Supertrend</b></td><td>SUPERTREND(n,mult).trend</td><td>Trend direction. 1 = up, -1 = down</td></tr>
            </table>

            <h3 id="toc-13">Momentum &amp; Volatility</h3>
            <table>
                <tr><th>Indicator</th><th>Syntax</th><th>What It Does</th></tr>
                <tr><td><b>RSI</b></td><td>RSI(period)</td><td>Momentum (0-100). &lt;30 oversold, &gt;70 overbought</td></tr>
                <tr><td><b>Stochastic</b></td><td>STOCHASTIC(k,d).k</td><td>Momentum oscillator (0-100)</td></tr>
                <tr><td><b>MACD</b></td><td>MACD(12,26,9).line</td><td>Trend momentum. Line crossing signal = change</td></tr>
                <tr><td><b>ATR</b></td><td>ATR(period)</td><td>Average True Range - measures volatility</td></tr>
                <tr><td><b>Bollinger</b></td><td>BBANDS(20,2).upper/lower</td><td>Volatility bands around price</td></tr>
                <tr><td><b>Range Position</b></td><td>RANGE_POSITION(20)</td><td>Where price is in recent range (-1 to +1)</td></tr>
            </table>

            <h3 id="toc-14">Orderflow &amp; Volume</h3>
            <table>
                <tr><th>Indicator</th><th>Syntax</th><th>What It Does</th></tr>
                <tr><td><b>Volume</b></td><td>volume</td><td>Current bar's trading volume</td></tr>
                <tr><td><b>Avg Volume</b></td><td>AVG_VOLUME(20)</td><td>Average volume over N bars</td></tr>
                <tr><td><b>VWAP</b></td><td>VWAP</td><td>Volume-weighted average price</td></tr>
                <tr><td><b>Delta</b></td><td>DELTA</td><td>Buy volume - sell volume</td></tr>
                <tr><td><b>CVD</b></td><td>CUM_DELTA</td><td>Cumulative delta (running total)</td></tr>
                <tr><td><b>Funding</b></td><td>FUNDING</td><td>Current funding rate (futures)</td></tr>
                <tr><td><b>OI</b></td><td>OI, OI_CHANGE</td><td>Open interest and change</td></tr>
            </table>

            <div class="box">
                <b>DSL Syntax Tips:</b><br/>
                - Use <code>[n]</code> for lookback: <code>RSI(14)[1]</code> = RSI one bar ago<br/>
                - Use <code>crosses_above</code> / <code>crosses_below</code> for crossover signals<br/>
                - Combine with <code>AND</code> / <code>OR</code> for complex conditions<br/>
                - Click the <b>?</b> button in condition editors for full DSL reference
            </div>

            <!-- ==================== ENTRY SECTION ==================== -->

            <h2 id="toc-15">Entry Settings</h2>

            <h3 id="toc-16">Entry Condition</h3>
            <p>A DSL formula that evaluates to true/false each bar. When true, a trade signal fires.</p>
            <div class="box">
                <b>Example:</b> RSI(14) &lt; 30 AND close &gt; SMA(200)<br>
                <span class="small">Looks for oversold conditions in an uptrend.</span>
            </div>
            <div class="tip">
                <b>Candlestick Patterns:</b> Use built-in functions for candle detection:<br>
                <span style="font-family: monospace;">HAMMER, SHOOTING_STAR, DOJI</span> - return 1 if pattern detected<br>
                <span style="font-family: monospace;">BODY_SIZE, BODY_RATIO, IS_BULLISH, IS_BEARISH</span> - candle properties (support [n] lookback)<br><br>
                <b>Example:</b> SHOOTING_STAR AND BODY_SIZE[1] &gt; ATR(14) * 1.5 AND IS_BULLISH[1] == 1<br>
                <span class="small">Shooting star after a strong bullish candle = reversal signal</span>
            </div>

            <h3 id="toc-17">Trade Limits</h3>
            <table>
                <tr><td><b>Max Open Trades</b></td><td>Maximum concurrent positions (DCA groups count as one)</td></tr>
                <tr><td><b>Min Candles Between</b></td><td>Minimum bars between new position entries</td></tr>
            </table>

            <h3 id="toc-18">Order Types</h3>
            <p>Controls <b>how</b> you enter after a signal fires:</p>
            <table>
                <tr><th>Type</th><th>Behavior</th><th>Settings</th></tr>
                <tr><td><b>Market</b></td><td>Enter immediately at current price</td><td>None</td></tr>
                <tr><td><b>Limit</b></td><td>Enter when price drops to X%% below signal price</td><td>Offset %% (negative)</td></tr>
                <tr><td><b>Stop</b></td><td>Enter when price rises to X%% above signal price</td><td>Offset %% (positive)</td></tr>
                <tr><td><b>Trailing</b></td><td>Trail price down, enter on X%% reversal up</td><td>Reversal %%</td></tr>
            </table>
            <p class="small"><b>Expiration:</b> For non-market orders, cancel if not filled within X bars.</p>

            <h3 id="toc-19">DCA (Dollar Cost Averaging)</h3>
            <p>Add to a position over multiple entries instead of going all-in at once.</p>
            <table>
                <tr><td><b>Enabled</b></td><td>Turn DCA on/off</td></tr>
                <tr><td><b>Max Entries</b></td><td>Maximum entries per position (e.g., 3)</td></tr>
                <tr><td><b>Bars Between</b></td><td>Minimum bars between DCA entries</td></tr>
                <tr><td><b>Signal Loss</b></td><td>What to do if entry signal turns false:</td></tr>
            </table>
            <ul>
                <li><b>Pause:</b> Wait for signal to return before adding</li>
                <li><b>Abort:</b> Close entire position if signal lost</li>
                <li><b>Continue:</b> Keep adding regardless of signal</li>
            </ul>

            <!-- ==================== EXIT SECTION ==================== -->

            <h2 id="toc-20">Exit Settings</h2>

            <h3 id="toc-21">Exit Zones</h3>
            <p>Zones define behavior at different P&L levels. Each zone has a range and exit rules.</p>
            <div class="box">
                <b>Example Setup:</b><br>
                Zone 1: P&L &lt; -5%% &rarr; Exit immediately (stop loss)<br>
                Zone 2: P&L 0%% to 5%% &rarr; Exit if RSI &gt; 70<br>
                Zone 3: P&L &gt; 10%% &rarr; Exit immediately (take profit)
            </div>

            <h3 id="toc-22">Zone Settings</h3>
            <table>
                <tr><th>Setting</th><th>Description</th></tr>
                <tr><td><b>P&L Range</b></td><td>Min/Max P&L %% where this zone applies</td></tr>
                <tr><td><b>Market Exit</b></td><td>Exit at market price (ignores SL/TP levels)</td></tr>
                <tr><td><b>Exit Condition</b></td><td>DSL condition that must be true to exit</td></tr>
                <tr><td><b>Min Bars Before Exit</b></td><td>Wait X bars after entry before allowing exit</td></tr>
            </table>

            <h3 id="toc-23">Stop Loss Types</h3>
            <table>
                <tr><th>Type</th><th>Description</th></tr>
                <tr><td><b>None</b></td><td>No stop loss in this zone</td></tr>
                <tr><td><b>Fixed %%</b></td><td>Stop X%% below entry price</td></tr>
                <tr><td><b>Fixed ATR</b></td><td>Stop X * ATR(14) below entry price</td></tr>
                <tr><td><b>Trailing %%</b></td><td>Trail X%% below highest price since entry</td></tr>
                <tr><td><b>Trailing ATR</b></td><td>Trail X * ATR(14) below highest price</td></tr>
                <tr><td><b>Clear</b></td><td>Reset trailing stop when entering this zone</td></tr>
            </table>

            <h3 id="toc-24">Take Profit Types</h3>
            <table>
                <tr><th>Type</th><th>Description</th></tr>
                <tr><td><b>None</b></td><td>No take profit target in this zone</td></tr>
                <tr><td><b>Fixed %%</b></td><td>Exit at X%% above entry price</td></tr>
                <tr><td><b>Fixed ATR</b></td><td>Exit at X * ATR(14) above entry price</td></tr>
            </table>

            <h3 id="toc-25">Partial Exits</h3>
            <p>Close only a portion of the position in a zone:</p>
            <table>
                <tr><td><b>Exit %%</b></td><td>Percentage of position to close (e.g., 50%%)</td></tr>
                <tr><td><b>Basis</b></td><td><b>Original:</b> %% of original size / <b>Remaining:</b> %% of what's left</td></tr>
                <tr><td><b>Max Exits</b></td><td>Maximum partial exits in this zone</td></tr>
                <tr><td><b>Min Bars Between</b></td><td>Minimum bars between partial exits</td></tr>
                <tr><td><b>Re-entry</b></td><td><b>Block:</b> No re-exit in same zone / <b>Reset:</b> Allow if price leaves and returns</td></tr>
            </table>

            <h3 id="toc-26">Zone Phase Filters</h3>
            <p>Each zone can have its own phase requirements (e.g., only trigger stop loss during high volatility).</p>

            <!-- ==================== PHASES SECTION ==================== -->

            <h2 id="toc-27">Phases (Market Filters)</h2>

            <p>Phases are <b>optional</b> filters that control <b>when</b> your strategy can trade. Without phases, your strategy trades purely based on the DSL entry condition.</p>

            <div class="box">
                <b>Simple strategy (no phases):</b> Entry fires whenever <code>RSI(14) &lt; 30</code> is true.<br>
                <b>Filtered strategy:</b> Entry fires when <code>RSI(14) &lt; 30</code> is true <i>AND</i> required phases are active.
            </div>

            <p>When phases are configured:</p>
            <ul>
                <li><b>Required phases:</b> ALL must be active to allow entry</li>
                <li><b>Excluded phases:</b> NONE must be active to allow entry</li>
            </ul>
            <div class="tip">
                <b>Multi-timeframe analysis:</b> Phases can run on different timeframes than your strategy. Use a daily uptrend phase to filter 1h entries.
            </div>

            <h3 id="toc-28">Session Phases</h3>
            <table>
                <tr><th>Phase</th><th>Hours (UTC)</th><th>Usage Ideas</th></tr>
                <tr><td><b>Asian Session</b></td><td>00:00 - 09:00</td><td>Range-bound strategies; lower volatility scalping; accumulation plays</td></tr>
                <tr><td><b>European Session</b></td><td>07:00 - 16:00</td><td>Breakout strategies at London open; trend continuation; high volume plays</td></tr>
                <tr><td><b>US Market Hours</b></td><td>14:00 - 21:00</td><td>High volatility momentum; news-driven moves; trend strategies</td></tr>
                <tr><td><b>US Core Hours</b></td><td>15:00 - 21:00</td><td>Most liquid period; tighter spreads; institutional flow</td></tr>
                <tr><td><b>Session Overlap</b></td><td>14:00 - 16:00</td><td>Maximum liquidity; best fills; strongest directional moves</td></tr>
            </table>
            <div class="box">
                <b>Example:</b> Require "US Market Hours" for momentum strategies, exclude for mean-reversion.<br>
                <b>Example:</b> Require "Session Overlap" for breakout plays needing high volume confirmation.
            </div>

            <h3 id="toc-29">Day/Time Phases</h3>
            <table>
                <tr><th>Phase</th><th>Usage Ideas</th></tr>
                <tr><td><b>Monday</b></td><td>Gap fills from weekend; cautious positioning; wait for direction</td></tr>
                <tr><td><b>Tuesday - Thursday</b></td><td>Core trading days; strongest trends; most reliable signals</td></tr>
                <tr><td><b>Friday</b></td><td>Position squaring; reduced late-day entries; watch for reversals</td></tr>
                <tr><td><b>Weekdays</b></td><td>Exclude weekend noise; focus on institutional flow</td></tr>
                <tr><td><b>Weekend</b></td><td>Lower liquidity plays; retail-driven moves; gap risk strategies</td></tr>
            </table>
            <div class="box">
                <b>Example:</b> Exclude Monday + Friday for trend strategies to avoid choppy opens/closes.<br>
                <b>Example:</b> Require "Weekdays" to avoid weekend low-liquidity wicks.
            </div>

            <h3 id="toc-30">Technical Phases</h3>
            <p>Evaluated on the <b>daily timeframe</b> for macro context:</p>
            <table>
                <tr><th>Phase</th><th>Condition</th><th>Usage Ideas</th></tr>
                <tr><td><b>Uptrend</b></td><td>ADX &gt; 25, +DI &gt; -DI</td><td>Long-only strategies; buy dips; trend following</td></tr>
                <tr><td><b>Downtrend</b></td><td>ADX &gt; 25, -DI &gt; +DI</td><td>Short-only strategies; sell rallies; avoid longs</td></tr>
                <tr><td><b>Ranging</b></td><td>ADX &lt; 20</td><td>Mean-reversion; Bollinger bounces; avoid breakouts</td></tr>
                <tr><td><b>Golden Cross</b></td><td>SMA(50) &gt; SMA(200)</td><td>Bull market bias; aggressive longs; higher risk tolerance</td></tr>
                <tr><td><b>Death Cross</b></td><td>SMA(50) &lt; SMA(200)</td><td>Bear market caution; defensive positioning; tighter stops</td></tr>
                <tr><td><b>Overbought</b></td><td>RSI(14) &gt; 70</td><td>Avoid new longs; look for shorts; take profits</td></tr>
                <tr><td><b>Oversold</b></td><td>RSI(14) &lt; 30</td><td>Avoid new shorts; look for longs; accumulation zones</td></tr>
            </table>
            <div class="box">
                <b>Example:</b> Require "Uptrend" + "Golden Cross" for maximum bullish confluence.<br>
                <b>Example:</b> Exclude "Overbought" from long entries to avoid buying tops.<br>
                <b>Example:</b> Require "Ranging" for mean-reversion strategies.
            </div>

            <h3 id="toc-31">Calendar Phases</h3>
            <table>
                <tr><th>Phase</th><th>Description</th><th>Usage Ideas</th></tr>
                <tr><td><b>Month Start</b></td><td>Days 1-5</td><td>Fresh capital inflows; bullish bias; institutional buying</td></tr>
                <tr><td><b>Month End</b></td><td>Days 25+</td><td>Rebalancing flows; window dressing; potential reversals</td></tr>
                <tr><td><b>Quarter End</b></td><td>Last week of Mar/Jun/Sep/Dec</td><td>Major rebalancing; increased volatility; fund flows</td></tr>
                <tr><td><b>US Bank Holiday</b></td><td>Fed holidays</td><td>Low liquidity; avoid or use wider stops; reduced position size</td></tr>
                <tr><td><b>FOMC Meeting</b></td><td>Fed meeting days</td><td>Extreme volatility; avoid or trade the event; wait for clarity</td></tr>
                <tr><td><b>Full Moon</b></td><td>~1 day/hour window</td><td>Sentiment extremes; contrarian signals; lunar cycle analysis</td></tr>
                <tr><td><b>New Moon</b></td><td>~1 day/hour window</td><td>Fresh starts; trend initiations; cycle beginnings</td></tr>
            </table>
            <div class="box">
                <b>Example:</b> Exclude "FOMC Meeting" to avoid unpredictable Fed volatility.<br>
                <b>Example:</b> Require "Month Start" for momentum strategies (capital inflow bias).<br>
                <b>Example:</b> Exclude "US Bank Holiday" to avoid low-liquidity traps.
            </div>

            <h3 id="toc-32">Funding Phases</h3>
            <p>Futures-specific phases based on funding rates:</p>
            <table>
                <tr><th>Phase</th><th>Condition</th><th>Usage Ideas</th></tr>
                <tr><td><b>High Funding</b></td><td>&gt; 0.05%%</td><td>Overleveraged longs; contrarian shorts; funding arbitrage</td></tr>
                <tr><td><b>Negative Funding</b></td><td>&lt; 0</td><td>Shorts paying longs; bullish bias; long entries favored</td></tr>
                <tr><td><b>Extreme Funding</b></td><td>&gt; 0.1%% or &lt; -0.05%%</td><td>Imminent squeeze risk; reduce exposure; wait for normalization</td></tr>
                <tr><td><b>Neutral Funding</b></td><td>-0.01%% to 0.02%%</td><td>Balanced market; trend strategies work best; normal conditions</td></tr>
            </table>
            <div class="box">
                <b>Example:</b> Exclude "High Funding" from longs (crowded trade risk).<br>
                <b>Example:</b> Require "Negative Funding" for long entries (shorts paying you).<br>
                <b>Example:</b> Exclude "Extreme Funding" entirely (squeeze risk both ways).
            </div>

            <h3 id="toc-33">Custom Phases</h3>
            <p>Create your own phases with <b>any DSL condition</b> on <b>any timeframe</b>. Open the Phases window to add custom phases.</p>

            <div class="box">
                <b>Custom Phase Examples:</b><br><br>
                <b>Strong Trend (4h):</b> ADX(14) &gt; 30 AND ATR(14) &gt; ATR(14)[20]<br>
                <span class="small">Use: Only trade when trend is strong and volatility expanding</span><br><br>
                <b>Whale Accumulation:</b> WHALE_DELTA(100000) &gt; 0 AND close &lt; SMA(20)<br>
                <span class="small">Use: Large players buying while price is below average</span><br><br>
                <b>Volatility Squeeze:</b> BBANDS(20,2).width &lt; LOWEST(BBANDS(20,2).width, 50) * 1.1<br>
                <span class="small">Use: Breakout imminent, prepare for directional move</span><br><br>
                <b>Volume Spike:</b> volume &gt; AVG_VOLUME(20) * 2<br>
                <span class="small">Use: Require confirmation of significant participation</span><br><br>
                <b>Near Support Ray:</b> SUPPORT_RAY_DISTANCE(1, 200, 5) &lt; 1.0<br>
                <span class="small">Use: Price approaching dynamic support trendline</span><br><br>
                <b>Hammer at Support (4h):</b> HAMMER AND RSI(14) &lt; 40<br>
                <span class="small">Use: Bullish reversal candle at oversold levels</span><br><br>
                <b>Large Candle (4h):</b> BODY_SIZE &gt; ATR(14) * 1.5<br>
                <span class="small">Use: Significant directional move, momentum confirmation</span>
            </div>
            <div class="tip">
                <b>Phase timeframes:</b> Use higher timeframes (4h, 1d) for context, lower timeframes (15m, 1h) for precision timing.
            </div>

            <!-- ==================== HOOPS SECTION ==================== -->

            <h2 id="toc-34">Hoop Patterns</h2>

            <p>Hoops detect <b>chart patterns</b> by checking if price passes through a sequence of "hoops" (price checkpoints). Each hoop defines a price range and timing window.</p>

            <h3 id="toc-35">How Hoops Work</h3>
            <p>A hoop pattern is a series of checkpoints that price must hit in sequence:</p>
            <ul>
                <li><b>Anchor:</b> Starting reference point (first price that enters hoop 1)</li>
                <li><b>Checkpoints:</b> Each subsequent hoop defines a price range relative to the anchor</li>
                <li><b>Timing:</b> Distance and tolerance control how many bars between hoops</li>
                <li><b>Completion:</b> Pattern fires when all hoops are hit in order within timing constraints</li>
            </ul>
            <div class="box">
                <b>Visual Example - V-Bottom:</b><br>
                <pre style="font-size: 9px; margin: 4px 0;">
     Anchor ─────┐
                 │  Hoop 1: -2%% to -4%%
                 ▼
              ╲    ╱  Hoop 2: -1%% to +1%%
               ╲  ╱
                ╲╱     ← Bottom (low point)
                       Hoop 3: +2%% to +5%% (breakout)
                </pre>
            </div>

            <h3 id="toc-36">Hoop Settings</h3>
            <table>
                <tr><th>Setting</th><th>Description</th><th>Tips</th></tr>
                <tr><td><b>Min Price %%</b></td><td>Minimum %% from anchor</td><td>Use negative for drops, positive for rises</td></tr>
                <tr><td><b>Max Price %%</b></td><td>Maximum %% from anchor</td><td>Leave null for "at least X%%" breakouts</td></tr>
                <tr><td><b>Distance</b></td><td>Expected bars from previous hoop</td><td>Based on your timeframe (e.g., 10 bars on 1h = 10 hours)</td></tr>
                <tr><td><b>Tolerance</b></td><td>+/- bars allowed from distance</td><td>Higher = looser pattern, more matches</td></tr>
                <tr><td><b>Anchor Mode</b></td><td>How next anchor is set</td><td><b>Actual:</b> Use real hit price, <b>Target:</b> Use zone midpoint</td></tr>
            </table>
            <p><b>Pattern-level settings:</b></p>
            <table>
                <tr><td><b>Cooldown Bars</b></td><td>Wait N bars after pattern completes before detecting another</td></tr>
                <tr><td><b>Allow Overlap</b></td><td>Can a new pattern start while previous is incomplete?</td></tr>
                <tr><td><b>Price Smoothing</b></td><td>Apply SMA/EMA/HLC3 to price for smoother detection</td></tr>
            </table>

            <h3 id="toc-37">Pattern Examples</h3>

            <div class="box">
                <b>Double Bottom</b> - Classic reversal pattern<br>
                <table style="margin-top: 4px;">
                    <tr><th>Hoop</th><th>Price %%</th><th>Distance</th><th>Description</th></tr>
                    <tr><td>1</td><td>-1%% to -3%%</td><td>5 bars</td><td>First low</td></tr>
                    <tr><td>2</td><td>+1%% to +4%%</td><td>7 bars</td><td>Middle peak</td></tr>
                    <tr><td>3</td><td>-3%% to +0.5%%</td><td>7 bars</td><td>Second low (near first)</td></tr>
                    <tr><td>4</td><td>+2%% to null</td><td>5 bars</td><td>Breakout confirmation</td></tr>
                </table>
                <span class="small">Use: Long entry on hoop 4 completion with stop below the lows.</span>
            </div>

            <div class="box">
                <b>Bull Flag</b> - Trend continuation pattern<br>
                <table style="margin-top: 4px;">
                    <tr><th>Hoop</th><th>Price %%</th><th>Distance</th><th>Description</th></tr>
                    <tr><td>1</td><td>+3%% to +8%%</td><td>5 bars</td><td>Flagpole (strong move up)</td></tr>
                    <tr><td>2</td><td>+1%% to +5%%</td><td>10 bars</td><td>Consolidation (flag)</td></tr>
                    <tr><td>3</td><td>+6%% to null</td><td>5 bars</td><td>Breakout above flag</td></tr>
                </table>
                <span class="small">Use: Continuation long after flag breakout.</span>
            </div>

            <div class="box">
                <b>Head and Shoulders</b> - Reversal pattern<br>
                <table style="margin-top: 4px;">
                    <tr><th>Hoop</th><th>Price %%</th><th>Distance</th><th>Description</th></tr>
                    <tr><td>1</td><td>+2%% to +4%%</td><td>5 bars</td><td>Left shoulder</td></tr>
                    <tr><td>2</td><td>-1%% to +1%%</td><td>5 bars</td><td>Neckline</td></tr>
                    <tr><td>3</td><td>+4%% to +7%%</td><td>5 bars</td><td>Head (higher high)</td></tr>
                    <tr><td>4</td><td>-1%% to +1%%</td><td>5 bars</td><td>Return to neckline</td></tr>
                    <tr><td>5</td><td>+2%% to +4%%</td><td>5 bars</td><td>Right shoulder (lower)</td></tr>
                    <tr><td>6</td><td>-2%% to null</td><td>5 bars</td><td>Breakdown</td></tr>
                </table>
                <span class="small">Use: Short entry on neckline breakdown.</span>
            </div>

            <h3 id="toc-38">Combine Modes</h3>
            <p>How to combine hoop patterns with DSL conditions:</p>
            <table>
                <tr><th>Mode</th><th>Behavior</th><th>Use Case</th></tr>
                <tr><td><b>DSL Only</b></td><td>Ignore hoops entirely</td><td>Traditional indicator-based strategies</td></tr>
                <tr><td><b>Hoop Only</b></td><td>Ignore DSL entirely</td><td>Pure pattern recognition</td></tr>
                <tr><td><b>AND</b></td><td>Both must trigger</td><td>Pattern + confirmation (e.g., double bottom + RSI oversold)</td></tr>
                <tr><td><b>OR</b></td><td>Either can trigger</td><td>Multiple entry signals (pattern or indicator)</td></tr>
            </table>
            <div class="tip">
                <b>Best practice:</b> Use AND mode with a confirming indicator (RSI, volume) to filter false pattern signals.
            </div>

            <!-- ==================== TIPS SECTION ==================== -->

            <h2 id="toc-39">Tips</h2>

            <div class="tip">
                <b>Start simple.</b> A clear entry with basic zones beats a complex system you don't understand.
            </div>

            <div class="tip">
                <b>Use phases for context.</b> A strategy that works in uptrends may fail in downtrends.
            </div>

            <div class="tip">
                <b>Watch the metrics.</b> Win rate alone doesn't matter - look at profit factor and max drawdown.
            </div>

            <div class="tip">
                <b>Test order types.</b> Limit entries can improve average entry price but reduce fill rate.
            </div>

            </body>
            </html>
            """;
    }

    private void performSearch() {
        clearSearchHighlights();
        searchMatches.clear();
        currentMatchIndex = -1;

        String searchText = searchField.getText().toLowerCase().trim();
        if (searchText.isEmpty()) {
            searchResultLabel.setText("");
            return;
        }

        try {
            Document doc = helpPane.getDocument();
            String text = doc.getText(0, doc.getLength()).toLowerCase();

            int index = 0;
            while ((index = text.indexOf(searchText, index)) != -1) {
                searchMatches.add(new int[]{index, index + searchText.length()});
                index += searchText.length();
            }

            if (searchMatches.isEmpty()) {
                searchResultLabel.setText("0 results");
            } else {
                // Highlight all matches
                Highlighter highlighter = helpPane.getHighlighter();
                for (int[] match : searchMatches) {
                    highlighter.addHighlight(match[0], match[1], searchHighlightPainter);
                }
                // Go to first match
                currentMatchIndex = 0;
                highlightCurrentMatch();
                updateSearchResultLabel();
            }
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    private void highlightCurrentMatch() {
        if (searchMatches.isEmpty() || currentMatchIndex < 0) return;

        try {
            int[] match = searchMatches.get(currentMatchIndex);

            // Remove previous current highlight and re-add all with normal color
            Highlighter highlighter = helpPane.getHighlighter();
            highlighter.removeAllHighlights();
            for (int i = 0; i < searchMatches.size(); i++) {
                int[] m = searchMatches.get(i);
                Highlighter.HighlightPainter painter = (i == currentMatchIndex) ? currentMatchPainter : searchHighlightPainter;
                highlighter.addHighlight(m[0], m[1], painter);
            }

            // Scroll to current match
            Rectangle rect = helpPane.modelToView(match[0]);
            if (rect != null) {
                rect.y = Math.max(0, rect.y - 50);
                rect.height = 100;
                helpPane.scrollRectToVisible(rect);
            }
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    private void goToNextMatch() {
        if (searchMatches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size();
        highlightCurrentMatch();
        updateSearchResultLabel();
    }

    private void goToPreviousMatch() {
        if (searchMatches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
        highlightCurrentMatch();
        updateSearchResultLabel();
    }

    private void updateSearchResultLabel() {
        if (searchMatches.isEmpty()) {
            searchResultLabel.setText("0 results");
        } else {
            searchResultLabel.setText((currentMatchIndex + 1) + "/" + searchMatches.size());
        }
    }

    private void clearSearchHighlights() {
        helpPane.getHighlighter().removeAllHighlights();
    }

    /**
     * Shows the strategy help dialog (singleton - reuses existing instance).
     */
    public static void show(Component parent) {
        if (instance != null && instance.isDisplayable()) {
            instance.toFront();
            instance.requestFocus();
            return;
        }

        Window window = SwingUtilities.getWindowAncestor(parent);
        instance = new StrategyHelpDialog(window);
        instance.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                instance = null;
            }
        });
        instance.setVisible(true);
    }
}
