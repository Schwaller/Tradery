package com.tradery.news.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Per-conversation chat panel with message display and input.
 * Shows messages for the currently selected peer.
 */
public class ChatPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int PAGE_SIZE = 100;

    private final SharingService sharingService;
    private final ChatStore chatStore;
    private final Consumer<SharingService.ChatMessage> chatListener;

    private final JPanel messageListPanel;
    private final JScrollPane messageScroll;
    private final JTextField inputField;
    private final JButton sendBtn;
    private final JLabel emptyLabel;

    private String currentPeer; // email of the peer whose conversation is displayed
    private Runnable onUnreadChanged;
    private Runnable onConversationListChanged;

    public ChatPanel(SharingService sharingService, ChatStore chatStore) {
        super(new BorderLayout());
        this.sharingService = sharingService;
        this.chatStore = chatStore;

        // Message list
        messageListPanel = new JPanel();
        messageListPanel.setLayout(new BoxLayout(messageListPanel, BoxLayout.Y_AXIS));
        messageListPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        messageScroll = new JScrollPane(messageListPanel);
        messageScroll.setBorder(null);
        messageScroll.getVerticalScrollBar().setUnitIncrement(16);

        emptyLabel = new JLabel("Select a conversation", SwingConstants.CENTER);
        emptyLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        emptyLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(emptyLabel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Input bar
        JPanel inputWrapper = new JPanel(new BorderLayout());
        inputWrapper.add(new JSeparator(), BorderLayout.NORTH);

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.setBorder(new EmptyBorder(6, 8, 6, 8));

        inputField = new JTextField();
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        inputField.putClientProperty("JTextField.placeholderText", "Type a message...");
        inputField.setEnabled(false);
        inputField.addActionListener(this::onSend);

        sendBtn = new JButton(">");
        sendBtn.setMargin(new Insets(2, 8, 2, 8));
        sendBtn.setEnabled(false);
        sendBtn.addActionListener(this::onSend);

        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        inputWrapper.add(inputRow, BorderLayout.CENTER);
        add(inputWrapper, BorderLayout.SOUTH);

        // Listen for incoming messages
        chatListener = this::onChatMessage;
        sharingService.addChatListener(chatListener);
    }

    /** Show conversation with a specific peer. Loads messages from store. */
    public void showConversation(String peerEmail) {
        this.currentPeer = peerEmail;
        inputField.setEnabled(peerEmail != null);
        sendBtn.setEnabled(peerEmail != null);

        if (peerEmail == null) {
            showEmpty();
            return;
        }

        // Mark as read
        chatStore.markRead(peerEmail);
        if (onUnreadChanged != null) onUnreadChanged.run();

        // Load messages
        List<ChatStore.Message> messages = chatStore.getMessages(peerEmail, PAGE_SIZE, 0);

        // Replace center content with message scroll
        removeAll();
        add(messageScroll, BorderLayout.CENTER);

        // Rebuild input bar
        JPanel inputWrapper = new JPanel(new BorderLayout());
        inputWrapper.add(new JSeparator(), BorderLayout.NORTH);
        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.setBorder(new EmptyBorder(6, 8, 6, 8));
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        inputWrapper.add(inputRow, BorderLayout.CENTER);
        add(inputWrapper, BorderLayout.SOUTH);

        messageListPanel.removeAll();

        String userEmail = IntelConfig.get().getUserEmail();
        for (ChatStore.Message msg : messages) {
            addMessageBubble(msg.senderEmail(), msg.text(), msg.timestamp(), userEmail);
        }

        revalidate();
        repaint();

        // Scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollBar vsb = messageScroll.getVerticalScrollBar();
            vsb.setValue(vsb.getMaximum());
        });
    }

    private void showEmpty() {
        removeAll();
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(emptyLabel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        JPanel inputWrapper = new JPanel(new BorderLayout());
        inputWrapper.add(new JSeparator(), BorderLayout.NORTH);
        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.setBorder(new EmptyBorder(6, 8, 6, 8));
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        inputWrapper.add(inputRow, BorderLayout.CENTER);
        add(inputWrapper, BorderLayout.SOUTH);

        revalidate();
        repaint();
    }

    private void addMessageBubble(String senderEmail, String text, long timestamp, String userEmail) {
        boolean isSelf = senderEmail.equals(userEmail);
        String time = LocalTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            .format(TIME_FMT);

        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setAlignmentX(isSelf ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
        bubble.setBorder(new EmptyBorder(2, 0, 2, 0));
        bubble.setOpaque(false);
        bubble.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

        JPanel msgContent = new JPanel();
        msgContent.setLayout(new BoxLayout(msgContent, BoxLayout.Y_AXIS));
        msgContent.setBorder(new EmptyBorder(4, 8, 4, 8));
        msgContent.setBackground(isSelf
            ? new Color(76, 148, 255, 30)
            : UIManager.getColor("Panel.background"));

        // Sender + time
        String sender = isSelf ? "you" : shortName(senderEmail);
        JLabel headerLabel = new JLabel(sender + "  " + time);
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        headerLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        msgContent.add(headerLabel);

        // Text
        JLabel textLabel = new JLabel("<html><body style='width: 200px'>" + escapeHtml(text) + "</body></html>");
        textLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        textLabel.setForeground(UIManager.getColor("Label.foreground"));
        textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        msgContent.add(textLabel);

        if (isSelf) {
            bubble.add(Box.createHorizontalStrut(40), BorderLayout.WEST);
            bubble.add(msgContent, BorderLayout.CENTER);
        } else {
            bubble.add(msgContent, BorderLayout.CENTER);
            bubble.add(Box.createHorizontalStrut(40), BorderLayout.EAST);
        }

        messageListPanel.add(bubble);
    }

    /** Add a system message (centered, muted). */
    public void addSystemMessage(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("SansSerif", Font.PLAIN, 10));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        label.setBorder(new EmptyBorder(4, 0, 4, 0));
        messageListPanel.add(label);
        messageListPanel.revalidate();

        SwingUtilities.invokeLater(() -> {
            JScrollBar vsb = messageScroll.getVerticalScrollBar();
            vsb.setValue(vsb.getMaximum());
        });
    }

    private void onSend(ActionEvent e) {
        String text = inputField.getText().trim();
        if (text.isEmpty() || currentPeer == null) return;
        inputField.setText("");

        String userEmail = IntelConfig.get().getUserEmail();
        long now = System.currentTimeMillis();

        // Persist locally
        chatStore.saveMessage(currentPeer, userEmail, text, now);

        // Send over network
        sharingService.sendChat(currentPeer, text);

        // Add bubble
        addMessageBubble(userEmail, text, now, userEmail);
        messageListPanel.revalidate();
        messageListPanel.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollBar vsb = messageScroll.getVerticalScrollBar();
            vsb.setValue(vsb.getMaximum());
        });

        if (onConversationListChanged != null) onConversationListChanged.run();
    }

    private void onChatMessage(SharingService.ChatMessage msg) {
        String userEmail = IntelConfig.get().getUserEmail();
        // Determine peer email (the other person)
        String peerEmail = msg.senderEmail().equals(userEmail) ? msg.recipientEmail() : msg.senderEmail();
        if (peerEmail == null) return;

        // Don't persist our own echoed messages (already saved in onSend)
        if (msg.senderEmail().equals(userEmail)) return;

        // Persist
        boolean isCurrentConvo = peerEmail.equals(currentPeer) && isShowing();
        chatStore.saveMessage(peerEmail, msg.senderEmail(), msg.text(), msg.timestamp());

        if (isCurrentConvo) {
            chatStore.markRead(peerEmail);
        }

        SwingUtilities.invokeLater(() -> {
            if (peerEmail.equals(currentPeer)) {
                // Add bubble to current conversation
                addMessageBubble(msg.senderEmail(), msg.text(), msg.timestamp(), userEmail);
                messageListPanel.revalidate();
                messageListPanel.repaint();
                SwingUtilities.invokeLater(() -> {
                    JScrollBar vsb = messageScroll.getVerticalScrollBar();
                    vsb.setValue(vsb.getMaximum());
                });
            }

            if (onUnreadChanged != null) onUnreadChanged.run();
            if (onConversationListChanged != null) onConversationListChanged.run();
        });
    }

    private String shortName(String email) {
        FriendConfig f = IntelConfig.get().getFriendByEmail(email);
        if (f != null && f.getDisplayName() != null) return f.getDisplayName();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public String getCurrentPeer() { return currentPeer; }

    public void setOnUnreadChanged(Runnable callback) {
        this.onUnreadChanged = callback;
    }

    public void setOnConversationListChanged(Runnable callback) {
        this.onConversationListChanged = callback;
    }

    public void dispose() {
        sharingService.removeChatListener(chatListener);
    }
}
