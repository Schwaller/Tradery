package com.tradery.ui;

import com.tradery.model.Candle;
import com.tradery.model.Hoop;
import com.tradery.model.HoopMatchResult;
import com.tradery.model.HoopPattern;
import com.tradery.ui.charts.ChartStyles;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import org.jfree.chart.plot.PlotRenderingInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Chart panel for displaying candlestick data with hoop pattern overlays.
 * Supports interactive editing via drag-to-resize.
 */
public class HoopPatternChartPanel extends JPanel {

    private JFreeChart chart;
    private ChartPanel chartPanel;
    private CombinedDomainXYPlot combinedPlot;
    private XYPlot pricePlot;
    private XYPlot volumePlot;
    private HoopPattern pattern;
    private List<Candle> candles = new ArrayList<>();
    private int selectedHoopIndex = -1;
    private int anchorBarIndex = -1;

    // Hoop zone bounds for hit testing
    private final List<HoopZoneBounds> hoopZones = new ArrayList<>();

    // Drag state
    private HoopZoneBounds.Edge currentDrag = HoopZoneBounds.Edge.NONE;
    private int draggingHoopIndex = -1;
    private static final double EDGE_TOLERANCE = 8.0;

    // Pan state
    private Point panStart = null;
    private double panStartDomainMin, panStartDomainMax;
    private double panStartRangeMin, panStartRangeMax;

    // Axis drag state
    private boolean draggingYAxis = false;
    private int yAxisDragStartY;
    private double yAxisDragStartRangeMin, yAxisDragStartRangeMax;

    // Match highlights
    private final List<HoopMatchResult> matches = new ArrayList<>();
    private boolean showMatches = true;

    // Callbacks
    private Runnable onPatternChanged;
    private Runnable onSelectionChanged;

    public HoopPatternChartPanel() {
        setLayout(new BorderLayout());
        initializeChart();
    }

    private void initializeChart() {
        // Shared date axis
        DateAxis dateAxis = new DateAxis();
        dateAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        dateAxis.setAxisLineVisible(false);

        // Price subplot (80% weight) - Y axis on right side
        NumberAxis priceAxis = new NumberAxis();
        priceAxis.setAutoRangeIncludesZero(false);
        priceAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        priceAxis.setAxisLineVisible(false);

        pricePlot = new XYPlot(new TimeSeriesCollection(), null, priceAxis, createPriceRenderer());
        pricePlot.setRangeAxisLocation(org.jfree.chart.axis.AxisLocation.TOP_OR_RIGHT);
        pricePlot.setBackgroundPaint(ChartStyles.PLOT_BACKGROUND_COLOR);
        pricePlot.setDomainGridlinePaint(ChartStyles.GRIDLINE_COLOR);
        pricePlot.setRangeGridlinePaint(ChartStyles.GRIDLINE_COLOR);
        pricePlot.setOutlineVisible(false);

        // Volume subplot (20% weight)
        NumberAxis volumeAxis = new NumberAxis();
        volumeAxis.setAutoRangeIncludesZero(true);
        volumeAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        volumeAxis.setAxisLineVisible(false);
        volumeAxis.setTickLabelsVisible(false);

        volumePlot = new XYPlot(new TimeSeriesCollection(), null, volumeAxis, createVolumeRenderer());
        volumePlot.setBackgroundPaint(ChartStyles.PLOT_BACKGROUND_COLOR);
        volumePlot.setDomainGridlinePaint(ChartStyles.GRIDLINE_COLOR);
        volumePlot.setRangeGridlinePaint(ChartStyles.GRIDLINE_COLOR);
        volumePlot.setOutlineVisible(false);

        // Combined plot: price (80%) + volume (20%)
        combinedPlot = new CombinedDomainXYPlot(dateAxis);
        combinedPlot.setGap(0);
        combinedPlot.add(pricePlot, 4);   // 80%
        combinedPlot.add(volumePlot, 1);  // 20%
        combinedPlot.setBackgroundPaint(ChartStyles.BACKGROUND_COLOR);
        combinedPlot.setOutlineVisible(false);

        // Create chart
        chart = new JFreeChart(null, null, combinedPlot, false);
        chart.setBackgroundPaint(ChartStyles.BACKGROUND_COLOR);

        // Create chart panel
        chartPanel = new ChartPanel(chart);

        // Disable built-in zoom/pan - we handle it ourselves
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);

