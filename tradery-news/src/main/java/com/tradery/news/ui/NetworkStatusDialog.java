package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.tradery.ui.controls.StatusBadge;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

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
    private JLabel publicIpv6Value;

    // LAN Discovery
    private StatusBadge lanBadge;
    private JLabel lanPeersValue;

    // Rendezvous
    private StatusBadge rendezvousBadge;

    // My Devices
    private JPanel myDevicesListPanel;

    // Friends
    private JPanel friendsListPanel;

    // Connections
    private JPanel connectionsListPanel;

    /** Listener for incoming perf test requests. */
    private final Consumer<SharingService.PerfTestRequest> perfRequestListener;

    public NetworkStatusDialog(Window owner, SharingService sharingService) {
        super(owner, "Network Status", ModalityType.MODELESS);
        this.sharingService = sharingService;

        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        setSize(420, 720);
        setLocationRelativeTo(owner);
        setResizable(false);

        // Register perf request listener
        perfRequestListener = this::handlePerfRequest;
        sharingService.addPerfRequestListener(perfRequestListener);

        initUI();
        refresh();

        refreshTimer = new javax.swing.Timer(3000, e -> refresh());
        refreshTimer.setRepeats(true);
        refreshTimer.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                refreshTimer.stop();
                sharingService.removePerfRequestListener(perfRequestListener);
            }
        });
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 52));
        header.setMinimumSize(new Dimension(0, 52));
        JPanel headerInner = new JPanel(null); // null layout for true centering
        headerInner.setPreferredSize(new Dimension(0, 52));
        JPanel placeholder = new JPanel();
        placeholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
        placeholder.setOpaque(false);
        headerInner.add(placeholder);
        JLabel title = new JLabel("Network Status", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerInner.add(title);
        // Position placeholder at left, title fills full width (centered ignoring traffic lights)
        headerInner.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                Dimension phPref = placeholder.getPreferredSize();
                placeholder.setBounds(0, 0, phPref.width, headerInner.getHeight());
                title.setBounds(0, 0, headerInner.getWidth(), headerInner.getHeight());
            }
        });
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
        content.add(createMyDevicesSection());
        content.add(new JSeparator());
        content.add(createFriendsSection());
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

        gbc.gridy = 3; gbc.gridx = 0;
        section.add(keyLabel("Public IPv6"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        publicIpv6Value = valueLabel("");
        section.add(publicIpv6Value, gbc);
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        // Hint
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2;
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

    private JPanel createMyDevicesSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(new EmptyBorder(10, 20, 10, 20));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 8);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        section.add(sectionLabel("My Devices"), gbc);

        gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        myDevicesListPanel = new JPanel();
        myDevicesListPanel.setLayout(new BoxLayout(myDevicesListPanel, BoxLayout.Y_AXIS));
        JLabel placeholder = valueLabel("Discovering...");
        placeholder.setForeground(UIManager.getColor("Label.disabledForeground"));
        myDevicesListPanel.add(placeholder);
        section.add(myDevicesListPanel, gbc);

        return section;
    }

    private JPanel createFriendsSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(new EmptyBorder(10, 20, 10, 20));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 8);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        section.add(sectionLabel("Friends"), gbc);

        gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        friendsListPanel = new JPanel();
        friendsListPanel.setLayout(new BoxLayout(friendsListPanel, BoxLayout.Y_AXIS));
        JLabel placeholder = valueLabel("Loading...");
        placeholder.setForeground(UIManager.getColor("Label.disabledForeground"));
        friendsListPanel.add(placeholder);
        section.add(friendsListPanel, gbc);

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

        gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        connectionsListPanel = new JPanel();
        connectionsListPanel.setLayout(new BoxLayout(connectionsListPanel, BoxLayout.Y_AXIS));
        JLabel placeholder = valueLabel("None");
        placeholder.setForeground(UIManager.getColor("Label.disabledForeground"));
        connectionsListPanel.add(placeholder);
        section.add(connectionsListPanel, gbc);

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
        publicIpv6Value.setText(ns.publicIpv6() != null ? ns.publicIpv6() : "\u2014");

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

        // My Devices
        var devices = sharingService.getMyDevices();
        myDevicesListPanel.removeAll();

        // This device
        String localAddr = formatDeviceAddress(ns.publicIp(), ns.publicIpv6(), ns.serverPort());
        JLabel thisDevice = valueLabel(localAddr + "  (this device)");
        thisDevice.setForeground(UIManager.getColor("Label.disabledForeground"));
        myDevicesListPanel.add(thisDevice);

        // Other devices
        for (var dev : devices) {
            String addr = formatDeviceAddress(dev.host(), dev.ipv6Host(), dev.port());
            myDevicesListPanel.add(valueLabel(addr));
        }

        myDevicesListPanel.revalidate();
        myDevicesListPanel.repaint();

        // Friends
        var friends = sharingService.getFriendNetworkStatuses();
        friendsListPanel.removeAll();
        if (friends.isEmpty()) {
            JLabel none = valueLabel("No friends added");
            none.setForeground(UIManager.getColor("Label.disabledForeground"));
            friendsListPanel.add(none);
        } else {
            for (var f : friends) {
                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(false);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

                String label = f.displayName() != null && !f.displayName().isBlank()
                        ? f.displayName() : f.email();
                row.add(valueLabel(label), BorderLayout.CENTER);

                StatusBadge badge = new StatusBadge(formatFriendBadge(f));
                switch (f.connectionState()) {
                    case "connected" -> badge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
                    case "discovered" -> badge.setStatusColor(StatusBadge.BG_WARNING, StatusBadge.FG_WARNING);
                    default -> badge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
                }
                row.add(badge, BorderLayout.EAST);
                friendsListPanel.add(row);
            }
        }
        friendsListPanel.revalidate();
        friendsListPanel.repaint();

        // Connections — show individual connected devices
        var connectedDevices = sharingService.getConnectedDevices();
        connectionsListPanel.removeAll();
        if (connectedDevices.isEmpty()) {
            JLabel none = valueLabel("No active connections");
            none.setForeground(UIManager.getColor("Label.disabledForeground"));
            connectionsListPanel.add(none);
        } else {
            String ourEmail = ns.email();
            for (var dev : connectedDevices) {
                connectionsListPanel.add(createConnectionRow(dev, ourEmail));
            }
        }
        connectionsListPanel.revalidate();
        connectionsListPanel.repaint();
    }

    private JPanel createConnectionRow(SharingService.ConnectedDevice dev, String ourEmail) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.setBorder(new EmptyBorder(2, 0, 2, 0));

        // Left: name + address
        boolean isSelf = ourEmail != null && ourEmail.equals(dev.email());
        String label = dev.displayName() != null && !dev.displayName().isBlank()
                ? dev.displayName() : dev.email();
        if (isSelf) label += "  (self)";
        JLabel nameLabel = valueLabel(label);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(nameLabel);

        // Connection type badge
        StatusBadge typeBadge = new StatusBadge(dev.connectionType());
        typeBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        left.add(typeBadge);

        // Mutual badge
        if (dev.mutualFriend()) {
            StatusBadge mutualBadge = new StatusBadge("Mutual");
            mutualBadge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
            left.add(mutualBadge);
        }

        row.add(left, BorderLayout.CENTER);

        // Right: Test button (only for mutual friends, not self)
        if (dev.mutualFriend() && !isSelf) {
            JButton testBtn = new JButton("Test");
            testBtn.setFont(testBtn.getFont().deriveFont(Font.PLAIN, 10f));
            testBtn.putClientProperty("JButton.buttonType", "roundRect");
            testBtn.addActionListener(e -> startPerfTest(dev, testBtn));
            row.add(testBtn, BorderLayout.EAST);
        }

        // Address as tooltip
        row.setToolTipText(dev.address());

        return row;
    }

    private void startPerfTest(SharingService.ConnectedDevice dev, JButton testBtn) {
        testBtn.setText("Requesting...");
        testBtn.setEnabled(false);
        sharingService.startPerfTest(dev.email(), result -> {
            // Called on EDT
            if (result == null) {
                testBtn.setText("Declined");
                javax.swing.Timer reEnableTimer = new javax.swing.Timer(3000, ev -> {
                    testBtn.setText("Test");
                    testBtn.setEnabled(true);
                });
                reEnableTimer.setRepeats(false);
                reEnableTimer.start();
            } else {
                testBtn.setText("Test");
                testBtn.setEnabled(true);
                showPerfResult(dev.displayName() != null ? dev.displayName() : dev.email(), result);
            }
        });
    }

    private void showPerfResult(String peerName, SharingService.PerfTestResult r) {
        String message = String.format(
                "Latency     avg %.0f ms  (min %.0f ms / max %.0f ms)\n" +
                "Throughput  %.0f KB/s\n" +
                "Packet Loss %.0f%%  (%d/%d pings)",
                r.avgLatencyMs(), r.minLatencyMs(), r.maxLatencyMs(),
                r.throughputKBps(),
                r.packetLossPercent(), r.pingsReceived(), r.pingsSent());
        JOptionPane.showMessageDialog(this, message,
                "Performance Test \u2014 " + peerName, JOptionPane.INFORMATION_MESSAGE);
    }

    private void handlePerfRequest(SharingService.PerfTestRequest request) {
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    request.fromDisplayName() + " wants to run a performance test.\nAllow?",
                    "Performance Test Request", JOptionPane.YES_NO_OPTION);
            sharingService.respondToPerfTest(request.testId(), choice == JOptionPane.YES_OPTION);
        });
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

    private static String formatFriendBadge(SharingService.FriendNetworkStatus f) {
        return switch (f.connectionState()) {
            case "connected" -> "Connected";
            case "discovered" -> "Discovered";
            default -> "ONLINE".equals(f.presence()) || "IDLE".equals(f.presence())
                    ? f.presence() : "Offline";
        };
    }

    private static String formatDeviceAddress(String host, String ipv6, int port) {
        if (ipv6 != null) return "[" + ipv6 + "]:" + port;
        if (host != null) return host + ":" + port;
        return ":" + port;
    }

    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return label;
    }
}
