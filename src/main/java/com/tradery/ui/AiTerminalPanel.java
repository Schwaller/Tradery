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
        add(terminalWidget, BorderLayout.CENTER);
    }

    /**
     * Start the Claude CLI process with the given initial prompt.
     */
    public void startClaude(String workingDir, String initialPrompt) {
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

            // Build PTY process
            PtyProcessBuilder builder = new PtyProcessBuilder()
                .setCommand(new String[]{"claude"})
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

            // Send initial prompt after delay (wait for Claude to fully initialize)
            if (initialPrompt != null && !initialPrompt.isEmpty()) {
                Thread promptSender = new Thread(() -> {
                    try {
                        Thread.sleep(2000);  // Wait 2 seconds for Claude to be ready
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