        // Add mouse listeners for hoop interaction, panning, and zooming
        chartPanel.addMouseListener(createMouseListener());
        chartPanel.addMouseMotionListener(createMouseMotionListener());
        chartPanel.addMouseWheelListener(createMouseWheelListener());

        add(chartPanel, BorderLayout.CENTER);
    }

    private XYLineAndShapeRenderer createPriceRenderer() {
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        // White, thin line with rounded joins
        renderer.setSeriesPaint(0, Color.WHITE);
        renderer.setSeriesStroke(0, new BasicStroke(
            1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        ));
        return renderer;
    }

    private XYAreaRenderer createVolumeRenderer() {
        XYAreaRenderer renderer = new XYAreaRenderer(XYAreaRenderer.AREA);
        renderer.setSeriesPaint(0, new Color(100, 100, 100, 150));
        renderer.setOutline(false);
        return renderer;
    }

    public void setCandles(List<Candle> candles) {
        this.candles = candles != null ? new ArrayList<>(candles) : new ArrayList<>();
        updateChart();
    }

    public void setPattern(HoopPattern pattern) {
        this.pattern = pattern;
        this.selectedHoopIndex = -1;
        renderHoopOverlays();
    }

    public void setAnchorBar(int barIndex) {
        if (barIndex >= 0 && barIndex < candles.size()) {
            this.anchorBarIndex = barIndex;
            renderHoopOverlays();
        }
    }

    public int getAnchorBar() {
        return anchorBarIndex;
    }

    public void setSelectedHoop(int index) {
        this.selectedHoopIndex = index;
        renderHoopOverlays();
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }

    public int getSelectedHoop() {
        return selectedHoopIndex;
    }

    public void setMatches(List<HoopMatchResult> matchResults) {
        this.matches.clear();
        if (matchResults != null) {
            this.matches.addAll(matchResults);
        }
        renderHoopOverlays();
    }

    public void setShowMatches(boolean show) {
        this.showMatches = show;
        renderHoopOverlays();
    }

    public void setOnPatternChanged(Runnable callback) {
        this.onPatternChanged = callback;
    }

    public void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback;
    }

    private void updateChart() {
        if (candles.isEmpty()) {
            pricePlot.setDataset(new TimeSeriesCollection());
            volumePlot.setDataset(new TimeSeriesCollection());
            return;
        }

        // Price series (close prices)
        TimeSeries priceSeries = new TimeSeries("Price");
        TimeSeries volumeSeries = new TimeSeries("Volume");

        for (Candle c : candles) {
            FixedMillisecond time = new FixedMillisecond(c.timestamp());
            priceSeries.addOrUpdate(time, c.close());
            volumeSeries.addOrUpdate(time, c.volume());
        }

        pricePlot.setDataset(new TimeSeriesCollection(priceSeries));
        volumePlot.setDataset(new TimeSeriesCollection(volumeSeries));

        renderHoopOverlays();
    }

    private void renderHoopOverlays() {
        clearHoopAnnotations(pricePlot);
        hoopZones.clear();

        // Draw anchor marker if set
        if (anchorBarIndex >= 0 && anchorBarIndex < candles.size()) {
            drawAnchorMarker(pricePlot);
        }

        // Draw hoop zones
        if (pattern != null && pattern.hasHoops() && !candles.isEmpty() && anchorBarIndex >= 0) {
            double anchorPrice = candles.get(anchorBarIndex).close();
            int currentBar = anchorBarIndex;

            for (int i = 0; i < pattern.getHoops().size(); i++) {
                Hoop hoop = pattern.getHoops().get(i);

                // Calculate time window
                int windowStart = currentBar + hoop.getWindowStart(0);
                int windowEnd = currentBar + hoop.getWindowEnd(0);

                if (windowStart >= candles.size()) break;
                windowEnd = Math.min(windowEnd, candles.size() - 1);

                // Calculate price bounds
                double minPrice = hoop.getMinAbsolutePrice(anchorPrice);
                double maxPrice = hoop.getMaxAbsolutePrice(anchorPrice);

                // Get timestamps
                long startTime = candles.get(windowStart).timestamp();
                long endTime = candles.get(windowEnd).timestamp();

                // Store bounds for hit testing
                HoopZoneBounds bounds = new HoopZoneBounds(
                    i, windowStart, windowEnd, minPrice, maxPrice, startTime, endTime
                );
                hoopZones.add(bounds);

                // Draw zone
                drawHoopZone(pricePlot, i, startTime, endTime, minPrice, maxPrice, hoop.name());

                // Update anchor for next hoop (use midpoint for preview)
                anchorPrice = (minPrice + maxPrice) / 2.0;
                currentBar = (windowStart + windowEnd) / 2;
            }
        }

        // Draw match highlights
        if (showMatches && !matches.isEmpty()) {
            drawMatchHighlights(pricePlot);
        }

        chart.fireChartChanged();
    }

    private void drawAnchorMarker(XYPlot plot) {
        Candle anchor = candles.get(anchorBarIndex);
        long timestamp = anchor.timestamp();
        double price = anchor.close();

        // Vertical line at anchor
        plot.addAnnotation(new XYLineAnnotation(
            timestamp, plot.getRangeAxis().getLowerBound(),
            timestamp, plot.getRangeAxis().getUpperBound(),
            new BasicStroke(2.0f), ChartStyles.HOOP_ANCHOR_COLOR
        ));

        // Label
        XYTextAnnotation label = new XYTextAnnotation("ANCHOR", timestamp, price * 1.01);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        label.setPaint(ChartStyles.HOOP_ANCHOR_COLOR);
        plot.addAnnotation(label);
    }

    private void drawHoopZone(XYPlot plot, int hoopIndex,
                              long startTime, long endTime,
                              double minPrice, double maxPrice, String name) {

        Color baseColor = ChartStyles.HOOP_COLORS[hoopIndex % ChartStyles.HOOP_COLORS.length];
        Color fillColor = new Color(baseColor.getRed(), baseColor.getGreen(),
                                     baseColor.getBlue(), 40);

        boolean selected = hoopIndex == selectedHoopIndex;

        HoopZoneAnnotation annotation = new HoopZoneAnnotation(
            startTime, endTime, minPrice, maxPrice,
            fillColor, baseColor, selected, hoopIndex, name
        );
        plot.addAnnotation(annotation);

        // Update screen bounds in hoopZones after drawing
        if (hoopIndex < hoopZones.size()) {
            // Screen bounds will be set during annotation draw
        }
    }

    private void drawMatchHighlights(XYPlot plot) {
        for (HoopMatchResult match : matches) {
            int[] hitBars = match.hoopHitBars();
            double[] hitPrices = match.hoopHitPrices();

            if (hitBars == null || hitBars.length == 0) continue;

            // Draw anchor point (pattern start)
            if (match.anchorBar() >= 0 && match.anchorBar() < candles.size()) {
                long anchorTime = candles.get(match.anchorBar()).timestamp();
                double anchorPrice = match.anchorPrice();

                // Anchor dot (larger, hollow)
                plot.addAnnotation(new org.jfree.chart.annotations.XYShapeAnnotation(
                    new java.awt.geom.Ellipse2D.Double(anchorTime - 2000000, anchorPrice - 0.5, 4000000, 1),
                    new BasicStroke(2.0f), ChartStyles.HOOP_MATCH_COLOR
                ));
            }

            // Draw path connecting hoop hits
            double prevTime = match.anchorBar() >= 0 && match.anchorBar() < candles.size()
                ? candles.get(match.anchorBar()).timestamp() : 0;
            double prevPrice = match.anchorPrice();

            for (int i = 0; i < hitBars.length; i++) {
                int bar = hitBars[i];
                if (bar < 0 || bar >= candles.size()) continue;

                long hitTime = candles.get(bar).timestamp();
                double hitPrice = hitPrices[i];

                // Line from previous hit to this hit
                if (prevTime > 0) {
                    plot.addAnnotation(new XYLineAnnotation(
                        prevTime, prevPrice, hitTime, hitPrice,
                        new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND),
                        ChartStyles.HOOP_MATCH_COLOR
                    ));
                }

                // Dot at this hit point
                double dotRadius = (plot.getRangeAxis().getUpperBound() - plot.getRangeAxis().getLowerBound()) * 0.01;
                long timeRadius = (long) ((combinedPlot.getDomainAxis().getUpperBound() - combinedPlot.getDomainAxis().getLowerBound()) * 0.005);

                plot.addAnnotation(new org.jfree.chart.annotations.XYShapeAnnotation(
                    new java.awt.geom.Ellipse2D.Double(
                        hitTime - timeRadius, hitPrice - dotRadius,
                        timeRadius * 2, dotRadius * 2
                    ),
                    new BasicStroke(1.0f), ChartStyles.HOOP_MATCH_COLOR, ChartStyles.HOOP_MATCH_COLOR
                ));

                prevTime = hitTime;
                prevPrice = hitPrice;
            }

            // Draw checkmark at completion
            if (match.completionBar() >= 0 && match.completionBar() < candles.size()) {
                long completeTime = candles.get(match.completionBar()).timestamp();
                double completePrice = hitPrices[hitPrices.length - 1];

                XYTextAnnotation label = new XYTextAnnotation("✓", completeTime, completePrice * 1.01);
                label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                label.setPaint(ChartStyles.HOOP_MATCH_COLOR);
                plot.addAnnotation(label);
            }
        }
    }

    private void clearHoopAnnotations(XYPlot plot) {
        // Remove all annotations except title
        plot.getAnnotations().stream()
            .filter(a -> !(a instanceof org.jfree.chart.annotations.XYTitleAnnotation))
            .toList()
            .forEach(plot::removeAnnotation);
    }

    // ===== Mouse Interaction =====

    private MouseAdapter createMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Handle right-click context menu
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }

                Point screenPoint = e.getPoint();

                // Check if clicking on Y axis area (for scaling)
                if (isOnYAxis(screenPoint)) {
                    startYAxisDrag(screenPoint.y);
                    return;
                }

                // Check if near any hoop edge (in price area) - for dragging
                for (HoopZoneBounds zone : hoopZones) {
                    updateZoneScreenBounds(zone);
                    HoopZoneBounds.Edge edge = zone.getNearEdge(screenPoint, EDGE_TOLERANCE);
                    if (edge != HoopZoneBounds.Edge.NONE) {
                        currentDrag = edge;
                        draggingHoopIndex = zone.getHoopIndex();
                        setSelectedHoop(draggingHoopIndex);
                        return;
                    }
                }

                // Check if inside a hoop zone - for selection (but also allow pan)
                for (HoopZoneBounds zone : hoopZones) {
                    if (zone.contains(screenPoint)) {
                        setSelectedHoop(zone.getHoopIndex());
                        // Don't return - allow panning even when clicking on hoop
                        break;
                    }
                }

                // Start panning
                startPan(screenPoint);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // Handle right-click context menu (for platforms that trigger on release)
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }

                if (currentDrag != HoopZoneBounds.Edge.NONE) {
                    if (onPatternChanged != null) {
                        onPatternChanged.run();
                    }
                }
                currentDrag = HoopZoneBounds.Edge.NONE;
                draggingHoopIndex = -1;
                panStart = null;
                draggingYAxis = false;
            }
        };
    }

    private boolean isOnYAxis(Point point) {
        Rectangle2D dataArea = getPriceSubplotDataArea();
        if (dataArea == null) return false;
        // Y axis is to the right of the data area
        return point.x > dataArea.getMaxX() && point.x < dataArea.getMaxX() + 60;
    }

    private void startYAxisDrag(int y) {
        draggingYAxis = true;
        yAxisDragStartY = y;
        yAxisDragStartRangeMin = pricePlot.getRangeAxis().getLowerBound();
        yAxisDragStartRangeMax = pricePlot.getRangeAxis().getUpperBound();
    }

    private void startPan(Point point) {
        panStart = point;
        // Store current axis ranges
        panStartDomainMin = combinedPlot.getDomainAxis().getLowerBound();
        panStartDomainMax = combinedPlot.getDomainAxis().getUpperBound();
        panStartRangeMin = pricePlot.getRangeAxis().getLowerBound();
        panStartRangeMax = pricePlot.getRangeAxis().getUpperBound();
    }

    private void showContextMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();

        // Get bar index at click position
        int barIndex = findBarFromScreenX(e.getX());

        if (barIndex >= 0 && barIndex < candles.size()) {
            Candle candle = candles.get(barIndex);
            String priceStr = String.format("%.2f", candle.close());

            JMenuItem setAnchorItem = new JMenuItem("Set Anchor Here (" + priceStr + ")");
            setAnchorItem.addActionListener(evt -> setAnchorBar(barIndex));
            menu.add(setAnchorItem);

            if (anchorBarIndex >= 0) {
                JMenuItem clearAnchorItem = new JMenuItem("Clear Anchor");
                clearAnchorItem.addActionListener(evt -> {
                    anchorBarIndex = -1;
                    renderHoopOverlays();
                });
                menu.add(clearAnchorItem);
            }

            menu.addSeparator();
        }

        // Fit options
        JMenuItem fitAllItem = new JMenuItem("Fit All");
        fitAllItem.addActionListener(evt -> fitAll());
        menu.add(fitAllItem);

        JMenuItem fitXItem = new JMenuItem("Fit X Axis");
        fitXItem.addActionListener(evt -> fitXAxis());
        menu.add(fitXItem);

        JMenuItem fitYItem = new JMenuItem("Fit Y Axis");
        fitYItem.addActionListener(evt -> fitYAxis());
        menu.add(fitYItem);

        menu.show(chartPanel, e.getX(), e.getY());
    }

    private void fitAll() {
        fitXAxis();
        fitYAxis();
    }

    private void fitXAxis() {
        if (candles.isEmpty()) return;
        long minTime = candles.get(0).timestamp();
        long maxTime = candles.get(candles.size() - 1).timestamp();
        // Add some padding
        long padding = (maxTime - minTime) / 20;
        combinedPlot.getDomainAxis().setRange(minTime - padding, maxTime + padding);
    }

    private void fitYAxis() {
        if (candles.isEmpty()) return;
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        for (Candle c : candles) {
            minPrice = Math.min(minPrice, c.low());
            maxPrice = Math.max(maxPrice, c.high());
        }
        // Add some padding
        double padding = (maxPrice - minPrice) * 0.05;
        pricePlot.getRangeAxis().setRange(minPrice - padding, maxPrice + padding);
    }

    /**
     * Fallback method to find nearest bar from screen X coordinate.
     * Used when click is outside the price subplot area.
     */
    private int findBarFromScreenX(int screenX) {
        Rectangle2D dataArea = getPriceSubplotDataArea();
        if (dataArea == null) {
            // If no rendering info yet, use chart panel bounds
            dataArea = chartPanel.getBounds();
            if (dataArea == null || dataArea.getWidth() == 0) return -1;
        }

        // Convert screen X to domain value
        double timestamp = combinedPlot.getDomainAxis().java2DToValue(
            screenX, dataArea, combinedPlot.getDomainAxisEdge()
        );

        return findNearestBar(timestamp);
    }

    /**
     * Get price value from screen Y coordinate.
     * Used as fallback when dragging price bounds outside the price area.
     */
    private Double getPriceFromScreenY(int screenY) {
        Rectangle2D dataArea = getPriceSubplotDataArea();
        if (dataArea == null) return null;

        return pricePlot.getRangeAxis().java2DToValue(
            screenY, dataArea, pricePlot.getRangeAxisEdge()
        );
    }

    private MouseMotionAdapter createMouseMotionListener() {
        return new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Point screenPoint = e.getPoint();

                // Handle Y-axis scaling
                if (draggingYAxis) {
                    handleYAxisDrag(screenPoint.y);
                    return;
                }

                // Handle hoop edge dragging
                if (currentDrag != HoopZoneBounds.Edge.NONE && draggingHoopIndex >= 0) {
                    handleHoopDrag(screenPoint);
                    return;
                }

                // Handle panning
                if (panStart != null) {
                    handlePan(screenPoint);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Point screenPoint = e.getPoint();

                // Check if on Y axis - show vertical resize cursor
                if (isOnYAxis(screenPoint)) {
                    chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                    return;
                }

                // Update cursor based on hoop edge proximity
                for (HoopZoneBounds zone : hoopZones) {
                    updateZoneScreenBounds(zone);
                    HoopZoneBounds.Edge edge = zone.getNearEdge(screenPoint, EDGE_TOLERANCE);
                    switch (edge) {
                        case TOP, BOTTOM -> {
                            chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                            return;
                        }
                        case LEFT, RIGHT -> {
                            chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                            return;
                        }
                    }
                }
                chartPanel.setCursor(Cursor.getDefaultCursor());
            }
        };
    }

    private MouseWheelListener createMouseWheelListener() {
        return e -> {
            // Zoom centered on mouse position
            Point point = e.getPoint();
            Rectangle2D dataArea = getPriceSubplotDataArea();
            if (dataArea == null) return;

            // Calculate zoom factor (scroll up = zoom in, scroll down = zoom out)
            double zoomFactor = e.getWheelRotation() < 0 ? 0.9 : 1.1;

            // Get current mouse position in data coordinates
            double mouseX = combinedPlot.getDomainAxis().java2DToValue(
                point.getX(), dataArea, combinedPlot.getDomainAxisEdge()
            );

            // Zoom domain axis centered on mouse X
            double domainMin = combinedPlot.getDomainAxis().getLowerBound();
            double domainMax = combinedPlot.getDomainAxis().getUpperBound();
            double domainRange = domainMax - domainMin;
            double newRange = domainRange * zoomFactor;

            // Keep mouse position at same screen location
            double mouseRatio = (mouseX - domainMin) / domainRange;
            double newMin = mouseX - mouseRatio * newRange;
            double newMax = newMin + newRange;

            combinedPlot.getDomainAxis().setRange(newMin, newMax);
        };
    }

    private void handleHoopDrag(Point screenPoint) {
        if (pattern == null || draggingHoopIndex >= pattern.getHoops().size()) return;

        // Try to translate to data space, with fallbacks
        Point2D dataPoint = translateToDataSpace(screenPoint);
        Double dragY = dataPoint != null ? dataPoint.getY() : getPriceFromScreenY(screenPoint.y);
        int targetBar = dataPoint != null
            ? findNearestBar(dataPoint.getX())
            : findBarFromScreenX(screenPoint.x);

        Hoop hoop = pattern.getHoops().get(draggingHoopIndex);
        double anchorPrice = getAnchorPriceForHoop(draggingHoopIndex);

        Hoop newHoop = switch (currentDrag) {
            case TOP -> {
                if (dragY == null || anchorPrice == 0) yield hoop;
                double newMaxPercent = ((dragY / anchorPrice) - 1.0) * 100.0;
                yield new Hoop(hoop.name(), hoop.minPricePercent(), newMaxPercent,
                    hoop.distance(), hoop.tolerance(), hoop.anchorMode());
            }
            case BOTTOM -> {
                if (dragY == null || anchorPrice == 0) yield hoop;
                double newMinPercent = ((dragY / anchorPrice) - 1.0) * 100.0;
                yield new Hoop(hoop.name(), newMinPercent, hoop.maxPricePercent(),
                    hoop.distance(), hoop.tolerance(), hoop.anchorMode());
            }
            case LEFT, RIGHT -> {
                if (targetBar < 0) yield hoop;
                int prevBar = draggingHoopIndex == 0 ? anchorBarIndex :
                    hoopZones.get(draggingHoopIndex - 1).getEndBar();
                int delta = targetBar - prevBar;

                if (currentDrag == HoopZoneBounds.Edge.LEFT) {
                    // Adjust distance - tolerance (window start)
                    int newDistance = delta + hoop.tolerance();
                    yield new Hoop(hoop.name(), hoop.minPricePercent(), hoop.maxPricePercent(),
                        Math.max(1, newDistance), hoop.tolerance(), hoop.anchorMode());
                } else {
                    // Adjust distance + tolerance (window end)
                    int newTolerance = delta - hoop.distance();
                    yield new Hoop(hoop.name(), hoop.minPricePercent(), hoop.maxPricePercent(),
                        hoop.distance(), Math.max(0, newTolerance), hoop.anchorMode());
                }
            }
            default -> hoop;
        };

        // Update pattern
        List<Hoop> hoops = new ArrayList<>(pattern.getHoops());
        hoops.set(draggingHoopIndex, newHoop);
        pattern.setHoops(hoops);

        renderHoopOverlays();
    }

    private void handlePan(Point currentPoint) {
        Rectangle2D dataArea = getPriceSubplotDataArea();
        if (dataArea == null) return;

        // Calculate pixel delta
        int dx = currentPoint.x - panStart.x;
        int dy = currentPoint.y - panStart.y;

        // Convert pixel delta to data delta
        double domainRange = panStartDomainMax - panStartDomainMin;
        double rangeRange = panStartRangeMax - panStartRangeMin;

        double domainDelta = -dx * domainRange / dataArea.getWidth();
        double rangeDelta = dy * rangeRange / dataArea.getHeight();

        // Apply new bounds
        combinedPlot.getDomainAxis().setRange(
            panStartDomainMin + domainDelta,
            panStartDomainMax + domainDelta
        );
        pricePlot.getRangeAxis().setRange(
            panStartRangeMin + rangeDelta,
            panStartRangeMax + rangeDelta
        );
    }

    private void handleYAxisDrag(int currentY) {
        // Scale Y axis based on drag distance
        // Dragging up = zoom in (smaller range), dragging down = zoom out (larger range)
        int dy = currentY - yAxisDragStartY;
        double scaleFactor = Math.pow(1.01, dy);  // 1% per pixel

        double originalRange = yAxisDragStartRangeMax - yAxisDragStartRangeMin;
        double originalCenter = (yAxisDragStartRangeMax + yAxisDragStartRangeMin) / 2.0;

        double newRange = originalRange * scaleFactor;
        double newMin = originalCenter - newRange / 2.0;
        double newMax = originalCenter + newRange / 2.0;

        pricePlot.getRangeAxis().setRange(newMin, newMax);
    }

    private Point2D translateToDataSpace(Point point) {
        // Get data area for price subplot from combined plot
        Rectangle2D dataArea = getPriceSubplotDataArea();
        if (dataArea == null || !dataArea.contains(point)) return null;

        double x = combinedPlot.getDomainAxis().java2DToValue(
            point.getX(), dataArea, combinedPlot.getDomainAxisEdge()
        );
        double y = pricePlot.getRangeAxis().java2DToValue(
            point.getY(), dataArea, pricePlot.getRangeAxisEdge()
        );

        return new Point2D.Double(x, y);
    }

    /**
     * Gets the data area for the price subplot (first subplot in combined plot).
     */
    private Rectangle2D getPriceSubplotDataArea() {
        if (chartPanel.getChartRenderingInfo() == null) return null;
        PlotRenderingInfo plotInfo = chartPanel.getChartRenderingInfo().getPlotInfo();
        if (plotInfo == null || plotInfo.getSubplotCount() == 0) return null;
        // Price plot is at index 0 in the combined plot
        return plotInfo.getSubplotInfo(0).getDataArea();
    }

    private void updateZoneScreenBounds(HoopZoneBounds zone) {
        // Get data area for price subplot from combined plot
        Rectangle2D dataArea = getPriceSubplotDataArea();
        if (dataArea == null) return;

        double x1 = combinedPlot.getDomainAxis().valueToJava2D(
            zone.getStartTime(), dataArea, combinedPlot.getDomainAxisEdge()
        );
        double x2 = combinedPlot.getDomainAxis().valueToJava2D(
            zone.getEndTime(), dataArea, combinedPlot.getDomainAxisEdge()
        );
        double y1 = pricePlot.getRangeAxis().valueToJava2D(
            zone.getMaxPrice(), dataArea, pricePlot.getRangeAxisEdge()
        );
        double y2 = pricePlot.getRangeAxis().valueToJava2D(
            zone.getMinPrice(), dataArea, pricePlot.getRangeAxisEdge()
        );

        zone.setScreenBounds(new Rectangle2D.Double(
            Math.min(x1, x2), Math.min(y1, y2),
            Math.abs(x2 - x1), Math.abs(y2 - y1)
        ));
    }

    private int findNearestBar(double timestamp) {
        if (candles.isEmpty()) return -1;

        int nearest = 0;
        double minDist = Double.MAX_VALUE;

        for (int i = 0; i < candles.size(); i++) {
            double dist = Math.abs(candles.get(i).timestamp() - timestamp);
            if (dist < minDist) {
                minDist = dist;
                nearest = i;
            }
        }

        return nearest;
    }

    private double getAnchorPriceForHoop(int hoopIndex) {
        if (candles.isEmpty() || anchorBarIndex < 0) return 0;

        double anchorPrice = candles.get(anchorBarIndex).close();

        // Walk through hoops to find cumulative anchor
        for (int i = 0; i < hoopIndex && i < pattern.getHoops().size(); i++) {
            Hoop h = pattern.getHoops().get(i);
            double minPrice = h.getMinAbsolutePrice(anchorPrice);
            double maxPrice = h.getMaxAbsolutePrice(anchorPrice);
            anchorPrice = (minPrice + maxPrice) / 2.0;
        }

        return anchorPrice;
    }
}
