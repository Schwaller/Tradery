package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.tradery.ui.controls.StatusBadge;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Comprehensive network status dialog showing all sharing infrastructure
 * systems, their state, and connectivity details.
 */
public class NetworkStatusDialog extends JDialog {

    private final SharingService sharingService;
    private javax.swing.Timer refreshTimer;

    // Identity
    private JLabel emailValue;
    private StatusBadge deviceBadge;

    // Peer Server
    private StatusBadge serverBadge;
    private JLabel serverPortValue;

    // Port Mapping
    private StatusBadge natBadge;
    private JLabel publicIpValue;

    // LAN Discovery
    private StatusBadge lanBadge;
    private JLabel lanPeersValue;

    // Rendezvous
    private StatusBadge rendezvousBadge;

    // Connections
    private StatusBadge connectionsBadge;
    private JLabel peersValue;
    private JLabel devicesValue;

    public NetworkStatusDialog(Window owner, SharingService sharingService) {
        super(owner, "Network Status", ModalityType.MODELESS);
        this.sharingService = sharingService;

        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        setSize(420, 520);
        setLocationRelativeTo(owner);
        setResizable(false);

        initUI();
        refresh();

        refreshTimer = new javax.swing.Timer(3000, e -> refresh());
        refreshTimer.setRepeats(true);
        refreshTimer.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                refreshTimer.stop();
            }
        });
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 52));
        header.setMinimumSize(new Dimension(0, 52));
        JPanel headerInner = new JPanel(new BorderLayout());
        JPanel placeholder = new JPanel();
        placeholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
        placeholder.setOpaque(false);
        headerInner.add(placeholder, BorderLayout.WEST);
        JLabel title = new JLabel("Network Status", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerInner.add(title, BorderLayout.CENTER);
        header.add(headerInner, BorderLayout.CENTER);
        header.add(new JSeparator(), BorderLayout.SOUTH);
        main.add(header, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(0, 0, 0, 0));

        content.add(createIdentitySection());
        content.add(new JSeparator());
        content.add(createServerSection());
        content.add(new JSeparator());
        content.add(createPortMappingSection());
        content.add(new JSeparator());
        content.add(createLanSection());
        content.add(new JSeparator());
        content.add(createRendezvousSection());
        content.add(new JSeparator());
        content.add(createConnectionsSection());
        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        main.add(scroll, BorderLayout.CENTER);

        // Button bar
        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.add(new JSeparator(), BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBorder(new EmptyBorder(10, 16, 10, 16));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttons.add(closeBtn);
        buttonBar.add(buttons, BorderLayout.CENTER);
        main.add(buttonBar, BorderLayout.SOUTH);

        setContentPane(main);
    }

    // ==================== SECTIONS ====================

    private JPanel createIdentitySection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(new EmptyBorder(10, 20, 10, 20));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 8);

        // Section header
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        section.add(sectionLabel("Identity"), gbc);
        gbc.gridwidth = 1;

        // Email
        gbc.gridy = 1; gbc.gridx = 0;
        section.add(keyLabel("Email"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        emailValue = valueLabel("");
        section.add(emailValue, gbc);
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        // Device
        gbc.gridy = 2; gbc.gridx = 0;
        section.add(keyLabel("Device"), gbc);
        gbc.gridx = 1;
        deviceBadge = new StatusBadge("Checking...");
        section.add(deviceBadge, gbc);

        return section;
    }

    private JPanel createServerSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(new EmptyBorder(10, 20, 10, 20));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 8);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        section.add(sectionLabel("Peer Server"), gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 1; gbc.gridx = 0;
        section.add(keyLabel("Status"), gbc);
        gbc.gridx = 1;
        serverBadge = new StatusBadge("Checking...");
        section.add(serverBadge, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        section.add(keyLabel("Port"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        serverPortValue = valueLabel("");
        section.add(serverPortValue, gbc);
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        return section;
    }

    private JPanel createPortMappingSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(new EmptyBorder(10, 20, 10, 20));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 8);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        section.add(sectionLabel("Port Mapping"), gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 1; gbc.gridx = 0;
        section.add(keyLabel("Method"), gbc);
        gbc.gridx = 1;
        natBadge = new StatusBadge("Checking...");
        section.add(natBadge, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        section.add(keyLabel("Public IP"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        publicIpValue = valueLabel("");
        section.add(publicIpValue, gbc);
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        // Hint
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel hint = new JLabel("Enables direct connections from outside your network");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 10));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        section.add(hint, gbc);

        return section;
    }

    private JPanel createLanSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(new EmptyBorder(10, 20, 10, 20));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 8);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        section.add(sectionLabel("LAN Discovery"), gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 1; gbc.gridx = 0;
        section.add(keyLabel("Status"), gbc);
        gbc.gridx = 1;
        lanBadge = new StatusBadge("Checking...");
        section.add(lanBadge, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        section.add(keyLabel("Visible Peers"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        lanPeersValue = valueLabel("");
        section.add(lanPeersValue, gbc);
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        // Hint
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel hint = new JLabel("Discovers peers on your local network via multicast");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 10));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        section.add(hint, gbc);

        return section;
    }

    private JPanel createRendezvousSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(new EmptyBorder(10, 20, 10, 20));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 8);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        section.add(sectionLabel("Rendezvous Server"), gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 1; gbc.gridx = 0;
        section.add(keyLabel("Status"), gbc);
        gbc.gridx = 1;
        rendezvousBadge = new StatusBadge("Checking...");
        section.add(rendezvousBadge, gbc);

        // Hint
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel hint = new JLabel("Coordinates peer discovery across the internet");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 10));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        section.add(hint, gbc);

        return section;
    }

    private JPanel createConnectionsSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(new EmptyBorder(10, 20, 10, 20));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 8);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        section.add(sectionLabel("Connections"), gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 1; gbc.gridx = 0;
        section.add(keyLabel("Status"), gbc);
        gbc.gridx = 1;
        connectionsBadge = new StatusBadge("Checking...");
        section.add(connectionsBadge, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        section.add(keyLabel("Peers"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        peersValue = valueLabel("");
        section.add(peersValue, gbc);
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        gbc.gridy = 3; gbc.gridx = 0;
        section.add(keyLabel("Devices"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        devicesValue = valueLabel("");
        section.add(devicesValue, gbc);

        return section;
    }

    // ==================== REFRESH ====================

    private void refresh() {
        SharingService.NetworkStatus ns = sharingService.getNetworkStatus();
        if (ns == null) return;

        // Identity
        if (ns.email() != null && !ns.email().isBlank()) {
            emailValue.setText(ns.email());
        } else {
            emailValue.setText("Not signed in");
        }
        if (ns.deviceEnrolled()) {
            deviceBadge.setText("Enrolled");
            deviceBadge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
        } else {
            deviceBadge.setText("Not enrolled");
            deviceBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        }

        // Server
        if (ns.serverPort() > 0) {
            serverBadge.setText("Running");
            serverBadge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
            serverPortValue.setText(String.valueOf(ns.serverPort()));
        } else {
            serverBadge.setText("Not started");
            serverBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
            serverPortValue.setText("\u2014");
        }

        // Port mapping
        if (ns.portMapping() != null) {
            natBadge.setText(ns.portMapping());
            natBadge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
        } else if (ns.publicIp() != null) {
            natBadge.setText("STUN only");
            natBadge.setStatusColor(StatusBadge.BG_WARNING, StatusBadge.FG_WARNING);
        } else {
            natBadge.setText("None");
            natBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        }
        publicIpValue.setText(ns.publicIp() != null ? ns.publicIp() : "\u2014");

        // LAN
        if (ns.lanActive()) {
            lanBadge.setText("Active");
            lanBadge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
            lanPeersValue.setText(String.valueOf(ns.lanPeerCount()));
        } else {
            lanBadge.setText("Inactive");
            lanBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
            lanPeersValue.setText("\u2014");
        }

        // Rendezvous
        if (ns.rendezvousAvailable()) {
            rendezvousBadge.setText("Available");
            rendezvousBadge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
        } else {
            rendezvousBadge.setText("Unavailable");
            rendezvousBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        }

        // Connections
        int totalPeers = ns.connectedPeers();
        int totalDevices = ns.connectedDevices();
        if (totalPeers > 0 || totalDevices > 0) {
            connectionsBadge.setText("Connected");
            connectionsBadge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
        } else {
            connectionsBadge.setText("No connections");
            connectionsBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        }
        peersValue.setText(String.valueOf(totalPeers));
        devicesValue.setText(String.valueOf(totalDevices));
    }

    // ==================== HELPERS ====================

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() + 1f));
        return label;
    }

    private static JLabel keyLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        return label;
    }

    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return label;
    }
}
