package com.tradery.forge.ui;

import com.tradery.help.MarkdownHelpDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog showing DSL syntax reference.
 * Thin wrapper around MarkdownHelpDialog loading /help/dsl-reference.md.
 */
public class DslHelpDialog {

    private static MarkdownHelpDialog instance;

    /**
     * Shows the DSL help dialog (singleton - reuses existing instance).
     */
    public static void show(Component parent) {
        if (instance != null && instance.isDisplayable()) {
            instance.toFront();
            instance.requestFocus();
            return;
        }

        Window window = SwingUtilities.getWindowAncestor(parent);
        instance = new MarkdownHelpDialog(window, "DSL Reference",
                "/help/dsl-reference.md", new Dimension(1100, 600));
        instance.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                instance = null;
            }
        });
        instance.setVisible(true);
    }
}
