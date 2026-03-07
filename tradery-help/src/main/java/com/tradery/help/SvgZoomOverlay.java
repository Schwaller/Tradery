package com.tradery.help;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * SVG zoom: lightbox overlay (click image to enlarge) and pop-out window (detachable, draggable, resizable).
 * <p>
 * Usage: {@code SvgZoomOverlay.showLightbox(container, svgDoc, width, height)}
 */
class SvgZoomOverlay extends JComponent {

    private static final int PADDING = 24;
    private static final int POP_OUT_SIZE = 16;
    private static final int POP_OUT_MARGIN = 10;

    private final SVGDocument svgDocument;
    private final float intrinsicWidth;
    private final float intrinsicHeight;
    private final JRootPane rootPane;
    private final Component originalGlassPane;
    private Rectangle svgBounds;
    private Rectangle popOutBounds;

    static void showLightbox(Container container, SVGDocument doc, float w, float h) {
        Window window = SwingUtilities.getWindowAncestor(container);
        if (window == null) return;

        JRootPane rootPane = null;
        if (window instanceof JDialog d) rootPane = d.getRootPane();
        else if (window instanceof JFrame f) rootPane = f.getRootPane();
        if (rootPane == null) return;

        var overlay = new SvgZoomOverlay(doc, w, h, rootPane);
        rootPane.setGlassPane(overlay);
        overlay.setVisible(true);
        overlay.requestFocusInWindow();
    }

    private SvgZoomOverlay(SVGDocument doc, float w, float h, JRootPane rootPane) {
        this.svgDocument = doc;
        this.intrinsicWidth = w;
        this.intrinsicHeight = h;
        this.rootPane = rootPane;
        this.originalGlassPane = rootPane.getGlassPane();

        setOpaque(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (popOutBounds != null && popOutBounds.contains(e.getPoint())) {
                    Window parent = SwingUtilities.getWindowAncestor(rootPane);
                    dismiss();
                    showPopup(parent, svgDocument, intrinsicWidth, intrinsicHeight);
                } else {
                    dismiss();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(popOutBounds != null && popOutBounds.contains(e.getPoint())
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dismiss();
            }
        });
    }

