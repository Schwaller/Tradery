package com.tradery.news.ui;

import com.tradery.news.ai.AiClient;
import com.tradery.news.ai.AiProfile;
import com.tradery.news.fetch.RssFetcher;
import com.tradery.news.ui.coin.CoinEntity;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.ui.settings.SettingsDialog;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Settings dialog for the Intelligence app.
 * Extends the shared base for header, Appearance section, and button bar.
 * Adds News Sources, AI Provider, and ERD Rendering sections.
 */
public class IntelSettingsDialog extends SettingsDialog {

    public IntelSettingsDialog(Window owner) {
        super(owner);
    }

    private EntityStore getEntityStore() {
        return (getOwner() instanceof IntelFrame frame) ? frame.getEntityStore() : null;
    }

    @Override
    protected List<SectionEntry> addSections() {
        return List.of(
            new SectionEntry("News Sources", createNewsSourcesContent()),
            new SectionEntry("AI Profiles", createAiProfilesContent()),
            new SectionEntry("ERD Rendering", createErdContent())
        );
    }

    // --- News Sources ---

    private JPanel createNewsSourcesContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));

        IntelConfig config0 = IntelConfig.get();
        DefaultListModel<RssFetcher> listModel = new DefaultListModel<>();
        JList<RssFetcher> sourceList = new JList<>(listModel);
        sourceList.setFixedCellHeight(-1);
        sourceList.setCellRenderer(new ListCellRenderer<>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends RssFetcher> list, RssFetcher fetcher,
                                                           int index, boolean isSelected, boolean cellHasFocus) {
                JPanel cell = new JPanel(new BorderLayout(6, 0));
                cell.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
                cell.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

                JCheckBox cb = new JCheckBox();
                cb.setSelected(!config0.isFeedDisabled(fetcher.getSourceId()));
                cb.setOpaque(false);
                cell.add(cb, BorderLayout.WEST);

                JPanel textPanel = new JPanel(new BorderLayout());
                textPanel.setOpaque(false);

                JLabel nameLabel = new JLabel(fetcher.getSourceName());
                boolean disabled = config0.isFeedDisabled(fetcher.getSourceId());
                nameLabel.setForeground(disabled
                    ? UIManager.getColor("Label.disabledForeground")
                    : (isSelected ? list.getSelectionForeground() : list.getForeground()));
                textPanel.add(nameLabel, BorderLayout.NORTH);

                JLabel urlLabel = new JLabel(fetcher.getFeedUrl());
                urlLabel.setFont(urlLabel.getFont().deriveFont(urlLabel.getFont().getSize2D() - 1f));
                urlLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                textPanel.add(urlLabel, BorderLayout.SOUTH);

                cell.add(textPanel, BorderLayout.CENTER);

                return cell;
            }
        });
        sourceList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = sourceList.locationToIndex(e.getPoint());
                if (index < 0) return;
                Rectangle bounds = sourceList.getCellBounds(index, index);
                if (bounds == null) return;
                // Toggle if click is within checkbox area (first ~30px)
                if (e.getX() - bounds.x < 30) {
                    RssFetcher fetcher = listModel.get(index);
                    String id = fetcher.getSourceId();
                    config0.setFeedDisabled(id, !config0.isFeedDisabled(id));
                    config0.save();
                    sourceList.repaint();
                }
            }
        });

        Runnable loadSources = () -> {
            listModel.clear();
            for (RssFetcher fetcher : RssFetcher.defaultSources()) {
                listModel.addElement(fetcher);
            }
            if (getEntityStore() != null) {
                for (CoinEntity entity : getEntityStore().loadEntitiesBySource("manual")) {
                    if (entity.type() == CoinEntity.Type.NEWS_SOURCE && entity.symbol() != null) {
                        listModel.addElement(new RssFetcher(
                            entity.id().replace("rss-", ""),
                            entity.name(),
                            entity.symbol()
                        ));
                    }
                }
            }
        };
        loadSources.run();

        JScrollPane scroll = new JScrollPane(sourceList);
        scroll.setPreferredSize(new Dimension(0, 180));
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        panel.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton addBtn = new JButton("Add Feed...");
        JButton removeBtn = new JButton("Remove");
        JButton resetBtn = new JButton("Reset to Defaults");

        addBtn.addActionListener(e -> {
            JPanel form = new JPanel(new GridLayout(2, 2, 5, 5));
            JTextField nameField = new JTextField();
            JTextField urlField = new JTextField();
            form.add(new JLabel("Name:"));
            form.add(nameField);
            form.add(new JLabel("RSS URL:"));
            form.add(urlField);

            int result = JOptionPane.showConfirmDialog(this, form, "Add RSS Feed",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION && getEntityStore() != null) {
                String name = nameField.getText().trim();
                String url = urlField.getText().trim();
                if (name.isEmpty() || url.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "All fields are required.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // Check for duplicate URL
                for (int i = 0; i < listModel.getSize(); i++) {
                    if (listModel.get(i).getFeedUrl().equalsIgnoreCase(url)) {
                        JOptionPane.showMessageDialog(this, "A feed with this URL already exists.", "Duplicate", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }
                String id = hashUrl(url);
                getEntityStore().saveEntity(new CoinEntity("rss-" + id, name, url, CoinEntity.Type.NEWS_SOURCE), "manual");
                loadSources.run();
            }
        });

        removeBtn.addActionListener(e -> {
            RssFetcher selected = sourceList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a feed to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String entityId = "rss-" + selected.getSourceId();
            if (getEntityStore() == null || !getEntityStore().entityExists(entityId)) {
                JOptionPane.showMessageDialog(this, "Built-in feeds cannot be removed.", "Cannot Remove", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int result = JOptionPane.showConfirmDialog(this,
                "Remove RSS feed '" + selected.getSourceName() + "'?",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                getEntityStore().deleteEntity(entityId);
                loadSources.run();
            }
        });

        resetBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                "Reset RSS feeds to factory defaults?\nThis will remove any custom feeds.",
                "Reset to Defaults", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION && getEntityStore() != null) {
                for (CoinEntity entity : getEntityStore().loadEntitiesBySource("manual")) {
                    if (entity.type() == CoinEntity.Type.NEWS_SOURCE) {
                        getEntityStore().deleteEntity(entity.id());
                    }
                }
                loadSources.run();
            }
        });

        buttonPanel.add(addBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(resetBtn);

        // Update interval
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);

        JPanel intervalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        intervalPanel.add(new JLabel("Update interval:"));

        String[] intervals = {"Manual only", "2 minutes", "3 minutes", "5 minutes", "15 minutes", "30 minutes", "1 hour"};
        int[] intervalValues = {0, 2, 3, 5, 15, 30, 60};
        JComboBox<String> intervalCombo = new JComboBox<>(intervals);

        IntelConfig config = IntelConfig.get();
        int currentInterval = config.getFetchIntervalMinutes();
        for (int i = 0; i < intervalValues.length; i++) {
            if (intervalValues[i] == currentInterval) {
                intervalCombo.setSelectedIndex(i);
                break;
            }
        }
        intervalCombo.addActionListener(e -> {
            int idx = intervalCombo.getSelectedIndex();
            if (idx >= 0 && idx < intervalValues.length) {
                config.setFetchIntervalMinutes(intervalValues[idx]);
                config.save();
                if (getOwner() instanceof IntelFrame frame) {
                    frame.updateAutoFetchTimer();
                }
            }
        });
        intervalPanel.add(intervalCombo);
        bottomPanel.add(intervalPanel, BorderLayout.SOUTH);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    // --- AI Provider ---

    private JPanel createAiContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        IntelConfig config = IntelConfig.get();

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(4, 0, 4, 8);

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = new Insets(4, 0, 4, 0);

        int row = 0;

        // Provider combo
        labelGbc.gridx = 0; labelGbc.gridy = row;
        panel.add(new JLabel("Provider:"), labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JComboBox<IntelConfig.AiProvider> providerCombo = new JComboBox<>(IntelConfig.AiProvider.values());
        providerCombo.setSelectedItem(config.getAiProvider());
        panel.add(providerCombo, fieldGbc);

        // Claude path
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel claudeLabel = new JLabel("Claude CLI path:");
        panel.add(claudeLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField claudeField = new JTextField(config.getClaudePath());
        panel.add(claudeField, fieldGbc);

        // Claude args
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel claudeArgsLabel = new JLabel("Claude CLI args:");
        panel.add(claudeArgsLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField claudeArgsField = new JTextField(config.getClaudeArgs());
        claudeArgsField.setToolTipText("CLI args. Prompt sent via stdin.");
        panel.add(claudeArgsField, fieldGbc);

        // Codex path
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel codexLabel = new JLabel("Codex CLI path:");
        panel.add(codexLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField codexField = new JTextField(config.getCodexPath());
        panel.add(codexField, fieldGbc);

        // Codex args
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel codexArgsLabel = new JLabel("Codex CLI args:");
        panel.add(codexArgsLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField codexArgsField = new JTextField(config.getCodexArgs());
        codexArgsField.setToolTipText("CLI args. Prompt appended as last argument.");
        panel.add(codexArgsField, fieldGbc);

        // Custom command
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel customLabel = new JLabel("Custom command:");
        panel.add(customLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField customField = new JTextField(config.getCustomCommand());
        customField.setToolTipText("Full command with args. Prompt appended as last argument.");
        panel.add(customField, fieldGbc);

        // Gemini API key
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel geminiKeyLabel = new JLabel("API Key:");
        panel.add(geminiKeyLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JPasswordField geminiKeyField = new JPasswordField(config.getGeminiApiKey());
        geminiKeyField.setToolTipText("Free API key from aistudio.google.com");
        panel.add(geminiKeyField, fieldGbc);

        // Gemini model
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel geminiModelLabel = new JLabel("Model:");
        panel.add(geminiModelLabel, labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JComboBox<String> geminiModelCombo = new JComboBox<>(new String[]{
            "gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.0-flash"
        });
        geminiModelCombo.setSelectedItem(config.getGeminiModel());
        panel.add(geminiModelCombo, fieldGbc);

        // Gemini help text
        labelGbc.gridx = 0; labelGbc.gridy = row;
        panel.add(new JLabel(), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JLabel geminiHelpLabel = new JLabel("<html><small>Free API key from aistudio.google.com - no credit card needed</small></html>");
        geminiHelpLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(geminiHelpLabel, fieldGbc);

        // Timeout
        labelGbc.gridx = 0; labelGbc.gridy = row;
        panel.add(new JLabel("Timeout (seconds):"), labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JSpinner timeoutSpinner = new JSpinner(new SpinnerNumberModel(config.getAiTimeoutSeconds(), 10, 300, 10));
        panel.add(timeoutSpinner, fieldGbc);

        // Visibility updater
        Runnable updateVisibility = () -> {
            IntelConfig.AiProvider selected = (IntelConfig.AiProvider) providerCombo.getSelectedItem();
            claudeLabel.setVisible(selected == IntelConfig.AiProvider.CLAUDE);
            claudeField.setVisible(selected == IntelConfig.AiProvider.CLAUDE);
            claudeArgsLabel.setVisible(selected == IntelConfig.AiProvider.CLAUDE);
            claudeArgsField.setVisible(selected == IntelConfig.AiProvider.CLAUDE);
            codexLabel.setVisible(selected == IntelConfig.AiProvider.CODEX);
            codexField.setVisible(selected == IntelConfig.AiProvider.CODEX);
            codexArgsLabel.setVisible(selected == IntelConfig.AiProvider.CODEX);
            codexArgsField.setVisible(selected == IntelConfig.AiProvider.CODEX);
            customLabel.setVisible(selected == IntelConfig.AiProvider.CUSTOM);
            customField.setVisible(selected == IntelConfig.AiProvider.CUSTOM);
            geminiKeyLabel.setVisible(selected == IntelConfig.AiProvider.GEMINI);
            geminiKeyField.setVisible(selected == IntelConfig.AiProvider.GEMINI);
            geminiModelLabel.setVisible(selected == IntelConfig.AiProvider.GEMINI);
            geminiModelCombo.setVisible(selected == IntelConfig.AiProvider.GEMINI);
            geminiHelpLabel.setVisible(selected == IntelConfig.AiProvider.GEMINI);
        };
        providerCombo.addActionListener(e -> updateVisibility.run());
        updateVisibility.run();

        // Test + Save buttons
        labelGbc.gridx = 0; labelGbc.gridy = row;
        panel.add(new JLabel(), labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton testBtn = new JButton("Test Connection");
        JButton saveBtn = new JButton("Save");

        // Test log area
        labelGbc.gridx = 0; labelGbc.gridy = row;
        panel.add(new JLabel(), labelGbc);

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextArea testLogArea = new JTextArea(5, 40);
        testLogArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        testLogArea.setEditable(false);
        testLogArea.setLineWrap(true);
        testLogArea.setVisible(false);
        JScrollPane testLogScroll = new JScrollPane(testLogArea);
        testLogScroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        testLogScroll.setVisible(false);
        panel.add(testLogScroll, fieldGbc);

        testBtn.addActionListener(e -> {
            testLogArea.setVisible(true);
            testLogScroll.setVisible(true);
            testLogArea.setText("");
            panel.revalidate();

            IntelConfig.AiProvider selectedProvider = (IntelConfig.AiProvider) providerCombo.getSelectedItem();
            testAiConnection(selectedProvider,
                claudeField.getText().trim(), claudeArgsField.getText().trim(),
                codexField.getText().trim(), codexArgsField.getText().trim(),
                customField.getText().trim(),
                new String(geminiKeyField.getPassword()).trim(),
                (String) geminiModelCombo.getSelectedItem(),
                testLogArea, testBtn);
        });

        saveBtn.addActionListener(e -> {
            config.setAiProvider((IntelConfig.AiProvider) providerCombo.getSelectedItem());
            config.setClaudePath(claudeField.getText().trim());
            config.setClaudeArgs(claudeArgsField.getText().trim());
            config.setCodexPath(codexField.getText().trim());
            config.setCodexArgs(codexArgsField.getText().trim());
            config.setCustomCommand(customField.getText().trim());
            config.setGeminiApiKey(new String(geminiKeyField.getPassword()).trim());
            config.setGeminiModel((String) geminiModelCombo.getSelectedItem());
            config.setAiTimeoutSeconds((Integer) timeoutSpinner.getValue());
            config.save();
            JOptionPane.showMessageDialog(this, "AI settings saved.", "Saved", JOptionPane.INFORMATION_MESSAGE);
        });

        buttonRow.add(testBtn);
        buttonRow.add(saveBtn);
        panel.add(buttonRow, fieldGbc);

        return panel;
    }

    private void testAiConnection(IntelConfig.AiProvider provider,
                                   String claudePath, String claudeArgs,
                                   String codexPath, String codexArgs,
                                   String customCommand, String geminiApiKey, String geminiModel,
                                   JTextArea logArea, JButton testBtn) {
        testBtn.setEnabled(false);

        Consumer<String> log = msg -> SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });

        IntelConfig config = IntelConfig.get();
        IntelConfig.AiProvider origProvider = config.getAiProvider();
        String origClaudePath = config.getClaudePath();
        String origClaudeArgs = config.getClaudeArgs();
        String origCodexPath = config.getCodexPath();
        String origCodexArgs = config.getCodexArgs();
        String origCustomCommand = config.getCustomCommand();
        String origGeminiKey = config.getGeminiApiKey();
        String origGeminiModel = config.getGeminiModel();

        config.setAiProvider(provider);
        config.setClaudePath(claudePath);
        config.setClaudeArgs(claudeArgs);
        config.setCodexPath(codexPath);
        config.setCodexArgs(codexArgs);
        config.setCustomCommand(customCommand);
        config.setGeminiApiKey(geminiApiKey);
        config.setGeminiModel(geminiModel);

        new Thread(() -> {
            try {
                if (provider == IntelConfig.AiProvider.GEMINI) {
                    testGeminiConnection(log, geminiApiKey, geminiModel);
                } else {
                    testCliConnection(provider, log, claudePath, claudeArgs, codexPath, codexArgs, customCommand);
                }
            } catch (Exception e) {
                log.accept("Error: " + e.getMessage());
            } finally {
                config.setAiProvider(origProvider);
                config.setClaudePath(origClaudePath);
                config.setClaudeArgs(origClaudeArgs);
                config.setCodexPath(origCodexPath);
                config.setCodexArgs(origCodexArgs);
                config.setCustomCommand(origCustomCommand);
                config.setGeminiApiKey(origGeminiKey);
                config.setGeminiModel(origGeminiModel);
                SwingUtilities.invokeLater(() -> testBtn.setEnabled(true));
            }
        }).start();
    }

    private void testGeminiConnection(Consumer<String> log, String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            log.accept("API key not configured");
            return;
        }
        log.accept("Testing Gemini API (" + model + ")...");
        try {
            AiClient client = AiClient.getInstance();
            if (!client.isAvailable()) {
                log.accept("API key invalid or cannot reach Gemini API");
                return;
            }
            log.accept("API key valid. Sending test query...");
            String response = client.query("Say OK");
            log.accept("Response: " + response.trim());
            log.accept("Connection working!");
        } catch (Exception e) {
            log.accept("Error: " + e.getMessage());
        }
    }

    private void testCliConnection(IntelConfig.AiProvider provider, Consumer<String> log,
                                    String claudePath, String claudeArgs,
                                    String codexPath, String codexArgs, String customCommand) {
        String cliPath = switch (provider) {
            case CLAUDE -> claudePath;
            case CODEX -> codexPath;
            case CUSTOM -> (customCommand != null && !customCommand.isBlank()) ? customCommand.split("\\s+")[0] : "";
            default -> "";
        };

        if (cliPath == null || cliPath.isBlank()) {
            log.accept("Path/command not configured");
            return;
        }

        try {
            log.accept("Checking CLI: " + cliPath + " --version");
            ProcessBuilder versionPb = new ProcessBuilder(cliPath, "--version");
            versionPb.redirectErrorStream(true);
            Process p = versionPb.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) log.accept("  " + line);
            }
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.accept("CLI timed out");
                return;
            }
            if (p.exitValue() != 0) {
                log.accept("CLI not found or returned error");
                return;
            }
            log.accept("CLI found. Sending test query...");

            java.util.List<String> cmd = new java.util.ArrayList<>();
            boolean useStdin = false;
            String testPrompt = "Respond with exactly: OK";

            switch (provider) {
                case CLAUDE -> {
                    cmd.add(claudePath);
                    if (claudeArgs != null && !claudeArgs.isBlank())
                        cmd.addAll(java.util.Arrays.asList(claudeArgs.split("\\s+")));
                    useStdin = true;
                }
                case CODEX -> {
                    cmd.add(codexPath);
                    if (codexArgs != null && !codexArgs.isBlank())
                        cmd.addAll(java.util.Arrays.asList(codexArgs.split("\\s+")));
                    cmd.add(testPrompt);
                }
                case CUSTOM -> {
                    if (customCommand != null && !customCommand.isBlank())
                        cmd.addAll(java.util.Arrays.asList(customCommand.split("\\s+")));
                    cmd.add(testPrompt);
                }
                default -> {}
            }

            ProcessBuilder testPb = new ProcessBuilder(cmd);
            testPb.redirectErrorStream(true);
            Process testProcess = testPb.start();

            if (useStdin) {
                try (var stdin = testProcess.getOutputStream()) {
                    stdin.write(testPrompt.getBytes());
                    stdin.flush();
                }
            }

            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(testProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) log.accept("  " + line);
            }

            if (!testProcess.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                testProcess.destroyForcibly();
                log.accept("Timeout after 60s");
                return;
            }

            log.accept(testProcess.exitValue() == 0 ? "Connection working!" : "CLI returned error (exit " + testProcess.exitValue() + ")");
        } catch (Exception e) {
            log.accept("Error: " + e.getMessage());
        }
    }

    // --- ERD Rendering ---

    private JPanel createErdContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        IntelConfig config = IntelConfig.get();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;

        JCheckBox flowModeCheck = new JCheckBox("Flow mode (relationship connections widen through boxes)");
        flowModeCheck.setSelected(config.isErdFlowMode());
        flowModeCheck.addActionListener(e -> {
            config.setErdFlowMode(flowModeCheck.isSelected());
            config.save();
        });
        panel.add(flowModeCheck, gbc);

        return panel;
    }

    private static String hashUrl(String url) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }
}
