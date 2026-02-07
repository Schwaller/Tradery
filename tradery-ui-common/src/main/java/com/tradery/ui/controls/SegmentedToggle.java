package com.tradery.ui.controls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Segmented toggle button group — tab-style selector for switching views.
 * Each segment is a styled JToggleButton managed by a ButtonGroup.
 * Colors are derived from the current FlatLaf theme and update on theme changes.
 */
public class SegmentedToggle extends JPanel {

    private static final Color TRACK_COLOR = new Color(0, 0, 0, 38); // 15% black

    private final ButtonGroup group = new ButtonGroup();
    private final List<JToggleButton> buttons = new ArrayList<>();
    private Consumer<Integer> onSelectionChanged;

    private Color bgSelected;
    private Color fgSelected;
    private Color fgUnselected;
    private Color fgHover;
    private int arc;

    public SegmentedToggle(String... labels) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);
        loadThemeColors();

        for (int i = 0; i < labels.length; i++) {
            JToggleButton btn = createButton(labels[i]);
            int index = i;
            btn.addActionListener(e -> {
                if (onSelectionChanged != null) onSelectionChanged.accept(index);
            });
            group.add(btn);
            buttons.add(btn);
            add(btn);
        }

        if (!buttons.isEmpty()) {
            buttons.get(0).setSelected(true);
        }

    }

    private int toggleHeight() {
        return ToolbarButton.HEIGHT - ToolbarButton.focusBorderHeight();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = toggleHeight();
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = super.getMinimumSize();
        d.height = toggleHeight();
        return d;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension d = super.getMaximumSize();
        d.height = toggleHeight();
        return d;
    }

    private void loadThemeColors() {
        // FlatLaf overrides button background during updateUI() to a different value
        // than UIManager "Button.background" — sample from a real JButton to match
        bgSelected = new JButton().getBackground();
        fgSelected = UIManager.getColor("Label.foreground");
        fgUnselected = UIManager.getColor("Label.disabledForeground");
        fgHover = UIManager.getColor("Label.foreground");
        Object arcObj = UIManager.get("Button.arc");
        arc = arcObj instanceof Integer ? (Integer) arcObj : 6;
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (!buttons.isEmpty()) {
            Rectangle first = buttons.get(0).getBounds();
            Rectangle last = buttons.get(buttons.size() - 1).getBounds();
            int x = first.x, y = first.y;
            int w = last.x + last.width - first.x;
            int h = first.height;

            // Track
            g2.setColor(TRACK_COLOR);
            g2.fillRoundRect(x, y, w, h, arc, arc);

            // Selected highlight
            for (JToggleButton btn : buttons) {
                if (btn.isSelected()) {
                    Rectangle b = btn.getBounds();
                    g2.setColor(bgSelected);
                    g2.fillRoundRect(b.x, b.y, b.width, b.height, arc, arc);
                    break;
                }
            }
        }

        g2.dispose();
        super.paintChildren(g);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        loadThemeColors();
        if (buttons != null) {
            for (JToggleButton btn : buttons) {
                applyColors(btn, false);
            }
        }
    }

    public void setOnSelectionChanged(Consumer<Integer> listener) {
        this.onSelectionChanged = listener;
    }

    public int getSelectedIndex() {
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).isSelected()) return i;
        }
        return -1;
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < buttons.size()) {
            buttons.get(index).setSelected(true);
        }
    }

    public JToggleButton getButton(int index) {
        return buttons.get(index);
    }

    private void applyColors(JToggleButton btn, boolean hovered) {
        if (btn.isSelected()) {
            btn.setForeground(fgSelected);
        } else {
            btn.setForeground(hovered ? fgHover : fgUnselected);
        }
        repaint();
    }

    private JToggleButton createButton(String text) {
        JToggleButton btn = new JToggleButton(text) {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = toggleHeight();
                return d;
            }
            @Override public Dimension getMinimumSize() {
                Dimension d = super.getMinimumSize();
                d.height = toggleHeight();
                return d;
            }
            @Override public Dimension getMaximumSize() {
                Dimension d = super.getMaximumSize();
                d.height = toggleHeight();
                return d;
            }
        };
        btn.setFont(ToolbarButton.TOOLBAR_FONT);
        btn.setMargin(ToolbarButton.TOOLBAR_MARGIN);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        applyColors(btn, false);
        btn.addChangeListener(e -> applyColors(btn, btn.getModel().isRollover()));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { applyColors(btn, true); }
            @Override
            public void mouseExited(MouseEvent e) { applyColors(btn, false); }
        });
        return btn;
    }
}
