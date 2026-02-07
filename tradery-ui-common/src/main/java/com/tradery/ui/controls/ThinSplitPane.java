package com.tradery.ui.controls;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;

/**
 * A JSplitPane with a 1px layout gap and ±5px mouse grab zone.
 * The divider is physically 1px (content truly separated by 1px),
 * but mouse events within 5px of either side are routed to the divider
 * so it's easy to grab. No 3-dot dragger. Survives FlatLaf theme switches.
 */
public class ThinSplitPane extends JSplitPane {

    private static final int GRAB_ZONE = 5;

    public ThinSplitPane(int orientation) {
        super(orientation);
        init();
    }

    public ThinSplitPane(int orientation, Component left, Component right) {
        super(orientation, left, right);
        init();
    }

    private void init() {
        setBorder(null);
        setContinuousLayout(true);
        applyDivider();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setBorder(null);
        applyDivider();
    }

    private void applyDivider() {
        setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(UIManager.getColor("Separator.foreground"));
                        if (orientation == HORIZONTAL_SPLIT) {
                            int x = getWidth() / 2;
                            g.drawLine(x, 0, x, getHeight());
                        } else {
                            int y = getHeight() / 2;
                            g.drawLine(0, y, getWidth(), y);
                        }
                    }

                    @Override
                    public boolean contains(int x, int y) {
                        int w = getWidth();
                        int h = getHeight();
                        if (orientation == HORIZONTAL_SPLIT) {
                            return x >= -GRAB_ZONE && x < w + GRAB_ZONE && y >= 0 && y < h;
                        } else {
                            return x >= 0 && x < w && y >= -GRAB_ZONE && y < h + GRAB_ZONE;
                        }
                    }
                };
            }
        });
        setDividerSize(1);
    }

    /**
     * Route mouse events within ±5px of the 1px divider to the divider,
     * even though the mouse is technically over a content panel.
     */
    @Override
    public Component findComponentAt(int x, int y) {
        if (!contains(x, y)) return null;

        if (getUI() instanceof BasicSplitPaneUI bsui) {
            BasicSplitPaneDivider divider = bsui.getDivider();
            if (divider != null && divider.isVisible()) {
                int dx = x - divider.getX();
                int dy = y - divider.getY();
                if (divider.contains(dx, dy)) {
                    return divider;
                }
            }
        }
        return super.findComponentAt(x, y);
    }
}
