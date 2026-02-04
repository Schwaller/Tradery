package com.tradery.desk.alert;

import com.tradery.desk.signal.SignalEvent;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;

/**
 * Desktop notifications for macOS.
 * <p>
 * When running as a packaged .app (with a bundle identifier), uses native
 * UNUserNotificationCenter via JNA — notifications are attributed to the app
 * and clicking them activates it.
 * <p>
 * When running from Gradle/CLI (no bundle ID), shows a Swing toast popup
 * in the top-right corner of the screen — no Script Editor, no subprocess.
 * <p>
 * Falls back gracefully on non-macOS systems.
 */
public class DesktopNotifier implements AlertOutput {

    private static final Logger log = LoggerFactory.getLogger(DesktopNotifier.class);

    private boolean enabled = true;
    private final boolean isMacOS;
    private Notifier notifier;

    public DesktopNotifier() {
        this.isMacOS = System.getProperty("os.name").toLowerCase().contains("mac");
        if (isMacOS) {
            if (isPackagedApp()) {
                try {
                    var un = new UNNotifier();
                    un.requestAuthorization();
                    this.notifier = un;
                    log.info("Using native UNUserNotificationCenter");
                } catch (Throwable e) {
                    log.info("UNUserNotificationCenter unavailable, using toast: {}", e.getMessage());
                    this.notifier = new ToastNotifier();
                }
            } else {
                log.info("Not running as packaged .app, using toast notifications");
                this.notifier = new ToastNotifier();
            }
        } else {
            log.info("Desktop notifications only supported on macOS");
        }
    }

    public DesktopNotifier(boolean enabled) {
        this();
        this.enabled = enabled;
    }

    private static boolean isPackagedApp() {
        String javaHome = System.getProperty("java.home", "");
        if (javaHome.contains(".app/Contents/runtime")) {
            String appPath = javaHome.substring(0, javaHome.indexOf(".app/Contents/runtime") + 4);
            return java.nio.file.Files.exists(java.nio.file.Path.of(appPath, "Contents", "Info.plist"));
        }
        return false;
    }

