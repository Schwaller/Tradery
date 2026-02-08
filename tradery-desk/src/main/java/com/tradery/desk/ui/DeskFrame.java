package com.tradery.desk.ui;

import com.tradery.core.model.Candle;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.desk.feed.BinanceWebSocketClient.ConnectionState;
import com.tradery.desk.signal.SignalEvent;
import com.tradery.desk.strategy.PublishedStrategy;
import com.tradery.symbols.service.SymbolService;
import com.tradery.symbols.ui.SymbolComboBox;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.ui.controls.SegmentedToggle;
import com.tradery.ui.controls.ThinSplitPane;
import com.tradery.ui.controls.ToolbarButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Main window for Trading Desk.
 * Displays connection status, active strategies, price chart, and signal log.
 */
public class DeskFrame extends JFrame {

    private final StatusPanel statusPanel;
    private final SymbolComboBox symbolCombo;
    private final JLabel priceLabel;
    private final JLabel updatedLabel;
    private volatile long lastUpdateReceived;
    private final Timer agoTimer;
    private final StrategyListPanel strategyListPanel;
    private final SignalLogPanel signalLogPanel;
    private final PriceChartPanel priceChartPanel;
    private final IndicatorSidePanel indicatorSidePanel;
    private JPanel headerBar;
    private PropertyChangeListener lafChangeListener;

    private Runnable onClose;

