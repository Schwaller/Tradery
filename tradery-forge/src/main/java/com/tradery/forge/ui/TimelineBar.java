package com.tradery.forge.ui;

import com.tradery.core.model.Candle;
import com.tradery.forge.ApplicationContext;
import com.tradery.data.page.PageState;
import com.tradery.forge.data.page.CandlePageManager;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;

import com.tradery.charts.util.ChartStyles;

import javax.swing.*;
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

    private static final int BAR_HEIGHT = 42;
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
    private int padding = 4;  // left/right padding for candle x-coords
    private int topPadding = 4;
    private int bottomPadding = 4;

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
        setOpaque(true);

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
        int chartHeight = height - topPadding - bottomPadding;

        // Fill with chart plot background
        g2.setColor(ChartStyles.getTheme().getPlotBackgroundColor());
        g2.fillRect(0, 0, width, height);

        if (weeklyCandles == null || weeklyCandles.isEmpty()) {
            // Still draw bottom separator even with no data
            Color sep = UIManager.getColor("Separator.foreground");
            if (sep != null) {
                g2.setColor(sep);
                g2.fillRect(0, height - 1, width, 1);
            }
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

                // Use candle bar color at 1/3 opacity for fill
                Color accent = UIManager.getColor("Component.accentColor");
                if (accent == null) accent = new Color(100, 140, 180);
                int fillAlpha = accent.getAlpha() / 3;

                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), fillAlpha));
                g2.fill(new Rectangle2D.Double(x1, 0, x2 - x1, height));

                // Left and right border lines in full candle color
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(0.5f));
                g2.draw(new java.awt.geom.Line2D.Double(x1, 0, x1, height));
                g2.draw(new java.awt.geom.Line2D.Double(x2, 0, x2, height));
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

            // Gradient from transparent at top to grid color at bottom
            GradientPaint gradient = new GradientPaint(
                0, 0, new Color(GRID_LINE.getRed(), GRID_LINE.getGreen(), GRID_LINE.getBlue(), 0),
                0, height, GRID_LINE
            );
            g2.setPaint(gradient);
            g2.drawLine((int) x, 0, (int) x, height);

            String yearStr = String.valueOf(cal.get(java.util.Calendar.YEAR));
            int textWidth = fm.stringWidth(yearStr);
            g2.setColor(Color.GRAY);
            g2.drawString(yearStr, (int) x - textWidth - 2, height - 2);

            cal.add(java.util.Calendar.YEAR, 1);
        }

        // Bottom separator line (1 logical pixel)
        Color sep = UIManager.getColor("Separator.foreground");
        if (sep != null) {
            g2.setColor(sep);
            g2.fillRect(0, height - 1, width, 1);
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
