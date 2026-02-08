package com.tradery.ai;

import com.formdev.flatlaf.FlatClientProperties;
import com.tradery.ai.AiDetector.DetectedProvider;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * First-run setup dialog shown when no AI profiles exist.
 * Auto-detects available AI providers and lets the user choose which to enable.
 */
public class AiSetupDialog extends JDialog {

    private List<DetectedProvider> providers;
    private List<JCheckBox> checkboxes;
    private JPanel listPanel;
    private JButton createBtn;

    private AiSetupDialog(Window owner) {
        super(owner, "AI Setup", ModalityType.APPLICATION_MODAL);
        setSize(520, 400);
        setResizable(false);
        setLocationRelativeTo(owner);

        // macOS integrated title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        initUI();
        startDetection();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 52));
        JLabel title = new JLabel("AI Setup", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(UIManager.getColor("Label.disabledForeground"));
        header.add(title, BorderLayout.CENTER);

        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(header, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        mainPanel.add(headerWrapper, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(new EmptyBorder(16, 20, 12, 20));

        JLabel subtitle = new JLabel(
            "<html>Select which AI providers to enable:</html>");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setBorder(new EmptyBorder(0, 0, 12, 0));
        content.add(subtitle, BorderLayout.NORTH);

        // Provider list (initially shows loading spinner)
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel loadingLabel = new JLabel("Detecting available AI providers...");
        loadingLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        loadingPanel.add(loadingLabel);
        listPanel.add(loadingPanel);

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scroll, BorderLayout.CENTER);

        JLabel hint = new JLabel("Profiles can be added and configured later in Settings.");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hint.setBorder(new EmptyBorder(10, 0, 0, 0));
        content.add(hint, BorderLayout.SOUTH);

        mainPanel.add(content, BorderLayout.CENTER);

        // Button bar
        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.add(new JSeparator(), BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBorder(new EmptyBorder(10, 16, 10, 16));

        JButton skipBtn = new JButton("Skip");
        skipBtn.addActionListener(e -> dispose());
        buttons.add(skipBtn);

        createBtn = new JButton("Create Profiles");
        createBtn.setEnabled(false);
        createBtn.addActionListener(e -> createProfiles());
        buttons.add(createBtn);

        buttonBar.add(buttons, BorderLayout.CENTER);
        mainPanel.add(buttonBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void startDetection() {
        new SwingWorker<List<DetectedProvider>, Void>() {
            @Override
            protected List<DetectedProvider> doInBackground() {
                return AiDetector.detectAll();
            }

            @Override
            protected void done() {
                try {
                    providers = get();
                    showProviders();
                } catch (Exception e) {
                    providers = List.of();
                    showProviders();
                }
            }
        }.execute();
    }

    private void showProviders() {
        listPanel.removeAll();
        checkboxes = new ArrayList<>();

        for (int i = 0; i < providers.size(); i++) {
            DetectedProvider dp = providers.get(i);
            if (i > 0) {
                listPanel.add(new JSeparator());
            }
            listPanel.add(createProviderRow(dp));
        }

        createBtn.setEnabled(true);
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createProviderRow(DetectedProvider dp) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBorder(new EmptyBorder(8, 8, 8, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Checkbox
        JCheckBox cb = new JCheckBox();
        cb.setSelected(dp.detected() && !dp.requiresSetup());
        cb.setAlignmentY(Component.TOP_ALIGNMENT);
        checkboxes.add(cb);
        row.add(cb, BorderLayout.WEST);

        // Text
        JPanel textPanel = new JPanel(new BorderLayout(0, 2));
        textPanel.setOpaque(false);

        // Top row: name + status badge/button
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JLabel nameLabel = new JLabel(dp.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        topRow.add(nameLabel, BorderLayout.WEST);

        // Right side: status badge + install button for non-detected
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        statusPanel.setOpaque(false);

        JLabel statusLabel = new JLabel(getStatusText(dp));
        statusLabel.setFont(statusLabel.getFont().deriveFont(statusLabel.getFont().getSize2D() - 1f));
        statusLabel.setForeground(getStatusColor(dp));
        statusPanel.add(statusLabel);

        if (!dp.detected() && dp.installUrl() != null) {
            JButton installBtn = new JButton("Install...");
            installBtn.setFont(installBtn.getFont().deriveFont(installBtn.getFont().getSize2D() - 1f));
            installBtn.putClientProperty("JButton.buttonType", "toolBarButton");
            installBtn.addActionListener(e -> {
                try {
                    Desktop.getDesktop().browse(new URI(dp.installUrl()));
                } catch (Exception ex) {
                    // Ignore
                }
            });
            statusPanel.add(installBtn);
        }

        topRow.add(statusPanel, BorderLayout.EAST);
        textPanel.add(topRow, BorderLayout.NORTH);

        // Description
        JLabel descLabel = new JLabel(dp.description());
        descLabel.setFont(descLabel.getFont().deriveFont(descLabel.getFont().getSize2D() - 1f));
        descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        textPanel.add(descLabel, BorderLayout.SOUTH);

        row.add(textPanel, BorderLayout.CENTER);

        return row;
    }

    private String getStatusText(DetectedProvider dp) {
        if (dp.detected() && !dp.requiresSetup()) return "Detected";
        if (dp.detected() && dp.requiresSetup()) return "Needs setup";
        return "Not found";
    }

    private Color getStatusColor(DetectedProvider dp) {
        if (dp.detected() && !dp.requiresSetup()) return new Color(80, 180, 100);
        if (dp.detected() && dp.requiresSetup()) return new Color(200, 170, 60);
        return UIManager.getColor("Label.disabledForeground");
    }

    private void createProfiles() {
        AiConfig config = AiConfig.get();
        boolean firstProfile = true;

        for (int i = 0; i < checkboxes.size(); i++) {
            if (!checkboxes.get(i).isSelected()) continue;
            DetectedProvider dp = providers.get(i);

            AiProfile profile = new AiProfile();
            String id = generateProfileId(dp);
            profile.setId(id);
            profile.setName(dp.name().replaceAll("\\s+v[\\d.]+$", "").trim());
            profile.setProvider(dp.provider());

            if (dp.path() != null) profile.setPath(dp.path());
            if (dp.args() != null) profile.setArgs(dp.args());
            if (dp.command() != null) profile.setCommand(dp.command());
            if (dp.provider() == AiProvider.GEMINI) {
                profile.setModel("gemini-2.5-flash-lite");
            }

            config.addProfile(profile);

            if (firstProfile) {
                config.setDefaultProfileId(id);
                firstProfile = false;
            }
        }

        config.save();
        dispose();
    }

    private String generateProfileId(DetectedProvider dp) {
        String base = dp.name().toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
        // Ensure uniqueness
        AiConfig config = AiConfig.get();
        String id = base;
        int counter = 2;
        while (config.getProfile(id) != null) {
            id = base + "-" + counter++;
        }
        return id;
    }

    /**
     * Show the first-run AI setup dialog.
     */
    public static void showSetup(Window owner) {
        AiSetupDialog dialog = new AiSetupDialog(owner);
        dialog.setVisible(true);
    }
}
