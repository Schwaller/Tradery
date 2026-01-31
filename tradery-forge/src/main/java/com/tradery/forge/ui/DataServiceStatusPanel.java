package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.TraderyApp;
import com.tradery.forge.ui.controls.StatusBadge;
import com.tradery.forge.data.page.DataPageManager;
import com.tradery.dataclient.DataServiceLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Status badge showing Data Service connection status and total data size.
 * Displayed in the lower-left of the status bar.
 */
public class DataServiceStatusPanel extends JPanel {

    private final StatusBadge statusLabel;
    private final Timer refreshTimer;

    public DataServiceStatusPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);

        statusLabel = new StatusBadge("Data Service");
        statusLabel.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        add(statusLabel);

        statusLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                DownloadDashboardWindow.showWindow();
            }
        });

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

        if (connected) {
            String sizeText = formatTotalDataSize();
            statusLabel.setText("Data Service: " + sizeText);
            statusLabel.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
            statusLabel.setToolTipText(
                "<html><b>Data Service</b><br>" +
                "Status: Connected<br>" +
                "Total data: " + sizeText + "</html>");
        } else {
            statusLabel.setText("Data Service: Offline");
            statusLabel.setStatusColor(StatusBadge.BG_ERROR, StatusBadge.FG_ERROR);
            statusLabel.setToolTipText(
                "<html><b>Data Service</b><br>" +
                "Status: Disconnected<br><br>" +
                "<i>The app will try to reconnect automatically</i></html>");
        }
    }

    private String formatTotalDataSize() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null) return "--";

        long total = 0;
        total += safeEstimate(ctx.getCandlePageManager());
        total += safeEstimate(ctx.getFundingPageManager());
        total += safeEstimate(ctx.getOIPageManager());
        total += safeEstimate(ctx.getAggTradesPageManager());
        total += safeEstimate(ctx.getPremiumPageManager());
        if (ctx.getIndicatorPageManager() != null) {
            total += ctx.getIndicatorPageManager().estimateMemoryBytes();
        }

        if (total < 1024) return total + " B";
        if (total < 1024 * 1024) return (total / 1024) + " KB";
        if (total < 1024L * 1024 * 1024) return String.format("%.1f MB", total / (1024.0 * 1024));
        return String.format("%.2f GB", total / (1024.0 * 1024 * 1024));
    }

    private long safeEstimate(DataPageManager<?> mgr) {
        return mgr != null ? mgr.estimateMemoryBytes() : 0;
    }

    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }
}
