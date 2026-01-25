package com.tradery.ui;

import com.tradery.TraderyApp;
import com.tradery.dataclient.DataServiceLauncher;

import javax.swing.*;
import java.awt.*;

/**
 * Status badge showing Data Service connection status.
 * Displayed in the lower-left of the status bar.
 */
public class DataServiceStatusPanel extends JPanel {

    // Colors
    private static final Color BG_CONNECTED = new Color(50, 120, 80);
    private static final Color BG_DISCONNECTED = new Color(120, 60, 60);
    private static final Color BG_UNAVAILABLE = new Color(80, 80, 85);
    private static final Color FG_COLOR = new Color(220, 220, 220);

    private final JLabel statusLabel;
    private final Timer refreshTimer;

    private boolean lastConnected = false;
    private int lastPort = 0;

    public DataServiceStatusPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);

        statusLabel = createBadge();
        add(statusLabel);

        // Refresh every 5 seconds
        refreshTimer = new Timer(5000, e -> refresh());
        refreshTimer.start();

        // Initial refresh
        refresh();
    }

    private JLabel createBadge() {
        JLabel label = new JLabel("Data Service") {
            private Color bgColor = BG_UNAVAILABLE;

            @Override
            public void setBackground(Color bg) {
                this.bgColor = bg;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 10f));
        label.setForeground(FG_COLOR);
        label.setOpaque(false);
        label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return label;
    }

    public void refresh() {
        DataServiceLauncher launcher = TraderyApp.getDataServiceLauncher();

        if (launcher == null) {
            statusLabel.setText("Data Service: N/A");
            statusLabel.setBackground(BG_UNAVAILABLE);
            statusLabel.setToolTipText("Data Service not initialized");
            return;
        }

        boolean connected = launcher.isRegistered();
        int port = launcher.getPort();

        if (connected) {
            statusLabel.setText("Data Service: :" + port);
            statusLabel.setBackground(BG_CONNECTED);
            statusLabel.setToolTipText(String.format(
                "<html><b>Data Service</b><br>" +
                "Status: Connected<br>" +
                "Port: %d<br><br>" +
                "<i>Click to open dashboard</i></html>",
                port));
        } else {
            statusLabel.setText("Data Service: Disconnected");
            statusLabel.setBackground(BG_DISCONNECTED);
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
