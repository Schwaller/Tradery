package com.tradery.news.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Non-modal dialog for managing the app-level friends list.
 * Shows online status from LAN discovery.
 */
public class FriendsDialog extends JDialog {

    private final SharingService sharingService;
    private DefaultListModel<SharingService.FriendStatus> listModel;
    private JList<SharingService.FriendStatus> friendList;
    private javax.swing.Timer refreshTimer;

    public FriendsDialog(JFrame owner, SharingService sharingService) {
        super(owner, "Friends", ModalityType.MODELESS);
        this.sharingService = sharingService;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        initComponents();
        loadFriends();

        setSize(380, 420);
        setResizable(true);
        setLocationRelativeTo(owner);

        // Refresh online status every 10 seconds
        refreshTimer = new javax.swing.Timer(10_000, e -> loadFriends());
        refreshTimer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                refreshTimer.stop();
            }
        });
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        setContentPane(content);

        // Header
        JPanel headerWrapper = new JPanel(new BorderLayout());
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setPreferredSize(new Dimension(0, 52));
        JLabel titleLabel = new JLabel("Friends", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerBar.add(titleLabel, BorderLayout.CENTER);
        headerWrapper.add(headerBar, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        content.add(headerWrapper, BorderLayout.NORTH);

        // Friend list
        listModel = new DefaultListModel<>();
        friendList = new JList<>(listModel);
        friendList.setCellRenderer(new FriendCellRenderer());
        friendList.setFixedCellHeight(44);

        JScrollPane scroll = new JScrollPane(friendList);
        scroll.setBorder(new EmptyBorder(4, 8, 4, 8));
        content.add(scroll, BorderLayout.CENTER);

        // Button bar
        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.add(new JSeparator(), BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setBorder(new EmptyBorder(10, 12, 10, 12));

        JButton addBtn = new JButton("Add...");
        addBtn.addActionListener(e -> onAdd());
        JButton removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> onRemove());
        JButton editBtn = new JButton("Edit Name...");
        editBtn.addActionListener(e -> onEditName());

        JButton chatBtn = new JButton("Chat");
        chatBtn.addActionListener(e -> ChatFrame.open(sharingService, this));

        buttons.add(addBtn);
        buttons.add(removeBtn);
        buttons.add(editBtn);
        buttons.add(chatBtn);

        JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        closePanel.setBorder(new EmptyBorder(10, 0, 10, 12));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        closePanel.add(closeBtn);

        buttonBar.add(buttons, BorderLayout.WEST);
        buttonBar.add(closePanel, BorderLayout.EAST);
        content.add(buttonBar, BorderLayout.SOUTH);
    }

    private void loadFriends() {
        int selectedIndex = friendList.getSelectedIndex();
        listModel.clear();
        List<SharingService.FriendStatus> friends = sharingService.getFriendsWithStatus();
        for (var f : friends) listModel.addElement(f);
        if (selectedIndex >= 0 && selectedIndex < listModel.size()) {
            friendList.setSelectedIndex(selectedIndex);
        }
    }

    private void onAdd() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 6));
        JTextField emailField = new JTextField();
        JTextField nameField = new JTextField();
        panel.add(new JLabel("Email:"));
        panel.add(emailField);
        panel.add(new JLabel("Display Name:"));
        panel.add(nameField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Friend",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String email = emailField.getText().trim().toLowerCase();
        if (email.isEmpty() || !email.contains("@")) {
            JOptionPane.showMessageDialog(this, "Please enter a valid email.",
                "Invalid Email", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = nameField.getText().trim();
        IntelConfig.get().addFriend(new FriendConfig(email, name.isEmpty() ? null : name));
        IntelConfig.get().save();
        loadFriends();
    }

    private void onRemove() {
        var selected = friendList.getSelectedValue();
        if (selected == null) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove " + selected.email() + " from friends?",
            "Remove Friend", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;
        IntelConfig.get().removeFriend(selected.email());
        IntelConfig.get().save();
        loadFriends();
    }

    private void onEditName() {
        var selected = friendList.getSelectedValue();
        if (selected == null) return;
        String name = JOptionPane.showInputDialog(this, "Display name for " + selected.email() + ":",
            selected.displayName());
        if (name == null) return;
        FriendConfig f = IntelConfig.get().getFriendByEmail(selected.email());
        if (f != null) {
            f.setDisplayName(name.trim().isEmpty() ? null : name.trim());
            IntelConfig.get().save();
            loadFriends();
        }
    }

    private static class FriendCellRenderer extends JPanel implements ListCellRenderer<SharingService.FriendStatus> {
        private final JLabel initialLabel = new JLabel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel statusLabel = new JLabel();

        FriendCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(4, 8, 4, 8));

            initialLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            initialLabel.setHorizontalAlignment(SwingConstants.CENTER);
            initialLabel.setPreferredSize(new Dimension(32, 32));
            initialLabel.setOpaque(true);

            JPanel textPanel = new JPanel(new BorderLayout());
            textPanel.setOpaque(false);
            nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            textPanel.add(nameLabel, BorderLayout.CENTER);
            textPanel.add(statusLabel, BorderLayout.EAST);

            add(initialLabel, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends SharingService.FriendStatus> list,
                SharingService.FriendStatus value, int index, boolean isSelected, boolean cellHasFocus) {

            String initial = value.email().substring(0, 1).toUpperCase();
            initialLabel.setText(initial);
            initialLabel.setBackground(new Color(76, 148, 255));
            initialLabel.setForeground(Color.WHITE);

            String display = value.displayName() != null && !value.displayName().equals(value.email())
                ? value.displayName() + "  " + value.email()
                : value.email();
            nameLabel.setText(display);

            if (value.online()) {
                statusLabel.setText("online");
                statusLabel.setForeground(new Color(80, 180, 100));
            } else if (value.lastSeenMs() > 0) {
                long ago = (System.currentTimeMillis() - value.lastSeenMs()) / 1000;
                String timeStr = ago < 60 ? ago + "s ago"
                    : ago < 3600 ? (ago / 60) + "m ago"
                    : (ago / 3600) + "h ago";
                statusLabel.setText(timeStr);
                statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            } else {
                statusLabel.setText("offline");
                statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            }

            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            setOpaque(true);
            return this;
        }
    }
}
