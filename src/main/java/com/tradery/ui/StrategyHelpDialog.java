package com.tradery.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Dialog explaining strategy concepts at a user level.
 * Focuses on "what" and "why" rather than JSON structure.
 * Can be used by AI to explain how strategies work.
 */
public class StrategyHelpDialog extends JDialog {

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

        // Title bar area (28px height for macOS traffic lights)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 28));
        JLabel titleLabel = new JLabel("Strategy Guide", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        String content = buildHelpContent();
        JEditorPane helpPane = new JEditorPane("text/html", content);
        helpPane.setEditable(false);
        helpPane.setCaretPosition(0);
        helpPane.setBorder(new EmptyBorder(4, 4, 4, 4));
        helpPane.setBackground(UIManager.getColor("Panel.background"));

        JScrollPane scrollPane = new JScrollPane(helpPane);
        scrollPane.setPreferredSize(new Dimension(800, 700));
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
                body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; font-size: 11px; margin: 12px; background: %s; color: %s; line-height: 1.4; }
                h1 { color: %s; font-size: 15px; margin: 0 0 10px 0; }
                h2 { color: %s; font-size: 13px; margin: 18px 0 6px 0; border-bottom: 1px solid %s; padding-bottom: 3px; }
                h3 { color: %s; font-size: 11px; margin: 10px 0 4px 0; font-weight: 600; }
                h4 { color: %s; font-size: 11px; margin: 8px 0 3px 0; font-weight: normal; font-style: italic; }
                p { margin: 4px 0; }
                .box { background: %s; padding: 6px 10px; border-radius: 5px; margin: 6px 0; }
                .tip { background: %s; border-left: 3px solid %s; padding: 6px 10px; margin: 6px 0; }
                ul { margin: 4px 0; padding-left: 18px; }
                li { margin: 2px 0; }
                table { border-collapse: collapse; width: 100%%; margin: 4px 0; font-size: 10px; }
                td, th { text-align: left; padding: 3px 6px; border-bottom: 1px solid %s; }
                th { background: %s; font-weight: 600; }
                .flow { font-family: monospace; background: %s; padding: 8px; border-radius: 5px; margin: 6px 0; text-align: center; font-size: 10px; }
                .small { font-size: 10px; color: %s; }
            </style>
            </head>
            <body>

            <h1>Strategy Guide</h1>

            <p>A strategy defines <b>when to enter</b> and <b>how to exit</b> trades. The backtester simulates your strategy on historical data.</p>

            <div class="flow">
                Phase Filter &rarr; Entry Signal &rarr; Order Type &rarr; Position Opens &rarr; Exit Zone &rarr; Position Closes
            </div>

            <!-- ==================== ENTRY SECTION ==================== -->

            <h2>Entry Settings</h2>

            <h3>Entry Condition</h3>
            <p>A DSL formula that evaluates to true/false each bar. When true, a trade signal fires.</p>
            <div class="box">
                <b>Example:</b> RSI(14) &lt; 30 AND close &gt; SMA(200)<br>
                <span class="small">Looks for oversold conditions in an uptrend.</span>
            </div>

            <h3>Trade Limits</h3>
            <table>
                <tr><td><b>Max Open Trades</b></td><td>Maximum concurrent positions (DCA groups count as one)</td></tr>
                <tr><td><b>Min Candles Between</b></td><td>Minimum bars between new position entries</td></tr>
            </table>

            <h3>Order Types</h3>
            <p>Controls <b>how</b> you enter after a signal fires:</p>
            <table>
                <tr><th>Type</th><th>Behavior</th><th>Settings</th></tr>
                <tr><td><b>Market</b></td><td>Enter immediately at current price</td><td>None</td></tr>
                <tr><td><b>Limit</b></td><td>Enter when price drops to X%% below signal price</td><td>Offset %% (negative)</td></tr>
                <tr><td><b>Stop</b></td><td>Enter when price rises to X%% above signal price</td><td>Offset %% (positive)</td></tr>
                <tr><td><b>Trailing</b></td><td>Trail price down, enter on X%% reversal up</td><td>Reversal %%</td></tr>
            </table>
            <p class="small"><b>Expiration:</b> For non-market orders, cancel if not filled within X bars.</p>

            <h3>DCA (Dollar Cost Averaging)</h3>
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

            <h2>Exit Settings</h2>

            <h3>Exit Zones</h3>
            <p>Zones define behavior at different P&L levels. Each zone has a range and exit rules.</p>
            <div class="box">
                <b>Example Setup:</b><br>
                Zone 1: P&L &lt; -5%% &rarr; Exit immediately (stop loss)<br>
                Zone 2: P&L 0%% to 5%% &rarr; Exit if RSI &gt; 70<br>
                Zone 3: P&L &gt; 10%% &rarr; Exit immediately (take profit)
            </div>