    private void dismiss() {
        setVisible(false);
        rootPane.setGlassPane(originalGlassPane);
        originalGlassPane.setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int width = getWidth();
            int height = getHeight();

            // Solid panel background
            Color bg = UIManager.getColor("Panel.background");
            if (bg == null) bg = new Color(30, 30, 46);
            g2.setColor(bg);
            g2.fillRect(0, 0, width, height);

            // Calculate SVG size: fill available space with padding
            float maxW = width - PADDING * 2;
            float maxH = height - PADDING * 2 - 30; // 30 for hint text area
            float scale = Math.min(maxW / intrinsicWidth, maxH / intrinsicHeight);
            float displayW = intrinsicWidth * scale;
            float displayH = intrinsicHeight * scale;
            float x = (width - displayW) / 2;
            float y = (height - displayH - 20) / 2; // offset up slightly for hint

            svgBounds = new Rectangle((int) x, (int) y, (int) displayW, (int) displayH);

            // Render SVG
            Graphics2D svgG = (Graphics2D) g2.create();
            svgG.translate(x, y);
            svgDocument.render(null, svgG, new ViewBox(0, 0, displayW, displayH));
            svgG.dispose();

            // Pop-out button (top-right corner of overlay)
            int btnX = width - POP_OUT_SIZE - POP_OUT_MARGIN;
            int btnY = POP_OUT_MARGIN;
            popOutBounds = new Rectangle(btnX - 6, btnY - 6, POP_OUT_SIZE + 12, POP_OUT_SIZE + 12);

            g2.setColor(new Color(127, 132, 156));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Pop-out icon: box with arrow escaping top-right
            g2.drawRoundRect(btnX, btnY + 5, POP_OUT_SIZE - 5, POP_OUT_SIZE - 5, 2, 2);
            g2.drawLine(btnX + POP_OUT_SIZE - 6, btnY, btnX + POP_OUT_SIZE, btnY);
            g2.drawLine(btnX + POP_OUT_SIZE, btnY, btnX + POP_OUT_SIZE, btnY + 6);
            g2.drawLine(btnX + POP_OUT_SIZE, btnY, btnX + POP_OUT_SIZE - 7, btnY + 7);

            // Hint text at bottom
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(new Color(127, 132, 156));
            String hint = "Click anywhere to close  \u2022  Pop-out \u2197";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(hint, (width - fm.stringWidth(hint)) / 2f, height - 12);

        } finally {
            g2.dispose();
        }
    }

    // ---- Pop-out Window ----

    private static void showPopup(Window parent, SVGDocument doc, float w, float h) {
        new SvgPopupWindow(parent, doc, w, h);
    }

    /**
     * Floating undecorated dialog for keeping an SVG visible while scrolling help content.
     * Draggable via the top area, resizable from bottom and right edges.
     */
    private static class SvgPopupWindow extends JDialog {
        private static final int TITLE_HEIGHT = 26;
        private static final int RESIZE_EDGE = 6;

        private final SVGDocument svgDocument;
        private final float intrinsicWidth;
        private final float intrinsicHeight;

        private Point dragOffset;
        private int resizeMode = 0;

        SvgPopupWindow(Window parent, SVGDocument doc, float w, float h) {
            super(parent);
            setUndecorated(true);
            setModal(false);

            this.svgDocument = doc;
            this.intrinsicWidth = w;
            this.intrinsicHeight = h;

            // Rounded corners on macOS
            getRootPane().putClientProperty("apple.awt.windowCornerRadius", 10.0f);

            // Size: 700px wide, maintain aspect ratio
            int initW = 700;
            int initH = (int) (initW * (h / w)) + TITLE_HEIGHT;
            setSize(initW, initH);
            setMinimumSize(new Dimension(300, 200));

            JPanel content = new SvgPanel();
            setContentPane(content);

            MouseAdapter handler = createMouseHandler();
            content.addMouseListener(handler);
            content.addMouseMotionListener(handler);

            // Escape to close
            content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke("ESCAPE"), "close");
            content.getActionMap().put("close", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dispose();
                }
            });

            if (parent != null) {
                setLocationRelativeTo(parent);
            } else {
                setLocationRelativeTo(null);
            }
            setVisible(true);
        }

        private boolean isOverCloseButton(Point p) {
            return p.x > getWidth() - 28 && p.y < TITLE_HEIGHT;
        }

        private int detectResize(Point p) {
            int w = getWidth(), h = getHeight();
            boolean bottom = p.y > h - RESIZE_EDGE;
            boolean right = p.x > w - RESIZE_EDGE;
            if (bottom && right) return Cursor.SE_RESIZE_CURSOR;
            if (bottom) return Cursor.S_RESIZE_CURSOR;
            if (right) return Cursor.E_RESIZE_CURSOR;
            return 0;
        }

        private MouseAdapter createMouseHandler() {
            return new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (isOverCloseButton(e.getPoint())) {
                        dispose();
                        return;
                    }
                    resizeMode = detectResize(e.getPoint());
                    if (resizeMode == 0 && e.getY() < TITLE_HEIGHT) {
                        dragOffset = e.getPoint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragOffset = null;
                    resizeMode = 0;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragOffset != null) {
                        Point loc = getLocation();
                        setLocation(loc.x + e.getX() - dragOffset.x,
                                loc.y + e.getY() - dragOffset.y);
                    } else if (resizeMode != 0) {
                        Point screen = e.getLocationOnScreen();
                        Rectangle b = getBounds();
                        Dimension min = getMinimumSize();
                        int newW = b.width, newH = b.height;
                        if (resizeMode == Cursor.E_RESIZE_CURSOR || resizeMode == Cursor.SE_RESIZE_CURSOR) {
                            newW = Math.max(min.width, screen.x - b.x);
                        }
                        if (resizeMode == Cursor.S_RESIZE_CURSOR || resizeMode == Cursor.SE_RESIZE_CURSOR) {
                            newH = Math.max(min.height, screen.y - b.y);
                        }
                        setSize(newW, newH);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    if (isOverCloseButton(e.getPoint())) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    } else {
                        int edge = detectResize(e.getPoint());
                        setCursor(edge != 0
                                ? Cursor.getPredefinedCursor(edge)
                                : Cursor.getDefaultCursor());
                    }
                }
            };
        }

        private class SvgPanel extends JPanel {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                    int w = getWidth(), h = getHeight();

                    // Background
                    Color bg = UIManager.getColor("Panel.background");
                    if (bg == null) bg = new Color(30, 30, 46);
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, w, h, 10, 10);

                    // Border
                    g2.setColor(new Color(88, 91, 112, 100));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

                    // Title bar separator
                    g2.setColor(new Color(88, 91, 112, 60));
                    g2.drawLine(1, TITLE_HEIGHT, w - 2, TITLE_HEIGHT);

                    // Close button (X)
                    int closeX = w - 20;
                    int closeY = 7;
                    g2.setColor(new Color(127, 132, 156));
                    g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(closeX, closeY, closeX + 10, closeY + 10);
                    g2.drawLine(closeX + 10, closeY, closeX, closeY + 10);

                    // Resize grip (bottom-right)
                    g2.setColor(new Color(88, 91, 112, 80));
                    g2.setStroke(new BasicStroke(1f));
                    for (int i = 0; i < 3; i++) {
                        int gx = w - 6 - i * 4;
                        int gy = h - 6 - i * 4;
                        g2.drawLine(gx, h - 4, w - 4, gy);
                    }

                    // SVG content area
                    int svgY = TITLE_HEIGHT + 4;
                    int svgAreaW = w - 8;
                    int svgAreaH = h - svgY - 4;

                    float scale = Math.min((float) svgAreaW / intrinsicWidth, (float) svgAreaH / intrinsicHeight);
                    float displayW = intrinsicWidth * scale;
                    float displayH = intrinsicHeight * scale;
                    float svgX = (w - displayW) / 2;
                    float svgYPos = svgY + (svgAreaH - displayH) / 2;

                    Graphics2D svgG = (Graphics2D) g2.create();
                    svgG.translate(svgX, svgYPos);
                    svgDocument.render(null, svgG, new ViewBox(0, 0, displayW, displayH));
                    svgG.dispose();

                } finally {
                    g2.dispose();
                }
            }
        }
    }
}