    public DeskFrame() {
        super("Trading Desk");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // Transparent title bar (same style as IntelFrame)
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        // Move traffic light buttons down to match native macOS spacing (FlatLaf 3.4+)
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        // Initialize header bar components before layout
        symbolCombo = new SymbolComboBox(new SymbolService(), true);
        symbolCombo.setToolbarMode();
        priceLabel = new JLabel("\u2014");
        priceLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        updatedLabel = new JLabel("");
        updatedLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
        updatedLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        agoTimer = new Timer(1000, e -> refreshAgoLabel());
        agoTimer.start();

        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));

        // Single header bar: [symbol combo]  --Trading Desk--  [price]
        mainPanel.add(createHeaderBar(), BorderLayout.NORTH);

        // Status bar at bottom
        statusPanel = new StatusPanel();
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JSeparator(), BorderLayout.NORTH);
        bottomPanel.add(statusPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Left panel: Strategies and Signals
        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.setPreferredSize(new Dimension(280, 0));

        ThinSplitPane leftSplit = new ThinSplitPane(JSplitPane.VERTICAL_SPLIT);
        leftSplit.setResizeWeight(0.3);

        strategyListPanel = new StrategyListPanel();
        leftSplit.setTopComponent(strategyListPanel);

        signalLogPanel = new SignalLogPanel();
        leftSplit.setBottomComponent(signalLogPanel);

        leftPanel.add(leftSplit, BorderLayout.CENTER);
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.EAST);

        // Center: Price chart + indicator side panel
        JPanel chartPanel = new JPanel(new BorderLayout(0, 0));

        priceChartPanel = new PriceChartPanel();
        chartPanel.add(priceChartPanel, BorderLayout.CENTER);

        indicatorSidePanel = new IndicatorSidePanel(priceChartPanel);
        chartPanel.add(indicatorSidePanel, BorderLayout.EAST);

        // Main horizontal split
        ThinSplitPane mainSplit = new ThinSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(280);
        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(chartPanel);

        mainPanel.add(mainSplit, BorderLayout.CENTER);

        setContentPane(mainPanel);

        // Listen for LAF changes to update borders
        lafChangeListener = evt -> {
            if ("lookAndFeel".equals(evt.getPropertyName()) && headerBar != null) {
                headerBar.setBackground(UIManager.getColor("Panel.background"));
                headerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));
            }
        };
        UIManager.addPropertyChangeListener(lafChangeListener);

        // Handle window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (lafChangeListener != null) {
                    UIManager.removePropertyChangeListener(lafChangeListener);
                }
                if (onClose != null) {
                    onClose.run();
                }
                dispose();
            }
        });
    }

    private JPanel createHeaderBar() {
        int barHeight = 52;

        headerBar = new JPanel(new GridBagLayout());
        headerBar.setBackground(UIManager.getColor("Panel.background"));
        headerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));
        headerBar.setPreferredSize(new Dimension(0, barHeight));
        headerBar.setMinimumSize(new Dimension(0, barHeight));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        // Left: Buttons placeholder + symbol combo
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.WEST;
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setOpaque(false);
        JPanel leftContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftContent.setOpaque(false);
        if (SystemInfo.isMacOS) {
            // Auto-sized placeholder that reserves space for traffic light buttons
            JPanel buttonsPlaceholder = new JPanel();
            buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
            buttonsPlaceholder.setOpaque(false);
            leftContent.add(buttonsPlaceholder);
        }
        leftContent.add(symbolCombo);
        SegmentedToggle chartModeToggle = new SegmentedToggle("Line", "Candles");
        chartModeToggle.setSelectedIndex(1); // Desk defaults to candles
        chartModeToggle.setOnSelectionChanged(index ->
            priceChartPanel.setCandlestickMode(index == 1));
        leftContent.add(chartModeToggle);
        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.weightx = 1.0;
        leftPanel.add(leftContent, lc);
        headerBar.add(leftPanel, gbc);

        // Center: Title
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel titleLabel = new JLabel("Trading Desk");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerBar.add(titleLabel, gbc);

        // Right: Price + updated ago + Settings
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.EAST;
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        JPanel rightContent = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightContent.setOpaque(false);
        rightContent.add(priceLabel);
        rightContent.add(updatedLabel);
        JButton dataBtn = new ToolbarButton("Data");
        dataBtn.addActionListener(e -> DeskStatusWindow.showWindow());
        rightContent.add(dataBtn);
        JButton settingsBtn = new ToolbarButton("Settings");
        settingsBtn.addActionListener(e -> new DeskSettingsDialog(this).setVisible(true));
        rightContent.add(settingsBtn);
        GridBagConstraints rc = new GridBagConstraints();
        rc.anchor = GridBagConstraints.EAST;
        rc.fill = GridBagConstraints.HORIZONTAL;
        rc.weightx = 1.0;
        rightPanel.add(rightContent, rc);
        headerBar.add(rightPanel, gbc);

        return headerBar;
    }

    private void refreshAgoLabel() {
        if (lastUpdateReceived == 0) return;
        long agoMs = System.currentTimeMillis() - lastUpdateReceived;
        String agoText;
        if (agoMs < 1000) agoText = "just now";
        else if (agoMs < 60_000) agoText = (agoMs / 1000) + "s ago";
        else if (agoMs < 3_600_000) agoText = (agoMs / 60_000) + "m ago";
        else agoText = (agoMs / 3_600_000) + "h ago";
        updatedLabel.setText(agoText);
    }

    /**
     * Set callback for window close.
     */
    public void setOnClose(Runnable callback) {
        this.onClose = callback;
    }

    /**
     * Update connection state display.
     */
    public void updateConnectionState(ConnectionState state) {
        statusPanel.updateState(state);
    }

    /**
     * Update symbol/timeframe display.
     */
    public void updateSymbol(String symbol, String timeframe) {
        statusPanel.updateSymbol(symbol, timeframe);
        SwingUtilities.invokeLater(() -> symbolCombo.setSelectedSymbol(symbol));
    }

    /**
     * Update current price display.
     */
    public void updatePrice(double price) {
        statusPanel.updatePrice(price);
    }

    /**
     * Update current price with exchange timestamp for "X ago" display.
     */
    public void updatePrice(double price, long exchangeTimestamp) {
        statusPanel.updatePrice(price);
        this.lastUpdateReceived = System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> {
            if (Double.isNaN(price)) {
                priceLabel.setText("\u2014");
                updatedLabel.setText("");
            } else {
                priceLabel.setText(String.format("%,.2f", price));
                refreshAgoLabel();
            }
        });
    }

    /**
     * Update strategy list.
     */
    public void setStrategies(List<PublishedStrategy> strategies) {
        strategyListPanel.setStrategies(strategies);
    }

    /**
     * Add a signal to the log.
     */
    public void addSignal(SignalEvent signal) {
        signalLogPanel.addSignal(signal);
    }

    /**
     * Get recent signals from the log.
     */
    public List<SignalEvent> getSignals() {
        return signalLogPanel.getSignals();
    }

    /**
     * Clear signal log.
     */
    public void clearSignals() {
        signalLogPanel.clear();
    }

    /**
     * Set historical candles for the chart.
     */
    public void setChartCandles(List<Candle> candles, String symbol, String timeframe) {
        priceChartPanel.setCandles(candles, symbol, timeframe);
    }

    /**
     * Update the current (incomplete) candle on the chart.
     */
    public void updateChartCandle(Candle candle) {
        priceChartPanel.updateCurrentCandle(candle);
    }

    /**
     * Add a completed candle to the chart.
     */
    public void addChartCandle(Candle candle) {
        priceChartPanel.addCandle(candle);
    }

    /**
     * Start polling symbol sync status from data service.
     */
    public void startSyncPolling(DataServiceClient client) {
        statusPanel.startSyncPolling(client);
    }

    /**
     * Add listener for symbol changes from the header bar.
     */
    public void addSymbolChangeListener(ActionListener listener) {
        symbolCombo.addActionListener(listener);
    }

    /**
     * Get the currently selected symbol info from the header bar.
     */
    public String getSelectedExchange() {
        return symbolCombo.getExchange();
    }

    public String getSelectedMarket() {
        return symbolCombo.getSymbolMarket();
    }

    public String getSelectedSymbol() {
        return symbolCombo.getSelectedSymbol();
    }

    /**
     * Get the price chart panel for direct access.
     */
    public PriceChartPanel getPriceChartPanel() {
        return priceChartPanel;
    }
}
