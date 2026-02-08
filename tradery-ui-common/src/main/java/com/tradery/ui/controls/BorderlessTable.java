package com.tradery.ui.controls;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;

/**
 * A JTable with no grid lines and zero intercell spacing.
 * Reapplies these settings after FlatLaf theme switches.
 */
public class BorderlessTable extends JTable {

    public BorderlessTable() {
        super();
        applyBorderless();
    }

    public BorderlessTable(TableModel model) {
        super(model);
        applyBorderless();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyBorderless();
    }

    private void applyBorderless() {
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setBorder(null);
    }
}
