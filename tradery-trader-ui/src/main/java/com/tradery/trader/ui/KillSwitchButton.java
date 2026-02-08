package com.tradery.trader.ui;

import com.tradery.execution.risk.KillSwitch;

import javax.swing.*;
import java.awt.*;

/**
 * Big red emergency button — close all positions + halt trading.
 */
public class KillSwitchButton extends JPanel {

    private final JButton button;
    private final KillSwitch killSwitch;

    public KillSwitchButton(KillSwitch killSwitch) {
        this.killSwitch = killSwitch;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        button = new JButton("KILL SWITCH");
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.setBackground(new Color(200, 30, 30));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(0, 48));

        button.addActionListener(e -> {
            if (killSwitch.isActivated()) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Reset kill switch and re-enable trading?",
                        "Reset Kill Switch", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    killSwitch.reset();
                    updateState();
                }
            } else {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "CLOSE ALL POSITIONS and halt ALL trading?",
                        "Activate Kill Switch", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    killSwitch.activate();
                    updateState();
                }
            }
        });

        add(button, BorderLayout.CENTER);
        updateState();
    }

    private void updateState() {
        if (killSwitch.isActivated()) {
            button.setText("RESET KILL SWITCH");
            button.setBackground(new Color(60, 60, 60));
        } else {
            button.setText("KILL SWITCH");
            button.setBackground(new Color(200, 30, 30));
        }
    }
}