    @Override
    public void send(SignalEvent signal) {
        if (!enabled || !isMacOS || notifier == null) {
            return;
        }

        String title = String.format("%s %s Signal",
            signal.type().getEmoji(),
            signal.type().getLabel()
        );

        String message = String.format("%s v%d | %s %s @ %.2f",
            signal.strategyName(),
            signal.strategyVersion(),
            signal.symbol(),
            signal.timeframe(),
            signal.price()
        );

        try {
            notifier.post(title, message);
        } catch (Exception e) {
            log.debug("Failed to show notification: {}", e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "Desktop";
    }

    @Override
    public boolean isEnabled() {
        return enabled && isMacOS && notifier != null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // --- Notification backends ---

    interface Notifier {
        void post(String title, String message);
    }

    /**
     * Swing toast notification in the top-right corner.
     * Auto-dismisses after a few seconds with a fade-out.
     */
    static class ToastNotifier implements Notifier {
        private static final int TOAST_WIDTH = 320;
        private static final int TOAST_HEIGHT = 72;
        private static final int MARGIN = 12;
        private static final int DISPLAY_MS = 4000;
        private static final int FADE_MS = 300;
        private static int activeCount = 0;

        @Override
        public void post(String title, String message) {
            SwingUtilities.invokeLater(() -> showToast(title, message));
        }

        private void showToast(String title, String message) {
            var toast = new JWindow();
            toast.setAlwaysOnTop(true);

            var panel = new JPanel();
            panel.setLayout(new BorderLayout(8, 2));
            panel.setBackground(new Color(50, 50, 54));
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 74), 1, true),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
            ));

            var baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

            var titleLabel = new JLabel(title);
            titleLabel.setFont(baseFont.deriveFont(Font.BOLD, 13f));
            titleLabel.setForeground(new Color(230, 230, 235));

            var msgLabel = new JLabel(message);
            msgLabel.setFont(baseFont.deriveFont(Font.PLAIN, 11.5f));
            msgLabel.setForeground(new Color(170, 170, 178));

            panel.add(titleLabel, BorderLayout.NORTH);
            panel.add(msgLabel, BorderLayout.CENTER);

            toast.setContentPane(panel);
            toast.setSize(TOAST_WIDTH, TOAST_HEIGHT);

            // Position top-right, stacking below any active toasts
            var screen = Toolkit.getDefaultToolkit().getScreenSize();
            var insets = Toolkit.getDefaultToolkit().getScreenInsets(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration()
            );
            int x = screen.width - TOAST_WIDTH - MARGIN;
            int y = insets.top + MARGIN + (activeCount * (TOAST_HEIGHT + 8));
            toast.setLocation(x, y);

            activeCount++;
            toast.setOpacity(0f);
            toast.setVisible(true);

            // Fade in
            var fadeIn = new Timer(16, null);
            fadeIn.addActionListener(e -> {
                float opacity = toast.getOpacity() + 0.1f;
                if (opacity >= 1f) {
                    toast.setOpacity(1f);
                    fadeIn.stop();
                } else {
                    toast.setOpacity(opacity);
                }
            });
            fadeIn.start();

            // Dismiss after delay
            var dismiss = new Timer(DISPLAY_MS, e -> {
                var fadeOut = new Timer(16, null);
                fadeOut.addActionListener(evt -> {
                    float opacity = toast.getOpacity() - (16f / FADE_MS);
                    if (opacity <= 0f) {
                        fadeOut.stop();
                        toast.dispose();
                        activeCount--;
                    } else {
                        toast.setOpacity(opacity);
                    }
                });
                fadeOut.start();
            });
            dismiss.setRepeats(false);
            dismiss.start();
        }
    }

    /**
     * Native UNUserNotificationCenter via JNA + ObjC runtime.
     * Notifications are attributed to the .app bundle. Requires a valid bundle identifier.
     */
    static class UNNotifier implements Notifier {

        private final com.sun.jna.Function objc_getClass;
        private final com.sun.jna.Function sel_registerName;
        private final com.sun.jna.Function objc_msgSend;

        UNNotifier() {
            var lib = com.sun.jna.NativeLibrary.getInstance("objc");
            this.objc_getClass = lib.getFunction("objc_getClass");
            this.sel_registerName = lib.getFunction("sel_registerName");
            this.objc_msgSend = lib.getFunction("objc_msgSend");
            com.sun.jna.NativeLibrary.getInstance("UserNotifications");
        }

        private Pointer cls(String name) {
            return (Pointer) objc_getClass.invoke(Pointer.class, new Object[]{name});
        }

        private Pointer sel(String name) {
            return (Pointer) sel_registerName.invoke(Pointer.class, new Object[]{name});
        }

        private Pointer msg(Pointer target, Pointer selector, Object... args) {
            Object[] callArgs = new Object[2 + args.length];
            callArgs[0] = target;
            callArgs[1] = selector;
            System.arraycopy(args, 0, callArgs, 2, args.length);
            return (Pointer) objc_msgSend.invoke(Pointer.class, callArgs);
        }

        private Pointer nsString(String text) {
            Pointer alloc = msg(cls("NSString"), sel("alloc"));
            return msg(alloc, sel("initWithUTF8String:"), text);
        }

        void requestAuthorization() {
            Pointer center = msg(cls("UNUserNotificationCenter"), sel("currentNotificationCenter"));
            long options = 1 | 2; // alert + sound
            msg(center, sel("requestAuthorizationWithOptions:completionHandler:"), options, Pointer.NULL);
        }

        @Override
        public void post(String title, String message) {
            Pointer content = msg(msg(cls("UNMutableNotificationContent"), sel("alloc")), sel("init"));
            msg(content, sel("setTitle:"), nsString(title));
            msg(content, sel("setBody:"), nsString(message));
            msg(content, sel("setSound:"), msg(cls("UNNotificationSound"), sel("defaultSound")));

            Pointer requestId = nsString(UUID.randomUUID().toString());
            Pointer request = msg(cls("UNNotificationRequest"),
                sel("requestWithIdentifier:content:trigger:"),
                requestId, content, Pointer.NULL);

            Pointer center = msg(cls("UNUserNotificationCenter"), sel("currentNotificationCenter"));
            msg(center, sel("addNotificationRequest:withCompletionHandler:"), request, Pointer.NULL);
        }
    }
}
