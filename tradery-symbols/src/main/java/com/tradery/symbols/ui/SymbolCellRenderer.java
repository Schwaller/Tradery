package com.tradery.symbols.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Cell renderer for the SymbolComboBox. Dims the "Browse..." item.
 */
public class SymbolCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                   boolean isSelected, boolean cellHasFocus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if ("Browse...".equals(value)) {
            label.setFont(label.getFont().deriveFont(Font.ITALIC));
            if (!isSelected) {
                label.setForeground(UIManager.getColor("Label.disabledForeground"));
            }
        } else {
            label.setFont(label.getFont().deriveFont(Font.PLAIN));
        }

        return label;
    }
}
