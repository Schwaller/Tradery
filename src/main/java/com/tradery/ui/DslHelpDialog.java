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
 * Dialog showing DSL syntax reference.
 *
 * MAINTENANCE NOTE: If the DSL is extended with new indicators, functions,
 * or operators, this help content must be updated to reflect those changes.
 * See also: Lexer.java (keywords), Parser.java (grammar), ConditionEvaluator.java (evaluation)
 */
public class DslHelpDialog extends JDialog {

    private static DslHelpDialog instance;

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

    public DslHelpDialog(Window owner) {
        super(owner, "DSL Reference", ModalityType.MODELESS);

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
        JLabel titleLabel = new JLabel("DSL Reference", SwingConstants.CENTER);
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
        mainContent.setPreferredSize(new Dimension(1100, 600));

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

        String bgHex = colors.bgHex;
        String fgHex = colors.fgHex;
        String fgSecHex = colors.fgSecHex;
        String accentHex = colors.accentHex;
        String codeBgHex = colors.codeBgHex;
        String borderHex = colors.borderHex;

        // Build TOC entries
        int tocIndex = 0;
        toc.add(new TocEntry("toc-" + tocIndex++, "Strategy DSL Reference", 2));
        toc.add(new TocEntry("toc-" + tocIndex++, "Indicators", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Price References", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Range & Volume Functions", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Orderflow Functions", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Daily Session Volume Profile", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Funding Rate Functions", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Open Interest Functions", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Time Functions", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Moon Functions", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Calendar Functions", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Comparison Operators", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Cross Operators", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Logical Operators", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Arithmetic Operators", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Math Functions", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Candlestick Patterns", 3));
        toc.add(new TocEntry("toc-" + tocIndex++, "Example Strategies", 3));

        return """
            <html>
            <head>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; font-size: 11px; margin: 8px; background: %s; color: %s; }
                h2 { color: %s; margin: 12px 0 6px 0; font-size: 13px; }
                h3 { color: %s; margin: 10px 0 4px 0; font-size: 12px; }
                code { background: %s; padding: 1px 4px; border-radius: 3px; font-family: monospace; color: %s; }
                .section { margin-bottom: 12px; }
                table { border-collapse: collapse; width: 100%%; margin: 4px 0; }
                td, th { text-align: left; padding: 3px 8px; border-bottom: 1px solid %s; }
                th { background: %s; font-weight: 600; }
                .example { background: %s; padding: 6px 10px; border-left: 3px solid %s; margin: 6px 0; font-family: monospace; }
            </style>
            </head>
            <body>

            <h2 id="toc-0">Strategy DSL Reference</h2>

            <div class="section">
            <h3 id="toc-1">Indicators</h3>
            <table>
                <tr><th>Indicator</th><th>Syntax</th><th>Description</th><th>Properties</th></tr>
                <tr><td>SMA</td><td><code>SMA(period)</code></td><td>Simple Moving Average</td><td></td></tr>
                <tr><td>EMA</td><td><code>EMA(period)</code></td><td>Exponential Moving Average</td><td></td></tr>
                <tr><td>RSI</td><td><code>RSI(period)</code></td><td>Relative Strength Index (0-100)</td><td></td></tr>
                <tr><td>ATR</td><td><code>ATR(period)</code></td><td>Average True Range</td><td></td></tr>
                <tr><td>ADX</td><td><code>ADX(period)</code></td><td>Average Directional Index (0-100, trend strength)</td><td></td></tr>
                <tr><td>+DI</td><td><code>PLUS_DI(period)</code></td><td>Plus Directional Indicator (upward pressure)</td><td></td></tr>
                <tr><td>-DI</td><td><code>MINUS_DI(period)</code></td><td>Minus Directional Indicator (downward pressure)</td><td></td></tr>
                <tr><td>MACD</td><td><code>MACD(fast,slow,signal)</code></td><td>MACD</td><td><code>.line</code> <code>.signal</code> <code>.histogram</code></td></tr>
                <tr><td>Bollinger</td><td><code>BBANDS(period,stdDev)</code></td><td>Bollinger Bands</td><td><code>.upper</code> <code>.middle</code> <code>.lower</code></td></tr>
                <tr><td>Stochastic</td><td><code>STOCHASTIC(kPeriod)</code> or <code>STOCHASTIC(kPeriod,dPeriod)</code></td><td>Stochastic Oscillator (0-100)</td><td><code>.k</code> <code>.d</code></td></tr>
            </table>
            <div class="example">MACD(12,26,9).histogram > 0<br>close &lt; BBANDS(20,2).lower<br>ADX(14) > 25 AND PLUS_DI(14) > MINUS_DI(14)<br>STOCHASTIC(14).k crosses_above STOCHASTIC(14).d</div>
            </div>

            <div class="section">
            <h3 id="toc-2">Price References</h3>
            <table>
                <tr><td><code>price</code> or <code>close</code></td><td>Closing price</td></tr>
                <tr><td><code>open</code></td><td>Opening price</td></tr>
                <tr><td><code>high</code></td><td>High price</td></tr>
                <tr><td><code>low</code></td><td>Low price</td></tr>
                <tr><td><code>volume</code></td><td>Volume</td></tr>
            </table>
            </div>

            <div class="section">
            <h3 id="toc-3">Range & Volume Functions</h3>
            <table>
                <tr><td><code>HIGH_OF(period)</code></td><td>Highest high over period</td></tr>
                <tr><td><code>LOW_OF(period)</code></td><td>Lowest low over period</td></tr>
                <tr><td><code>AVG_VOLUME(period)</code></td><td>Average volume over period</td></tr>
                <tr><td><code>RANGE_POSITION(period)</code></td><td>Position in range: -1=low, 0=mid, +1=high (extends beyond for breakouts)</td></tr>
                <tr><td><code>RANGE_POSITION(period,skip)</code></td><td>Same as above, but skip last N bars before calculating range</td></tr>
            </table>
            <div class="example">close > HIGH_OF(20)<br>volume > AVG_VOLUME(20) * 1.5<br>RANGE_POSITION(20) > 1<br>RANGE_POSITION(50,5) &lt; -1</div>
            <span style="color: %s; font-size: 10px;">RANGE_POSITION extends beyond ±1 for breakouts (e.g., 1.5 if 50%% above range). Use STOCHASTIC (clamped 0-100) for traditional oscillator.</span>
            </div>

            <div class="section">
            <h3 id="toc-4">Orderflow Functions</h3>
            <span style="color: %s; font-size: 10px;">Enable Orderflow Mode in strategy settings. Tier 1 = instant, Tier 2 = requires sync.</span>
            <table>
                <tr><th>Function</th><th>Tier</th><th>Description</th></tr>
                <tr><td><code>VWAP</code></td><td>1</td><td>Volume Weighted Average Price (session)</td></tr>
                <tr><td><code>POC(period)</code></td><td>1</td><td>Point of Control - price level with most volume (default: 20)</td></tr>
                <tr><td><code>VAH(period)</code></td><td>1</td><td>Value Area High - top of 70%% volume zone (default: 20)</td></tr>
                <tr><td><code>VAL(period)</code></td><td>1</td><td>Value Area Low - bottom of 70%% volume zone (default: 20)</td></tr>
                <tr><td><code>DELTA</code></td><td>2</td><td>Bar delta (buy volume - sell volume)</td></tr>
                <tr><td><code>CUM_DELTA</code></td><td>2</td><td>Cumulative delta from session start</td></tr>
                <tr><td><code>WHALE_DELTA(threshold)</code></td><td>2</td><td>Delta from trades &gt; $threshold only</td></tr>
                <tr><td><code>WHALE_BUY_VOL(threshold)</code></td><td>2</td><td>Buy volume from trades &gt; $threshold</td></tr>
                <tr><td><code>WHALE_SELL_VOL(threshold)</code></td><td>2</td><td>Sell volume from trades &gt; $threshold</td></tr>
                <tr><td><code>LARGE_TRADE_COUNT(threshold)</code></td><td>2</td><td>Number of trades &gt; $threshold in bar</td></tr>
            </table>
            <div class="example">close > VWAP<br>WHALE_DELTA(50000) > 0<br>LARGE_TRADE_COUNT(100000) > 5</div>
            </div>

            <div class="section">
            <h3 id="toc-ohlcv-volume">OHLCV Volume Functions</h3>
            <span style="color: %s; font-size: 10px;">Extended volume data from Binance klines. Available instantly - no aggTrades download needed!</span>
            <table>
                <tr><th>Function</th><th>Description</th></tr>
                <tr><td><code>QUOTE_VOLUME</code></td><td>Volume in quote currency (USD for BTCUSDT)</td></tr>
                <tr><td><code>BUY_VOLUME</code></td><td>Taker buy volume - aggressive buyers</td></tr>
                <tr><td><code>SELL_VOLUME</code></td><td>Taker sell volume - aggressive sellers</td></tr>
                <tr><td><code>OHLCV_DELTA</code></td><td>Buy volume - sell volume (basic delta from OHLCV)</td></tr>
                <tr><td><code>OHLCV_CVD</code></td><td>Cumulative delta from OHLCV data</td></tr>
                <tr><td><code>BUY_RATIO</code></td><td>Buy volume / total volume (0-1, where 0.5 = balanced)</td></tr>
                <tr><td><code>TRADE_COUNT</code></td><td>Number of trades in the bar</td></tr>
            </table>
            <div class="example">BUY_RATIO > 0.6<br>OHLCV_DELTA > 0 AND close > SMA(20)<br>OHLCV_CVD > OHLCV_CVD[1]</div>
            <span style="color: %s; font-size: 10px;">These are less granular than aggTrades-based DELTA/CUM_DELTA but available instantly!</span>
            </div>

            <div class="section">
            <h3 id="toc-5">Daily Session Volume Profile</h3>
            <span style="color: %s; font-size: 10px;">Key support/resistance levels from daily sessions (UTC day boundary).</span>
            <table>
                <tr><th>Function</th><th>Description</th></tr>
                <tr><td><code>PREV_DAY_POC</code></td><td>Previous day's Point of Control</td></tr>
                <tr><td><code>PREV_DAY_VAH</code></td><td>Previous day's Value Area High</td></tr>
                <tr><td><code>PREV_DAY_VAL</code></td><td>Previous day's Value Area Low</td></tr>
                <tr><td><code>TODAY_POC</code></td><td>Current day's developing POC (updates each bar)</td></tr>
                <tr><td><code>TODAY_VAH</code></td><td>Current day's developing VAH (updates each bar)</td></tr>
                <tr><td><code>TODAY_VAL</code></td><td>Current day's developing VAL (updates each bar)</td></tr>
            </table>
            <div class="example">close crosses_above PREV_DAY_POC<br>close > PREV_DAY_VAH AND TODAY_POC > PREV_DAY_POC<br>close &lt; TODAY_VAL AND close > PREV_DAY_VAL</div>
            </div>

            <div class="section">
            <h3 id="toc-6">Funding Rate Functions</h3>
            <span style="color: %s; font-size: 10px;">Requires funding data (auto-fetched from Binance Futures).</span>
            <table>
                <tr><td><code>FUNDING</code></td><td>Current funding rate (%%, e.g., 0.01 = 0.01%%)</td></tr>
                <tr><td><code>FUNDING_8H</code></td><td>8-hour rolling average funding rate</td></tr>
            </table>
            <div class="example">FUNDING > 0.05<br>FUNDING &lt; 0 AND WHALE_DELTA(50000) > 0</div>
            <span style="color: %s; font-size: 10px;">Positive funding = longs pay shorts (overleveraged longs). Negative = shorts pay longs.</span>
            </div>

            <div class="section">
            <h3 id="toc-7">Open Interest Functions</h3>
            <span style="color: %s; font-size: 10px;">Requires OI data (auto-fetched from Binance Futures, 5m resolution).</span>
            <table>
                <tr><td><code>OI</code></td><td>Current open interest value (in billions USD)</td></tr>
                <tr><td><code>OI_CHANGE</code></td><td>OI change from previous bar</td></tr>
                <tr><td><code>OI_DELTA(period)</code></td><td>OI change over N bars</td></tr>
            </table>
            <div class="example">OI_CHANGE > 0 AND close > close[1]<br>OI_DELTA(12) &lt; 0 AND close > SMA(20)</div>
            <span style="color: %s; font-size: 10px;">Rising OI + rising price = new longs. Falling OI + rising price = short covering.</span>
            </div>

            <div class="section">
            <h3 id="toc-8">Time Functions</h3>
            <table>
                <tr><td><code>DAYOFWEEK</code></td><td>Day of week (1=Mon, 2=Tue, ..., 7=Sun)</td></tr>
                <tr><td><code>HOUR</code></td><td>Hour of day (0-23, UTC)</td></tr>
                <tr><td><code>DAY</code></td><td>Day of month (1-31)</td></tr>
                <tr><td><code>MONTH</code></td><td>Month of year (1-12)</td></tr>
            </table>
            <div class="example">DAYOFWEEK == 1<br>HOUR >= 8 AND HOUR &lt;= 16</div>
            </div>

            <div class="section">
            <h3 id="toc-9">Moon Functions</h3>
            <table>
                <tr><td><code>MOON_PHASE</code></td><td>Moon phase (0.0=new, 0.5=full, 1.0=new)</td></tr>
            </table>
            <div class="example">MOON_PHASE >= 0.48 AND MOON_PHASE &lt;= 0.52<br>MOON_PHASE &lt;= 0.02 OR MOON_PHASE >= 0.98</div>
            </div>

            <div class="section">
            <h3 id="toc-10">Calendar Functions</h3>
            <table>
                <tr><td><code>IS_US_HOLIDAY</code></td><td>US Federal Reserve bank holiday (1=yes, 0=no)</td></tr>
                <tr><td><code>IS_FOMC_MEETING</code></td><td>FOMC meeting day (1=yes, 0=no)</td></tr>
            </table>
            <div class="example">IS_US_HOLIDAY == 1<br>IS_FOMC_MEETING == 1</div>
            <span style="color: %s; font-size: 10px;">Holidays: New Year's, MLK Day, Presidents Day, Memorial Day, Juneteenth, July 4th, Labor Day, Columbus Day, Veterans Day, Thanksgiving, Christmas<br>FOMC: 8 meetings/year (2024-2026 schedule built-in)</span>
            </div>

            <div class="section">
            <h3 id="toc-11">Comparison Operators</h3>
            <table>
                <tr><td><code>&gt;</code></td><td>Greater than</td></tr>
                <tr><td><code>&lt;</code></td><td>Less than</td></tr>
                <tr><td><code>&gt;=</code></td><td>Greater than or equal</td></tr>
                <tr><td><code>&lt;=</code></td><td>Less than or equal</td></tr>
                <tr><td><code>==</code></td><td>Equal to</td></tr>
            </table>
            </div>

            <div class="section">
            <h3 id="toc-12">Cross Operators</h3>
            <table>
                <tr><td><code>crosses_above</code></td><td>Value crosses above another</td></tr>
                <tr><td><code>crosses_below</code></td><td>Value crosses below another</td></tr>
            </table>
            <div class="example">EMA(9) crosses_above EMA(21)<br>RSI(14) crosses_below 30</div>
            </div>

            <div class="section">
            <h3 id="toc-13">Logical Operators</h3>
            <table>
                <tr><td><code>AND</code></td><td>Both conditions must be true</td></tr>
                <tr><td><code>OR</code></td><td>Either condition must be true</td></tr>
            </table>
            <div class="example">RSI(14) &lt; 30 AND price > SMA(200)<br>EMA(9) > EMA(21) OR MACD(12,26,9).histogram > 0</div>
            </div>

            <div class="section">
            <h3 id="toc-14">Arithmetic Operators</h3>
            <table>
                <tr><td><code>+</code> <code>-</code> <code>*</code> <code>/</code></td><td>Add, subtract, multiply, divide</td></tr>
            </table>
            <div class="example">volume > AVG_VOLUME(20) * 1.2<br>close > SMA(20) + ATR(14) * 2</div>
            </div>

            <div class="section">
            <h3 id="toc-15">Math Functions</h3>
            <table>
                <tr><td><code>abs(expr)</code></td><td>Absolute value</td></tr>
                <tr><td><code>min(expr1, expr2)</code></td><td>Minimum of two values</td></tr>
                <tr><td><code>max(expr1, expr2)</code></td><td>Maximum of two values</td></tr>
            </table>
            <div class="example">abs(close - open) > ATR(14) * 0.5<br>min(open, close) - low > 2 * abs(close - open)<br>max(RSI(14), RSI(14)[1]) &lt; 30</div>
            <span style="color: %s; font-size: 10px;">Useful for candlestick patterns: body = abs(close - open), lower_wick = min(open, close) - low</span>
            </div>

            <div class="section">
            <h3 id="toc-16">Candlestick Patterns</h3>
            <span style="color: %s; font-size: 10px;">Pattern functions return 1 if detected, 0 otherwise. Property functions support lookback [n].</span>
            <table>
                <tr><th>Function</th><th>Description</th></tr>
                <tr><td><code>HAMMER(ratio)</code></td><td>Hammer pattern - long lower wick (default ratio: 2.0)</td></tr>
                <tr><td><code>SHOOTING_STAR(ratio)</code></td><td>Shooting star - long upper wick (default ratio: 2.0)</td></tr>
                <tr><td><code>DOJI(ratio)</code></td><td>Doji - tiny body relative to range (default ratio: 0.1)</td></tr>
                <tr><td><code>BODY_SIZE</code></td><td>Absolute body size (close - open)</td></tr>
                <tr><td><code>BODY_RATIO</code></td><td>Body / total range (0-1)</td></tr>
                <tr><td><code>IS_BULLISH</code></td><td>1 if green candle (close > open)</td></tr>
                <tr><td><code>IS_BEARISH</code></td><td>1 if red candle (close &lt; open)</td></tr>
            </table>
            <div class="example">SHOOTING_STAR(2.0) AND BODY_SIZE[1] > ATR(14) * 1.5 AND IS_BULLISH[1] == 1<br>HAMMER AND RSI(14) &lt; 30<br>DOJI AND BODY_SIZE[1] > ATR(14)</div>
            <span style="color: %s; font-size: 10px;">Combine patterns with context: shooting star after strong bullish candle = reversal signal</span>
            </div>

            <div class="section">
            <h3 id="toc-17">Example Strategies</h3>
            <div class="example">
            <b>RSI Oversold:</b> RSI(14) &lt; 30<br><br>
            <b>Trend Following:</b> EMA(9) crosses_above EMA(21) AND price > SMA(50)<br><br>
            <b>Bollinger Bounce:</b> close &lt; BBANDS(20,2).lower AND RSI(14) &lt; 35<br><br>
            <b>Breakout:</b> close > HIGH_OF(20) AND volume > AVG_VOLUME(20) * 1.5
            </div>
            </div>

            </body>
            </html>
            """.formatted(bgHex, fgHex, accentHex, accentHex, codeBgHex, fgHex, borderHex, codeBgHex, codeBgHex, accentHex, fgSecHex, fgSecHex, fgSecHex, fgSecHex, fgSecHex, fgSecHex, fgSecHex, fgSecHex, fgSecHex, fgSecHex, fgSecHex, fgSecHex, fgSecHex);
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

    private String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Shows the DSL help dialog (singleton - reuses existing instance).
     */
    public static void show(Component parent) {
        if (instance != null && instance.isDisplayable()) {
            instance.toFront();
            instance.requestFocus();
            return;
        }

        Window window = SwingUtilities.getWindowAncestor(parent);
        instance = new DslHelpDialog(window);
        instance.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                instance = null;
            }
        });
        instance.setVisible(true);
    }
}
