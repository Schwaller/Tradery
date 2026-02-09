package com.tradery.news.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Modal dialog shown after first Keycloak login when no local UER exists.
 * Offers two paths: recover from a friend's backup, or fresh start with new password.
 */
public class RecoveryDialog extends JDialog {

    public enum Result { RECOVER, FRESH_START, CANCELLED }

    private Result result = Result.CANCELLED;
    private String password;
    private String friendEmail;

    public RecoveryDialog(Window owner) {
        super(owner, "Welcome", ModalityType.APPLICATION_MODAL);
        initComponents();
        setSize(420, 320);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(20, 24, 16, 24));
        setContentPane(content);

        JLabel header = new JLabel("Set up your device");
        header.setFont(new Font("SansSerif", Font.BOLD, 16));
        content.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPasswordField passwordField = new JPasswordField(20);
        JPasswordField confirmField = new JPasswordField(20);
        JTextField friendField = new JTextField(20);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        form.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(passwordField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("Confirm:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(confirmField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        form.add(new JLabel("Friend email:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(friendField, gbc);

        JLabel hint = new JLabel("<html><i>Enter a friend's email to recover from their backup,<br>or leave blank for a fresh start.</i></html>");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        form.add(hint, gbc);

        content.add(form, BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JButton recoverBtn = new JButton("Recover from Friend");
        recoverBtn.addActionListener(e -> {
            String pwd = new String(passwordField.getPassword());
            String friend = friendField.getText().trim();
            if (pwd.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a password.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (friend.isEmpty() || !friend.contains("@")) {
                JOptionPane.showMessageDialog(this, "Please enter a valid friend email for recovery.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            password = pwd;
            friendEmail = friend;
            result = Result.RECOVER;
            dispose();
        });

        JButton freshBtn = new JButton("Fresh Start");
        freshBtn.addActionListener(e -> {
            String pwd = new String(passwordField.getPassword());
            String confirm = new String(confirmField.getPassword());
            if (pwd.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a password.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!pwd.equals(confirm)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            password = pwd;
            result = Result.FRESH_START;
            dispose();
        });

        buttons.add(cancelBtn);
        buttons.add(recoverBtn);
        buttons.add(freshBtn);
        content.add(buttons, BorderLayout.SOUTH);
    }

    public Result getResult() { return result; }
    public String getPassword() { return password; }
    public String getFriendEmail() { return friendEmail; }

    public static RecoveryDialog showDialog(Window owner) {
        var dlg = new RecoveryDialog(owner);
        dlg.setVisible(true);
        return dlg;
    }
}
