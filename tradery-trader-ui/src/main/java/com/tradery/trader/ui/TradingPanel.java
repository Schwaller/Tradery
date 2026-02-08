package com.tradery.trader.ui;

import com.tradery.exchange.TradingClient;
import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.model.*;
import com.tradery.execution.ExecutionEngine;
import com.tradery.execution.ExecutionState;
import com.tradery.execution.order.LiveOrder;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main container panel composing all trading sub-panels.
 */
public class TradingPanel extends JPanel {

    private final ExecutionEngine engine;

    private final AccountPanel accountPanel;
    private final OrderEntryPanel orderEntryPanel;
    private final PositionsPanel positionsPanel;
    private final OrdersPanel ordersPanel;
    private final FillsPanel fillsPanel;
    private final AssetBrowserPanel assetBrowserPanel;
    private final KillSwitchButton killSwitchButton;
    private final ExecutionLogPanel logPanel;

    private ScheduledExecutorService poller;

    public TradingPanel(ExecutionEngine engine) {
        this.engine = engine;

        // Create sub-panels
        accountPanel = new AccountPanel();
        orderEntryPanel = new OrderEntryPanel();
        positionsPanel = new PositionsPanel();
        ordersPanel = new OrdersPanel();
        fillsPanel = new FillsPanel();
        assetBrowserPanel = new AssetBrowserPanel();
        killSwitchButton = new KillSwitchButton(engine.getKillSwitch());
        logPanel = new ExecutionLogPanel();

        // Wire up events
        orderEntryPanel.setOnSubmit(this::onOrderSubmit);
        ordersPanel.setOnCancelOrder(this::onCancelOrder);
        assetBrowserPanel.setOnAssetSelected(orderEntryPanel::setSymbol);

        // Subscribe to real-time fills
        TradingClient client = engine.getClient();
        client.subscribeFills(fill -> SwingUtilities.invokeLater(() -> fillsPanel.addFill(fill)));

        // Subscribe to journal
        logPanel.subscribe(engine.getJournal());

        // Build layout
        buildLayout();
    }

    /**
     * Start polling for account state and position updates.
     */
    public void startPolling() {
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trading-panel-poll");
            t.setDaemon(true);
            return t;
        });

        poller.scheduleAtFixedRate(this::poll, 0, 3, TimeUnit.SECONDS);

        // Initial data load
        loadAssets();
    }

    /**
     * Stop polling.
     */
    public void stopPolling() {
        if (poller != null) {
            poller.shutdownNow();
        }
    }

    private void buildLayout() {
        setLayout(new BorderLayout());

        // Left panel: Account + Asset Browser + Kill Switch
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(220, 0));

        leftPanel.add(accountPanel, BorderLayout.NORTH);

        JPanel assetWrapper = new JPanel(new BorderLayout());
        JLabel assetTitle = new JLabel("Assets");
        assetTitle.setFont(assetTitle.getFont().deriveFont(Font.BOLD, assetTitle.getFont().getSize() + 1f));
        assetTitle.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 0));
        assetWrapper.add(assetTitle, BorderLayout.NORTH);
        assetWrapper.add(assetBrowserPanel, BorderLayout.CENTER);
        leftPanel.add(assetWrapper, BorderLayout.CENTER);

        leftPanel.add(killSwitchButton, BorderLayout.SOUTH);

        // Right panel: Order Entry + Positions + Orders + Fills/Log
        JPanel rightPanel = new JPanel(new BorderLayout());

        // Top: Order entry
        orderEntryPanel.setPreferredSize(new Dimension(0, 280));
        rightPanel.add(orderEntryPanel, BorderLayout.NORTH);

        // Center: Tabbed pane for positions, orders, fills, log
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM);
        tabs.addTab("Positions", positionsPanel);
        tabs.addTab("Orders", ordersPanel);
        tabs.addTab("Fills", fillsPanel);
        tabs.addTab("Log", logPanel);
        rightPanel.add(tabs, BorderLayout.CENTER);

        // Main split
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setDividerLocation(220);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);
    }

    private void poll() {
        if (engine.getState() != ExecutionState.RUNNING) return;

        try {
            TradingClient client = engine.getClient();

            // Account state
            AccountState account = client.getAccountState();
            accountPanel.update(account);

            // Positions
            List<ExchangePosition> positions = client.getPositions();
            positionsPanel.update(positions);

            // Open orders
            List<OrderResponse> openOrders = client.getOpenOrders(null);
            ordersPanel.update(openOrders);

        } catch (ExchangeException e) {
            // Silently handle polling errors — connection might be temporarily lost
        }
    }

    private void loadAssets() {
        try {
            List<AssetInfo> assets = engine.getClient().getAssets();
            assetBrowserPanel.update(assets);
        } catch (ExchangeException e) {
            // Will retry on next poll
        }
    }

    private void onOrderSubmit(OrderRequest request) {
        try {
            LiveOrder order = engine.placeOrder(request, "manual");
            if (order.getStatus() == OrderStatus.FILLED) {
                JOptionPane.showMessageDialog(this,
                        String.format("Order filled: %s %s %s @ %.2f",
                                request.side(), request.quantity(), request.symbol(),
                                order.getAvgFillPrice()),
                        "Order Filled", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (ExchangeException e) {
            JOptionPane.showMessageDialog(this,
                    "Order failed: " + e.getMessage(),
                    "Order Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onCancelOrder(String symbol, String orderId) {
        try {
            engine.cancelOrder(symbol, orderId);
        } catch (ExchangeException e) {
            JOptionPane.showMessageDialog(this,
                    "Cancel failed: " + e.getMessage(),
                    "Cancel Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
