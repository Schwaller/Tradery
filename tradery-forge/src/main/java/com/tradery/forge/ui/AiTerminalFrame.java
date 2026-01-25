package com.tradery.forge.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Frame window to host the embedded AI terminal panel.
 * Can be shown/hidden and supports docking/undocking.
 */
public class AiTerminalFrame extends JFrame {

    private AiTerminalPanel terminalPanel;
    private final String strategyName;
    private final Runnable onFileChange;
    private final Runnable onRedock;
    private JPanel contentPanel;
    private JButton redockBtn;

    public AiTerminalFrame(String strategyName, Runnable onFileChange, Runnable onRedock) {
        super("Claude - " + strategyName);
        this.strategyName = strategyName;
        this.onFileChange = onFileChange;
        this.onRedock = onRedock;

        initializeFrame();
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(700, 500);
        setMinimumSize(new Dimension(400, 300));

        // Dark theme for terminal
        getContentPane().setBackground(new Color(30, 30, 30));

        // Content panel to hold terminal + header
        contentPanel = new JPanel(new BorderLayout(0, 0));
        contentPanel.setBackground(new Color(30, 30, 30));

        // Header with redock button
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(45, 45, 45));
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel titleLabel = new JLabel("Claude Terminal");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));

        redockBtn = new JButton("Redock");
        redockBtn.setFont(redockBtn.getFont().deriveFont(10f));
        redockBtn.setMargin(new Insets(2, 8, 2, 8));
        redockBtn.addActionListener(e -> {
            if (onRedock != null) {
                onRedock.run();
            }
        });

        header.add(titleLabel, BorderLayout.WEST);
        header.add(redockBtn, BorderLayout.EAST);

        contentPanel.add(header, BorderLayout.NORTH);
        add(contentPanel);

        // Center relative to parent or screen
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Don't dispose, just hide - process keeps running
            }
        });
    }

    /**
     * Set an external terminal panel (for undocking from main window).
     */
    public void setTerminalPanel(AiTerminalPanel panel) {
        // Remove existing panel if any
        if (terminalPanel != null) {
            contentPanel.remove(terminalPanel);
        }

        this.terminalPanel = panel;
        contentPanel.add(terminalPanel, BorderLayout.CENTER);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /**
     * Remove terminal panel (for redocking to main window).
     */
    public void removeTerminalPanel() {
        if (terminalPanel != null) {
            contentPanel.remove(terminalPanel);
            terminalPanel = null;
            contentPanel.revalidate();
            contentPanel.repaint();
        }
    }

    /**
     * Start an AI CLI with the given context (creates own terminal panel if needed).
     * Always uses fresh terminal to clear any previous history.
     */
    public void startAi(String aiType, String workingDir, String initialPrompt) {
        if (terminalPanel == null) {
            terminalPanel = new AiTerminalPanel();
            terminalPanel.setOnFileChange(onFileChange);
            contentPanel.add(terminalPanel, BorderLayout.CENTER);
        }
        terminalPanel.restartAi(aiType, workingDir, initialPrompt);
        setVisible(true);
        toFront();
    }

    /**
     * Show the frame and bring to front if already running.
     */
    public void showAndFocus() {
        setVisible(true);
        toFront();
        requestFocus();
    }

    /**
     * Check if AI process is currently running.
     */
    public boolean isAiRunning() {
        return terminalPanel != null && terminalPanel.isRunning();
    }

    @Override
    public void dispose() {
        if (terminalPanel != null) {
            terminalPanel.dispose();
        }
        super.dispose();
    }
}
