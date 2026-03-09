package com.tradery.ui.controls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;

/**
 * A JScrollPane that stays borderless across FlatLaf theme switches.
 * FlatLaf resets borders on theme change via updateUI(); this class
 * overrides that to keep the border removed.
 *
 * Also forwards mouse wheel events to the parent scroll pane when this
 * pane cannot scroll in the event's direction, preventing nested scroll
 * panes from swallowing events they can't act on.
 */
public class BorderlessScrollPane extends JScrollPane {

    public BorderlessScrollPane(Component view) {
        super(view);
        applyBorderless();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyBorderless();
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e) {
        if (!canScroll(e)) {
            // Forward to parent scroll pane
            JScrollPane parent = findParentScrollPane();
            if (parent != null) {
                parent.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, parent));
                return;
            }
        }
        super.processMouseWheelEvent(e);
    }

    private boolean canScroll(MouseWheelEvent e) {
        JScrollBar vBar = getVerticalScrollBar();
        JScrollBar hBar = getHorizontalScrollBar();

        // Shift+wheel = horizontal scroll on macOS
        boolean horizontal = e.isShiftDown();

        if (horizontal) {
            return hBar != null && hBar.isVisible()
                && hBar.getMaximum() > hBar.getModel().getExtent();
        } else {
            return vBar != null && vBar.isVisible()
                && vBar.getMaximum() > vBar.getModel().getExtent();
        }
    }

    private JScrollPane findParentScrollPane() {
        Component p = getParent();
        while (p != null) {
            if (p instanceof JScrollPane sp && sp != this) return sp;
            if (p instanceof JViewport vp) { p = vp.getParent(); continue; }
            p = p.getParent();
        }
        return null;
    }

    private void applyBorderless() {
        setBorder(BorderFactory.createEmptyBorder());
        setViewportBorder(BorderFactory.createEmptyBorder());
    }
}
