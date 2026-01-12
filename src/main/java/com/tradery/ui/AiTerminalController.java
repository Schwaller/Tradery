package com.tradery.ui;

import com.tradery.io.WindowStateStore;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Controls the AI terminal (Claude/Codex) integration in ProjectWindow.
 * Handles launching, docking/undocking, and switching between AI assistants.
 */
public class AiTerminalController {

    private final JFrame parentFrame;
    private final Runnable onBacktest;
    private final Consumer<String> onStatus;

    private AiTerminalFrame aiTerminalFrame;
    private AiTerminalPanel dockedTerminalPanel;
    private JPanel dockedTerminalWrapper;
    private JLabel terminalTitleLabel;
    private JSplitPane editorTerminalSplit;
    private boolean terminalDocked = true;
    private String currentAiType = null;  // "claude" or "codex" or null

    public AiTerminalController(JFrame parentFrame, Runnable onBacktest, Consumer<String> onStatus) {
        this.parentFrame = parentFrame;
        this.onBacktest = onBacktest;
        this.onStatus = onStatus;
    }

    /**
     * Initialize the docked terminal panel and wrapper.
     */
    public void initializeDockedTerminal(JSplitPane editorTerminalSplit) {
        this.editorTerminalSplit = editorTerminalSplit;

        dockedTerminalPanel = new AiTerminalPanel();
        dockedTerminalPanel.setOnFileChange(onBacktest);

        // Terminal wrapper with header (undock button)
        dockedTerminalWrapper = new JPanel(new BorderLayout(0, 0));
        dockedTerminalWrapper.setVisible(false);

        JPanel terminalHeader = new JPanel(new BorderLayout());
        terminalHeader.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        terminalTitleLabel = new JLabel("AI Terminal");
        terminalTitleLabel.setFont(terminalTitleLabel.getFont().deriveFont(Font.BOLD, 11f));
        JButton undockBtn = new JButton("Undock");
        undockBtn.setFont(undockBtn.getFont().deriveFont(10f));
        undockBtn.setMargin(new Insets(1, 4, 1, 4));
        undockBtn.addActionListener(e -> undockTerminal());
        terminalHeader.add(terminalTitleLabel, BorderLayout.WEST);
        terminalHeader.add(undockBtn, BorderLayout.EAST);

        dockedTerminalWrapper.add(terminalHeader, BorderLayout.NORTH);
        dockedTerminalWrapper.add(dockedTerminalPanel, BorderLayout.CENTER);

        editorTerminalSplit.setBottomComponent(dockedTerminalWrapper);
    }

    public JPanel getDockedTerminalWrapper() {
        return dockedTerminalWrapper;
    }

    public AiTerminalPanel getDockedTerminalPanel() {
        return dockedTerminalPanel;
    }

    /**
     * Check if a command is available on the system PATH.
     */
    public boolean isCommandAvailable(String command) {
        // Method 1: Use login shell to get full PATH (includes ~/.zshrc, ~/.bashrc paths)
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-l", "-c", "which " + command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) return true;
        } catch (Exception ignored) {}

