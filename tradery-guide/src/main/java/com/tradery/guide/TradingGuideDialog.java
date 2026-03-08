package com.tradery.guide;

import com.tradery.help.MarkdownHelpDialog;
import com.tradery.help.MarkdownHelpDialog.Tab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog showing the Trading Guide — educational content with SVG diagrams
 * covering trading concepts from basics to crypto specifics.
 * Uses tabbed navigation with 4 category tabs backed by separate markdown files.
 */
public class TradingGuideDialog {

    private static final Tab[] TABS = {
            new Tab("Basics", "/guide/basics.md"),
            new Tab("Indicators", "/guide/indicators.md"),
            new Tab("Strategy", "/guide/strategy.md"),
            new Tab("Advanced", "/guide/advanced.md"),
            new Tab("Macro", "/guide/macro.md"),
    };

    private static MarkdownHelpDialog instance;

    /**
     * Shows the trading guide dialog (singleton - reuses existing instance).
     */
    public static void show(Component parent) {
        try {
            if (instance != null && instance.isDisplayable()) {
                instance.toFront();
                instance.requestFocus();
                return;
            }

            Window window = parent == null ? null
                    : parent instanceof Window w ? w
                    : SwingUtilities.getWindowAncestor(parent);
            instance = new MarkdownHelpDialog(window, "Trading Guide",
                    TABS[0].resourcePath(), new Dimension(1200, 800),
                    TradingGuideDialog.class, TABS);
            instance.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    instance = null;
                }
            });
            instance.setVisible(true);
            instance.toFront();
        } catch (Exception e) {
            System.err.println("Failed to open Trading Guide: " + e.getMessage());
            e.printStackTrace();
            instance = null;
        }
    }
}
