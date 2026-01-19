package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.data.PageState;
import com.tradery.data.page.CandlePageManager;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.model.Candle;

import javax.swing.*;
import javax.swing.UIManager;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.Consumer;

/**
 * Horizontal bar showing weekly resolution of entire dataset with selected window highlighted.
 * Acts as a minimap/overview of the data range. Supports dragging to move the window.
 *
 * Uses CandlePageManager for efficient event-driven data access.
 */
public class TimelineBar extends JPanel implements DataPageListener<Candle> {

    private static final int BAR_HEIGHT = 48;
    private static final Color CANDLE_UP = new Color(38, 166, 91);
    private static final Color CANDLE_DOWN = new Color(214, 69, 65);
    private static final Color GRID_LINE = new Color(128, 128, 128, 80);

    private static final long TEN_YEARS_MS = 10L * 365 * 24 * 60 * 60 * 1000;

    private final CandlePageManager candlePageMgr;

    private String symbol = "BTCUSDT";
    private String title = "";
    private DataPageView<Candle> dataPage;
    private List<Candle> weeklyCandles;
    private long windowStart;
    private long windowEnd;
    private boolean useLogScale = true;

    // Drag state
    private boolean isDragging = false;
    private boolean isHovering = false;
    private int dragStartX;
    private long dragStartWindowEnd;

    // Cached time range for coordinate conversion
    private long minTime;
    private long maxTime;
    private int padding = 4;
    private int topPadding = 14;  // Extra space for title

    // Callback when anchor date changes
    private Consumer<Long> onAnchorDateChanged;

    // Context menu
    private JPopupMenu contextMenu;
    private JCheckBoxMenuItem logScaleItem;

    // Loading animation
    private boolean isLoading = false;
    private Timer pulseTimer;
    private float pulsePhase = 0f;

    public TimelineBar() {
        this.candlePageMgr = ApplicationContext.getInstance().getCandlePageManager();
        setPreferredSize(new Dimension(0, BAR_HEIGHT));
        setMinimumSize(new Dimension(0, BAR_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, BAR_HEIGHT));
        setOpaque(false);

        createContextMenu();
        setupMouseHandlers();
    }

    public void setOnAnchorDateChanged(Consumer<Long> callback) {
        this.onAnchorDateChanged = callback;
    }

    public void setTitle(String title) {
        this.title = title;
        repaint();
    }

    public void setLoading(boolean loading) {
        this.isLoading = loading;
        if (loading) {
            if (pulseTimer == null) {
                pulseTimer = new Timer(50, e -> {
                    pulsePhase += 0.05f;  // ~1 second cycle (20 steps * 50ms)
                    if (pulsePhase >= 1f) pulsePhase = 0f;
                    repaint();
                });
            }
            pulseTimer.start();
        } else {
            if (pulseTimer != null) {
                pulseTimer.stop();
            }
            repaint();
        }
    }

    private void createContextMenu() {
        contextMenu = new JPopupMenu();

        logScaleItem = new JCheckBoxMenuItem("Log Scale", useLogScale);
        logScaleItem.addActionListener(e -> {
            useLogScale = logScaleItem.isSelected();
            repaint();
        });
        contextMenu.add(logScaleItem);
    }

