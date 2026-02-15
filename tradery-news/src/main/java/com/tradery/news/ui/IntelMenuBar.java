package com.tradery.news.ui;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.util.List;

/**
 * Shared menu bar for all Intel app windows.
 * On macOS, every JFrame needs a JMenuBar so the global menu bar
 * doesn't go blank when the window gets focus.
 */
public class IntelMenuBar {

    /** Create the standard Intel app menu bar for the given window. */
    public static JMenuBar create(Window owner) {
        JMenuBar menuBar = new JMenuBar();

        JMenu friendsMenu = new JMenu("Friends");
        friendsMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                friendsMenu.removeAll();
                boolean hasSharingService = IntelDocumentFrame.getSharingService() != null;
                List<FriendConfig> friends = IntelConfig.get().getFriends();
                for (FriendConfig friend : friends) {
                    JMenuItem item = new JMenuItem(friend.label());
                    item.setEnabled(hasSharingService);
                    item.addActionListener(ev -> {
                        SharingService ss = IntelDocumentFrame.getSharingService();
                        ChatStore cs = IntelDocumentFrame.getChatStore();
                        if (ss != null && cs != null) {
                            ChatFrame.open(ss, cs, owner);
                            ChatFrame.getInstance().openConversation(friend.getEmail());
                        }
                    });
                    friendsMenu.add(item);
                }
                if (!friends.isEmpty()) {
                    friendsMenu.addSeparator();
                }
                JMenuItem manageFriends = new JMenuItem("Manage Friends...");
                manageFriends.setEnabled(hasSharingService);
                manageFriends.addActionListener(ev -> {
                    SharingService ss = IntelDocumentFrame.getSharingService();
                    ChatStore cs = IntelDocumentFrame.getChatStore();
                    if (ss != null) {
                        FriendsDialog dialog = new FriendsDialog(owner, ss, cs);
                        dialog.setVisible(true);
                    }
                });
                friendsMenu.add(manageFriends);
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        menuBar.add(friendsMenu);

        JMenu windowMenu = new JMenu("Window");
        windowMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                windowMenu.removeAll();

                JMenuItem minimize = new JMenuItem("Minimize");
                minimize.setAccelerator(KeyStroke.getKeyStroke("meta M"));
                minimize.addActionListener(ev -> {
                    if (owner instanceof JFrame f) f.setExtendedState(f.getExtendedState() | Frame.ICONIFIED);
                });
                windowMenu.add(minimize);

                JMenuItem zoom = new JMenuItem("Zoom");
                zoom.addActionListener(ev -> {
                    if (owner instanceof JFrame f) {
                        int state = f.getExtendedState();
                        f.setExtendedState((state & Frame.MAXIMIZED_BOTH) != 0
                            ? state & ~Frame.MAXIMIZED_BOTH : state | Frame.MAXIMIZED_BOTH);
                    }
                });
                windowMenu.add(zoom);

                windowMenu.addSeparator();

                JMenuItem bringAll = new JMenuItem("Bring All to Front");
                bringAll.addActionListener(ev -> {
                    for (Window w : Window.getWindows()) {
                        if (w.isVisible() && w instanceof JFrame) w.toFront();
                    }
                });
                windowMenu.add(bringAll);

                // List all visible JFrame windows
                Window[] windows = Window.getWindows();
                boolean hasWindows = false;
                for (Window w : windows) {
                    if (w.isVisible() && w instanceof JFrame f) {
                        if (!hasWindows) {
                            windowMenu.addSeparator();
                            hasWindows = true;
                        }
                        String title = f.getTitle();
                        if (title == null || title.isEmpty()) title = f.getClass().getSimpleName();
                        JCheckBoxMenuItem item = new JCheckBoxMenuItem(title, w == owner);
                        item.addActionListener(ev -> {
                            f.toFront();
                            f.requestFocus();
                        });
                        windowMenu.add(item);
                    }
                }
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        menuBar.add(windowMenu);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem guideItem = new JMenuItem("Intelligence Guide");
        guideItem.addActionListener(e -> IntelHelpDialog.show(owner));
        helpMenu.add(guideItem);
        menuBar.add(helpMenu);

        return menuBar;
    }
}