            <h3>Zone Settings</h3>
            <table>
                <tr><th>Setting</th><th>Description</th></tr>
                <tr><td><b>P&L Range</b></td><td>Min/Max P&L %% where this zone applies</td></tr>
                <tr><td><b>Market Exit</b></td><td>Exit at market price (ignores SL/TP levels)</td></tr>
                <tr><td><b>Exit Condition</b></td><td>DSL condition that must be true to exit</td></tr>
                <tr><td><b>Min Bars Before Exit</b></td><td>Wait X bars after entry before allowing exit</td></tr>
            </table>

            <h3>Stop Loss Types</h3>
            <table>
                <tr><th>Type</th><th>Description</th></tr>
                <tr><td><b>None</b></td><td>No stop loss in this zone</td></tr>
                <tr><td><b>Fixed %%</b></td><td>Stop X%% below entry price</td></tr>
                <tr><td><b>Fixed ATR</b></td><td>Stop X * ATR(14) below entry price</td></tr>
                <tr><td><b>Trailing %%</b></td><td>Trail X%% below highest price since entry</td></tr>
                <tr><td><b>Trailing ATR</b></td><td>Trail X * ATR(14) below highest price</td></tr>
                <tr><td><b>Clear</b></td><td>Reset trailing stop when entering this zone</td></tr>
            </table>

            <h3>Take Profit Types</h3>
            <table>
                <tr><th>Type</th><th>Description</th></tr>
                <tr><td><b>None</b></td><td>No take profit target in this zone</td></tr>
                <tr><td><b>Fixed %%</b></td><td>Exit at X%% above entry price</td></tr>
                <tr><td><b>Fixed ATR</b></td><td>Exit at X * ATR(14) above entry price</td></tr>
            </table>

            <h3>Partial Exits</h3>
            <p>Close only a portion of the position in a zone:</p>
            <table>
                <tr><td><b>Exit %%</b></td><td>Percentage of position to close (e.g., 50%%)</td></tr>
                <tr><td><b>Basis</b></td><td><b>Original:</b> %% of original size / <b>Remaining:</b> %% of what's left</td></tr>
                <tr><td><b>Max Exits</b></td><td>Maximum partial exits in this zone</td></tr>
                <tr><td><b>Min Bars Between</b></td><td>Minimum bars between partial exits</td></tr>
                <tr><td><b>Re-entry</b></td><td><b>Block:</b> No re-exit in same zone / <b>Reset:</b> Allow if price leaves and returns</td></tr>
            </table>

            <h3>Zone Phase Filters</h3>
            <p>Each zone can have its own phase requirements (e.g., only trigger stop loss during high volatility).</p>

            <!-- ==================== PHASES SECTION ==================== -->

            <h2>Phases (Market Filters)</h2>

            <p>Phases filter <b>when</b> your strategy can trade. They evaluate on their own timeframe and act as gates for entry.</p>
            <ul>
                <li><b>Required phases:</b> ALL must be active to allow entry</li>
                <li><b>Excluded phases:</b> NONE must be active to allow entry</li>
            </ul>

            <h3>Built-in Phases</h3>

            <h4>Session Phases</h4>
            <table>
                <tr><th>Phase</th><th>Hours (UTC)</th><th>Description</th></tr>
                <tr><td>Asian Session</td><td>00:00 - 09:00</td><td>Tokyo/Hong Kong trading hours</td></tr>
                <tr><td>European Session</td><td>07:00 - 16:00</td><td>London/Frankfurt trading hours</td></tr>
                <tr><td>US Market Hours</td><td>14:00 - 21:00</td><td>NYSE/NASDAQ including open</td></tr>
                <tr><td>US Core Hours</td><td>15:00 - 21:00</td><td>NYSE/NASDAQ core session</td></tr>
                <tr><td>Session Overlap</td><td>14:00 - 16:00</td><td>US/Europe overlap - highest liquidity</td></tr>
            </table>

            <h4>Day/Time Phases</h4>
            <table>
                <tr><td>Monday - Sunday</td><td>Individual day filters</td></tr>
                <tr><td>Weekdays</td><td>Monday through Friday</td></tr>
                <tr><td>Weekend</td><td>Saturday and Sunday</td></tr>
            </table>

            <h4>Technical Phases (Daily Timeframe)</h4>
            <table>
                <tr><th>Phase</th><th>Condition</th></tr>
                <tr><td>Uptrend</td><td>ADX &gt; 25 and +DI &gt; -DI</td></tr>
                <tr><td>Downtrend</td><td>ADX &gt; 25 and -DI &gt; +DI</td></tr>
                <tr><td>Ranging</td><td>ADX &lt; 20</td></tr>
                <tr><td>Golden Cross</td><td>SMA(50) &gt; SMA(200)</td></tr>
                <tr><td>Death Cross</td><td>SMA(50) &lt; SMA(200)</td></tr>
                <tr><td>Overbought</td><td>RSI(14) &gt; 70</td></tr>
                <tr><td>Oversold</td><td>RSI(14) &lt; 30</td></tr>
            </table>