    private void setupMouseHandlers() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e) && isOverWindow(e.getX())) {
                    isDragging = true;
                    dragStartX = e.getX();
                    dragStartWindowEnd = windowEnd;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }
                if (isDragging) {
                    isDragging = false;
                    setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                isHovering = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isDragging) {
                    isHovering = false;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDragging && weeklyCandles != null && !weeklyCandles.isEmpty()) {
                    int deltaX = e.getX() - dragStartX;
                    long deltaTime = xToTimeDelta(deltaX);

                    long newWindowEnd = dragStartWindowEnd + deltaTime;
                    long windowDuration = windowEnd - windowStart;

                    // Clamp to valid range
                    newWindowEnd = Math.max(minTime + windowDuration, Math.min(maxTime, newWindowEnd));

                    if (newWindowEnd != windowEnd && onAnchorDateChanged != null) {
                        onAnchorDateChanged.accept(newWindowEnd);
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean overWindow = isOverWindow(e.getX());
                setCursor(overWindow ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : Cursor.getDefaultCursor());
            }
        });
    }

    private boolean isOverWindow(int x) {
        if (weeklyCandles == null || weeklyCandles.isEmpty() || minTime >= maxTime) return false;

        int width = getWidth();
        long timeRange = maxTime - minTime;

        double x1 = padding + ((Math.max(windowStart, minTime) - minTime) / (double) timeRange) * (width - padding * 2);
        double x2 = padding + ((Math.min(windowEnd, maxTime) - minTime) / (double) timeRange) * (width - padding * 2);

        return x >= x1 && x <= x2;
    }

    private long xToTimeDelta(int deltaX) {
        if (minTime >= maxTime) return 0;
        int width = getWidth();
        long timeRange = maxTime - minTime;
        return (long) (deltaX / (double) (width - padding * 2) * timeRange);
    }

    private void showContextMenu(MouseEvent e) {
        contextMenu.show(this, e.getX(), e.getY());
    }

    /**
     * Update the timeline with new symbol and window parameters.
     */
    public void update(String symbol, long windowStart, long windowEnd) {
        boolean symbolChanged = !symbol.equals(this.symbol);
        this.symbol = symbol;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;

        if (symbolChanged || dataPage == null) {
            // Release old page if switching symbols
            if (dataPage != null) {
                candlePageMgr.release(dataPage, this);
            }

            // Request new page - returns immediately with cached data, loads in background
            long end = System.currentTimeMillis();
            long start = end - TEN_YEARS_MS;
            dataPage = candlePageMgr.request(symbol, "1w", start, end, this, "TimelineBar");

            // Use whatever data is available immediately
            weeklyCandles = dataPage.getData();
            updateTimeRange();
            repaint();
        } else {
            repaint();
        }
    }

    private void updateTimeRange() {
        minTime = Long.MAX_VALUE;
        maxTime = Long.MIN_VALUE;
        if (weeklyCandles != null) {
            for (Candle c : weeklyCandles) {
                minTime = Math.min(minTime, c.timestamp());
                maxTime = Math.max(maxTime, c.timestamp());
            }
        }
    }

    /**
     * Update just the window selection without refetching candles.
     */
    public void updateWindow(long windowStart, long windowEnd) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int chartHeight = height - topPadding - padding;

        if (weeklyCandles == null || weeklyCandles.isEmpty()) {
            g2.setColor(Color.GRAY);
            g2.setFont(g2.getFont().deriveFont(10f));
            g2.drawString("Loading timeline...", 10, height / 2 + 4);
            g2.dispose();
            return;
        }

        // Find price range
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;

        for (Candle c : weeklyCandles) {
            minPrice = Math.min(minPrice, c.low());
            maxPrice = Math.max(maxPrice, c.high());
        }

        if (minTime >= maxTime || minPrice >= maxPrice) {
            g2.dispose();
            return;
        }

        long timeRange = maxTime - minTime;

        // For log scale, use log of prices
        double logMin = useLogScale ? Math.log(minPrice) : minPrice;
        double logMax = useLogScale ? Math.log(maxPrice) : maxPrice;
        double priceRange = logMax - logMin;

        // Draw candles as vertical lines
        for (Candle c : weeklyCandles) {
            double x = padding + ((c.timestamp() - minTime) / (double) timeRange) * (width - padding * 2);

            double high = useLogScale ? Math.log(c.high()) : c.high();
            double low = useLogScale ? Math.log(c.low()) : c.low();

            double yHigh = topPadding + ((logMax - high) / priceRange) * chartHeight;
            double yLow = topPadding + ((logMax - low) / priceRange) * chartHeight;

            // Use theme accent color
            Color accent = UIManager.getColor("Component.accentColor");
            g2.setColor(accent != null ? accent : new Color(100, 140, 180));
            g2.draw(new Rectangle2D.Double(x, yHigh, 1, Math.max(1, yLow - yHigh)));
        }

        // Draw selected window overlay
        if (windowStart > 0 && windowEnd > 0 && windowEnd > windowStart) {
            // Clamp window to visible range
            long visibleStart = Math.max(windowStart, minTime);
            long visibleEnd = Math.min(windowEnd, maxTime);

            if (visibleEnd > visibleStart) {
                double x1 = padding + ((visibleStart - minTime) / (double) timeRange) * (width - padding * 2);
                double x2 = padding + ((visibleEnd - minTime) / (double) timeRange) * (width - padding * 2);

                // Check theme brightness
                Color bg = UIManager.getColor("Panel.background");
                boolean isDarkTheme = bg == null || (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3 < 128;

                // Fill with 25% white/black depending on theme
                Color fillColor = isDarkTheme
                    ? new Color(255, 255, 255, 64)
                    : new Color(0, 0, 0, 64);
                g2.setColor(fillColor);
                g2.fill(new Rectangle2D.Double(x1, topPadding, x2 - x1, chartHeight));

                // Border around selection (0.5px, 50% opacity)
                Color borderColor = isDarkTheme
                    ? new Color(255, 255, 255, 128)
                    : new Color(0, 0, 0, 128);
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(0.5f));
                g2.draw(new Rectangle2D.Double(x1, topPadding, x2 - x1, chartHeight));

                // Bottom bar (3px)
                int alpha = 128;
                if (isLoading) {
                    // Pulse between 64 and 192 alpha
                    alpha = 64 + (int) (128 * (0.5 + 0.5 * Math.sin(pulsePhase * 2 * Math.PI)));
                }

                Color barColor = isDarkTheme
                    ? new Color(255, 255, 255, alpha)
                    : new Color(0, 0, 0, alpha);
                g2.setColor(barColor);
                g2.fill(new Rectangle2D.Double(x1, height - padding - 3, x2 - x1, 3));
            }
        }

        // Draw year markers
        Font yearFont = g2.getFont().deriveFont(9f);
        g2.setFont(yearFont);
        FontMetrics fm = g2.getFontMetrics();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(minTime);
        cal.set(java.util.Calendar.DAY_OF_YEAR, 1);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.add(java.util.Calendar.YEAR, 1);

        while (cal.getTimeInMillis() < maxTime) {
            long yearStart = cal.getTimeInMillis();
            double x = padding + ((yearStart - minTime) / (double) timeRange) * (width - padding * 2);

            g2.setColor(GRID_LINE);
            g2.drawLine((int) x, topPadding, (int) x, height);

            String yearStr = String.valueOf(cal.get(java.util.Calendar.YEAR));
            int textWidth = fm.stringWidth(yearStr);
            g2.setColor(Color.GRAY);
            g2.drawString(yearStr, (int) x - textWidth - 2, height - 2);

            cal.add(java.util.Calendar.YEAR, 1);
        }

        // Draw title at top (normal font size, centered)
        if (title != null && !title.isEmpty()) {
            Font titleFont = g2.getFont().deriveFont(Font.BOLD).deriveFont(11f);

            g2.setFont(titleFont);
            FontMetrics tfm = g2.getFontMetrics();
            int titleWidth = tfm.stringWidth(title);
            int titleX = (width - titleWidth) / 2;
            // Center vertically in the top padding area
            int titleY = (topPadding + tfm.getAscent() - tfm.getDescent()) / 2;

            // Use theme's window title color
            Color titleColor = UIManager.getColor("TitlePane.foreground");
            if (titleColor == null) {
                titleColor = UIManager.getColor("Label.foreground");
            }
            if (titleColor == null) {
                titleColor = Color.GRAY;
            }
            g2.setColor(titleColor);
            g2.drawString(title, titleX, titleY);
        }

        g2.dispose();
    }

    // ========== DataPageListener Implementation ==========

    @Override
    public void onStateChanged(DataPageView<Candle> page, PageState oldState, PageState newState) {
        // Show loading indicator when loading
        setLoading(newState == PageState.LOADING || newState == PageState.UPDATING);
    }

    @Override
    public void onDataChanged(DataPageView<Candle> page) {
        // Update candles when page data changes (called on EDT)
        weeklyCandles = page.getData();
        updateTimeRange();
        repaint();
    }

    /**
     * Release the page when this component is disposed.
     */
    public void dispose() {
        if (dataPage != null) {
            candlePageMgr.release(dataPage, this);
            dataPage = null;
        }
    }
}
