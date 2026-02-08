package com.tradery.news.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Lightweight ephemeral P2P chat panel.
 * Messages are not persisted — only live while the app runs.
 */
public class ChatPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final SharingService sharingService;
    private final JTextArea messageArea;
    private final JTextField inputField;
    private final Consumer<SharingService.ChatMessage> chatListener;
    private int unreadCount;
    private Runnable onUnreadChanged;

    public ChatPanel(SharingService sharingService) {
        super(new BorderLayout());
        this.sharingService = sharingService;

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(6, 10, 6, 6));
        JLabel titleLabel = new JLabel("Friends Chat");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        header.add(titleLabel, BorderLayout.WEST);

        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(header, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        add(headerWrapper, BorderLayout.NORTH);

        // Message area
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
        messageArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        messageArea.setForeground(UIManager.getColor("Label.foreground"));

        JScrollPane scroll = new JScrollPane(messageArea);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        // Input bar
        JPanel inputBar = new JPanel(new BorderLayout(4, 0));
        inputBar.setBorder(new EmptyBorder(6, 8, 6, 8));
        inputBar.add(new JSeparator(), BorderLayout.NORTH);

        inputField = new JTextField();
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        inputField.putClientProperty("JTextField.placeholderText", "Type a message...");

        JButton sendBtn = new JButton(">");
        sendBtn.setMargin(new Insets(2, 8, 2, 8));
        sendBtn.addActionListener(this::onSend);

        // Enter key sends
        inputField.addActionListener(this::onSend);

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        JPanel inputWrapper = new JPanel(new BorderLayout());
        inputWrapper.add(new JSeparator(), BorderLayout.NORTH);
        inputWrapper.add(inputRow, BorderLayout.CENTER);
        inputWrapper.setBorder(new EmptyBorder(4, 8, 6, 8));
        add(inputWrapper, BorderLayout.SOUTH);

        // Listen for incoming messages
        chatListener = this::onChatMessage;
        sharingService.addChatListener(chatListener);

        setPreferredSize(new Dimension(280, 0));
    }

    private void onSend(ActionEvent e) {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.setText("");
        sharingService.sendChat(text);
    }

    private void onChatMessage(SharingService.ChatMessage msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.ofInstant(Instant.ofEpochMilli(msg.timestamp()), ZoneId.systemDefault())
                .format(TIME_FMT);
            String sender = shortName(msg.senderEmail());
            String userEmail = IntelConfig.get().getUserEmail();
            String prefix = msg.senderEmail().equals(userEmail) ? "you" : sender;
            messageArea.append("[" + time + "] " + prefix + ": " + msg.text() + "\n");
            messageArea.setCaretPosition(messageArea.getDocument().getLength());

            // Track unread if panel is not showing
            if (!isShowing()) {
                unreadCount++;
                if (onUnreadChanged != null) onUnreadChanged.run();
            }
        });
    }

    private String shortName(String email) {
        FriendConfig f = IntelConfig.get().getFriendByEmail(email);
        if (f != null && f.getDisplayName() != null) return f.getDisplayName();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    public int getUnreadCount() { return unreadCount; }

    public void clearUnread() {
        unreadCount = 0;
        if (onUnreadChanged != null) onUnreadChanged.run();
    }

    public void setOnUnreadChanged(Runnable callback) {
        this.onUnreadChanged = callback;
    }

    /** Call when disposing the parent to clean up the listener. */
    public void dispose() {
        sharingService.removeChatListener(chatListener);
    }
}
