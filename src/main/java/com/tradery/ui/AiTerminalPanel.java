package com.tradery.ui;

import com.jediterm.pty.PtyProcessTtyConnector;
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
        setBackground(new Color(30, 30, 30));

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
     * Find JediTerm's internal scrollbar and make it look more native.
     */
    private void customizeScrollbar(JediTermWidget widget) {
        // JediTerm uses a custom scrollbar - find it and customize
        for (Component comp : widget.getComponents()) {
            if (comp instanceof JScrollBar scrollBar) {
                // Use system default UI for native look
                scrollBar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
                    @Override
                    protected void configureScrollBarColors() {
                        this.thumbColor = new Color(100, 100, 100);
                        this.thumbDarkShadowColor = new Color(70, 70, 70);
                        this.thumbHighlightColor = new Color(120, 120, 120);
                        this.thumbLightShadowColor = new Color(90, 90, 90);
                        this.trackColor = new Color(45, 45, 45);
                        this.trackHighlightColor = new Color(50, 50, 50);
                    }

                    @Override
                    protected JButton createDecreaseButton(int orientation) {
                        return createZeroButton();
                    }

                    @Override
                    protected JButton createIncreaseButton(int orientation) {
                        return createZeroButton();
                    }

                    private JButton createZeroButton() {
                        JButton button = new JButton();
                        button.setPreferredSize(new Dimension(0, 0));
                        button.setMinimumSize(new Dimension(0, 0));
                        button.setMaximumSize(new Dimension(0, 0));
                        return button;
                    }
                });
                scrollBar.setPreferredSize(new Dimension(12, 0));
            }
        }
    }

    /**
     * Start an AI CLI process (claude or codex) with the given initial prompt.
     */
    public void startAi(String aiType, String workingDir, String initialPrompt) {
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
            String[] command;
            if ("claude".equals(aiType)) {
                // Pre-approve edit/write permissions for strategy files
                String traderyPath = System.getProperty("user.home") + "/.tradery";
                String allowedTools = String.format(
                    "Edit:%s/**,Write:%s/**,Read:%s/**",
                    traderyPath, traderyPath, traderyPath
                );
                command = new String[]{"claude", "--allowedTools", allowedTools};
            } else {
                // Codex or other AI - just use the command directly
                command = new String[]{aiType};
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

            // Send initial prompt after delay (wait for AI to fully initialize)
            if (initialPrompt != null && !initialPrompt.isEmpty()) {
                Thread promptSender = new Thread(() -> {
                    try {
                        Thread.sleep(2000);  // Wait 2 seconds for AI to be ready
                        connector.write(initialPrompt);
                        Thread.sleep(500);   // Pause before enter
                        connector.write("\r");  // Try carriage return
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                promptSender.setDaemon(true);
                promptSender.start();
            }

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
     * Custom settings provider for dark theme terminal.
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

        // Note: getDefaultBackground/getDefaultForeground may not exist in older API versions
        // Colors are handled via terminal color scheme instead
    }
}
