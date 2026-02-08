package com.tradery.license;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;

/**
 * Non-modal "new version available" notification dialog.
 */
public class UpdateNotification extends JDialog {

    private UpdateNotification(UpdateChecker.UpdateInfo info) {
        super((Frame) null, "Update Available", false); // non-modal
        setSize(380, 200);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // macOS integrated title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        JPanel mainPanel = new JPanel(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 44));
        JLabel title = new JLabel("Update Available", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(UIManager.getColor("Label.disabledForeground"));
        header.add(title, BorderLayout.CENTER);

        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(header, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        mainPanel.add(headerWrapper, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(12, 20, 8, 20));

        String versionText = "Version " + info.latestVersion + " is available.";
        JLabel versionLabel = new JLabel(versionText);
        versionLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        content.add(versionLabel, BorderLayout.NORTH);

        if (info.releaseNotes != null && !info.releaseNotes.isBlank()) {
            JLabel notesLabel = new JLabel("<html>" + info.releaseNotes + "</html>");
            notesLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            notesLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            content.add(notesLabel, BorderLayout.CENTER);
        }

        mainPanel.add(content, BorderLayout.CENTER);

        // Button bar
        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.add(new JSeparator(), BorderLayout.NORTH);

        JPanel buttonsRow = new JPanel(new BorderLayout());
        buttonsRow.setBorder(new EmptyBorder(10, 16, 10, 16));

        JCheckBox skipBox = new JCheckBox("Skip this version");
        skipBox.setFont(new Font("SansSerif", Font.PLAIN, 11));
        buttonsRow.add(skipBox, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        JButton dismissBtn = new JButton("Dismiss");
        dismissBtn.addActionListener(e -> {
            if (skipBox.isSelected()) {
                UpdateChecker.skipVersion(info.latestVersion);
            }
            dispose();
        });
        rightButtons.add(dismissBtn);

        if (info.downloadUrl != null && !info.downloadUrl.isBlank()) {
            JButton downloadBtn = new JButton("Download");
            downloadBtn.addActionListener(e -> {
                try {
                    Desktop.getDesktop().browse(new URI(info.downloadUrl));
                } catch (Exception ex) {
                    // Silently fail
                }
                dispose();
            });
            rightButtons.add(downloadBtn);
        }

        buttonsRow.add(rightButtons, BorderLayout.EAST);
        buttonBar.add(buttonsRow, BorderLayout.CENTER);
        mainPanel.add(buttonBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    /**
     * Show the update notification on the EDT.
     */
    static void show(UpdateChecker.UpdateInfo info) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(info));
            return;
        }
        new UpdateNotification(info).setVisible(true);
    }
}
