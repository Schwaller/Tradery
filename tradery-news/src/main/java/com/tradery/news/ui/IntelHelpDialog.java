package com.tradery.news.ui;

import com.tradery.help.MarkdownHelpDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Help dialog for the Intelligence app.
 * Thin wrapper around MarkdownHelpDialog loading /help/intel-guide.md.
 */
public class IntelHelpDialog {

    private static MarkdownHelpDialog instance;

    public static void show(Component parent) {
        if (instance != null && instance.isDisplayable()) {
            instance.toFront();
            instance.requestFocus();
            return;
        }

        Window window = SwingUtilities.getWindowAncestor(parent);
        instance = new MarkdownHelpDialog(window, "Intelligence Guide",
                "/help/intel-guide.md", new Dimension(800, 600));
        instance.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                instance = null;
            }
        });
        instance.setVisible(true);
    }
}
