package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Chat window with conversation list (left) and message panel (right).
 * Singleton — only one instance at a time.
 */
public class ChatFrame extends JFrame {

    private static ChatFrame instance;
    private static ChatStore sharedChatStore;

    private final SharingService sharingService;
    private final ChatStore chatStore;
    private final ChatPanel chatPanel;

    private DefaultListModel<ConversationEntry> conversationModel;
    private JList<ConversationEntry> conversationList;
    private javax.swing.Timer refreshTimer;
    private boolean refreshing; // re-entrancy guard

    private record ConversationEntry(String email, String displayName, String lastMessage,
                                     long lastTimestamp, int unreadCount, boolean isMutual) {}

    private ChatFrame(SharingService sharingService, ChatStore chatStore) {
        super("Chat");
        this.sharingService = sharingService;
        this.chatStore = chatStore;
        this.chatPanel = new ChatPanel(sharingService, chatStore);

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        initUI();
        setSize(520, 560);
        setMinimumSize(new Dimension(400, 350));

        chatPanel.setOnUnreadChanged(this::refreshConversationList);
        chatPanel.setOnConversationListChanged(this::refreshConversationList);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                // Mark current conversation as read
                String peer = chatPanel.getCurrentPeer();
                if (peer != null) {
                    chatStore.markRead(peer);
                    refreshConversationList();
                }
            }
        });

        // Refresh conversation list periodically (for online status, new messages from background)
        refreshTimer = new javax.swing.Timer(5000, e -> refreshConversationList());
        refreshTimer.start();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Header
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setPreferredSize(new Dimension(0, 52));
        JLabel titleLabel = new JLabel("Chat", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerBar.add(titleLabel, BorderLayout.CENTER);

        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(headerBar, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        mainPanel.add(headerWrapper, BorderLayout.NORTH);

        // Conversation list (left)
        conversationModel = new DefaultListModel<>();
        conversationList = new JList<>(conversationModel);
        conversationList.setCellRenderer(new ConversationCellRenderer());
        conversationList.setFixedCellHeight(52);
        conversationList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            ConversationEntry sel = conversationList.getSelectedValue();
            if (sel != null) {
                chatPanel.showConversation(sel.email());
            }
        });

        // Right-click context menu
        conversationList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int idx = conversationList.locationToIndex(e.getPoint());
                if (idx < 0) return;
                conversationList.setSelectedIndex(idx);
                ConversationEntry entry = conversationModel.get(idx);
                showContextMenu(e, entry);
            }
        });

        JScrollPane listScroll = new JScrollPane(conversationList);
        listScroll.setBorder(null);
        listScroll.setPreferredSize(new Dimension(180, 0));
        listScroll.setMinimumSize(new Dimension(140, 0));

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(listScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, chatPanel);
        splitPane.setDividerLocation(180);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        setContentPane(mainPanel);

        refreshConversationList();
    }

    private void showContextMenu(MouseEvent e, ConversationEntry entry) {
        JPopupMenu menu = new JPopupMenu();

        FriendConfig friend = IntelConfig.get().getFriendByEmail(entry.email());
        if (friend == null) {
            JMenuItem addItem = new JMenuItem("Add as Friend");
            addItem.addActionListener(ev -> {
                String name = JOptionPane.showInputDialog(this,
                    "Display name for " + entry.email() + ":", entry.displayName());
                if (name == null) return;
                IntelConfig.get().addFriend(new FriendConfig(entry.email(), name.trim().isEmpty() ? null : name.trim()));
                IntelConfig.get().save();
                sharingService.onFriendListChanged();
                refreshConversationList();
            });
            menu.add(addItem);
        } else {
            JMenuItem removeItem = new JMenuItem("Remove Friend");
            removeItem.addActionListener(ev -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Remove " + entry.email() + " from friends?",
                    "Remove Friend", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.OK_OPTION) return;
                IntelConfig.get().removeFriend(entry.email());
                IntelConfig.get().save();
                sharingService.onFriendListChanged();
                refreshConversationList();
            });
            menu.add(removeItem);
        }

        JMenuItem clearItem = new JMenuItem("Clear Chat");
        clearItem.addActionListener(ev -> {
            chatStore.clearConversation(entry.email());
            if (entry.email().equals(chatPanel.getCurrentPeer())) {
                chatPanel.showConversation(entry.email());
            }
            refreshConversationList();
        });
        menu.add(clearItem);

        menu.show(conversationList, e.getX(), e.getY());
    }

    void refreshConversationList() {
        if (refreshing) return;
        refreshing = true;
        try {
            refreshConversationListImpl();
        } finally {
            refreshing = false;
        }
    }

    private void refreshConversationListImpl() {
        // Save focus so the text field doesn't lose it during rebuild
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

        String selectedEmail = null;
        ConversationEntry sel = conversationList.getSelectedValue();
        if (sel != null) selectedEmail = sel.email();

        conversationModel.clear();

        // Build list of conversations from ChatStore + friends
        List<ChatStore.Conversation> convos = chatStore.getConversations();
        Set<String> convoEmails = new HashSet<>();
        for (var c : convos) convoEmails.add(c.peerEmail());

        // Also include friends who haven't chatted yet
        for (FriendConfig f : IntelConfig.get().getFriends()) {
            if (!convoEmails.contains(f.getEmail())) {
                convoEmails.add(f.getEmail());
            }
        }

        // Build entries
        List<ConversationEntry> friends = new ArrayList<>();
        List<ConversationEntry> requests = new ArrayList<>();

        for (String email : convoEmails) {
            FriendConfig fc = IntelConfig.get().getFriendByEmail(email);
            boolean isFriend = fc != null;
            boolean isMutual = sharingService.isMutualFriend(email);
            String displayName = fc != null ? fc.label() : email;

            // Find conversation data
            ChatStore.Conversation convo = null;
            for (var c : convos) {
                if (c.peerEmail().equals(email)) { convo = c; break; }
            }

            String lastMsg = convo != null ? convo.lastMessage() : "";
            long lastTs = convo != null ? convo.lastTimestamp() : 0;
            int unread = convo != null ? convo.unreadCount() : 0;

            ConversationEntry entry = new ConversationEntry(email, displayName, lastMsg, lastTs, unread, isMutual);
            if (isFriend) {
                friends.add(entry);
            } else {
                requests.add(entry);
            }
        }

        // Sort by last message timestamp (most recent first), friends without messages last
        Comparator<ConversationEntry> byRecent = (a, b) -> Long.compare(b.lastTimestamp(), a.lastTimestamp());
        friends.sort(byRecent);
        requests.sort(byRecent);

        for (var e : friends) conversationModel.addElement(e);
        for (var e : requests) conversationModel.addElement(e);

        // Re-select without triggering showConversation (already showing the right one)
        if (selectedEmail != null) {
            for (int i = 0; i < conversationModel.size(); i++) {
                if (conversationModel.get(i).email().equals(selectedEmail)) {
                    conversationList.setSelectedIndex(i);
                    break;
                }
            }
        }

        // Restore focus
        if (focusOwner != null && focusOwner.isDisplayable()) {
            focusOwner.requestFocusInWindow();
        }
    }

    /** Start or show a conversation with a specific peer. */
    public void openConversation(String peerEmail) {
        chatPanel.showConversation(peerEmail);
        refreshConversationList();
        // Select in list
        for (int i = 0; i < conversationModel.size(); i++) {
            if (conversationModel.get(i).email().equals(peerEmail)) {
                conversationList.setSelectedIndex(i);
                break;
            }
        }
    }

    // ==================== Static API ====================

    public static void open(SharingService sharingService, ChatStore chatStore, Component relativeTo) {
        sharedChatStore = chatStore;
        if (instance == null || !instance.isDisplayable()) {
            instance = new ChatFrame(sharingService, chatStore);
            instance.setLocationRelativeTo(relativeTo);
        }
        instance.refreshConversationList();
        instance.setVisible(true);
        instance.toFront();
        instance.requestFocus();
    }

    public static int getUnreadCount() {
        return sharedChatStore != null ? sharedChatStore.unreadCount() : 0;
    }

    public static void setOnUnreadChanged(Runnable callback) {
        if (instance != null) {
            instance.chatPanel.setOnUnreadChanged(() -> {
                instance.refreshConversationList();
                if (callback != null) callback.run();
            });
        }
    }

    public static void disposeInstance() {
        if (instance != null) {
            if (instance.refreshTimer != null) instance.refreshTimer.stop();
            instance.chatPanel.dispose();
            instance.dispose();
            instance = null;
        }
    }

    // ==================== Cell Renderer ====================

    private class ConversationCellRenderer extends JPanel implements ListCellRenderer<ConversationEntry> {
        private final JLabel initialLabel = new JLabel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel previewLabel = new JLabel();
        private final JLabel badgeLabel = new JLabel();
        private final JLabel sectionLabel = new JLabel();

        // Track section headers
        private boolean isFriendsSection = true;
        private int friendsCount = 0;

        ConversationCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(4, 8, 4, 8));

            initialLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            initialLabel.setHorizontalAlignment(SwingConstants.CENTER);
            initialLabel.setPreferredSize(new Dimension(28, 28));
            initialLabel.setOpaque(true);

            JPanel textPanel = new JPanel(new BorderLayout());
            textPanel.setOpaque(false);
            nameLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
            previewLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            previewLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            textPanel.add(nameLabel, BorderLayout.NORTH);
            textPanel.add(previewLabel, BorderLayout.CENTER);

            badgeLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
            badgeLabel.setForeground(Color.WHITE);
            badgeLabel.setOpaque(true);
            badgeLabel.setBackground(new Color(76, 148, 255));
            badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
            badgeLabel.setPreferredSize(new Dimension(20, 16));
            badgeLabel.setVisible(false);

            add(initialLabel, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
            add(badgeLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ConversationEntry> list,
                ConversationEntry value, int index, boolean isSelected, boolean cellHasFocus) {

            // Determine section header needed
            boolean isFriend = IntelConfig.get().getFriendByEmail(value.email()) != null;

            String initial = value.email().substring(0, 1).toUpperCase();
            initialLabel.setText(initial);
            initialLabel.setBackground(isFriend ? new Color(76, 148, 255) : new Color(180, 140, 80));
            initialLabel.setForeground(Color.WHITE);

            nameLabel.setText(value.displayName());
            nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

            String preview = value.lastMessage();
            if (preview != null && preview.length() > 30) preview = preview.substring(0, 27) + "...";
            previewLabel.setText(preview != null ? preview : "");

            if (value.unreadCount() > 0) {
                badgeLabel.setText(String.valueOf(value.unreadCount()));
                badgeLabel.setVisible(true);
            } else {
                badgeLabel.setVisible(false);
            }

            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setOpaque(true);
            return this;
        }
    }
}
