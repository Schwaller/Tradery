package com.tradery.help;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.BlockView;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;

/**
 * Custom HTMLEditorKit that adds SVG rendering support and copyable code blocks.
 * For {@code <img>} tags with {@code .svg} sources, uses JSVG to render;
 * for {@code <pre>} blocks, adds a copy-to-clipboard icon overlay;
 * all other elements delegate to the standard HTMLFactory.
 * <p>
 * Set the following document properties for correct resource resolution:
 * <ul>
 *   <li>{@code resourceBasePath} — base path for resolving relative image sources (e.g., "/guide/")</li>
 *   <li>{@code resourceClass} — Class whose classloader is used to load resources (for cross-module JPMS access)</li>
 * </ul>
 */
public class HelpEditorKit extends HTMLEditorKit {

    @Override
    public ViewFactory getViewFactory() {
        return new SvgAwareViewFactory(super.getViewFactory());
    }

    /**
     * ViewFactory that intercepts {@code <img>} tags with .svg sources
     * and {@code <pre>} tags for copy button overlay.
     */
    private static class SvgAwareViewFactory implements ViewFactory {
        private final ViewFactory delegate;

        SvgAwareViewFactory(ViewFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public View create(Element elem) {
            if ("img".equals(elem.getName())) {
                Object srcAttr = elem.getAttributes().getAttribute(HTML.Attribute.SRC);
                if (srcAttr != null && srcAttr.toString().endsWith(".svg")) {
                    return new SvgImageView(elem);
                }
            }
            // Intercept <pre> elements for copy button overlay
            AttributeSet attrs = elem.getAttributes();
            Object nameAttr = attrs.getAttribute(StyleConstants.NameAttribute);
            if (nameAttr == HTML.Tag.PRE) {
                return new CopyableCodeView(elem, delegate);
            }
            return delegate.create(elem);
        }
    }

    /**
     * View that wraps a {@code <pre>} block with a copy-to-clipboard icon in the top-right corner.
     * Delegates all rendering to the default BlockView, then paints the icon overlay.
     */
    private static class CopyableCodeView extends BlockView {
        private static final int ICON_SIZE = 14;
        private static final int ICON_MARGIN = 6;

        private final ViewFactory delegateFactory;
        private boolean copied = false;
        private Timer resetTimer;
        private boolean listenerInstalled = false;

        CopyableCodeView(Element elem, ViewFactory delegateFactory) {
            super(elem, View.Y_AXIS);
            this.delegateFactory = delegateFactory;
        }

        @Override
        public ViewFactory getViewFactory() {
            // Return delegate factory so child elements get normal rendering
            return delegateFactory;
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            super.paint(g, allocation);
            installMouseListenerOnce();
            paintCopyIcon(g, allocation);
        }

        private void paintCopyIcon(Graphics g, Shape allocation) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Rectangle bounds = allocation.getBounds();
                // Position inside the visible box (allocation includes CSS margin)
                int topInset = getTopInset();     // margin + padding
                int rightInset = getRightInset(); // margin + padding
                int x = bounds.x + bounds.width - ICON_SIZE - rightInset + 2;
                int y = bounds.y + topInset;

                // Icon color: green briefly after copy, otherwise subtle gray
                Color iconColor = copied
                        ? new Color(166, 227, 161)  // Catppuccin green
                        : new Color(127, 132, 156);  // Catppuccin overlay1

                g2.setColor(iconColor);
                g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                // Draw clipboard icon (two overlapping rectangles)
                // Back rectangle
                g2.drawRoundRect(x + 3, y, ICON_SIZE - 5, ICON_SIZE - 3, 2, 2);
                // Front rectangle
                g2.drawRoundRect(x, y + 3, ICON_SIZE - 5, ICON_SIZE - 3, 2, 2);

