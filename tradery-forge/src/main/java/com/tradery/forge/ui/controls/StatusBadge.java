package com.tradery.forge.ui.controls;

import javax.swing.*;
import java.awt.*;

/**
 * Reusable rounded-rect badge label for status indicators.
 * Used in status bar panels (PageManagerBadges, DataServiceStatus, MemoryStatus).
 * All badges share the same font, padding, arc, and color palette.
 */
public class StatusBadge extends JLabel {

    // Shared status color palette
    public static final Color BG_IDLE = new Color(80, 80, 80);
    public static final Color FG_IDLE = new Color(160, 160, 160);
    public static final Color BG_OK = new Color(39, 174, 96);
    public static final Color FG_OK = Color.WHITE;
    public static final Color BG_WARNING = new Color(241, 196, 15);
    public static final Color FG_WARNING = new Color(40, 40, 40);
    public static final Color BG_ERROR = new Color(231, 76, 60);
    public static final Color FG_ERROR = Color.WHITE;

    private static final int ARC = 12;
    private static final int HEIGHT = 18;

    private Color bgColor;

    public StatusBadge(String text) {
        super(text);
        this.bgColor = BG_IDLE;
        setFont(getFont().deriveFont(Font.PLAIN, 10f));
        setForeground(FG_IDLE);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, HEIGHT);
        return d;
    }

    public void setStatusColor(Color bg) {
        this.bgColor = bg;
        repaint();
    }

    public void setStatusColor(Color bg, Color fg) {
        this.bgColor = bg;
        setForeground(fg);
        repaint();
    }

    @Override
    public void setBackground(Color bg) {
        this.bgColor = bg;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bgColor);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
        g2.dispose();
        super.paintComponent(g);
    }
}
