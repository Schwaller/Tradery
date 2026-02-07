package com.tradery.ui.controls;

import javax.swing.*;
import java.awt.*;

/**
 * A JScrollPane that stays borderless across FlatLaf theme switches.
 * FlatLaf resets borders on theme change via updateUI(); this class
 * overrides that to keep the border removed.
 */
public class BorderlessScrollPane extends JScrollPane {

    public BorderlessScrollPane(Component view) {
        super(view);
        setBorder(null);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setBorder(null);
    }
}