                if (copied) {
                    // Draw checkmark over the icon
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + 2, y + 9, x + 5, y + 12);
                    g2.drawLine(x + 5, y + 12, x + 11, y + 5);
                }
            } finally {
                g2.dispose();
            }
        }

        private void installMouseListenerOnce() {
            if (listenerInstalled) return;
            listenerInstalled = true;

            Container container = getContainer();
            if (container == null) return;

            container.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    handleClick(e);
                }
            });
        }

        private void handleClick(MouseEvent e) {
            // Get the current allocation for this view
            try {
                Container container = getContainer();
                if (container instanceof JEditorPane editorPane) {
                    Shape alloc = getAllocation(editorPane);
                    if (alloc == null) return;

                    Rectangle bounds = alloc.getBounds();
                    int iconX = bounds.x + bounds.width - ICON_SIZE - getRightInset() + 2;
                    int iconY = bounds.y + getTopInset();

                    // Check if click is within icon area (with generous padding)
                    Rectangle iconRect = new Rectangle(iconX - 4, iconY - 4, ICON_SIZE + 8, ICON_SIZE + 8);
                    if (iconRect.contains(e.getPoint())) {
                        copyToClipboard();
                    }
                }
            } catch (Exception ex) {
                // Ignore
            }
        }

        private Shape getAllocation(JEditorPane editorPane) {
            // Walk the view hierarchy to find our allocation
            View rootView = editorPane.getUI().getRootView(editorPane);
            Shape alloc = new Rectangle(0, 0, editorPane.getWidth(), editorPane.getHeight());
            return findAllocation(rootView, this, alloc);
        }

        private static Shape findAllocation(View parent, View target, Shape parentAlloc) {
            if (parent == target) return parentAlloc;
            for (int i = 0; i < parent.getViewCount(); i++) {
                Shape childAlloc = parent.getChildAllocation(i, parentAlloc);
                if (childAlloc == null) continue;
                View child = parent.getView(i);
                if (child == target) return childAlloc;
                Shape found = findAllocation(child, target, childAlloc);
                if (found != null) return found;
            }
            return null;
        }

        private void copyToClipboard() {
            try {
                // Extract text from the <pre> element
                Document doc = getDocument();
                int start = getStartOffset();
                int end = getEndOffset();
                String text = doc.getText(start, end - start).trim();

                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(text), null);

                // Visual feedback
                copied = true;
                Container container = getContainer();
                if (container != null) container.repaint();

                if (resetTimer != null) resetTimer.stop();
                resetTimer = new Timer(1000, e -> {
                    copied = false;
                    if (container != null) container.repaint();
                });
                resetTimer.setRepeats(false);
                resetTimer.start();
            } catch (BadLocationException e) {
                // Ignore
            }
        }
    }

    /**
     * View that renders SVG images using JSVG.
     * Reads SVG from classpath, scales to fit container width while maintaining aspect ratio.
     * Click on the image to open a lightbox overlay; from there, pop out to a floating window.
     */
    private static class SvgImageView extends View {
        private static final int ZOOM_ICON_SIZE = 12;
        private static final int ZOOM_ICON_MARGIN = 8;

        private SVGDocument svgDocument;
        private float intrinsicWidth;
        private float intrinsicHeight;
        private boolean loaded;
        private boolean zoomListenerInstalled = false;

        SvgImageView(Element elem) {
            super(elem);
            loadSvg();
        }

        private void loadSvg() {
            Object srcAttr = getElement().getAttributes().getAttribute(HTML.Attribute.SRC);
            if (srcAttr == null) return;

            String src = srcAttr.toString();

            // Resolve relative path using resourceBasePath document property
            String basePath = (String) getDocument().getProperty("resourceBasePath");
            String resourcePath;
            if (src.startsWith("/")) {
                resourcePath = src;
            } else if (basePath != null) {
                resourcePath = basePath + src;
            } else {
                resourcePath = "/" + src;
            }

            // Use resourceClass from document property for cross-module JPMS access
            Class<?> resourceClass = (Class<?>) getDocument().getProperty("resourceClass");
            if (resourceClass == null) {
                resourceClass = HelpEditorKit.class;
            }

            try {
                InputStream is = resourceClass.getResourceAsStream(resourcePath);
                if (is == null) {
                    System.err.println("SVG not found: " + resourcePath);
                    return;
                }

                SVGLoader loader = new SVGLoader();
                svgDocument = loader.load(is);
                is.close();

                if (svgDocument != null) {
                    var size = svgDocument.size();
                    intrinsicWidth = size.width;
                    intrinsicHeight = size.height;
                    loaded = true;
                }
            } catch (Exception e) {
                System.err.println("Failed to load SVG: " + resourcePath + " - " + e.getMessage());
            }
        }

        @Override
        public float getPreferredSpan(int axis) {
            if (!loaded) return 0;

            if (axis == View.X_AXIS) {
                // Scale down to container width but never scale up
                Container container = getContainer();
                if (container != null) {
                    int containerWidth = container.getWidth();
                    if (containerWidth > 0) {
                        return Math.min(intrinsicWidth, containerWidth - 20);
                    }
                }
                return intrinsicWidth;
            } else {
                // Maintain aspect ratio
                float displayWidth = getPreferredSpan(View.X_AXIS);
                if (intrinsicWidth > 0) {
                    return intrinsicHeight * (displayWidth / intrinsicWidth);
                }
                return intrinsicHeight;
            }
        }

        @Override
        public float getMinimumSpan(int axis) {
            return getPreferredSpan(axis);
        }

        @Override
        public float getMaximumSpan(int axis) {
            return getPreferredSpan(axis);
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            if (!loaded || svgDocument == null) return;

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                Rectangle2D bounds = allocation.getBounds2D();
                float displayWidth = (float) bounds.getWidth();
                float displayHeight = (float) bounds.getHeight();

                // Render SVG scaled to the allocation bounds
                g2.translate(bounds.getX(), bounds.getY());
                svgDocument.render(null, g2, new com.github.weisj.jsvg.attributes.ViewBox(0, 0, displayWidth, displayHeight));
                g2.translate(-bounds.getX(), -bounds.getY());

                // Expand icon hint (bottom-right corner)
                paintZoomIcon(g2, allocation.getBounds());
            } finally {
                g2.dispose();
            }

            installZoomListenerOnce();
        }

        private void paintZoomIcon(Graphics2D g2, Rectangle bounds) {
            int x = bounds.x + bounds.width - ZOOM_ICON_SIZE - ZOOM_ICON_MARGIN;
            int y = bounds.y + bounds.height - ZOOM_ICON_SIZE - ZOOM_ICON_MARGIN;

            g2.setColor(new Color(127, 132, 156, 140));
            g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Expand icon: two opposing diagonal arrows
            // Top-right arrow
            g2.drawLine(x + 7, y, x + ZOOM_ICON_SIZE, y);
            g2.drawLine(x + ZOOM_ICON_SIZE, y, x + ZOOM_ICON_SIZE, y + 5);
            g2.drawLine(x + ZOOM_ICON_SIZE, y, x + 6, y + 6);

            // Bottom-left arrow
            g2.drawLine(x, y + 5, x, y + ZOOM_ICON_SIZE);
            g2.drawLine(x, y + ZOOM_ICON_SIZE, x + 5, y + ZOOM_ICON_SIZE);
            g2.drawLine(x, y + ZOOM_ICON_SIZE, x + 6, y + 6);
        }

        private void installZoomListenerOnce() {
            if (zoomListenerInstalled) return;
            zoomListenerInstalled = true;

            Container container = getContainer();
            if (container == null) return;

            container.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!loaded || svgDocument == null) return;
                    if (!(container instanceof JEditorPane ep)) return;
                    Shape alloc = findViewAllocation(ep);
                    if (alloc != null && alloc.getBounds().contains(e.getPoint())) {
                        SvgZoomOverlay.showLightbox(container, svgDocument, intrinsicWidth, intrinsicHeight);
                    }
                }
            });
        }

        private Shape findViewAllocation(JEditorPane editorPane) {
            View rootView = editorPane.getUI().getRootView(editorPane);
            Shape alloc = new Rectangle(0, 0, editorPane.getWidth(), editorPane.getHeight());
            return findAllocation(rootView, this, alloc);
        }

        private static Shape findAllocation(View parent, View target, Shape parentAlloc) {
            if (parent == target) return parentAlloc;
            for (int i = 0; i < parent.getViewCount(); i++) {
                Shape childAlloc = parent.getChildAllocation(i, parentAlloc);
                if (childAlloc == null) continue;
                View child = parent.getView(i);
                if (child == target) return childAlloc;
                Shape found = findAllocation(child, target, childAlloc);
                if (found != null) return found;
            }
            return null;
        }

        @Override
        public Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException {
            Rectangle alloc = a.getBounds();
            return new Rectangle(alloc.x, alloc.y, 0, (int) getPreferredSpan(View.Y_AXIS));
        }

        @Override
        public int viewToModel(float x, float y, Shape a, Position.Bias[] bias) {
            bias[0] = Position.Bias.Forward;
            return getStartOffset();
        }
    }
}
