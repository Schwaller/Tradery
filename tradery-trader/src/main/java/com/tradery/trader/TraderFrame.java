package com.tradery.trader;

import com.formdev.flatlaf.FlatClientProperties;
import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.model.TradingConfig;
import com.tradery.execution.ExecutionEngine;
import com.tradery.execution.ExecutionState;
import com.tradery.trader.ui.TradingPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class TraderFrame extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(TraderFrame.class);

    private ExecutionEngine engine;
    private TradingPanel tradingPanel;

    // Header components
    private final JLabel statusLabel = new JLabel("Disconnected");
    private final JButton connectButton = new JButton("Connect");

    public TraderFrame() {
        setTitle("Tradery Trader");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // macOS integrated title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        buildUI();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());

        // Header bar (52px)
        JPanel header = buildHeader();
        root.add(header, BorderLayout.NORTH);

        // Placeholder until connected
        JPanel placeholder = new JPanel(new GridBagLayout());
        JLabel msg = new JLabel("Click Connect to start trading");
        msg.setForeground(UIManager.getColor("Label.disabledForeground"));
        placeholder.add(msg);
        root.add(placeholder, BorderLayout.CENTER);

        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new GridBagLayout());
        header.setPreferredSize(new Dimension(0, 52));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 8, 0, 8);

        // Traffic light placeholder (macOS)
        gbc.gridx = 0;
        gbc.weightx = 0;
        JPanel placeholder = new JPanel();
        placeholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
        header.add(placeholder, gbc);

        // Title
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel title = new JLabel("Trader");
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        header.add(title, gbc);

        // Status
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        header.add(statusLabel, gbc);

        // Connect button
        gbc.gridx = 3;
        connectButton.addActionListener(e -> toggleConnection());
        header.add(connectButton, gbc);

        // Bottom separator
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(header, BorderLayout.CENTER);
        wrapper.add(new JSeparator(), BorderLayout.SOUTH);
        return wrapper;
    }

    private void toggleConnection() {
        if (engine != null && engine.getState() == ExecutionState.RUNNING) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        connectButton.setEnabled(false);
        statusLabel.setText("Connecting...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                TradingConfig config = TradingConfig.load(TradingConfig.defaultPath());
                engine = new ExecutionEngine(config);
                engine.start();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    onConnected();
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    statusLabel.setText("Failed");
                    connectButton.setEnabled(true);
                    JOptionPane.showMessageDialog(TraderFrame.this,
                            "Connection failed: " + msg, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void onConnected() {
        statusLabel.setText(engine.getClient().getVenueName());
        statusLabel.setForeground(new Color(0, 180, 80));
        connectButton.setText("Disconnect");
        connectButton.setEnabled(true);

        // Build trading panel
        tradingPanel = new TradingPanel(engine);

        // Replace placeholder with trading panel
        Container root = getContentPane();
        if (root.getComponentCount() > 1) {
            root.remove(1);
        }
        root.add(tradingPanel, BorderLayout.CENTER);
        root.revalidate();
        root.repaint();

        tradingPanel.startPolling();
        log.info("Connected to {}", engine.getClient().getVenueName());
    }

    private void disconnect() {
        if (tradingPanel != null) {
            tradingPanel.stopPolling();
        }
        if (engine != null) {
            engine.stop();
        }

        statusLabel.setText("Disconnected");
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        connectButton.setText("Connect");
    }

    private void shutdown() {
        if (tradingPanel != null) {
            tradingPanel.stopPolling();
        }
        if (engine != null) {
            engine.stop();
        }
    }
}
