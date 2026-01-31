package com.tradery.forge.ui;

import com.tradery.forge.TraderyApp;
import com.tradery.forge.ui.controls.StatusBadge;
import com.tradery.dataclient.DataServiceLauncher;

import javax.swing.*;
import java.awt.*;

/**
 * Status badge showing Data Service connection status.
 * Displayed in the lower-left of the status bar.
 */
public class DataServiceStatusPanel extends JPanel {


    private final StatusBadge statusLabel;
    private final Timer refreshTimer;

    private boolean lastConnected = false;
    private int lastPort = 0;

    public DataServiceStatusPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);

        statusLabel = new StatusBadge("Data Service");
        statusLabel.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        add(statusLabel);

        // Refresh every 5 seconds
        refreshTimer = new Timer(5000, e -> refresh());
        refreshTimer.start();

        // Initial refresh
        refresh();
    }

    public void refresh() {
        DataServiceLauncher launcher = TraderyApp.getDataServiceLauncher();

        if (launcher == null) {
            statusLabel.setText("Data Service: N/A");
            statusLabel.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
            statusLabel.setToolTipText("Data Service not initialized");
            return;
        }

        boolean connected = launcher.isRegistered();
        int port = launcher.getPort();

        if (connected) {
            statusLabel.setText("Data Service: :" + port);
            statusLabel.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
            statusLabel.setToolTipText(String.format(
                "<html><b>Data Service</b><br>" +
                "Status: Connected<br>" +
                "Port: %d<br><br>" +
                "<i>Click to open dashboard</i></html>",
                port));
        } else {
            statusLabel.setText("Data Service: Disconnected");
            statusLabel.setStatusColor(StatusBadge.BG_ERROR, StatusBadge.FG_ERROR);
            statusLabel.setToolTipText(
                "<html><b>Data Service</b><br>" +
                "Status: Disconnected<br><br>" +
                "<i>The app will try to reconnect automatically</i></html>");
        }

        lastConnected = connected;
        lastPort = port;
    }

    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }
}