        // Method 2: Try zsh login shell (default on macOS)
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/zsh", "-l", "-c", "which " + command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) return true;
        } catch (Exception ignored) {}

        // Method 3: Check common installation paths directly
        String[] commonPaths = {
            "/usr/local/bin/" + command,
            "/opt/homebrew/bin/" + command,
            System.getProperty("user.home") + "/.local/bin/" + command,
            System.getProperty("user.home") + "/.npm-global/bin/" + command,
            "/usr/bin/" + command
        };
        for (String path : commonPaths) {
            if (new File(path).exists()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Open a URL in the default browser.
     */
    public void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            onStatus.accept("Could not open browser: " + e.getMessage());
        }
    }

    /**
     * Open Claude terminal for the given strategy.
     */
    public void openClaudeTerminal(String strategyId, String strategyName, String symbol,
                                   String timeframe, String duration) {
        if (!isCommandAvailable("claude")) {
            int result = JOptionPane.showConfirmDialog(parentFrame,
                "Claude Code CLI is not installed.\n\n" +
                "Would you like to open the installation instructions?",
                "Claude Code Not Found",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                openUrl("https://docs.anthropic.com/en/docs/claude-code");
            }
            return;
        }

        String initialPrompt = String.format(
            "[Launched from Tradery app] " +
            "Currently open: Strategy '%s' (id: %s), " +
            "backtesting %s on %s timeframe for %s. " +
            "Read strategies/%s/strategy.json and strategies/%s/latest.json to understand the current setup. " +
            "Then WAIT for instructions - do not make changes until I say 'go' or give specific directions. " +
            "The app auto-reloads when you save changes to strategy.json.",
            strategyName, strategyId, symbol, timeframe, duration,
            strategyId, strategyId
        );

        openAiTerminal("claude", initialPrompt, strategyName);
    }

    /**
     * Open Codex terminal for the given strategy.
     */
    public void openCodexTerminal(String strategyId, String strategyName, String symbol,
                                  String timeframe, String duration) {
        if (!isCommandAvailable("codex")) {
            int result = JOptionPane.showConfirmDialog(parentFrame,
                "OpenAI Codex CLI is not installed.\n\n" +
                "Would you like to open the installation instructions?",
                "Codex CLI Not Found",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                openUrl("https://github.com/openai/codex");
            }
            return;
        }

        String traderyDir = System.getProperty("user.home") + "/.tradery";
        String initialPrompt = String.format(
            "[Launched from Tradery app] " +
            "Currently open: Strategy '%s' (id: %s), " +
            "backtesting %s on %s timeframe for %s. " +
            "Read strategies/%s/strategy.json and strategies/%s/latest.json. " +
            "Then WAIT for instructions - do not make changes until I say 'go' or give specific directions. " +
            "The app auto-reloads when you save changes to strategy.json.",
            strategyName, strategyId, symbol, timeframe, duration,
            strategyId, strategyId
        );

        // Open Codex in OS Terminal (embedded terminal has compatibility issues)
        openOsCodexTerminal(traderyDir, initialPrompt, strategyName);
    }

    private void openAiTerminal(String aiType, String initialPrompt, String strategyName) {
        String traderyDir = System.getProperty("user.home") + "/.tradery";
        String displayName = aiType.substring(0, 1).toUpperCase() + aiType.substring(1);

        // Check if external terminal mode is configured
        String terminalMode = WindowStateStore.getInstance().getAiTerminalMode();
        if ("external".equals(terminalMode)) {
            openOsAiTerminal(aiType, traderyDir, initialPrompt, strategyName);
            return;
        }

        // Check if a different AI is currently running
        if (currentAiType != null && !currentAiType.equals(aiType) && dockedTerminalPanel.isRunning()) {
            String currentName = currentAiType.substring(0, 1).toUpperCase() + currentAiType.substring(1);
            int result = JOptionPane.showConfirmDialog(parentFrame,
                currentName + " is currently running.\n\n" +
                "Switch to " + displayName + "? This will terminate the current session.",
                "Switch AI Assistant",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
            dockedTerminalPanel.stopProcess();
        }

        // Toggle off: if same AI is visible and running, hide and stop
        if (aiType.equals(currentAiType) && dockedTerminalWrapper.isVisible() && dockedTerminalPanel.isRunning()) {
            dockedTerminalPanel.stopProcess();
            dockedTerminalWrapper.setVisible(false);
            currentAiType = null;
            onStatus.accept(displayName + " stopped");
            return;
        }

        // Update title and tracking
        currentAiType = aiType;
        terminalTitleLabel.setText(displayName);
        if (aiTerminalFrame != null) {
            aiTerminalFrame.setTitle(displayName + " - " + strategyName);
        }

        // Show and start
        if (terminalDocked) {
            dockedTerminalWrapper.setVisible(true);
            editorTerminalSplit.setDividerLocation(0.5);
            dockedTerminalPanel.startAi(aiType, traderyDir, initialPrompt);
            dockedTerminalPanel.grabFocus();
            onStatus.accept("Opened " + displayName + " for " + strategyName);
        } else {
            if (aiTerminalFrame == null) {
                aiTerminalFrame = new AiTerminalFrame(strategyName, onBacktest, this::redockTerminal);
            }
            aiTerminalFrame.setTitle(displayName + " - " + strategyName);
            aiTerminalFrame.startAi(aiType, traderyDir, initialPrompt);
            onStatus.accept("Opened " + displayName + " for " + strategyName);
        }
    }

    private void undockTerminal() {
        if (!terminalDocked) return;

        terminalDocked = false;
        dockedTerminalWrapper.setVisible(false);

        // Create floating frame if needed
        if (aiTerminalFrame == null) {
            aiTerminalFrame = new AiTerminalFrame("AI Terminal", onBacktest, this::redockTerminal);
        }

        // Transfer the terminal panel to the frame
        aiTerminalFrame.setTerminalPanel(dockedTerminalPanel);
        aiTerminalFrame.setVisible(true);
        aiTerminalFrame.toFront();

        onStatus.accept("Undocked Claude terminal");
    }

    private void redockTerminal() {
        if (terminalDocked) return;

        terminalDocked = true;

        // Take back the terminal panel from the frame
        if (aiTerminalFrame != null) {
            aiTerminalFrame.setVisible(false);
            aiTerminalFrame.removeTerminalPanel();
        }

        // Re-add to docked wrapper
        dockedTerminalWrapper.add(dockedTerminalPanel, BorderLayout.CENTER);
        dockedTerminalWrapper.setVisible(true);
        editorTerminalSplit.setDividerLocation(0.5);
        dockedTerminalWrapper.revalidate();
        dockedTerminalWrapper.repaint();

        onStatus.accept("Redocked Claude terminal");
    }

    private void openOsAiTerminal(String aiType, String traderyDir, String initialPrompt, String strategyName) {
        String displayName = aiType.substring(0, 1).toUpperCase() + aiType.substring(1);
        String aiCommand;

        if ("claude".equals(aiType)) {
            // Claude with file access and MCP tools pre-approved
            aiCommand = String.format(
                "claude --allowedTools 'Edit:~/.tradery/**,Write:~/.tradery/**,Read:~/.tradery/**,mcp__tradery__*' '%s'",
                initialPrompt.replace("'", "'\\''")
            );
        } else {
            // Codex or other AI
            aiCommand = String.format("%s '%s'", aiType, initialPrompt.replace("'", "'\\''"));
        }

        String command = String.format(
            "cd '%s' && %s",
            traderyDir.replace("'", "'\\''"),
            aiCommand
        );

        try {
            String[] osascript = {
                "osascript", "-e",
                String.format(
                    "tell application \"Terminal\"\n" +
                    "    activate\n" +
                    "    do script \"%s\"\n" +
                    "end tell",
                    command.replace("\\", "\\\\").replace("\"", "\\\"")
                )
            };

            Runtime.getRuntime().exec(osascript);
            onStatus.accept("Opened " + displayName + " CLI for " + strategyName);
        } catch (IOException e) {
            onStatus.accept("Error opening terminal: " + e.getMessage());
            JOptionPane.showMessageDialog(parentFrame,
                "Could not open Terminal: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openOsCodexTerminal(String traderyDir, String initialPrompt, String strategyName) {
        String command = String.format(
            "cd '%s' && codex '%s'",
            traderyDir.replace("'", "'\\''"),
            initialPrompt.replace("'", "'\\''")
        );

        try {
            String[] osascript = {
                "osascript", "-e",
                String.format(
                    "tell application \"Terminal\"\n" +
                    "    activate\n" +
                    "    do script \"%s\"\n" +
                    "end tell",
                    command.replace("\\", "\\\\").replace("\"", "\\\"")
                )
            };

            Runtime.getRuntime().exec(osascript);
            onStatus.accept("Opened Codex CLI for " + strategyName);
        } catch (IOException e) {
            onStatus.accept("Error opening terminal: " + e.getMessage());
            JOptionPane.showMessageDialog(parentFrame,
                "Could not open Terminal: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Dispose terminal resources.
     */
    public void dispose() {
        if (dockedTerminalPanel != null) {
            dockedTerminalPanel.dispose();
        }
        if (aiTerminalFrame != null) {
            aiTerminalFrame.dispose();
            aiTerminalFrame = null;
        }
    }
}
