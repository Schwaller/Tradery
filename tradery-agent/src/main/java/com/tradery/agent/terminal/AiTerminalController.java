package com.tradery.agent.terminal;

import com.tradery.ai.AiConfig;
import com.tradery.ai.AiDetector;
import com.tradery.ai.AiProfile;
import com.tradery.ai.AiProvider;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Controls the AI terminal integration.
 * Handles launching, docking/undocking, and switching between AI assistants.
 * Decoupled from any specific app's persistence — callers set terminalMode externally.
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

    // Terminal mode: "integrated" or "external" — set by caller
    private String terminalMode = "integrated";

    public AiTerminalController(JFrame parentFrame, Runnable onBacktest, Consumer<String> onStatus) {
        this.parentFrame = parentFrame;
        this.onBacktest = onBacktest;
        this.onStatus = onStatus;
    }

    /**
     * Set the terminal mode ("integrated" or "external").
     * Callers wire this from their own persistence (e.g. WindowStateStore).
     */
    public void setTerminalMode(String mode) {
        this.terminalMode = mode != null ? mode : "integrated";
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
     * Open the default AI terminal using the profile from AiConfig.
     */
    public void openDefaultAiTerminal(String strategyId, String strategyName,
                                       String symbol, String timeframe, String duration) {
        AiProfile profile = AiConfig.get().getDefaultProfile();
        if (profile == null) {
            onStatus.accept("No AI profiles configured. Add one in Settings.");
            return;
        }
        openAiTerminalForProfile(profile, strategyName);
    }

    /**
     * Open AI terminal for a specific profile.
     */
    public void openAiTerminalForProfile(AiProfile profile, String strategyName) {
        AiProvider provider = profile.getProvider();
        String aiType;

        switch (provider) {
            case CLAUDE -> aiType = "claude";
            case CODEX -> aiType = "codex";
            case GEMINI -> aiType = "gemini";
            default -> aiType = profile.getCommand() != null && !profile.getCommand().isEmpty()
                ? profile.getCommand() : "claude";
        }

        // Check if the CLI is available using AiDetector
        String cliCommand = switch (provider) {
            case CLAUDE -> "claude";
            case CODEX -> "codex";
            case GEMINI -> "gemini";
            default -> null;  // Custom commands — skip check
        };

        if (cliCommand != null && !isCommandAvailable(cliCommand)) {
            String installUrl = getInstallUrl(provider);
            int result = JOptionPane.showConfirmDialog(parentFrame,
                profile.getName() + " CLI is not installed.\n\n" +
                "Would you like to open the installation instructions?",
                "CLI Not Found",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION && installUrl != null) {
                openUrl(installUrl);
            }
            return;
        }

        openAiTerminal(aiType, strategyName);
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

        openAiTerminal("claude", strategyName);
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

        // Open Codex in OS Terminal (embedded terminal has compatibility issues)
        openOsCodexTerminal(traderyDir, strategyName);
    }

    private void openAiTerminal(String aiType, String strategyName) {
        String traderyDir = System.getProperty("user.home") + "/.tradery";
        String displayName = aiType.substring(0, 1).toUpperCase() + aiType.substring(1);

        // Check if external terminal mode is configured
        if ("external".equals(terminalMode)) {
            openOsAiTerminal(aiType, traderyDir, strategyName);
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
            aiTerminalFrame.setTitleText(displayName + " \u2014 " + strategyName);
        }

        // Show and start (always use fresh terminal to clear history)
        if (terminalDocked) {
            dockedTerminalWrapper.setVisible(true);
            editorTerminalSplit.setDividerLocation(0.5);
            dockedTerminalPanel.restartAi(aiType, traderyDir);
            dockedTerminalPanel.grabFocus();
            onStatus.accept("Opened " + displayName + " for " + strategyName);
        } else {
            if (aiTerminalFrame == null) {
                aiTerminalFrame = new AiTerminalFrame(strategyName, onBacktest, this::redockTerminal);
            }
            aiTerminalFrame.setTitle(displayName + " - " + strategyName);
            aiTerminalFrame.setTitleText(displayName + " \u2014 " + strategyName);
            aiTerminalFrame.startAi(aiType, traderyDir);
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

        onStatus.accept("Undocked AI terminal");
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

        onStatus.accept("Redocked AI terminal");
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
            if (new java.io.File(path).exists()) {
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

    private String getInstallUrl(AiProvider provider) {
        return switch (provider) {
            case CLAUDE -> "https://docs.anthropic.com/en/docs/claude-code";
            case CODEX -> "https://github.com/openai/codex";
            case GEMINI -> "https://github.com/google-gemini/gemini-cli";
            default -> null;
        };
    }

    private void openOsAiTerminal(String aiType, String traderyDir, String strategyName) {
        String displayName = aiType.substring(0, 1).toUpperCase() + aiType.substring(1);
        String aiCommand;

        if ("claude".equals(aiType)) {
            // Claude with file access and MCP tools pre-approved, plus initial prompt
            String allowedTools = "Edit:~/.tradery/**,Write:~/.tradery/**,Read:~/.tradery/**," +
                    "Bash(cat ~/.tradery/**),Bash(head ~/.tradery/**),Bash(tail ~/.tradery/**),Bash(ls ~/.tradery/**)," +
                    "Bash(curl http://localhost:*)," +
                    "mcp__plaiiin__*,mcp__tradery__*";
            aiCommand = "claude --allowedTools '" + allowedTools + "' --append-system-prompt 'On session start, immediately call tradery_get_context and briefly summarize the focused strategy and its key metrics. Do not list all strategies.'";
        } else {
            // Codex or other AI
            aiCommand = aiType;
        }

        String command = String.format(
            "unset CLAUDECODE && cd '%s' && %s",
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

    private void openOsCodexTerminal(String traderyDir, String strategyName) {
        String command = String.format(
            "cd '%s' && codex 'Read CODEX.md for session startup instructions, then follow them'",
            traderyDir.replace("'", "'\\''")
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
     * Check if an AI terminal session is currently running.
     */
    public boolean isTerminalRunning() {
        if (dockedTerminalPanel != null && dockedTerminalPanel.isRunning()) return true;
        if (aiTerminalFrame != null && aiTerminalFrame.isAiRunning()) return true;
        return false;
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
