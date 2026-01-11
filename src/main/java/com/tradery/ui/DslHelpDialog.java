package com.tradery.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Dialog showing DSL syntax reference.
 *
 * MAINTENANCE NOTE: If the DSL is extended with new indicators, functions,
 * or operators, this help content must be updated to reflect those changes.
 * See also: Lexer.java (keywords), Parser.java (grammar), ConditionEvaluator.java (evaluation)
 */
public class DslHelpDialog extends JDialog {

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

        // Title bar area (28px height for macOS traffic lights)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 28));
        JLabel titleLabel = new JLabel("DSL Reference", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        String content = buildHelpContent();
        JEditorPane helpPane = new JEditorPane("text/html", content);
        helpPane.setEditable(false);
        helpPane.setCaretPosition(0);
        helpPane.setBorder(new EmptyBorder(4, 4, 4, 4));
        helpPane.setBackground(UIManager.getColor("Panel.background"));

        JScrollPane scrollPane = new JScrollPane(helpPane);
        scrollPane.setPreferredSize(new Dimension(960, 600));
        scrollPane.setBorder(null);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(closeButton);

        // Wrap content with separators
        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.add(new JSeparator(), BorderLayout.NORTH);
        mainContent.add(scrollPane, BorderLayout.CENTER);

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

    private String buildHelpContent() {
        // Get theme colors
        Color bg = UIManager.getColor("Panel.background");
        Color fg = UIManager.getColor("Label.foreground");
        Color fgSecondary = UIManager.getColor("Label.disabledForeground");
        Color accent = UIManager.getColor("Component.accentColor");
        Color codeBg = UIManager.getColor("TextField.background");
        Color tableBorder = UIManager.getColor("Separator.foreground");

        if (bg == null) bg = Color.WHITE;
        if (fg == null) fg = Color.BLACK;
        if (fgSecondary == null) fgSecondary = Color.GRAY;
        if (accent == null) accent = new Color(70, 130, 180);
        if (codeBg == null) codeBg = new Color(240, 240, 240);
        if (tableBorder == null) tableBorder = new Color(200, 200, 200);

        String bgHex = toHex(bg);
        String fgHex = toHex(fg);
        String fgSecHex = toHex(fgSecondary);
        String accentHex = toHex(accent);
        String codeBgHex = toHex(codeBg);
        String borderHex = toHex(tableBorder);

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

            <h2>Strategy DSL Reference</h2>

            <div class="section">
            <h3>Indicators</h3>
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
            </table>
            <div class="example">MACD(12,26,9).histogram > 0<br>close &lt; BBANDS(20,2).lower<br>ADX(14) > 25 AND PLUS_DI(14) > MINUS_DI(14)</div>
            </div>

            <div class="section">
            <h3>Price References</h3>
            <table>
                <tr><td><code>price</code> or <code>close</code></td><td>Closing price</td></tr>
                <tr><td><code>open</code></td><td>Opening price</td></tr>
                <tr><td><code>high</code></td><td>High price</td></tr>
                <tr><td><code>low</code></td><td>Low price</td></tr>
                <tr><td><code>volume</code></td><td>Volume</td></tr>
            </table>
            </div>

            <div class="section">
            <h3>Range & Volume Functions</h3>
            <table>
                <tr><td><code>HIGH_OF(period)</code></td><td>Highest high over period</td></tr>
                <tr><td><code>LOW_OF(period)</code></td><td>Lowest low over period</td></tr>
                <tr><td><code>AVG_VOLUME(period)</code></td><td>Average volume over period</td></tr>
            </table>
            <div class="example">close > HIGH_OF(20)<br>volume > AVG_VOLUME(20) * 1.5</div>
            </div>

            <div class="section">
            <h3>Orderflow Functions</h3>
            <span style="color: %s; font-size: 10px;">Enable Orderflow Mode in strategy settings. Tier 1 = instant, Tier 2 = requires sync.</span>
            <table>
                <tr><th>Function</th><th>Tier</th><th>Description</th></tr>
                <tr><td><code>VWAP</code></td><td>1</td><td>Volume Weighted Average Price (session)</td></tr>
                <tr><td><code>POC(period)</code></td><td>1</td><td>Point of Control - price level with most volume (default: 20)</td></tr>
                <tr><td><code>VAH(period)</code></td><td>1</td><td>Value Area High - top of 70%% volume zone (default: 20)</td></tr>
                <tr><td><code>VAL(period)</code></td><td>1</td><td>Value Area Low - bottom of 70%% volume zone (default: 20)</td></tr>
                <tr><td><code>DELTA</code></td><td>2</td><td>Bar delta (buy volume - sell volume)</td></tr>
                <tr><td><code>CUM_DELTA</code></td><td>2</td><td>Cumulative delta from session start</td></tr>
            </table>
            <div class="example">close > VWAP<br>price crosses_above POC(20)<br>DELTA > 0 AND close > VAH(20)</div>
            </div>

            <div class="section">
            <h3>Time Functions</h3>
            <table>
                <tr><td><code>DAYOFWEEK</code></td><td>Day of week (1=Mon, 2=Tue, ..., 7=Sun)</td></tr>
                <tr><td><code>HOUR</code></td><td>Hour of day (0-23, UTC)</td></tr>
                <tr><td><code>DAY</code></td><td>Day of month (1-31)</td></tr>
                <tr><td><code>MONTH</code></td><td>Month of year (1-12)</td></tr>
            </table>
            <div class="example">DAYOFWEEK == 1<br>HOUR >= 8 AND HOUR &lt;= 16</div>
            </div>

            <div class="section">
            <h3>Moon Functions</h3>
            <table>
                <tr><td><code>MOON_PHASE</code></td><td>Moon phase (0.0=new, 0.5=full, 1.0=new)</td></tr>
            </table>
            <div class="example">MOON_PHASE >= 0.48 AND MOON_PHASE &lt;= 0.52<br>MOON_PHASE &lt;= 0.02 OR MOON_PHASE >= 0.98</div>
            </div>

            <div class="section">
            <h3>Calendar Functions</h3>
            <table>
                <tr><td><code>IS_US_HOLIDAY</code></td><td>US Federal Reserve bank holiday (1=yes, 0=no)</td></tr>
                <tr><td><code>IS_FOMC_MEETING</code></td><td>FOMC meeting day (1=yes, 0=no)</td></tr>
            </table>
            <div class="example">IS_US_HOLIDAY == 1<br>IS_FOMC_MEETING == 1</div>
            <span style="color: %s; font-size: 10px;">Holidays: New Year's, MLK Day, Presidents Day, Memorial Day, Juneteenth, July 4th, Labor Day, Columbus Day, Veterans Day, Thanksgiving, Christmas<br>FOMC: 8 meetings/year (2024-2026 schedule built-in)</span>
            </div>

            <div class="section">
            <h3>Comparison Operators</h3>
            <table>
                <tr><td><code>&gt;</code></td><td>Greater than</td></tr>
                <tr><td><code>&lt;</code></td><td>Less than</td></tr>
                <tr><td><code>&gt;=</code></td><td>Greater than or equal</td></tr>
                <tr><td><code>&lt;=</code></td><td>Less than or equal</td></tr>
                <tr><td><code>==</code></td><td>Equal to</td></tr>
            </table>
            </div>

            <div class="section">
            <h3>Cross Operators</h3>
            <table>
                <tr><td><code>crosses_above</code></td><td>Value crosses above another</td></tr>
                <tr><td><code>crosses_below</code></td><td>Value crosses below another</td></tr>
            </table>
            <div class="example">EMA(9) crosses_above EMA(21)<br>RSI(14) crosses_below 30</div>
            </div>

            <div class="section">
            <h3>Logical Operators</h3>
            <table>
                <tr><td><code>AND</code></td><td>Both conditions must be true</td></tr>
                <tr><td><code>OR</code></td><td>Either condition must be true</td></tr>
            </table>
            <div class="example">RSI(14) &lt; 30 AND price > SMA(200)<br>EMA(9) > EMA(21) OR MACD(12,26,9).histogram > 0</div>
            </div>

            <div class="section">
            <h3>Arithmetic Operators</h3>
            <table>
                <tr><td><code>+</code> <code>-</code> <code>*</code> <code>/</code></td><td>Add, subtract, multiply, divide</td></tr>
            </table>
            <div class="example">volume > AVG_VOLUME(20) * 1.2<br>close > SMA(20) + ATR(14) * 2</div>
            </div>

            <div class="section">
            <h3>Example Strategies</h3>
            <div class="example">
            <b>RSI Oversold:</b> RSI(14) &lt; 30<br><br>
            <b>Trend Following:</b> EMA(9) crosses_above EMA(21) AND price > SMA(50)<br><br>
            <b>Bollinger Bounce:</b> close &lt; BBANDS(20,2).lower AND RSI(14) &lt; 35<br><br>
            <b>Breakout:</b> close > HIGH_OF(20) AND volume > AVG_VOLUME(20) * 1.5
            </div>
            </div>

            </body>
            </html>
            """.formatted(bgHex, fgHex, fgHex, fgSecHex, codeBgHex, fgHex, borderHex, codeBgHex, codeBgHex, accentHex, fgSecHex, fgSecHex);
    }

    private String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Shows the DSL help dialog.
     */
    public static void show(Component parent) {
        Window window = SwingUtilities.getWindowAncestor(parent);
        DslHelpDialog dialog = new DslHelpDialog(window);
        dialog.setVisible(true);
    }
}
