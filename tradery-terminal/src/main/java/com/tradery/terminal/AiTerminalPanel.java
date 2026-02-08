package com.tradery.terminal;

import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Embedded AI terminal panel using JediTerm for full terminal emulation.
 * Provides pixel-perfect rendering of Claude CLI's TUI.
 */
public class AiTerminalPanel extends JPanel {

    private JediTermWidget terminalWidget;
    private PtyProcess ptyProcess;
    private volatile boolean isRunning = false;

    // Callbacks
    private Runnable onFileChange;

    public AiTerminalPanel() {
        setLayout(new BorderLayout());

        // Create terminal widget with custom settings
        terminalWidget = new JediTermWidget(new AiTerminalSettingsProvider());

        // Try to find and customize the scrollbar to look more native
        customizeScrollbar(terminalWidget);

        // Add click-to-focus behavior
        terminalWidget.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                grabFocus();
            }
        });

        add(terminalWidget, BorderLayout.CENTER);
    }

    /**
     * Request focus on the terminal widget.
     */
    public void grabFocus() {
        SwingUtilities.invokeLater(() -> {
            terminalWidget.requestFocusInWindow();
            terminalWidget.getTerminalPanel().requestFocusInWindow();
        });
    }

    /**
     * Find JediTerm's internal scrollbar and let FlatLaf style it.
     */
    private void customizeScrollbar(JediTermWidget widget) {
        for (Component comp : widget.getComponents()) {
            if (comp instanceof JScrollBar scrollBar) {
                scrollBar.setPreferredSize(new Dimension(12, 0));
            }
        }
    }

    /**
     * Start an AI CLI process (claude or codex).
     */
    public void startAi(String aiType, String workingDir) {
        if (isRunning) {
            return;
        }

        try {
            // Set up environment
            Map<String, String> env = new HashMap<>(System.getenv());
            env.put("TERM", "xterm-256color");
            env.put("COLORTERM", "truecolor");
            env.put("LC_ALL", "en_US.UTF-8");
            env.put("LANG", "en_US.UTF-8");

            // Build command based on AI type
            // Use login shell to ensure full PATH is available (npm, homebrew, etc.)
            String shell = System.getenv("SHELL");
            if (shell == null || shell.isEmpty()) {
                shell = "/bin/zsh";  // Default on macOS
            }

            // Start interactive login shell - we'll send the command after it starts
            // This ensures we see any error messages if the command fails
            String[] command = new String[]{shell, "-l", "-i"};

            // Build the actual AI command to send after shell starts
            String aiCommand;
            if ("claude".equals(aiType)) {
                // Pre-approve permissions for strategy files AND MCP tools
                String traderyPath = System.getProperty("user.home") + "/.tradery";
                String allowedTools = String.format(
                    "Edit:%s/**,Write:%s/**,Read:%s/**,mcp__tradery__*",
                    traderyPath, traderyPath, traderyPath
                );
                aiCommand = "claude --allowedTools '" + allowedTools + "' --append-system-prompt 'On session start, immediately call tradery_get_context and briefly summarize the focused strategy and its key metrics. Do not list all strategies.'";
            } else if ("codex".equals(aiType)) {
                aiCommand = "codex 'Read CODEX.md for session startup instructions, then follow them'";
            } else {
                aiCommand = aiType;
            }

            // Build PTY process
            PtyProcessBuilder builder = new PtyProcessBuilder()
                .setCommand(command)
                .setDirectory(workingDir)
                .setEnvironment(env)
                .setConsole(false)
                .setCygwin(false);

            ptyProcess = builder.start();
            isRunning = true;

            // Create TtyConnector for JediTerm
            TtyConnector connector = new PtyProcessTtyConnector(ptyProcess, StandardCharsets.UTF_8);

            // Start the terminal session
            terminalWidget.createTerminalSession(connector);
            terminalWidget.start();

            // Request focus on the terminal widget
            SwingUtilities.invokeLater(() -> {
                terminalWidget.requestFocusInWindow();
                terminalWidget.getTerminalPanel().requestFocusInWindow();
            });

            // Send AI command after shell starts, then send initial prompt once AI is ready
            final String finalAiCommand = aiCommand;
            final boolean isClaude = "claude".equals(aiType);
            Thread commandSender = new Thread(() -> {
                try {
                    // Wait for shell to fully initialize (prompt rendered)
                    Thread.sleep(1000);

                    // Clear shell startup noise, then send the AI command
                    connector.write("clear && " + finalAiCommand);
                    Thread.sleep(100);
                    connector.write("\n");

                    // For Claude: wait for it to start, then send initial prompt
                    if (isClaude) {
                        Thread.sleep(4000);
                        String initialPrompt = "Call tradery_get_context and briefly summarize what strategy is focused and its key metrics. Do not list all strategies.";
                        connector.write(initialPrompt);
                        Thread.sleep(100);
                        connector.write("\n");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            commandSender.setDaemon(true);
            commandSender.start();

            // Monitor process completion
            Thread monitor = new Thread(() -> {
                try {
                    ptyProcess.waitFor();
                    SwingUtilities.invokeLater(() -> {
                        isRunning = false;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            monitor.setDaemon(true);
            monitor.start();

        } catch (Exception e) {
            e.printStackTrace();
            isRunning = false;
        }
    }

    public void stopProcess() {
        if (ptyProcess != null && ptyProcess.isAlive()) {
            ptyProcess.destroyForcibly();
            isRunning = false;
        }
    }

    /**
     * Stop the current process and reset the terminal to a fresh state.
     * This clears all scrollback history and creates a new terminal widget.
     */
    public void resetTerminal() {
        stopProcess();

        // Remove and dispose old widget
        if (terminalWidget != null) {
            remove(terminalWidget);
            terminalWidget.close();
        }

        // Create fresh terminal widget
        terminalWidget = new JediTermWidget(new AiTerminalSettingsProvider());
        customizeScrollbar(terminalWidget);

        // Re-add click-to-focus behavior
        terminalWidget.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                grabFocus();
            }
        });

        add(terminalWidget, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Restart the AI with a fresh terminal - clears all history and starts anew.
     */
    public void restartAi(String aiType, String workingDir) {
        resetTerminal();
        startAi(aiType, workingDir);
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setOnFileChange(Runnable callback) {
        this.onFileChange = callback;
    }

    public void dispose() {
        stopProcess();
        if (terminalWidget != null) {
            terminalWidget.close();
        }
    }

    /**
     * Custom settings provider that reads background/foreground from UIManager
     * so the terminal adapts to the current FlatLaf theme.
     */
    private static class AiTerminalSettingsProvider extends DefaultSettingsProvider {

        @Override
        public float getTerminalFontSize() {
            return 13.0f;
        }

        @Override
        public Font getTerminalFont() {
            return new Font(Font.MONOSPACED, Font.PLAIN, 13);
        }

        @Override
        public TerminalColor getDefaultForeground() {
            Color fg = UIManager.getColor("Panel.foreground");
            if (fg == null) fg = new Color(204, 204, 204);
            return TerminalColor.rgb(fg.getRed(), fg.getGreen(), fg.getBlue());
        }

        @Override
        public TerminalColor getDefaultBackground() {
            Color bg = UIManager.getColor("Panel.background");
            if (bg == null) bg = new Color(50, 49, 48);
            return TerminalColor.rgb(bg.getRed(), bg.getGreen(), bg.getBlue());
        }
    }
}
