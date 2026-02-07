package com.tradery.forge.ui;

import com.tradery.help.MarkdownHelpDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog explaining strategy concepts at a user level.
 * Thin wrapper around MarkdownHelpDialog loading /help/strategy-guide.md.
 */
public class StrategyHelpDialog {

    private static MarkdownHelpDialog instance;

    /**
     * Shows the strategy help dialog (singleton - reuses existing instance).
     */
    public static void show(Component parent) {
        try {
            if (instance != null && instance.isDisplayable()) {
                instance.toFront();
                instance.requestFocus();
                return;
            }

            Window window = SwingUtilities.getWindowAncestor(parent);
            instance = new MarkdownHelpDialog(window, "Strategy Guide",
                    "/help/strategy-guide.md", new Dimension(1000, 700));
            instance.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    instance = null;
                }
            });
            instance.setVisible(true);
            instance.toFront();
        } catch (Exception e) {
            System.err.println("Failed to open Strategy Help: " + e.getMessage());
            e.printStackTrace();
            instance = null;
        }
    }
}
