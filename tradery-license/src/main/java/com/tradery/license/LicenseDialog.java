package com.tradery.license;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;

/**
 * Modal license key entry dialog.
 * Styled consistently with AiSetupDialog (macOS transparent title bar, centered layout).
 */
public class LicenseDialog extends JDialog {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private JTextField keyField;
    private JLabel statusLabel;
    private JButton activateBtn;
    private boolean activated = false;

    public LicenseDialog(Window owner) {
        super(owner, "License Activation", ModalityType.APPLICATION_MODAL);
        setSize(440, 260);
        setResizable(false);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // macOS integrated title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 52));
        JLabel title = new JLabel("License Activation", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(UIManager.getColor("Label.disabledForeground"));
        header.add(title, BorderLayout.CENTER);

        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(header, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        mainPanel.add(headerWrapper, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(16, 24, 12, 24));

        JLabel subtitle = new JLabel("Enter your license key to activate Plaiiin:");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        content.add(subtitle, BorderLayout.NORTH);

        // Key input with auto-dash formatting
        JPanel inputPanel = new JPanel(new BorderLayout(0, 6));

        keyField = new JTextField();
        keyField.setFont(new Font("Monospaced", Font.PLAIN, 16));
        keyField.setHorizontalAlignment(JTextField.CENTER);
        keyField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "XXXX-XXXX-XXXX-XXXX");
        ((AbstractDocument) keyField.getDocument()).setDocumentFilter(new LicenseKeyFilter());
        inputPanel.add(keyField, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        inputPanel.add(statusLabel, BorderLayout.SOUTH);

        content.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(content, BorderLayout.CENTER);

        // Button bar
        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.add(new JSeparator(), BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBorder(new EmptyBorder(10, 16, 10, 16));

        JButton quitBtn = new JButton("Quit");
        quitBtn.addActionListener(e -> System.exit(0));
        buttons.add(quitBtn);

        activateBtn = new JButton("Activate");
        activateBtn.addActionListener(e -> tryActivate());
        buttons.add(activateBtn);

        buttonBar.add(buttons, BorderLayout.CENTER);
        mainPanel.add(buttonBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Enter key activates
        getRootPane().setDefaultButton(activateBtn);
    }

    private void tryActivate() {
        String key = keyField.getText().trim();
        LicenseKeyCodec.LicenseResult result = LicenseKeyCodec.validate(key);

        if (result.isValid()) {
            statusLabel.setText("Valid until " + result.expiryDate().format(DATE_FMT));
            statusLabel.setForeground(new Color(80, 180, 100));

            // Save to config
            LicenseConfig config = new LicenseConfig();
            config.setLicenseKey(key);
            config.save();

            activated = true;

            // Brief pause so user sees the success message
            Timer timer = new Timer(600, e -> dispose());
            timer.setRepeats(false);
            timer.start();
        } else if (result.isExpired()) {
            statusLabel.setText("License expired on " + result.expiryDate().format(DATE_FMT));
            statusLabel.setForeground(new Color(220, 80, 80));
        } else {
            statusLabel.setText(result.message());
            statusLabel.setForeground(new Color(220, 80, 80));
        }
    }

    public boolean isActivated() {
        return activated;
    }

    /**
     * Show the license dialog and return true if activation succeeded.
     */
    public static boolean showDialog(Window owner) {
        LicenseDialog dialog = new LicenseDialog(owner);
        dialog.setVisible(true); // blocks until disposed
        return dialog.isActivated();
    }

    /**
     * Document filter that auto-formats input as XXXX-XXXX-XXXX-XXXX.
     * Accepts only valid base32 characters and inserts dashes automatically.
     */
    private static class LicenseKeyFilter extends DocumentFilter {
        private static final String VALID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null) {
                super.replace(fb, offset, length, text, attrs);
                return;
            }

            // Get current text, apply the replacement, then reformat
            Document doc = fb.getDocument();
            String current = doc.getText(0, doc.getLength());
            String before = current.substring(0, offset);
            String after = current.substring(offset + length);
            String raw = (before + text + after).replace("-", "").toUpperCase();

            // Filter to only valid characters
            StringBuilder filtered = new StringBuilder();
            for (char c : raw.toCharArray()) {
                if (VALID_CHARS.indexOf(c) >= 0 && filtered.length() < 16) {
                    filtered.append(c);
                }
            }

            // Format with dashes
            String formatted = formatWithDashes(filtered.toString());

            // Replace entire document content
            fb.remove(0, doc.getLength());
            fb.insertString(0, formatted, attrs);
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                throws BadLocationException {
            replace(fb, offset, 0, text, attrs);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            Document doc = fb.getDocument();
            String current = doc.getText(0, doc.getLength());
            String before = current.substring(0, offset);
            String after = current.substring(offset + length);
            String raw = (before + after).replace("-", "");

            String formatted = formatWithDashes(raw);
            fb.remove(0, doc.getLength());
            fb.insertString(0, formatted, null);
        }

        private String formatWithDashes(String raw) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                if (i > 0 && i % 4 == 0) sb.append('-');
                sb.append(raw.charAt(i));
            }
            return sb.toString();
        }
    }
}
