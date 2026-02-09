package com.tradery.agent.terminal;

import com.formdev.flatlaf.FlatClientProperties;
import com.tradery.ui.controls.ToolbarButton;

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
    private JLabel titleLabel;

    public AiTerminalFrame(String strategyName, Runnable onFileChange, Runnable onRedock) {
        super("AI Terminal - " + strategyName);
        this.strategyName = strategyName;
        this.onFileChange = onFileChange;
        this.onRedock = onRedock;

        initializeFrame();
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(700, 500);
        setMinimumSize(new Dimension(400, 300));

        // Integrated macOS title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        // Content panel
        contentPanel = new JPanel(new BorderLayout(0, 0));

        // --- Toolbar header ---
        int barHeight = 52;

        // OverlayLayout: title layer centered to full width, controls layer on top
        JPanel toolbar = new JPanel();
        toolbar.setPreferredSize(new Dimension(0, barHeight));
        toolbar.setMinimumSize(new Dimension(0, barHeight));
        toolbar.setLayout(new OverlayLayout(toolbar));

        // Title layer — fills entire toolbar, centered
        titleLabel = new JLabel("AI Terminal");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel titleLayer = new JPanel(new BorderLayout());
        titleLayer.setOpaque(false);
        titleLayer.add(titleLabel, BorderLayout.CENTER);
        titleLayer.setAlignmentX(0.5f);
        titleLayer.setAlignmentY(0.5f);

        // Controls layer — GridBagLayout for vertical centering
        JPanel controlsLayer = new JPanel(new GridBagLayout());
        controlsLayer.setOpaque(false);
        controlsLayer.setAlignmentX(0.5f);
        controlsLayer.setAlignmentY(0.5f);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;

        // Left: traffic light placeholder
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        JPanel leftContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftContent.setOpaque(false);
        JPanel buttonsPlaceholder = new JPanel();
        buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
        buttonsPlaceholder.setOpaque(false);
        leftContent.add(buttonsPlaceholder);
        controlsLayer.add(leftContent, gbc);

        // Right: redock + close ToolbarButtons
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 0, 0, 8);
        JPanel rightContent = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightContent.setOpaque(false);

        JButton redockBtn = new ToolbarButton("Redock");
        redockBtn.setToolTipText("Redock into main window");
        redockBtn.addActionListener(e -> {
            if (onRedock != null) onRedock.run();
        });
        rightContent.add(redockBtn);

        controlsLayer.add(rightContent, gbc);

        // Controls on top of title
        toolbar.add(controlsLayer);
        toolbar.add(titleLayer);

        // Stack toolbar + separator
        JPanel topStack = new JPanel();
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        topStack.add(toolbar);
        topStack.add(new JSeparator());

        contentPanel.add(topStack, BorderLayout.NORTH);
        add(contentPanel);

        // Center relative to parent or screen
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Don't dispose, just hide — process keeps running
            }
        });
    }

    /**
     * Update the title label text.
     */
    public void setTitleText(String text) {
        if (titleLabel != null) {
            titleLabel.setText(text);
        }
    }

    /**
     * Set an external terminal panel (for undocking from main window).
     */
    public void setTerminalPanel(AiTerminalPanel panel) {
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
    public void startAi(String aiType, String workingDir) {
        if (terminalPanel == null) {
            terminalPanel = new AiTerminalPanel();
            terminalPanel.setOnFileChange(onFileChange);
            contentPanel.add(terminalPanel, BorderLayout.CENTER);
        }
        terminalPanel.restartAi(aiType, workingDir);
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
