package com.tradery.ui.controls;

import javax.swing.*;
import java.awt.*;

/**
 * Compact button sized for toolbar/header bars.
 */
public class ToolbarButton extends JButton {

    static final Font TOOLBAR_FONT = new Font("SansSerif", Font.PLAIN, 11);
    static final Insets TOOLBAR_MARGIN = new Insets(6, 14, 6, 14);
    static final int HEIGHT = 32;

    /** Vertical pixels added by FlatLaf's outer focus border (top + bottom). */
    static int focusBorderHeight() {
        int fw = UIManager.getInt("Component.focusWidth");
        return fw * 2;
    }

    public ToolbarButton(String text) {
        super(text);
        setFont(TOOLBAR_FONT);
        setMargin(TOOLBAR_MARGIN);
        setFocusPainted(false);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = HEIGHT;
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = super.getMinimumSize();
        d.height = HEIGHT;
        return d;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension d = super.getMaximumSize();
        d.height = HEIGHT;
        return d;
    }
}