            <h4>Calendar Phases</h4>
            <table>
                <tr><td>Month Start</td><td>Days 1-5 of month (fresh capital inflows)</td></tr>
                <tr><td>Month End</td><td>Days 25+ of month (rebalancing flows)</td></tr>
                <tr><td>Quarter End</td><td>Last week of Mar, Jun, Sep, Dec</td></tr>
                <tr><td>US Bank Holiday</td><td>Federal Reserve holidays</td></tr>
                <tr><td>FOMC Meeting</td><td>Fed meeting days (8 per year)</td></tr>
            </table>

            <h4>Moon Phases</h4>
            <table>
                <tr><td>Full Moon (Day)</td><td>~1 day around full moon</td></tr>
                <tr><td>Full Moon (Hour)</td><td>~1 hour precision around full moon</td></tr>
                <tr><td>New Moon (Day)</td><td>~1 day around new moon</td></tr>
                <tr><td>New Moon (Hour)</td><td>~1 hour precision around new moon</td></tr>
            </table>

            <h4>Funding Rate Phases</h4>
            <table>
                <tr><td>High Funding</td><td>Funding &gt; 0.05%% (overleveraged longs)</td></tr>
                <tr><td>Negative Funding</td><td>Funding &lt; 0 (shorts paying longs)</td></tr>
                <tr><td>Extreme Funding</td><td>Funding &gt; 0.1%% or &lt; -0.05%%</td></tr>
                <tr><td>Neutral Funding</td><td>Funding between -0.01%% and 0.02%%</td></tr>
            </table>

            <h3>Custom Phases</h3>
            <p>Create your own phases with any DSL condition and timeframe.</p>

            <!-- ==================== HOOPS SECTION ==================== -->

            <h2>Hoop Patterns</h2>

            <p>Detect chart patterns by checking if price passes through a sequence of "hoops" (price checkpoints).</p>

            <div class="box">
                <b>Example - Double Bottom:</b><br>
                1. First Low: price drops 1-3%% from anchor<br>
                2. Middle Peak: price rises 1-4%% from first low<br>
                3. Second Low: price drops to within 0.5%% of first low<br>
                4. Breakout: price rises 2%% above middle peak
            </div>

            <h3>Hoop Settings</h3>
            <table>
                <tr><th>Setting</th><th>Description</th></tr>
                <tr><td><b>Min/Max Price %%</b></td><td>P&L range from anchor where hoop is valid</td></tr>
                <tr><td><b>Distance</b></td><td>Expected bars from previous hoop</td></tr>
                <tr><td><b>Tolerance</b></td><td>Allowed deviation (+/-) from expected distance</td></tr>
                <tr><td><b>Anchor Mode</b></td><td><b>Actual Hit:</b> Anchor at actual price hit / <b>Target:</b> Anchor at target midpoint</td></tr>
            </table>

            <h3>Pattern Settings</h3>
            <table>
                <tr><td><b>Cooldown Bars</b></td><td>Bars to wait after pattern completes before allowing another</td></tr>
                <tr><td><b>Allow Overlap</b></td><td>Whether patterns can overlap in time</td></tr>
            </table>

            <h3>Combine Modes</h3>
            <p>How to combine hoop patterns with DSL conditions:</p>
            <table>
                <tr><td><b>DSL Only</b></td><td>Ignore hoops, use only DSL condition</td></tr>
                <tr><td><b>Hoop Only</b></td><td>Ignore DSL, use only hoop pattern</td></tr>
                <tr><td><b>AND</b></td><td>Both DSL and hoop must trigger</td></tr>
                <tr><td><b>OR</b></td><td>Either DSL or hoop can trigger</td></tr>
            </table>

            <h3>Entry vs Exit Patterns</h3>
            <ul>
                <li><b>Required Entry Patterns:</b> Pattern must complete for entry</li>
                <li><b>Excluded Entry Patterns:</b> Pattern must NOT be active for entry</li>
                <li><b>Required Exit Patterns:</b> Pattern must complete for exit</li>
                <li><b>Excluded Exit Patterns:</b> Pattern must NOT be active for exit</li>
            </ul>

            <!-- ==================== TIPS SECTION ==================== -->

            <h2>Tips</h2>

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
            """.formatted(
                bgHex, fgHex, accentHex, fgHex, fgSecHex, fgSecHex, fgSecHex,
                codeBgHex, codeBgHex, accentHex, borderHex, codeBgHex, codeBgHex, fgSecHex
            );
    }

    private String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Shows the strategy help dialog.
     */
    public static void show(Component parent) {
        Window window = SwingUtilities.getWindowAncestor(parent);
        StrategyHelpDialog dialog = new StrategyHelpDialog(window);
        dialog.setVisible(true);
    }
}
