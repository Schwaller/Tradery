package com.tradery.desk.ui;

import com.tradery.dataclient.DataServiceClient;
import com.tradery.desk.feed.BinanceWebSocketClient.ConnectionState;
import com.tradery.ui.controls.StatusBadge;
import com.tradery.ui.status.MemoryStatusPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Status bar showing WebSocket connection state, symbol/timeframe, memory, and current price.
 */
public class StatusPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(StatusPanel.class);

    private final StatusBadge connectionBadge;
    private final StatusBadge symbolBadge;
    private final StatusBadge syncBadge;
    private final MemoryStatusPanel memoryStatusPanel;
    private final JLabel priceLabel;
    private final Timer syncPollTimer;

    public StatusPanel() {
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // Left side: connection + symbol + memory badges
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);

        connectionBadge = new StatusBadge("Disconnected");
        connectionBadge.setStatusColor(StatusBadge.BG_ERROR, StatusBadge.FG_ERROR);
        leftPanel.add(connectionBadge);

        symbolBadge = new StatusBadge("");
        symbolBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        symbolBadge.setVisible(false);
        leftPanel.add(symbolBadge);

        syncBadge = new StatusBadge("Pairs: —");
        syncBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        syncBadge.setToolTipText("Symbol database sync status");
        leftPanel.add(syncBadge);

        memoryStatusPanel = new MemoryStatusPanel();
        leftPanel.add(memoryStatusPanel);

        add(leftPanel, BorderLayout.WEST);

        // Right side: current price
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.setOpaque(false);

        priceLabel = new JLabel("");
        priceLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        rightPanel.add(priceLabel);

        add(rightPanel, BorderLayout.EAST);

        updateState(ConnectionState.DISCONNECTED);

        // Click any badge to open the status window
        MouseAdapter statusWindowOpener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                try {
                    DeskStatusWindow.showWindow();
                } catch (Exception ex) {
                    log.error("Failed to open status window", ex);
                    ex.printStackTrace();
                }
            }
        };
        connectionBadge.addMouseListener(statusWindowOpener);
        syncBadge.addMouseListener(statusWindowOpener);
        memoryStatusPanel.addMouseListener(statusWindowOpener);

        // Poll sync status every 30s
        syncPollTimer = new Timer(30_000, e -> {});
        syncPollTimer.setRepeats(true);
    }

    /**
     * Update connection state display.
     */
    public void updateState(ConnectionState state) {
        SwingUtilities.invokeLater(() -> {
            switch (state) {
                case CONNECTED -> {
                    connectionBadge.setText("Connected");
                    connectionBadge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
                    connectionBadge.setToolTipText("Connected");
                }
                case CONNECTING, RECONNECTING -> {
                    connectionBadge.setText(state == ConnectionState.CONNECTING ? "Connecting" : "Reconnecting");
                    connectionBadge.setStatusColor(StatusBadge.BG_WARNING, StatusBadge.FG_WARNING);
                    connectionBadge.setToolTipText(state == ConnectionState.CONNECTING
                        ? "Connecting..." : "Reconnecting...");
                }
                case DISCONNECTED -> {
                    connectionBadge.setText("Disconnected");
                    connectionBadge.setStatusColor(StatusBadge.BG_ERROR, StatusBadge.FG_ERROR);
                    connectionBadge.setToolTipText("Disconnected");
                }
            }
        });
    }

    /**
     * Update the symbol display.
     */
    public void updateSymbol(String symbol, String timeframe) {
        SwingUtilities.invokeLater(() -> {
            symbolBadge.setText(symbol.toUpperCase() + " " + timeframe);
            symbolBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
            symbolBadge.setVisible(true);
        });
    }

    /**
     * Update the current price display.
     */
    public void updatePrice(double price) {
        SwingUtilities.invokeLater(() -> {
            if (Double.isNaN(price)) {
                priceLabel.setText("");
            } else {
                priceLabel.setText(String.format("%,.2f", price));
            }
        });
    }

    /**
     * Update symbol sync status display.
     */
    public void updateSyncStatus(DataServiceClient.SymbolStats stats) {
        SwingUtilities.invokeLater(() -> {
            if (stats == null) {
                syncBadge.setText("Pairs: —");
                syncBadge.setStatusColor(StatusBadge.BG_ERROR, StatusBadge.FG_ERROR);
                syncBadge.setToolTipText("Could not fetch symbol stats");
                return;
            }
            if (stats.syncInProgress()) {
                syncBadge.setText("Syncing...");
                syncBadge.setStatusColor(StatusBadge.BG_WARNING, StatusBadge.FG_WARNING);
                syncBadge.setToolTipText(String.format("Syncing — %,d pairs, %,d assets, %,d coins",
                    stats.totalPairs(), stats.totalAssets(), stats.totalCoins()));
            } else if (stats.totalPairs() > 0) {
                syncBadge.setText(String.format("%,d pairs", stats.totalPairs()));
                syncBadge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
                syncBadge.setToolTipText(String.format("%,d pairs | %,d assets | %,d coins",
                    stats.totalPairs(), stats.totalAssets(), stats.totalCoins()));
            } else {
                syncBadge.setText("No pairs");
                syncBadge.setStatusColor(StatusBadge.BG_ERROR, StatusBadge.FG_ERROR);
                syncBadge.setToolTipText("Symbol database is empty — sync may not have run yet");
            }
        });
    }

    /**
     * Start periodic sync status polling.
     */
    public void startSyncPolling(DataServiceClient client) {
        if (client == null) return;
        // Fetch immediately
        fetchAndUpdateSync(client);
        // Then poll every 30s
        syncPollTimer.addActionListener(e -> fetchAndUpdateSync(client));
        syncPollTimer.start();
    }

    private void fetchAndUpdateSync(DataServiceClient client) {
        // Run off EDT
        new Thread(() -> {
            try {
                var stats = client.getSymbolStats();
                log.info("Symbol DB: {} pairs, {} assets, {} coins, syncing={}",
                    stats.totalPairs(), stats.totalAssets(), stats.totalCoins(), stats.syncInProgress());
                updateSyncStatus(stats);
            } catch (Exception ex) {
                log.warn("Failed to fetch symbol stats: {}", ex.getMessage());
                updateSyncStatus(null);
            }
        }, "sync-status-poll").start();
    }

    public void dispose() {
        syncPollTimer.stop();
        memoryStatusPanel.dispose();
    }
}
