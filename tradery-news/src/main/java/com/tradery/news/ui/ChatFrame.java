package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Standalone chat window. Singleton — only one instance at a time.
 */
public class ChatFrame extends JFrame {

    private static ChatFrame instance;

    private final ChatPanel chatPanel;

    private ChatFrame(SharingService sharingService) {
        super("Chat");
        this.chatPanel = new ChatPanel(sharingService);

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        setContentPane(chatPanel);
        setSize(360, 500);
        setMinimumSize(new Dimension(280, 300));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                chatPanel.clearUnread();
            }
        });
    }

    /**
     * Show the singleton chat window. Creates it on first call.
     */
    public static void open(SharingService sharingService, Component relativeTo) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new ChatFrame(sharingService);
            instance.setLocationRelativeTo(relativeTo);
        }
        instance.chatPanel.clearUnread();
        instance.setVisible(true);
        instance.toFront();
        instance.requestFocus();
    }

    /**
     * Get unread count (for badge updates from other windows).
     */
    public static int getUnreadCount() {
        return instance != null ? instance.chatPanel.getUnreadCount() : 0;
    }

    /**
     * Register a callback for unread count changes.
     */
    public static void setOnUnreadChanged(Runnable callback) {
        if (instance != null) {
            instance.chatPanel.setOnUnreadChanged(callback);
        }
    }

    /**
     * Dispose the singleton instance (call on app shutdown).
     */
    public static void disposeInstance() {
        if (instance != null) {
            instance.chatPanel.dispose();
            instance.dispose();
            instance = null;
        }
    }
}
