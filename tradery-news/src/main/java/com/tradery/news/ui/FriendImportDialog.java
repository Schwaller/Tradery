package com.tradery.news.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Modal dialog for password-reset friend import.
 * Shows when a peer with the same email but a different public key
 * offers proof of prior friendship.
 */
public class FriendImportDialog extends JDialog {

    private boolean accepted;

    public FriendImportDialog(Window owner, String email, long certIssuedAt) {
        super(owner, "Friend Import", ModalityType.APPLICATION_MODAL);
        initComponents(email, certIssuedAt);
        setSize(400, 200);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void initComponents(String email, long certIssuedAt) {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(20, 24, 16, 24));
        setContentPane(content);

        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(certIssuedAt));

        JLabel message = new JLabel("<html><b>" + email + "</b> says you were friends since " + date + ".<br><br>"
                + "They have a new device key (password reset). Import as friend?</html>");
        content.add(message, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton rejectBtn = new JButton("Reject");
        rejectBtn.addActionListener(e -> dispose());

        JButton acceptBtn = new JButton("Accept");
        acceptBtn.addActionListener(e -> {
            accepted = true;
            dispose();
        });

        buttons.add(rejectBtn);
        buttons.add(acceptBtn);
        content.add(buttons, BorderLayout.SOUTH);
    }

    public boolean isAccepted() { return accepted; }

    public static boolean showDialog(Window owner, String email, long certIssuedAt) {
        var dlg = new FriendImportDialog(owner, email, certIssuedAt);
        dlg.setVisible(true);
        return dlg.isAccepted();
    }
}
