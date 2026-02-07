package com.tradery.ui.controls;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.function.Consumer;

/**
 * Compact search field sized for toolbar/header bars.
 * Matches {@link ToolbarButton} height (32px).
 * <p>
 * Features: magnifying glass icon (via FlatLaf leadingIcon), clear button,
 * placeholder text, match counter badge, Enter/Shift+Enter/Esc keyboard nav.
 */
public class ToolbarSearchField extends JPanel {

    private static final int HEIGHT = ToolbarButton.HEIGHT;

    private final JTextField textField;
    private final JLabel matchLabel;

    private Consumer<String> searchListener;
    private Runnable nextMatchAction;
    private Runnable prevMatchAction;

    public ToolbarSearchField(int columns) {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(false);

        matchLabel = new JLabel();
        matchLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        matchLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        matchLabel.setBorder(new EmptyBorder(0, 0, 0, 6));

        textField = new JTextField(columns);
        textField.setFont(ToolbarButton.TOOLBAR_FONT);
        textField.putClientProperty("JTextField.placeholderText", "Search\u2026");
        textField.putClientProperty("JTextField.leadingIcon", new SearchIcon());
        textField.putClientProperty("JTextField.showClearButton", true);

        // Live search on text change
        textField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { fireSearch(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { fireSearch(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { fireSearch(); }
        });

        // Keyboard navigation
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        if (prevMatchAction != null) prevMatchAction.run();
                    } else {
                        if (nextMatchAction != null) nextMatchAction.run();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    textField.setText("");
                }
            }
        });

        add(matchLabel);
        add(textField);
    }

    /** Set callback invoked on every text change. */
    public void setSearchListener(Consumer<String> listener) {
        this.searchListener = listener;
    }

    /** Set action for Enter (next match). */
    public void setNextMatchAction(Runnable action) {
        this.nextMatchAction = action;
    }

    /** Set action for Shift+Enter (previous match). */
    public void setPrevMatchAction(Runnable action) {
        this.prevMatchAction = action;
    }

    /** Update match counter. Pass (0, 0) to clear. */
    public void setMatchInfo(int current, int total) {
        if (total <= 0) {
            if (textField.getText().trim().isEmpty()) {
                matchLabel.setText("");
            } else {
                matchLabel.setText("0 results");
            }
        } else {
            matchLabel.setText(current + "/" + total);
        }
    }

    /** Clear the match counter. */
    public void clearMatchInfo() {
        matchLabel.setText("");
    }

    public String getText() {
        return textField.getText();
    }

    public JTextField getTextField() {
        return textField;
    }

    private void fireSearch() {
        if (searchListener != null) {
            searchListener.accept(textField.getText());
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = HEIGHT;
        return d;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension d = super.getMaximumSize();
        d.height = HEIGHT;
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = super.getMinimumSize();
        d.height = HEIGHT;
        return d;
    }

    /**
     * Magnifying glass icon painted with Java2D. Theme-aware via Label.disabledForeground.
     */
    private static class SearchIcon implements Icon {
        private static final int PADDING_LEFT = 2;
        private static final int SIZE = 14 + PADDING_LEFT;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Color iconColor = UIManager.getColor("Label.disabledForeground");
            if (iconColor == null) iconColor = Color.GRAY;
            g2.setColor(iconColor);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Circle (lens) — centered in upper-left area
            double cx = x + PADDING_LEFT + 6, cy = y + 6, r = 4.5;
            g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

            // Handle — from lower-right of circle outward
            double angle = Math.toRadians(45);
            double hx1 = cx + r * Math.cos(angle);
            double hy1 = cy + r * Math.sin(angle);
            double hx2 = hx1 + 3.5;
            double hy2 = hy1 + 3.5;
            g2.draw(new Line2D.Double(hx1, hy1, hx2, hy2));

            g2.dispose();
        }

        @Override public int getIconWidth() { return SIZE; }
        @Override public int getIconHeight() { return SIZE; }
    }
}
