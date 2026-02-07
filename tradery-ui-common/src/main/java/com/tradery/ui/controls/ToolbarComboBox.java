package com.tradery.ui.controls;

import javax.swing.*;
import java.awt.*;

/**
 * Compact combo box sized for toolbar/header bars.
 * Matches {@link ToolbarButton} height.
 */
public class ToolbarComboBox<E> extends JComboBox<E> {

    public ToolbarComboBox(E[] items) {
        super(items);
        setFont(ToolbarButton.TOOLBAR_FONT);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = ToolbarButton.HEIGHT;
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = super.getMinimumSize();
        d.height = ToolbarButton.HEIGHT;
        return d;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension d = super.getMaximumSize();
        d.height = ToolbarButton.HEIGHT;
        return d;
    }
}
