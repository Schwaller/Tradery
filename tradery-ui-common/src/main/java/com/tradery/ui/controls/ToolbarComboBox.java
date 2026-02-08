package com.tradery.ui.controls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Button-styled dropdown for toolbar/header bars.
 * Renders as a real {@link JButton} (full theme chrome) with a chevron
 * indicator and a popup menu for item selection.
 */
public class ToolbarComboBox<E> extends JButton {

    private final List<E> items;
    private int selectedIndex;
    private final List<ActionListener> actionListeners = new ArrayList<>();

    public ToolbarComboBox(E[] items) {
        this.items = new ArrayList<>(Arrays.asList(items));
        this.selectedIndex = items.length > 0 ? 0 : -1;
        setFont(ToolbarButton.TOOLBAR_FONT);
        setMargin(new Insets(6, 10, 6, 6));
        setFocusPainted(false);
        setHorizontalAlignment(SwingConstants.LEFT);
        setIconTextGap(4);
        updateDisplay();

        super.addActionListener(e -> showPopup());
    }

    private void showPopup() {
        JPopupMenu popup = new JPopupMenu();
        popup.setFont(ToolbarButton.TOOLBAR_FONT);
        for (int i = 0; i < items.size(); i++) {
            E item = items.get(i);
            JMenuItem mi = new JMenuItem(item.toString());
            mi.setFont(ToolbarButton.TOOLBAR_FONT);
            if (i == selectedIndex) {
                mi.setEnabled(false); // visually mark current selection
            }
            int idx = i;
            mi.addActionListener(ev -> {
                selectedIndex = idx;
                updateDisplay();
                fireSelectionChanged();
            });
            popup.add(mi);
        }
        popup.show(this, 0, getHeight());
    }

    private void updateDisplay() {
        if (selectedIndex >= 0 && selectedIndex < items.size()) {
            setText(items.get(selectedIndex).toString());
        }
        setIcon(new ChevronIcon());
        setHorizontalTextPosition(SwingConstants.LEADING);
    }

    private void fireSelectionChanged() {
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "selectionChanged");
        for (ActionListener l : actionListeners) {
            l.actionPerformed(e);
        }
    }

    // --- JComboBox-compatible API ---

    @Override
    public void addActionListener(ActionListener l) {
        actionListeners.add(l);
    }

    @Override
    public void removeActionListener(ActionListener l) {
        actionListeners.remove(l);
    }

    public E getSelectedItem() {
        if (selectedIndex >= 0 && selectedIndex < items.size()) {
            return items.get(selectedIndex);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void setSelectedItem(Object item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).equals(item)) {
                selectedIndex = i;
                updateDisplay();
                return;
            }
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < items.size()) {
            selectedIndex = index;
            updateDisplay();
        }
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

    /** Small downward chevron icon, theme-aware and HiDPI-clean. */
    private static class ChevronIcon implements Icon {
        private static final int W = 10;
        private static final int H = 10;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(c.getForeground());
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Center a 7x4 chevron in the icon area
            double cx = x + W / 2.0;
            double cy = y + H / 2.0;
            Path2D path = new Path2D.Double();
            path.moveTo(cx - 3.5, cy - 1.5);
            path.lineTo(cx, cy + 2);
            path.lineTo(cx + 3.5, cy - 1.5);
            g2.draw(path);
            g2.dispose();
        }

        @Override public int getIconWidth() { return W; }
        @Override public int getIconHeight() { return H; }
    }
}
